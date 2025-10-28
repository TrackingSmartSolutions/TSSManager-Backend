package com.tss.tssmanager_backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.tss.tssmanager_backend.entity.Sector;
import com.tss.tssmanager_backend.repository.SectorRepository;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.tss.tssmanager_backend.enums.*;
import org.apache.commons.csv.*;
import com.tss.tssmanager_backend.dto.CopiaSeguridadDTO;
import com.tss.tssmanager_backend.dto.ConfiguracionCopiasDTO;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CopiaSeguridadService {

    private final CopiaSeguridadRepository copiaSeguridadRepository;
    private final ConfiguracionCopiasRepository configuracionCopiasRepository;
    private final TratoRepository tratosRepository;
    private final EmpresaRepository empresasRepository;
    private final ContactoRepository contactosRepository;
    private final SectorRepository sectorRepository;
    private final EquipoRepository equiposRepository;
    private final SimRepository simsRepository;
    private final UsuarioRepository usuarioRepository;
    private final PlataformaService plataformaService;

    @Value("${google.drive.client.id}")
    private String clientId;

    @Value("${google.drive.client.secret}")
    private String clientSecret;

    @Value("${google.drive.redirect.uri}")
    private String redirectUri;

    @Value("${copias.storage.path}")
    private String storagePath;

    private static final String APPLICATION_NAME = "TSS Manager 3";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String FOLDER_NAME = "TSS_Copias";

    // Configuración
    public ConfiguracionCopiasDTO obtenerConfiguracion(Integer usuarioId) {
        Optional<ConfiguracionCopias> config = configuracionCopiasRepository.findByUsuarioId(usuarioId);

        if (config.isPresent()) {
            return mapToDTO(config.get());
        } else {
            // Crear configuración por defecto
            ConfiguracionCopias nuevaConfig = ConfiguracionCopias.builder()
                    .usuarioId(usuarioId)
                    .datosRespaldar(Arrays.stream(TipoCopiaSeguridadEnum.values()).toArray(TipoCopiaSeguridadEnum[]::new))
                    .frecuencia("SEMANAL")
                    .horaRespaldo(LocalTime.of(2, 0))
                    .googleDriveVinculada(false)
                    .fechaCreacion(LocalDateTime.now())
                    .fechaActualizacion(LocalDateTime.now())
                    .build();

            ConfiguracionCopias guardada = configuracionCopiasRepository.save(nuevaConfig);
            return mapToDTO(guardada);
        }
    }

    @Transactional
    public ConfiguracionCopiasDTO guardarConfiguracion(ConfiguracionCopiasDTO dto) {
        ConfiguracionCopias config = configuracionCopiasRepository.findByUsuarioId(dto.getUsuarioId())
                .orElse(ConfiguracionCopias.builder()
                        .usuarioId(dto.getUsuarioId())
                        .fechaCreacion(LocalDateTime.now())
                        .build());

        config.setDatosRespaldar(dto.getDatosRespaldar().toArray(new TipoCopiaSeguridadEnum[0]));
        config.setFrecuencia(dto.getFrecuencia());
        config.setHoraRespaldo(dto.getHoraRespaldo());
        config.setFechaActualizacion(LocalDateTime.now());

        ConfiguracionCopias guardada = configuracionCopiasRepository.save(config);
        return mapToDTO(guardada);
    }

    // Google Drive Authentication
    public String obtenerUrlAutenticacionGoogleDrive(Integer usuarioId) throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        GoogleClientSecrets clientSecrets = new GoogleClientSecrets();
        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
        details.setClientId(clientId);
        details.setClientSecret(clientSecret);
        clientSecrets.setInstalled(details);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets,
                Arrays.asList(DriveScopes.DRIVE, "https://www.googleapis.com/auth/userinfo.email"))
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();

        return flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setState(usuarioId.toString())
                .build();
    }

    @Transactional
    public boolean procesarCallbackGoogleDrive(Integer usuarioId, String code) {
        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

            GoogleClientSecrets clientSecrets = new GoogleClientSecrets();
            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
            details.setClientId(clientId);
            details.setClientSecret(clientSecret);
            clientSecrets.setInstalled(details);

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, Collections.singletonList(DriveScopes.DRIVE))
                    .setAccessType("offline")
                    .build();

            var tokenResponse = flow.newTokenRequest(code)
                    .setRedirectUri(redirectUri)
                    .execute();

            ConfiguracionCopias config = configuracionCopiasRepository.findByUsuarioId(usuarioId)
                    .orElseThrow(() -> new RuntimeException("Configuración no encontrada"));

            // Guardar tokens
            config.setGoogleDriveToken(tokenResponse.getAccessToken());
            config.setGoogleDriveRefreshToken(tokenResponse.getRefreshToken());
            config.setGoogleDriveVinculada(true);
            config.setFechaActualizacion(LocalDateTime.now());

            String userEmail = obtenerEmailUsuarioGoogleDrive(tokenResponse.getAccessToken());
            if (userEmail != null) {
                config.setGoogleDriveEmail(userEmail);
                log.info("Email de Google Drive obtenido y guardado: {}", userEmail);
            }

            // Crear la carpeta TSS_Copias si no existe
            Drive driveService = crearDriveService(config);
            String folderId = crearCarpetaSiNoExiste(driveService);
            config.setGoogleDriveFolderId(folderId);

            configuracionCopiasRepository.save(config);
            return true;

        } catch (Exception e) {
            log.error("Error al procesar callback de Google Drive", e);
            return false;
        }
    }


    private String obtenerEmailUsuarioGoogleDrive(String accessToken) {
        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

            // Crear la solicitud HTTP
            com.google.api.client.http.GenericUrl genericUrl =
                    new com.google.api.client.http.GenericUrl("https://www.googleapis.com/oauth2/v2/userinfo");

            com.google.api.client.http.HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory();
            com.google.api.client.http.HttpRequest request = requestFactory.buildGetRequest(genericUrl);
            request.getHeaders().setAuthorization("Bearer " + accessToken);

            // Ejecutar la solicitud
            com.google.api.client.http.HttpResponse response = request.execute();
            String responseBody = response.parseAsString();

            log.info("Respuesta completa de Google OAuth2: {}", responseBody);

            // Usar expresión regular para extraer el email de forma más segura
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"email\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher matcher = pattern.matcher(responseBody);

            if (matcher.find()) {
                String email = matcher.group(1);
                log.info("Email extraído exitosamente: {}", email);
                return email;
            } else {
                log.warn("No se pudo encontrar el email en la respuesta: {}", responseBody);
                return null;
            }

        } catch (Exception e) {
            log.error("Error al obtener el email del usuario de Google Drive", e);
            return null;
        }
    }


    @Transactional
    public void desvincularGoogleDrive(Integer usuarioId) {
        ConfiguracionCopias config = configuracionCopiasRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new RuntimeException("Configuración no encontrada"));

        config.setGoogleDriveToken(null);
        config.setGoogleDriveRefreshToken(null);
        config.setGoogleDriveVinculada(false);
        config.setGoogleDriveEmail(null);
        config.setGoogleDriveFolderId(null);
        config.setFechaActualizacion(LocalDateTime.now());

        configuracionCopiasRepository.save(config);
    }

    // Generación de copias
    @Async
    @Transactional
    public void generarCopiaInstantanea(Integer usuarioId, List<TipoCopiaSeguridadEnum> tiposDatos) {
        try {
            ConfiguracionCopias config = configuracionCopiasRepository.findByUsuarioId(usuarioId)
                    .orElseThrow(() -> new RuntimeException("Configuración no encontrada"));

            for (TipoCopiaSeguridadEnum tipo : tiposDatos) {
                generarCopia(config, tipo, "MANUAL");
            }
        } catch (Exception e) {
            log.error("Error al generar copia instantánea", e);
            throw new RuntimeException("Error al generar copia de seguridad", e);
        }
    }

    @Scheduled(cron = "0 0 2 * * MON") // Cada lunes a las 2:00 AM
    public void ejecutarCopiasSemanales() {
        List<ConfiguracionCopias> configuraciones = configuracionCopiasRepository.findByFrecuencia("SEMANAL");

        for (ConfiguracionCopias config : configuraciones) {
            if (esHoraEjecucion(config.getHoraRespaldo())) {
                ejecutarCopiasProgramadas(config);
            }
        }
    }

    @Scheduled(cron = "0 0 2 1 * ?") // El primer día de cada mes a las 2:00 AM
    public void ejecutarCopiasMensuales() {
        List<ConfiguracionCopias> configuraciones = configuracionCopiasRepository.findByFrecuencia("MENSUAL");

        for (ConfiguracionCopias config : configuraciones) {
            if (esHoraEjecucion(config.getHoraRespaldo())) {
                ejecutarCopiasProgramadas(config);
            }
        }
    }

    @Transactional(timeout = 300)
    private void ejecutarCopiasProgramadas(ConfiguracionCopias config) {
        try {
            for (TipoCopiaSeguridadEnum tipo : config.getDatosRespaldar()) {
                try {
                    generarCopia(config, tipo, config.getFrecuencia());
                } catch (Exception e) {
                    log.error("Error al generar copia de tipo {} para usuario: {}",
                            tipo, config.getUsuarioId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error crítico al ejecutar copias programadas para usuario: " +
                    config.getUsuarioId(), e);
        }
    }

    private boolean esHoraEjecucion(LocalTime horaConfigurада) {
        LocalTime horaActual = LocalTime.now();
        return Math.abs(horaActual.toSecondOfDay() - horaConfigurада.toSecondOfDay()) <= 300; // 5 minutos de tolerancia
    }

    private void generarCopia(ConfiguracionCopias config, TipoCopiaSeguridadEnum tipo, String frecuencia) throws Exception {
        List<Object> datos = obtenerDatos(config.getUsuarioId(), tipo);

        if (datos.isEmpty()) {
            log.info("No hay datos para respaldar del tipo: " + tipo);
            return;
        }

        // Generar archivos
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss"));
        String baseName = tipo.name().toLowerCase() + "_" + timestamp;

        Path pdfPath = generarPDF(datos, tipo, baseName);
        Path csvPath = generarCSV(datos, tipo, baseName);

        // Calcular tamaño
        long sizePdf = Files.size(pdfPath);
        long sizeCsv = Files.size(csvPath);
        String tamañoTotal = formatearTamaño(sizePdf + sizeCsv);

        CopiasSeguridad copia = CopiasSeguridad.builder()
                .usuarioId(config.getUsuarioId())
                .tipoDatos(tipo)
                .fechaCreacion(LocalDateTime.now())
                .fechaEliminacion(LocalDateTime.now().plusMonths(3))
                .frecuencia(frecuencia)
                .estado("COMPLETADA") // Cambiar a completada inmediatamente
                .tamanoArchivo(tamañoTotal)
                .archivoPdfUrl(pdfPath.toString())
                .archivoCsvUrl(csvPath.toString())
                .build();

        copia = copiaSeguridadRepository.save(copia);

        // Subir a Google Drive de forma ASÍNCRONA y SIN BLOQUEAR
        final Integer copiaId = copia.getId();
        if (config.getGoogleDriveVinculada()) {
            subirAGoogleDriveAsync(config, pdfPath, csvPath, copiaId);
        }
    }

    @Async
    public void subirAGoogleDriveAsync(ConfiguracionCopias config, Path pdfPath, Path csvPath, Integer copiaId) {
        int intentos = 0;
        int maxIntentos = 3;
        long delayBase = 5000;

        while (intentos < maxIntentos) {
            try {
                Drive driveService = crearDriveService(config);
                String folderId = config.getGoogleDriveFolderId();

                // Subir PDF
                File pdfMetadata = new File();
                pdfMetadata.setName(pdfPath.getFileName().toString());
                pdfMetadata.setParents(Collections.singletonList(folderId));
                FileContent pdfContent = new FileContent("application/pdf", pdfPath.toFile());
                driveService.files().create(pdfMetadata, pdfContent)
                        .setFields("id")
                        .execute();

                // Subir CSV
                File csvMetadata = new File();
                csvMetadata.setName(csvPath.getFileName().toString());
                csvMetadata.setParents(Collections.singletonList(folderId));
                FileContent csvContent = new FileContent("text/csv", csvPath.toFile());
                driveService.files().create(csvMetadata, csvContent)
                        .setFields("id")
                        .execute();

                log.info("Archivos subidos exitosamente a Google Drive para copia: {}", copiaId);
                return; // Éxito, salir del método

            } catch (javax.net.ssl.SSLHandshakeException | java.io.EOFException e) {
                intentos++;
                log.warn("Error SSL al subir a Google Drive (intento {}/{}): {}",
                        intentos, maxIntentos, e.getMessage());

                if (intentos < maxIntentos) {
                    try {
                        long delay = delayBase * intentos; // Backoff exponencial
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("Error no recuperable al subir archivos a Google Drive para copia {}",
                        copiaId, e);
                break; // No reintentar en otros tipos de error
            }
        }

        log.error("No se pudo subir la copia {} a Google Drive después de {} intentos",
                copiaId, maxIntentos);
    }

    // Obtención de datos por tipo
    private List<Object> obtenerDatos(Integer usuarioId, TipoCopiaSeguridadEnum tipo) {
        switch (tipo) {
            case TRATOS:
                return new ArrayList<>(tratosRepository.findByPropietarioId(usuarioId));
            case EMPRESAS:
                return new ArrayList<>(empresasRepository.findByPropietario_Id(usuarioId));
            case CONTACTOS:
                return new ArrayList<>(contactosRepository.findByPropietario_Id(usuarioId));
            case EQUIPOS:
                // Ajustar según cómo determines la propiedad del equipo
                return new ArrayList<>(equiposRepository.findAll()); // Necesitas definir el criterio
            case SIMS:
                // Ajustar según cómo determines la propiedad del SIM
                return new ArrayList<>(simsRepository.findAll()); // Necesitas definir el criterio
            default:
                return new ArrayList<>();
        }
    }

    // Generación de archivos
    private Path generarPDF(List<Object> datos, TipoCopiaSeguridadEnum tipo, String baseName) throws Exception {
        Path filePath = Paths.get(storagePath, baseName + ".pdf");
        Files.createDirectories(filePath.getParent());

        Document document = new Document(PageSize.A4.rotate(), 36, 36, 54, 54);
        PdfWriter.getInstance(document, new FileOutputStream(filePath.toFile()));

        document.open();

        // Título
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Paragraph title = new Paragraph("Copia de Seguridad - " + tipo.name(), titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        // Información de la copia
        Font infoFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
        Paragraph info = new Paragraph("Generada el: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")), infoFont);
        info.setSpacingAfter(20);
        document.add(info);

        // Tabla con datos
        if (!datos.isEmpty()) {
            PdfPTable table = crearTablaParaTipo(tipo, datos);
            document.add(table);
        } else {
            document.add(new Paragraph("No hay datos para mostrar."));
        }

        document.close();
        return filePath;
    }

    private Path generarCSV(List<Object> datos, TipoCopiaSeguridadEnum tipo, String baseName) throws Exception {
        Path filePath = Paths.get(storagePath, baseName + ".csv");
        Files.createDirectories(filePath.getParent());

        try (FileWriter fileWriter = new FileWriter(filePath.toFile());
             CSVPrinter csvPrinter = new CSVPrinter(fileWriter, CSVFormat.DEFAULT)) {

            // Headers
            String[] headers = obtenerHeadersParaTipo(tipo);
            csvPrinter.printRecord((Object[]) headers);

            // Datos
            for (Object dato : datos) {
                String[] fila = convertirObjetoAFila(dato, tipo);
                csvPrinter.printRecord((Object[]) fila);
            }
        }

        return filePath;
    }

    // Helpers para generación de archivos
    private PdfPTable crearTablaParaTipo(TipoCopiaSeguridadEnum tipo, List<Object> datos) {
        String[] headers = obtenerHeadersParaTipo(tipo);
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setSpacingBefore(15f);
        table.setSpacingAfter(15f);

        Font headerFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
        Color headerBgColor = new Color(50, 100, 150);

        Font dataFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK);
        Color evenRowColor = new Color(245, 245, 245);
        Color oddRowColor = Color.WHITE;

        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(headerBgColor);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(8);
            cell.setBorderColor(Color.GRAY);
            table.addCell(cell);
        }

        int rowCount = 0;
        for (Object dato : datos) {
            String[] fila = convertirObjetoAFila(dato, tipo);
            for (String valor : fila) {
                PdfPCell cell = new PdfPCell(new Phrase(valor != null ? valor : "", dataFont));
                cell.setBackgroundColor(rowCount % 2 == 0 ? oddRowColor : evenRowColor);
                cell.setPadding(5);
                cell.setBorderColor(Color.LIGHT_GRAY);
                cell.setHorizontalAlignment(Element.ALIGN_LEFT);
                cell.setVerticalAlignment(Element.ALIGN_TOP);
                table.addCell(cell);
            }
            rowCount++;
        }
        return table;
    }


    private String[] obtenerHeadersParaTipo(TipoCopiaSeguridadEnum tipo) {
        switch (tipo) {
            case TRATOS:
                return new String[]{"ID", "Nombre", "Empresa ID", "Contacto", "Unidades", "Ingresos Esperados", "Descripción", "Propietario ID", "Fecha Cierre", "No. Trato", "Probabilidad", "Fase", "Fecha Creación"};
            case EMPRESAS:
                return new String[]{"ID", "Nombre", "Propietario", "Estatus", "Sitio Web", "Sector", "Domicilio Físico", "Domicilio Fiscal", "RFC", "Razón Social", "Régimen Fiscal", "Fecha Creación"};
            case CONTACTOS:
                return new String[]{"ID", "Nombre", "Empresa", "Rol", "Celular", "Propietario", "Creado Por", "Fecha Creación"};
            case EQUIPOS:
                return new String[]{"ID", "IMEI", "Nombre", "Modelo ID", "Cliente ID", "Proveedor ID", "Tipo", "Estatus", "Tipo Activación", "Plataforma", "Fecha Activación", "Fecha Expiración"};
            case SIMS:
                return new String[]{"ID", "Número", "Tarifa", "Vigencia", "Recarga", "Responsable", "Principal", "Grupo", "Equipo IMEI", "Contraseña"};
            default:
                return new String[]{"ID", "Datos"};
        }
    }

    private String[] convertirObjetoAFila(Object dato, TipoCopiaSeguridadEnum tipo) {
        switch (tipo) {
            case TRATOS:
                Trato trato = (Trato) dato;
                return new String[]{
                        trato.getId() != null ? trato.getId().toString() : "",
                        trato.getNombre() != null ? trato.getNombre() : "",
                        trato.getEmpresaId() != null ? trato.getEmpresaId().toString() : "",
                        trato.getContacto() != null ? trato.getContacto().getNombre() : "",
                        trato.getNumeroUnidades() != null ? trato.getNumeroUnidades().toString() : "",
                        trato.getIngresosEsperados() != null ? trato.getIngresosEsperados().toString() : "",
                        trato.getDescripcion() != null ? trato.getDescripcion() : "",
                        trato.getPropietarioId() != null ? trato.getPropietarioId().toString() : "",
                        trato.getFechaCierre() != null ? trato.getFechaCierre().toString() : "",
                        trato.getNoTrato() != null ? trato.getNoTrato() : "",
                        trato.getProbabilidad() != null ? trato.getProbabilidad().toString() : "",
                        trato.getFase() != null ? trato.getFase() : "",
                        trato.getFechaCreacion() != null ? trato.getFechaCreacion().toString() : ""
                };
            case EMPRESAS:
                Empresa empresa = (Empresa) dato;
                return new String[]{
                        empresa.getId() != null ? empresa.getId().toString() : "",
                        empresa.getNombre() != null ? empresa.getNombre() : "",
                        empresa.getPropietario() != null ? empresa.getPropietario().getNombreUsuario() : "",
                        empresa.getEstatus() != null ? empresa.getEstatus().toString() : "",
                        empresa.getSitioWeb() != null ? empresa.getSitioWeb() : "",
                        empresa.getSector() != null ? empresa.getSector().getNombreSector() : "",
                        empresa.getDomicilioFisico() != null ? empresa.getDomicilioFisico() : "",
                        empresa.getDomicilioFiscal() != null ? empresa.getDomicilioFiscal() : "",
                        empresa.getRfc() != null ? empresa.getRfc() : "",
                        empresa.getRazonSocial() != null ? empresa.getRazonSocial() : "",
                        empresa.getRegimenFiscal() != null ? empresa.getRegimenFiscal() : "",
                        empresa.getFechaCreacion() != null ? empresa.getFechaCreacion().toString() : ""
                };
            case CONTACTOS:
                Contacto contacto = (Contacto) dato;
                return new String[]{
                        contacto.getId() != null ? contacto.getId().toString() : "",
                        contacto.getNombre() != null ? contacto.getNombre() : "",
                        contacto.getEmpresa() != null ? contacto.getEmpresa().getNombre() : "",
                        contacto.getRol() != null ? contacto.getRol().toString() : "",
                        contacto.getCelular() != null ? contacto.getCelular() : "",
                        contacto.getPropietario() != null ? contacto.getPropietario().getNombreUsuario() : "",
                        contacto.getCreadoPor() != null ? contacto.getCreadoPor() : "",
                        contacto.getFechaCreacion() != null ? contacto.getFechaCreacion().toString() : ""
                };
            case EQUIPOS:
                Equipo equipo = (Equipo) dato;
                return new String[]{
                        equipo.getId() != null ? equipo.getId().toString() : "",
                        equipo.getImei() != null ? equipo.getImei() : "",
                        equipo.getNombre() != null ? equipo.getNombre() : "",
                        equipo.getModeloId() != null ? equipo.getModeloId().toString() : "",
                        equipo.getClienteId() != null ? equipo.getClienteId().toString() : "",
                        equipo.getProveedorId() != null ? equipo.getProveedorId().toString() : "",
                        equipo.getTipo() != null ? equipo.getTipo().toString() : "",
                        equipo.getEstatus() != null ? equipo.getEstatus().toString() : "",
                        equipo.getTipoActivacion() != null ? equipo.getTipoActivacion().toString() : "",
                        equipo.getPlataforma() != null ? equipo.getPlataforma().toString() : "",
                        equipo.getFechaActivacion() != null ? equipo.getFechaActivacion().toString() : "",
                        equipo.getFechaExpiracion() != null ? equipo.getFechaExpiracion().toString() : ""
                };
            case SIMS:
                Sim sim = (Sim) dato;
                return new String[]{
                        sim.getId() != null ? sim.getId().toString() : "",
                        sim.getNumero() != null ? sim.getNumero() : "",
                        sim.getTarifa() != null ? sim.getTarifa().toString() : "",
                        sim.getVigencia() != null ? sim.getVigencia().toString() : "",
                        sim.getRecarga() != null ? sim.getRecarga().toString() : "",
                        sim.getResponsable() != null ? sim.getResponsable().toString() : "",
                        sim.getPrincipal() != null ? sim.getPrincipal().toString() : "",
                        sim.getGrupo() != null ? sim.getGrupo().toString() : "",
                        sim.getEquipoImei() != null ? sim.getEquipoImei() : "",
                        sim.getContrasena() != null ? sim.getContrasena() : ""
                };
            default:
                return new String[]{dato.toString()};
        }
    }

    // Google Drive
    private Drive crearDriveService(ConfiguracionCopias config) throws Exception {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(HTTP_TRANSPORT)
                .setJsonFactory(JSON_FACTORY)
                .setClientSecrets(clientId, clientSecret)
                .build();

        credential.setAccessToken(config.getGoogleDriveToken());
        credential.setRefreshToken(config.getGoogleDriveRefreshToken());

        return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private String crearCarpetaSiNoExiste(Drive driveService) throws IOException {
        // Buscar si ya existe la carpeta
        String query = "name='" + FOLDER_NAME + "' and mimeType='application/vnd.google-apps.folder'";
        var result = driveService.files().list().setQ(query).execute();

        if (!result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }

        // Crear carpeta
        File fileMetadata = new File();
        fileMetadata.setName(FOLDER_NAME);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");

        File folder = driveService.files().create(fileMetadata).setFields("id").execute();
        return folder.getId();
    }

    private void subirAGoogleDrive(ConfiguracionCopias config, Path pdfPath, Path csvPath, CopiasSeguridad copia) {
        try {
            Drive driveService = crearDriveService(config);
            String folderId = config.getGoogleDriveFolderId();

            // Subir PDF
            File pdfMetadata = new File();
            pdfMetadata.setName(pdfPath.getFileName().toString());
            pdfMetadata.setParents(Collections.singletonList(folderId));

            FileContent pdfContent = new FileContent("application/pdf", pdfPath.toFile());
            driveService.files().create(pdfMetadata, pdfContent).execute();

            // Subir CSV
            File csvMetadata = new File();
            csvMetadata.setName(csvPath.getFileName().toString());
            csvMetadata.setParents(Collections.singletonList(folderId));

            FileContent csvContent = new FileContent("text/csv", csvPath.toFile());
            driveService.files().create(csvMetadata, csvContent).execute();

            log.info("Archivos subidos a Google Drive correctamente");

        } catch (Exception e) {
            log.error("Error al subir archivos a Google Drive", e);
            throw new RuntimeException("Error al subir a Google Drive", e);
        }
    }

    // Gestión de copias existentes
    public List<CopiaSeguridadDTO> obtenerCopiasByUsuario(Integer usuarioId) {
        LocalDateTime fechaActual = LocalDateTime.now();
        List<CopiasSeguridad> copias = copiaSeguridadRepository.findCopiasActivasByUsuario(usuarioId, fechaActual);

        return copias.stream()
                .map(this::mapCopiaToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(timeout = 600)
    public void restaurarCopia(Integer copiaId) {
        CopiasSeguridad copia = copiaSeguridadRepository.findById(copiaId)
                .orElseThrow(() -> new RuntimeException("Copia no encontrada"));

        if (copia.getFechaEliminacion().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("La copia ya no está disponible");
        }

        try {
            Path csvPath = Paths.get(copia.getArchivoCsvUrl());
            if (!Files.exists(csvPath)) {
                throw new RuntimeException("Archivo de copia no encontrado");
            }

            List<String[]> datos = leerCSV(csvPath);
            restaurarDatos(copia.getUsuarioId(), copia.getTipoDatos(), datos);

            log.info("Datos restaurados correctamente desde copia: " + copiaId);

        } catch (Exception e) {
            log.error("Error al restaurar copia: " + copiaId, e);
            throw new RuntimeException("Error al restaurar la copia", e);
        }
    }

    @Transactional
    public void eliminarCopia(Integer copiaId) {
        CopiasSeguridad copia = copiaSeguridadRepository.findById(copiaId)
                .orElseThrow(() -> new RuntimeException("Copia no encontrada"));

        try {
            // Eliminar archivos físicos
            eliminarArchivos(copia);

            // Eliminar registro de BD
            copiaSeguridadRepository.delete(copia);

        } catch (Exception e) {
            log.error("Error al eliminar copia: " + copiaId, e);
            throw new RuntimeException("Error al eliminar la copia", e);
        }
    }

    public Resource descargarArchivo(Integer copiaId, String tipoArchivo) {
        CopiasSeguridad copia = copiaSeguridadRepository.findById(copiaId)
                .orElseThrow(() -> new RuntimeException("Copia no encontrada"));

        try {
            Path archivePath;
            if ("PDF".equalsIgnoreCase(tipoArchivo)) {
                archivePath = Paths.get(copia.getArchivoPdfUrl());
            } else {
                archivePath = Paths.get(copia.getArchivoCsvUrl());
            }

            if (!Files.exists(archivePath)) {
                throw new RuntimeException("Archivo no encontrado");
            }

            byte[] data = Files.readAllBytes(archivePath);
            return new ByteArrayResource(data);

        } catch (Exception e) {
            log.error("Error al descargar archivo", e);
            throw new RuntimeException("Error al descargar el archivo", e);
        }
    }

    // Limpieza automática
    @Scheduled(cron = "0 0 3 * * *") // Todos los días a las 3:00 AM
    @Transactional
    public void limpiarCopiasVencidas() {
        LocalDateTime fechaActual = LocalDateTime.now();
        List<CopiasSeguridad> copiasVencidas = copiaSeguridadRepository.findCopiasParaEliminar(fechaActual);

        for (CopiasSeguridad copia : copiasVencidas) {
            try {
                eliminarArchivos(copia);
                copiaSeguridadRepository.delete(copia);
                log.info("Copia eliminada automáticamente: " + copia.getId());
            } catch (Exception e) {
                log.error("Error al eliminar copia automáticamente: " + copia.getId(), e);
            }
        }
    }

    // Estadísticas
    public Map<String, Object> obtenerEstadisticas(Integer usuarioId) {
        List<CopiasSeguridad> todasLasCopias = copiaSeguridadRepository.findByUsuarioIdOrderByFechaCreacionDesc(usuarioId);
        LocalDateTime fechaActual = LocalDateTime.now();

        long copiasActivas = todasLasCopias.stream()
                .filter(c -> c.getFechaEliminacion().isAfter(fechaActual))
                .count();

        long copiasEstesMes = todasLasCopias.stream()
                .filter(c -> c.getFechaCreacion().getMonth() == fechaActual.getMonth()
                        && c.getFechaCreacion().getYear() == fechaActual.getYear())
                .count();

        Map<TipoCopiaSeguridadEnum, Long> copiasPorTipo = todasLasCopias.stream()
                .filter(c -> c.getFechaEliminacion().isAfter(fechaActual))
                .collect(Collectors.groupingBy(CopiasSeguridad::getTipoDatos, Collectors.counting()));

        long espacioUtilizado = todasLasCopias.stream()
                .filter(c -> c.getFechaEliminacion().isAfter(fechaActual))
                .filter(c -> c.getTamanoArchivo() != null)
                .mapToLong(c -> parsearTamaño(c.getTamanoArchivo()))
                .sum();

        Map<String, Object> estadisticas = new HashMap<>();
        estadisticas.put("copiasActivas", copiasActivas);
        estadisticas.put("copiasEstesMes", copiasEstesMes);
        estadisticas.put("copiasPorTipo", copiasPorTipo);
        estadisticas.put("espacioUtilizado", formatearTamaño(espacioUtilizado));
        estadisticas.put("ultimaCopia", todasLasCopias.isEmpty() ? null : todasLasCopias.get(0).getFechaCreacion());

        return estadisticas;
    }

    public Map<String, Object> validarEstadoSistema(Integer usuarioId) {
        ConfiguracionCopias config = configuracionCopiasRepository.findByUsuarioId(usuarioId)
                .orElse(null);

        Map<String, Object> estado = new HashMap<>();
        estado.put("configuracionExiste", config != null);
        estado.put("googleDriveVinculado", config != null && config.getGoogleDriveVinculada());
        estado.put("espacioDisponible", validarEspacioDisponible());
        estado.put("ultimaEjecucion", obtenerUltimaEjecucion(usuarioId));
        estado.put("proximaEjecucion", calcularProximaEjecucion(config));

        return estado;
    }

    // Métodos auxiliares
    private ConfiguracionCopiasDTO mapToDTO(ConfiguracionCopias entity) {
        ConfiguracionCopiasDTO dto = new ConfiguracionCopiasDTO();
        dto.setId(entity.getId());
        dto.setUsuarioId(entity.getUsuarioId());
        dto.setDatosRespaldar(Arrays.asList(entity.getDatosRespaldar()));
        dto.setFrecuencia(entity.getFrecuencia());
        dto.setHoraRespaldo(entity.getHoraRespaldo());
        dto.setGoogleDriveEmail(entity.getGoogleDriveEmail());
        dto.setGoogleDriveVinculada(entity.getGoogleDriveVinculada());
        return dto;
    }

    private CopiaSeguridadDTO mapCopiaToDTO(CopiasSeguridad entity) {
        return CopiaSeguridadDTO.builder()
                .id(entity.getId())
                .tipoDatos(entity.getTipoDatos())
                .fechaCreacion(entity.getFechaCreacion())
                .fechaEliminacion(entity.getFechaEliminacion())
                .estado(entity.getEstado())
                .tamañoArchivo(entity.getTamanoArchivo())
                .frecuencia(entity.getFrecuencia())
                .archivoPdfUrl(entity.getArchivoPdfUrl())
                .archivoCsvUrl(entity.getArchivoCsvUrl())
                .build();
    }

    private List<String[]> leerCSV(Path csvPath) throws Exception {
        List<String[]> datos = new ArrayList<>();
        try (FileReader fileReader = new FileReader(csvPath.toFile());
             CSVParser csvParser = new CSVParser(fileReader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            for (CSVRecord record : csvParser) {
                String[] fila = new String[record.size()];
                for (int i = 0; i < record.size(); i++) {
                    fila[i] = record.get(i);
                }
                datos.add(fila);
            }
        }
        return datos;
    }

    // Método principal que distribuye la restauración
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 300)
    private void restaurarDatos(Integer usuarioId, TipoCopiaSeguridadEnum tipo, List<String[]> datos) {
        log.info("Iniciando restauración de {} registros de tipo {} para usuario {}",
                datos.size(), tipo, usuarioId);

        switch (tipo) {
            case TRATOS:
                restaurarTratos(usuarioId, datos);
                break;
            case EMPRESAS:
                restaurarEmpresas(usuarioId, datos);
                break;
            case CONTACTOS:
                restaurarContactos(usuarioId, datos);
                break;
            case EQUIPOS:
                restaurarEquipos(usuarioId, datos);
                break;
            case SIMS:
                restaurarSims(usuarioId, datos);
                break;
            default:
                throw new IllegalArgumentException("Tipo de datos no soportado: " + tipo);
        }

        log.info("Restauración completada para tipo {} usuario {}", tipo, usuarioId);
    }

    // 1. RESTAURAR TRATOS
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 300)
    private void restaurarTratos(Integer usuarioId, List<String[]> datos) {
        try {
            log.info("Eliminando tratos existentes para usuario {}", usuarioId);
            tratosRepository.deleteByPropietarioId(usuarioId);
            tratosRepository.flush();

            int batchSize = 50;
            List<Trato> batch = new ArrayList<>();
            int procesados = 0;

            for (int i = 0; i < datos.size(); i++) {
                String[] fila = datos.get(i);
                if (fila.length < 13) {
                    log.warn("Fila {} tiene menos columnas de las esperadas, saltando", i);
                    continue;
                }

                try {
                    Trato trato = new Trato();
                    trato.setNombre(validarString(fila[1]));
                    trato.setEmpresaId(validarInteger(fila[2]));

                    if (!validarString(fila[3]).isEmpty()) {
                        Contacto contacto = contactosRepository
                                .findByNombreAndPropietario_Id(fila[3], usuarioId)
                                .orElse(null);
                        trato.setContacto(contacto);
                    }

                    trato.setNumeroUnidades(validarInteger(fila[4]));
                    trato.setIngresosEsperados(validarBigDecimal(fila[5]));
                    trato.setDescripcion(validarString(fila[6]));
                    trato.setPropietarioId(usuarioId);
                    trato.setFechaCierre(validarLocalDateTime(fila[8]));
                    trato.setNoTrato(validarString(fila[9]));
                    trato.setProbabilidad(validarInteger(fila[10]));
                    trato.setFase(validarString(fila[11]));

                    Instant ahora = Instant.now();
                    trato.setFechaCreacion(ahora);
                    trato.setFechaModificacion(ahora);
                    trato.setFechaUltimaActividad(ahora);
                    trato.setCorreosAutomaticosActivos(false);
                    trato.setCorreosSeguimientoActivo(false);
                    trato.setCorreosSeguimientoEnviados(0);

                    batch.add(trato);

                    if (batch.size() >= batchSize || i == datos.size() - 1) {
                        tratosRepository.saveAll(batch);
                        tratosRepository.flush();
                        procesados += batch.size();
                        log.info("Procesados {}/{} tratos", procesados, datos.size());
                        batch.clear();
                    }
                } catch (Exception e) {
                    log.error("Error procesando fila {} de tratos: {}", i, e.getMessage());
                }
            }

            log.info("Restaurados {} tratos exitosamente para usuario {}", procesados, usuarioId);
        } catch (Exception e) {
            log.error("Error crítico al restaurar tratos", e);
            throw new RuntimeException("Error al restaurar tratos: " + e.getMessage());
        }
    }

    // 2. RESTAURAR EMPRESAS
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 300)
    private void restaurarEmpresas(Integer usuarioId, List<String[]> datos) {
        try {
            log.info("Eliminando empresas existentes para usuario {}", usuarioId);
            empresasRepository.deleteByPropietario_Id(usuarioId);
            empresasRepository.flush();

            Usuario usuario = usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            int batchSize = 50;
            List<Empresa> batch = new ArrayList<>();
            int procesados = 0;

            for (int i = 0; i < datos.size(); i++) {
                String[] fila = datos.get(i);
                if (fila.length < 12) {
                    log.warn("Fila {} tiene menos columnas de las esperadas, saltando", i);
                    continue;
                }

                try {
                    Empresa empresa = new Empresa();
                    empresa.setNombre(validarString(fila[1]));
                    empresa.setPropietario(usuario);
                    empresa.setEstatus(validarEstatusEmpresa(fila[3]));
                    empresa.setSitioWeb(validarString(fila[4]));
                    empresa.setSector(validarSectorEmpresa(fila[5]));
                    empresa.setDomicilioFisico(validarString(fila[6]));
                    empresa.setDomicilioFiscal(validarString(fila[7]));
                    empresa.setRfc(validarString(fila[8]));
                    empresa.setRazonSocial(validarString(fila[9]));
                    empresa.setRegimenFiscal(validarString(fila[10]));

                    Instant ahora = Instant.now();
                    empresa.setFechaCreacion(ahora);
                    empresa.setFechaModificacion(ahora);
                    empresa.setFechaUltimaActividad(ahora);

                    batch.add(empresa);

                    if (batch.size() >= batchSize || i == datos.size() - 1) {
                        empresasRepository.saveAll(batch);
                        empresasRepository.flush();
                        procesados += batch.size();
                        log.info("Procesadas {}/{} empresas", procesados, datos.size());
                        batch.clear();
                    }
                } catch (Exception e) {
                    log.error("Error procesando fila {} de empresas: {}", i, e.getMessage());
                }
            }

            log.info("Restauradas {} empresas exitosamente para usuario {}", procesados, usuarioId);
        } catch (Exception e) {
            log.error("Error crítico al restaurar empresas", e);
            throw new RuntimeException("Error al restaurar empresas: " + e.getMessage());
        }
    }

    // 3. RESTAURAR CONTACTOS
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 300)
    private void restaurarContactos(Integer usuarioId, List<String[]> datos) {
        try {
            log.info("Eliminando contactos existentes para usuario {}", usuarioId);
            contactosRepository.deleteByPropietario_Id(usuarioId);
            contactosRepository.flush();

            Usuario usuario = usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            int batchSize = 50;
            List<Contacto> batch = new ArrayList<>();
            int procesados = 0;

            for (int i = 0; i < datos.size(); i++) {
                String[] fila = datos.get(i);
                if (fila.length < 8) {
                    log.warn("Fila {} tiene menos columnas de las esperadas, saltando", i);
                    continue;
                }

                try {
                    Contacto contacto = new Contacto();
                    contacto.setNombre(validarString(fila[1]));

                    if (!validarString(fila[2]).isEmpty()) {
                        Empresa empresa = empresasRepository
                                .findByNombreAndPropietario_Id(fila[2], usuarioId)
                                .orElse(null);
                        contacto.setEmpresa(empresa);
                    }

                    contacto.setRol(validarRolContacto(fila[3]));
                    contacto.setCelular(validarString(fila[4]));
                    contacto.setPropietario(usuario);

                    Instant ahora = Instant.now();
                    contacto.setFechaCreacion(ahora);
                    contacto.setFechaModificacion(ahora);
                    contacto.setFechaUltimaActividad(ahora);
                    contacto.setCreadoPor("SISTEMA_RESTAURACION");
                    contacto.setModificadoPor("SISTEMA_RESTAURACION");

                    batch.add(contacto);

                    if (batch.size() >= batchSize || i == datos.size() - 1) {
                        contactosRepository.saveAll(batch);
                        contactosRepository.flush();
                        procesados += batch.size();
                        log.info("Procesados {}/{} contactos", procesados, datos.size());
                        batch.clear();
                    }
                } catch (Exception e) {
                    log.error("Error procesando fila {} de contactos: {}", i, e.getMessage());
                }
            }

            log.info("Restaurados {} contactos exitosamente para usuario {}", procesados, usuarioId);
        } catch (Exception e) {
            log.error("Error crítico al restaurar contactos", e);
            throw new RuntimeException("Error al restaurar contactos: " + e.getMessage());
        }
    }

    // 4. RESTAURAR EQUIPOS
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 300)
    private void restaurarEquipos(Integer usuarioId, List<String[]> datos) {
        try {
            log.info("Iniciando restauración de {} equipos", datos.size());

            int batchSize = 50;
            List<Equipo> batch = new ArrayList<>();
            int procesados = 0;

            for (int i = 0; i < datos.size(); i++) {
                String[] fila = datos.get(i);
                if (fila.length < 12) {
                    log.warn("Fila {} tiene menos columnas de las esperadas, saltando", i);
                    continue;
                }

                try {
                    Equipo equipo = new Equipo();
                    equipo.setImei(validarString(fila[1]));
                    equipo.setNombre(validarString(fila[2]));
                    equipo.setModeloId(validarInteger(fila[3]));
                    equipo.setClienteId(validarInteger(fila[4]));
                    equipo.setProveedorId(validarInteger(fila[5]));
                    equipo.setTipo(validarTipoEquipo(fila[6]));
                    equipo.setEstatus(validarEstatusEquipo(fila[7]));
                    equipo.setTipoActivacion(validarTipoActivacion(fila[8]));
                    equipo.setPlataforma(validarPlataforma(fila[9]));
                    equipo.setFechaActivacion(validarSqlDate(fila[10]));
                    equipo.setFechaExpiracion(validarSqlDate(fila[11]));

                    batch.add(equipo);

                    if (batch.size() >= batchSize || i == datos.size() - 1) {
                        equiposRepository.saveAll(batch);
                        equiposRepository.flush();
                        procesados += batch.size();
                        log.info("Procesados {}/{} equipos", procesados, datos.size());
                        batch.clear();
                    }
                } catch (Exception e) {
                    log.error("Error procesando fila {} de equipos: {}", i, e.getMessage());
                }
            }

            log.info("Restaurados {} equipos exitosamente", procesados);
        } catch (Exception e) {
            log.error("Error crítico al restaurar equipos", e);
            throw new RuntimeException("Error al restaurar equipos: " + e.getMessage());
        }
    }

    // 5. RESTAURAR SIMS
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 300)
    private void restaurarSims(Integer usuarioId, List<String[]> datos) {
        try {
            log.info("Iniciando restauración de {} SIMs", datos.size());

            int batchSize = 50;
            List<Sim> batch = new ArrayList<>();
            int procesados = 0;

            for (int i = 0; i < datos.size(); i++) {
                String[] fila = datos.get(i);
                if (fila.length < 10) {
                    log.warn("Fila {} tiene menos columnas de las esperadas, saltando", i);
                    continue;
                }

                try {
                    Sim sim = new Sim();
                    sim.setNumero(validarString(fila[1]));
                    sim.setTarifa(validarTarifaSim(fila[2]));
                    sim.setVigencia(validarSqlDate(fila[3]));
                    sim.setRecarga(validarBigDecimal(fila[4]));
                    sim.setResponsable(validarResponsableSim(fila[5]));
                    sim.setPrincipal(validarPrincipalSim(fila[6]));
                    sim.setGrupo(validarInteger(fila[7]));
                    sim.setContrasena(validarString(fila[9]));

                    String equipoImei = validarString(fila[8]);
                    if (!equipoImei.isEmpty()) {
                        Equipo equipo = equiposRepository.findByImei(equipoImei).orElse(null);
                        sim.setEquipo(equipo);
                    }

                    batch.add(sim);

                    if (batch.size() >= batchSize || i == datos.size() - 1) {
                        simsRepository.saveAll(batch);
                        simsRepository.flush();
                        procesados += batch.size();
                        log.info("Procesados {}/{} SIMs", procesados, datos.size());
                        batch.clear();
                    }
                } catch (Exception e) {
                    log.error("Error procesando fila {} de SIMs: {}", i, e.getMessage());
                }
            }

            log.info("Restaurados {} SIMs exitosamente", procesados);
        } catch (Exception e) {
            log.error("Error crítico al restaurar SIMs", e);
            throw new RuntimeException("Error al restaurar SIMs: " + e.getMessage());
        }
    }

    // Métodos auxiliares para validar y convertir tipos de datos
    private String validarString(String valor) {
        return valor != null && !valor.trim().isEmpty() ? valor.trim() : "";
    }

    private Integer validarInteger(String valor) {
        try {
            return valor != null && !valor.trim().isEmpty() ? Integer.parseInt(valor.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal validarBigDecimal(String valor) {
        try {
            return valor != null && !valor.trim().isEmpty() ? new BigDecimal(valor.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime validarLocalDateTime(String valor) {
        try {
            return valor != null && !valor.trim().isEmpty() ?
                    LocalDateTime.parse(valor.trim()) : LocalDateTime.now();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private java.sql.Date validarSqlDate(String valor) {
        try {
            if (valor != null && !valor.trim().isEmpty()) {
                String fechaSolo = valor.trim().length() > 10 ? valor.trim().substring(0, 10) : valor.trim();
                return java.sql.Date.valueOf(fechaSolo);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private EstatusEmpresaEnum validarEstatusEmpresa(String valor) {
        try {
            return valor != null && !valor.trim().isEmpty() ?
                    EstatusEmpresaEnum.valueOf(valor.trim()) : EstatusEmpresaEnum.POR_CONTACTAR;
        } catch (IllegalArgumentException e) {
            return EstatusEmpresaEnum.POR_CONTACTAR; // Valor por defecto
        }
    }

    private Sector validarSectorEmpresa(String valor) {
        try {
            if (valor != null && !valor.trim().isEmpty()) {
                Optional<Sector> sectorOpt = sectorRepository.findByNombreSectorIgnoreCase(valor.trim());
                if (sectorOpt.isPresent()) {
                    return sectorOpt.get();
                } else {
                    // Crear sector si no existe durante restauración
                    Sector nuevoSector = new Sector();
                    nuevoSector.setNombreSector(valor.trim());
                    nuevoSector.setCreadoPor("SISTEMA_RESTAURACION");
                    nuevoSector.setModificadoPor("SISTEMA_RESTAURACION");
                    return sectorRepository.save(nuevoSector);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private RolContactoEnum validarRolContacto(String valor) {
        try {
            return valor != null && !valor.trim().isEmpty() ?
                    RolContactoEnum.valueOf(valor.trim()) : RolContactoEnum.RECEPCION;
        } catch (IllegalArgumentException e) {
            return RolContactoEnum.RECEPCION; // Valor por defecto
        }
    }

    private TipoEquipoEnum validarTipoEquipo(String valor) {
        try {
            return valor != null && !valor.trim().isEmpty() ?
                    TipoEquipoEnum.valueOf(valor.trim()) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private EstatusEquipoEnum validarEstatusEquipo(String valor) {
        try {
            return valor != null && !valor.trim().isEmpty() ?
                    EstatusEquipoEnum.valueOf(valor.trim()) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private TipoActivacionEquipoEnum validarTipoActivacion(String valor) {
        try {
            return valor != null && !valor.trim().isEmpty() ?
                    TipoActivacionEquipoEnum.valueOf(valor.trim()) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Plataforma validarPlataforma(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        try {
            String nombrePlataforma = null;
            switch (valor.trim()) {
                case "TRACK_SOLID":
                    nombrePlataforma = "Track Solid";
                    break;
                case "WHATSGPS":
                    nombrePlataforma = "WhatsGPS";
                    break;
                case "TRACKERKING":
                    nombrePlataforma = "TrackerKing";
                    break;
                case "JOINTCLOUD":
                    nombrePlataforma = "Joint Cloud";
                    break;
            }

            if (nombrePlataforma != null) {
                List<Plataforma> plataformas = plataformaService.obtenerTodasLasPlataformas();
                for (Plataforma p : plataformas) {
                    if (nombrePlataforma.equals(p.getNombrePlataforma())) {
                        return p;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private TarifaSimEnum validarTarifaSim(String valor) {
        try {
            return valor != null && !valor.trim().isEmpty() ?
                    TarifaSimEnum.valueOf(valor.trim()) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ResponsableSimEnum validarResponsableSim(String valor) {
        try {
            return valor != null && !valor.trim().isEmpty() ?
                    ResponsableSimEnum.valueOf(valor.trim()) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private PrincipalSimEnum validarPrincipalSim(String valor) {
        try {
            return valor != null && !valor.trim().isEmpty() ?
                    PrincipalSimEnum.valueOf(valor.trim()) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void eliminarArchivos(CopiasSeguridad copia) {
        try {
            if (copia.getArchivoPdfUrl() != null) {
                Files.deleteIfExists(Paths.get(copia.getArchivoPdfUrl()));
            }
            if (copia.getArchivoCsvUrl() != null) {
                Files.deleteIfExists(Paths.get(copia.getArchivoCsvUrl()));
            }
        } catch (Exception e) {
            log.warn("Error al eliminar archivos físicos para copia: " + copia.getId(), e);
        }
    }

    private String formatearTamaño(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private long parsearTamaño(String tamañoStr) {
        try {
            if (tamañoStr.endsWith(" B")) {
                return Long.parseLong(tamañoStr.replace(" B", ""));
            } else if (tamañoStr.endsWith(" KB")) {
                return (long) (Double.parseDouble(tamañoStr.replace(" KB", "")) * 1024);
            } else if (tamañoStr.endsWith(" MB")) {
                return (long) (Double.parseDouble(tamañoStr.replace(" MB", "")) * 1024 * 1024);
            } else if (tamañoStr.endsWith(" GB")) {
                return (long) (Double.parseDouble(tamañoStr.replace(" GB", "")) * 1024 * 1024 * 1024);
            }
        } catch (Exception e) {
            log.warn("Error al parsear tamaño: " + tamañoStr, e);
        }
        return 0L;
    }

    private boolean validarEspacioDisponible() {
        try {
            Path storagePath = Paths.get(this.storagePath);
            long espacioLibre = Files.getFileStore(storagePath).getUsableSpace();
            return espacioLibre > (1024 * 1024 * 100); // Al menos 100MB libres
        } catch (Exception e) {
            log.warn("Error al validar espacio disponible", e);
            return true; // Asumir que hay espacio si no se puede verificar
        }
    }

    private LocalDateTime obtenerUltimaEjecucion(Integer usuarioId) {
        List<CopiasSeguridad> copias = copiaSeguridadRepository.findByUsuarioIdOrderByFechaCreacionDesc(usuarioId);
        return copias.isEmpty() ? null : copias.get(0).getFechaCreacion();
    }

    private LocalDateTime calcularProximaEjecucion(ConfiguracionCopias config) {
        if (config == null) return null;

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime proximaEjecucion;

        if ("SEMANAL".equals(config.getFrecuencia())) {
            proximaEjecucion = ahora.with(java.time.DayOfWeek.MONDAY)
                    .with(config.getHoraRespaldo());
            if (proximaEjecucion.isBefore(ahora)) {
                proximaEjecucion = proximaEjecucion.plusWeeks(1);
            }
        } else { // MENSUAL
            proximaEjecucion = ahora.withDayOfMonth(1)
                    .with(config.getHoraRespaldo());
            if (proximaEjecucion.isBefore(ahora)) {
                proximaEjecucion = proximaEjecucion.plusMonths(1);
            }
        }

        return proximaEjecucion;
    }
}