package com.tss.tssmanager_backend.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.tss.tssmanager_backend.entity.EmailRecord;
import com.tss.tssmanager_backend.repository.EmailRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;

@Service
public class EmailService {

    private final Resend resendClient;
    private final String fromEmail;
    private final EmailRecordRepository emailRecordRepository;

    public EmailService(@Value("${resend.api.key}") String resendApiKey,
                        @Value("${resend.email.from}") String fromEmail,
                        EmailRecordRepository emailRecordRepository) {
        this.resendClient = new Resend(resendApiKey);
        this.fromEmail = fromEmail;
        this.emailRecordRepository = emailRecordRepository;
    }

    public EmailRecord enviarCorreo(String destinatario, String asunto, String cuerpo, List<String> rutasArchivosAdjuntos, Integer tratoId) {
        boolean exito = false;
        String resendEmailId = null;

        try {
            CreateEmailOptions.Builder emailBuilder = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(destinatario)
                    .subject(asunto)
                    .html(cuerpo);

            // Manejo de adjuntos
            List<com.resend.services.emails.model.Attachment> attachments = new ArrayList<>();
            if (rutasArchivosAdjuntos != null && !rutasArchivosAdjuntos.isEmpty()) {
                for (String ruta : rutasArchivosAdjuntos) {
                    try {
                        byte[] fileContent;
                        String fileName;

                        if (isUrl(ruta)) {
                            // Es una URL de Cloudinary
                            fileContent = downloadFileFromUrl(ruta);
                            fileName = extractFileNameFromUrl(ruta);
                        } else {
                            // Es una ruta local
                            Path filePath = Path.of(ruta);
                            fileContent = Files.readAllBytes(filePath);
                            fileName = filePath.getFileName().toString();
                        }

                        String base64Content = Base64.getEncoder().encodeToString(fileContent);

                        attachments.add(com.resend.services.emails.model.Attachment.builder()
                                .fileName(fileName)
                                .content(base64Content)
                                .build());

                    } catch (IOException e) {
                        System.err.println("Error procesando archivo adjunto " + ruta + ": " + e.getMessage());
                    }
                }
            }
            emailBuilder.attachments(attachments);

            CreateEmailResponse response = resendClient.emails().send(emailBuilder.build());

            exito = response.getId() != null;
            resendEmailId = response.getId();
            System.out.println("Correo enviado con éxito. ID de Resend: " + resendEmailId);

        } catch (ResendException e) {
            System.err.println("Error enviando correo con Resend: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error inesperado al enviar correo: " + e.getMessage());
            e.printStackTrace();
        }

        EmailRecord emailRecord = new EmailRecord();
        emailRecord.setDestinatario(destinatario);
        emailRecord.setAsunto(asunto);
        emailRecord.setCuerpo(cuerpo);
        emailRecord.setArchivosAdjuntos(rutasArchivosAdjuntos != null ? String.join(",", rutasArchivosAdjuntos) : null);
        emailRecord.setFechaEnvio(LocalDateTime.now());
        emailRecord.setTratoId(tratoId);
        emailRecord.setExito(exito);
        emailRecord.setResendEmailId(resendEmailId);

        return emailRecordRepository.save(emailRecord);
    }

    private boolean isUrl(String path) {
        return path.startsWith("http://") || path.startsWith("https://");
    }

