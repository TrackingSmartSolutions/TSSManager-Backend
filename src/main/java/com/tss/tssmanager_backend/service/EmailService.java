package com.tss.tssmanager_backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.tss.tssmanager_backend.entity.EmailRecord;
import com.tss.tssmanager_backend.repository.EmailRecordRepository;
import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Service
public class EmailService {

    @Autowired
    private Cloudinary cloudinary;

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
            String[] emailArray;
            if (destinatario.contains(",")) {
                emailArray = destinatario.split(",");
                for (int i = 0; i < emailArray.length; i++) {
                    emailArray[i] = emailArray[i].trim();
                }
            } else {
                emailArray = new String[]{destinatario.trim()};
            }

            CreateEmailOptions.Builder emailBuilder = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(emailArray)
                    .subject(asunto)
                    .html(procesarImagenesEmbebidas(cuerpo));

            // Manejo de adjuntos
            List<com.resend.services.emails.model.Attachment> attachments = new ArrayList<>();
            if (rutasArchivosAdjuntos != null && !rutasArchivosAdjuntos.isEmpty()) {

                for (String ruta : rutasArchivosAdjuntos) {
                    try {
                        byte[] fileContent = null;
                        String fileName;

                        if (isUrl(ruta)) {
                            fileContent = downloadFileFromUrl(ruta);
                            fileName = extractFileNameFromUrl(ruta);
                        } else {
                            Path filePath = Path.of(ruta);
                            long fileSize = Files.size(filePath);
                            if (fileSize > 5 * 1024 * 1024L) {
                                System.err.println("Archivo omitido por tamaño: " + filePath.getFileName() + " (" + (fileSize/1024/1024) + "MB)");
                                continue;
                            }
                            fileContent = Files.readAllBytes(filePath);
                            fileName = filePath.getFileName().toString();
                        }

                        if (fileContent != null && fileContent.length > 0) {
                            String base64Content = encodeToBase64Chunked(fileContent);

                            attachments.add(com.resend.services.emails.model.Attachment.builder()
                                    .fileName(fileName)
                                    .content(base64Content)
                                    .build());

                            fileContent = null;
                            System.gc();
                        }

                    } catch (IOException e) {
                        System.err.println("Error procesando archivo adjunto " + ruta + ": " + e.getMessage());
                    } catch (OutOfMemoryError e) {
                        System.err.println("Error de memoria con adjunto " + ruta + ". Archivo omitido.");
                        System.gc();
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
        emailRecord.setFechaEnvio(ZonedDateTime.now(ZoneId.of("America/Mexico_City"))); emailRecord.setTratoId(tratoId);
        emailRecord.setExito(exito);
        emailRecord.setResendEmailId(resendEmailId);

        return emailRecordRepository.save(emailRecord);
    }

    private String encodeToBase64Chunked(byte[] data) {
        if (data.length > 1024 * 1024) {
            StringBuilder result = new StringBuilder();
            int chunkSize = 1024 * 512;

            for (int i = 0; i < data.length; i += chunkSize) {
                int end = Math.min(i + chunkSize, data.length);
                byte[] chunk = Arrays.copyOfRange(data, i, end);
                result.append(Base64.getEncoder().encodeToString(chunk));
            }
            return result.toString();
        } else {
            return Base64.getEncoder().encodeToString(data);
        }
    }

    private boolean isUrl(String path) {
        return path.startsWith("http://") || path.startsWith("https://");
    }

    private byte[] downloadFileFromUrl(String url) throws IOException {
        URL fileUrl = new URL(url);
        URLConnection connection = fileUrl.openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(20000);

        // Verificar tamaño del archivo
        int contentLength = connection.getContentLength();
        if (contentLength > 5 * 1024 * 1024) { // 5MB límite
            throw new IOException("Archivo demasiado grande: " + (contentLength / 1024 / 1024) + "MB. Máximo: 5MB");
        }

        try (InputStream inputStream = connection.getInputStream()) {
            byte[] buffer = new byte[4096];
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            int bytesRead;
            int totalBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > 5 * 1024 * 1024) {
                    throw new IOException("Archivo excede 5MB durante descarga");
                }
                outputStream.write(buffer, 0, bytesRead);
            }

            return outputStream.toByteArray();
        }
    }

    private String extractFileNameFromUrl(String url) {
        try {
            String[] parts = url.split("/");
            String fileName = "archivo_adjunto";

            if (url.contains("cloudinary.com")) {
                for (int i = 0; i < parts.length; i++) {
                    String part = parts[i];

                    if ((part.equals("plantillas_correos") || part.equals("correos_temporales")) && i + 1 < parts.length) {
                        String publicIdPart = parts[i + 1];

                        if (publicIdPart.contains("?")) {
                            publicIdPart = publicIdPart.split("\\?")[0];
                        }


                        if (publicIdPart.contains("_")) {
                            String[] publicIdParts = publicIdPart.split("_");
                            if (publicIdParts.length >= 2) {
                                StringBuilder nombreBuilder = new StringBuilder();
                                for (int j = 0; j < publicIdParts.length - 1; j++) {
                                    if (j > 0) nombreBuilder.append("_");
                                    nombreBuilder.append(publicIdParts[j]);
                                }
                                fileName = nombreBuilder.toString();
                            }
                        }

                        if (!fileName.contains(".")) {
                            String detectedExtension = detectExtensionFromUrl(url);
                            if (detectedExtension != null) {
                                fileName = fileName + "." + detectedExtension;
                            } else {
                                String lastPart = parts[parts.length - 1];
                                if (lastPart.contains(".")) {
                                    String extension = lastPart.substring(lastPart.lastIndexOf("."));
                                    fileName = fileName + extension;
                                } else {
                                    fileName = fileName + ".pdf";
                                }
                            }
                        }
                        break;
                    }
                }
            } else {
                // Para URLs regulares (código original)
                String lastPart = parts[parts.length - 1];
                if (lastPart.contains("?")) {
                    lastPart = lastPart.split("\\?")[0];
                }
                if (lastPart.contains(".") && isValidFileName(lastPart)) {
                    fileName = lastPart;
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

    private String procesarImagenesEmbebidas(String cuerpoHtml) {
        if (cuerpoHtml == null || !cuerpoHtml.contains("data:image")) {
            return cuerpoHtml;
        }
        return cuerpoHtml;
    }

    public String uploadTempFileToCloudinary(Path tempFilePath) throws IOException {
        String fileName = tempFilePath.getFileName().toString();

        // Remover el UUID que agregamos anteriormente para obtener el nombre original
        String originalName = fileName.contains("_") ? fileName.substring(fileName.indexOf("_") + 1) : fileName;
        String nombreSinExtension = originalName.substring(0, originalName.lastIndexOf('.'));
        String publicId = "correos_temporales/" + nombreSinExtension + "_" + System.currentTimeMillis();

        Map uploadResult = cloudinary.uploader().upload(Files.readAllBytes(tempFilePath), ObjectUtils.asMap(
                "resource_type", "raw",
                "public_id", publicId,
                "use_filename", true,
                "unique_filename", false
        ));

        return uploadResult.get("url").toString();
    }
}