    private byte[] downloadFileFromUrl(String url) throws IOException {
        URL fileUrl = new URL(url);
        URLConnection connection = fileUrl.openConnection();

        try (InputStream inputStream = connection.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    private String extractFileNameFromUrl(String url) {
        try {
            // Primero intentar detectar la extensión desde el Content-Type
            String detectedExtension = detectExtensionFromUrl(url);

            String[] parts = url.split("/");
            String fileName = "archivo_adjunto";

            if (url.contains("cloudinary.com")) {
                // Para URLs de Cloudinary, buscar el nombre del archivo en la URL
                for (int i = 0; i < parts.length; i++) {
                    String part = parts[i];
                    // Buscar la parte que contiene el nombre del archivo (después de /upload/)
                    if (part.equals("upload") && i + 1 < parts.length) {
                        // Siguiente parte después de upload contiene versión y transformaciones
                        for (int j = i + 1; j < parts.length; j++) {
                            String candidate = parts[j];
                            // Limpiar parámetros de query
                            if (candidate.contains("?")) {
                                candidate = candidate.split("\\?")[0];
                            }
                            // Si contiene punto y no es una transformación, es probable que sea el nombre
                            if (candidate.contains(".") &&
                                    !candidate.startsWith("v") && // No es versión
                                    !candidate.equals("upload") &&
                                    !candidate.equals("image") &&
                                    !candidate.equals("raw") &&
                                    !candidate.equals("video") &&
                                    !candidate.equals("auto")) {
                                fileName = candidate;
                                break;
                            }
                        }
                        break;
                    }
                }
            } else {
                // Para URLs regulares, tomar la última parte
                String lastPart = parts[parts.length - 1];
                if (lastPart.contains("?")) {
                    lastPart = lastPart.split("\\?")[0];
                }
                if (lastPart.contains(".") && isValidFileName(lastPart)) {
                    fileName = lastPart;
                }
            }

            // Validar que el nombre de archivo tenga una extensión válida
            if (!fileName.contains(".") || !isValidFileExtension(fileName)) {
                // Si no tiene extensión válida, usar la detectada desde Content-Type
                if (detectedExtension != null) {
                    // Limpiar el nombre base (sin extensión incorrecta)
                    String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
                    fileName = baseName + "." + detectedExtension;
                } else {
                    // Fallback a PDF
                    String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
                    fileName = baseName + ".pdf";
                }
            }

            return fileName;

        } catch (Exception e) {
            System.err.println("Error extrayendo nombre de archivo de URL: " + e.getMessage());
            return "archivo_adjunto.pdf";
        }
    }

    private boolean isValidFileName(String fileName) {
        return !fileName.endsWith(".com") &&
                !fileName.endsWith(".org") &&
                !fileName.endsWith(".net") &&
                !fileName.endsWith(".edu") &&
                !fileName.equals("cloudinary.com");
    }

    private boolean isValidFileExtension(String fileName) {
        if (!fileName.contains(".")) {
            return false;
        }

        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();

        // Lista de extensiones válidas para archivos adjuntos
        String[] validExtensions = {
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                "txt", "rtf", "odt", "ods", "odp",
                "jpg", "jpeg", "png", "gif", "bmp", "tiff", "svg",
                "zip", "rar", "7z", "tar", "gz",
                "mp3", "wav", "mp4", "avi", "mov", "wmv",
                "csv", "json", "xml", "html", "css", "js"
        };

        for (String validExt : validExtensions) {
            if (extension.equals(validExt)) {
                return true;
            }
        }

        return false;
    }

    private String detectExtensionFromUrl(String url) {
        try {
            URL fileUrl = new URL(url);
            URLConnection connection = fileUrl.openConnection();
            connection.setConnectTimeout(5000); // 5 segundos timeout
            connection.setReadTimeout(5000);
            String contentType = connection.getContentType();

            if (contentType != null) {
                // Limpiar el content-type (remover charset, etc.)
                contentType = contentType.split(";")[0].trim().toLowerCase();

                switch (contentType) {
                    case "application/pdf":
                        return "pdf";
                    case "application/msword":
                        return "doc";
                    case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                        return "docx";
                    case "application/vnd.ms-excel":
                        return "xls";
                    case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
                        return "xlsx";
                    case "application/vnd.ms-powerpoint":
                        return "ppt";
                    case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
                        return "pptx";
                    case "image/jpeg":
                        return "jpg";
                    case "image/png":
                        return "png";
                    case "image/gif":
                        return "gif";
                    case "image/bmp":
                        return "bmp";
                    case "image/tiff":
                        return "tiff";
                    case "image/svg+xml":
                        return "svg";
                    case "text/plain":
                        return "txt";
                    case "text/csv":
                        return "csv";
                    case "application/json":
                        return "json";
                    case "application/xml":
                    case "text/xml":
                        return "xml";
                    case "application/zip":
                        return "zip";
                    case "application/x-rar-compressed":
                        return "rar";
                    case "audio/mpeg":
                        return "mp3";
                    case "audio/wav":
                        return "wav";
                    case "video/mp4":
                        return "mp4";
                    case "video/avi":
                        return "avi";
                    default:
                        return null;
                }
            }
        } catch (Exception e) {
            System.err.println("Error detectando extensión desde Content-Type: " + e.getMessage());
        }
        return null;
    }

    public List<EmailRecord> obtenerCorreosPorTratoId(Integer tratoId) {
        return emailRecordRepository.findByTratoId(tratoId);
    }
}