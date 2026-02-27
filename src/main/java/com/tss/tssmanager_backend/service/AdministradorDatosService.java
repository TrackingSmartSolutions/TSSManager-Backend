package com.tss.tssmanager_backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Font;
import com.tss.tssmanager_backend.dto.*;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.*;
import com.tss.tssmanager_backend.entity.Sector;
import com.tss.tssmanager_backend.repository.SectorRepository;
import com.tss.tssmanager_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdministradorDatosService {

    private final PlantillaImportacionRepository plantillaRepository;
    private final HistorialExportacionRepository historialExportacionRepository;
    private final HistorialImportacionRepository historialImportacionRepository;
    private final TratoRepository tratosRepository;
    private final EmpresaRepository empresasRepository;
    private final ContactoRepository contactosRepository;
    private final CorreoContactoRepository correoContactoRepository;
    private final ModeloEquipoRepository modeloRepository;
    private final ProveedorEquipoRepository proveedorRepository;
    private final EquipoRepository equipoRepository;
    private final SectorRepository sectorRepository;
    private final SimRepository simRepository;
    private final HistorialSaldosSimRepository historialSaldoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final ResourceLoader resourceLoader;
    private final PlataformaService plataformaService;

    @Value("${app.archivos.exportaciones:/exportaciones}")
    private String rutaExportaciones;

    @Value("${app.archivos.importaciones:/importaciones}")
    private String rutaImportaciones;

    private final org.springframework.context.ApplicationContext applicationContext;

    public Resource descargarPlantilla(String tipoDatos) {
        try {
            Optional<PlantillaImportacion> plantillaOpt = plantillaRepository.findByTipoDatosAndActivoTrue(tipoDatos);
            if (plantillaOpt.isEmpty()) {
                throw new RuntimeException("Plantilla no encontrada para el tipo: " + tipoDatos);
            }

            PlantillaImportacion plantilla = plantillaOpt.get();
            Path rutaArchivo = Paths.get(plantilla.getRutaArchivo());

            // Siempre generar el archivo dinámicamente
            generarArchivoPlantilla(plantilla);

            return resourceLoader.getResource("file:" + rutaArchivo.toString());
        } catch (Exception e) {
            throw new RuntimeException("Error al descargar la plantilla: " + e.getMessage());
        }
    }

    private void generarArchivoPlantilla(PlantillaImportacion plantilla) {
        try {
            // Obtener directorio desde la ruta de la plantilla
            Path rutaArchivo = Paths.get(plantilla.getRutaArchivo());
            Path directorioPlantillas = rutaArchivo.getParent();

            if (!Files.exists(directorioPlantillas)) {
                Files.createDirectories(directorioPlantillas);
            }

            String[] columnas = plantilla.getCamposCsv().split(",");

            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                // Usar el nombre del tipo de datos como nombre de la hoja
                Sheet sheet = workbook.createSheet(plantilla.getTipoDatos());

                // Crear estilo para el encabezado
                CellStyle headerStyle = workbook.createCellStyle();
                XSSFFont headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);

                // Crear fila de encabezado
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < columnas.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(columnas[i].trim());
                    cell.setCellStyle(headerStyle);
                    sheet.autoSizeColumn(i);
                }

                // Agregar algunas filas de ejemplo vacías
                for (int i = 1; i <= 3; i++) {
                    Row row = sheet.createRow(i);
                    for (int j = 0; j < columnas.length; j++) {
                        row.createCell(j).setCellValue("");
                    }
                }

                try (FileOutputStream out = new FileOutputStream(rutaArchivo.toFile())) {
                    workbook.write(out);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error al generar la plantilla: " + e.getMessage());
        }
    }

    // Método para obtener tipos de datos disponibles desde la base de datos
    public List<TipoDatosDTO> obtenerTiposDatos() {
        return plantillaRepository.findByActivoTrue()
                .stream()
                .map(plantilla -> new TipoDatosDTO(
                        plantilla.getTipoDatos(),
                        plantilla.getTipoDatos(),
                        plantilla.getDescripcion()
                ))
                .collect(Collectors.toList());
    }

    public ResultadoImportacionDTO importarDatos(MultipartFile archivo, String tipoDatos, Integer usuarioId) {
        ResultadoImportacionDTO resultado = new ResultadoImportacionDTO();
        StringBuilder errores = new StringBuilder();

        try {
            // Validar archivo
            if (archivo.isEmpty()) {
                resultado.setExito(false);
                resultado.setMensaje("El archivo está vacío");
                return resultado;
            }

            // Crear directorio si no existe
            Path directorioImportaciones = Paths.get(rutaImportaciones);
            if (!Files.exists(directorioImportaciones)) {
                Files.createDirectories(directorioImportaciones);
            }

            // Guardar archivo temporalmente
            String originalFilename = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "";
            boolean esXLSX = originalFilename.endsWith(".xlsx");
            String extension = esXLSX ? ".xlsx" : ".csv";
            String nombreArchivo = "importacion_" + tipoDatos + "_" + System.currentTimeMillis() + extension;
            Path rutaArchivo = directorioImportaciones.resolve(nombreArchivo);
            Files.copy(archivo.getInputStream(), rutaArchivo);

            String contenidoCSV;
            if (esXLSX) {
                contenidoCSV = convertirXLSXaCSV(rutaArchivo);
            } else {
                contenidoCSV = new String(Files.readAllBytes(rutaArchivo), java.nio.charset.StandardCharsets.UTF_8);
                contenidoCSV = limpiarBOM(contenidoCSV);
            }

            try (Reader reader = new StringReader(contenidoCSV);
                 CSVParser csvParser = new CSVParser(reader,
                         CSVFormat.DEFAULT
                                 .withFirstRecordAsHeader()
                                 .withIgnoreEmptyLines(true)
                                 .withTrim())) {

                List<CSVRecord> records = csvParser.getRecords();
                resultado.setRegistrosProcesados(records.size());
                int exitosos = 0;
                int fallidos = 0;
                AdministradorDatosService proxy = applicationContext.getBean(AdministradorDatosService.class);
                for (CSVRecord record : records) {
                    try {
                        boolean procesado = proxy.procesarRegistro(record, tipoDatos, usuarioId);

                        if (procesado) {
                            exitosos++;
                        } else {
                            fallidos++;
                            String error = "Fila " + record.getRecordNumber() + ": No se pudo procesar\n";
                            errores.append(error);
                            System.err.println(error);
                        }
                    } catch (Exception e) {
                        fallidos++;
                        String error = "Fila " + record.getRecordNumber() + ": " + e.getMessage() + "\n";
                        errores.append(error);
                        System.err.println("ERROR en fila " + record.getRecordNumber() + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                resultado.setRegistrosExitosos(exitosos);
                resultado.setRegistrosFallidos(fallidos);
                resultado.setErrores(errores.toString());
                resultado.setExito(exitosos > 0);
                resultado.setMensaje(exitosos > 0 ?
                        "Importación completada: " + exitosos + " exitosos, " + fallidos + " fallidos" :
                        "No se pudo importar ningún registro");

                // Guardar historial
                HistorialImportacion historial = new HistorialImportacion();
                historial.setUsuarioId(usuarioId);
                historial.setTipoDatos(tipoDatos);
                historial.setNombreArchivo(archivo.getOriginalFilename());
                historial.setRegistrosProcesados(resultado.getRegistrosProcesados());
                historial.setRegistrosExitosos(resultado.getRegistrosExitosos());
                historial.setRegistrosFallidos(resultado.getRegistrosFallidos());
                historial.setErrores(resultado.getErrores());

                historialImportacionRepository.save(historial);
            }

            // Limpiar archivo temporal
            Files.deleteIfExists(rutaArchivo);

        } catch (Exception e) {
            e.printStackTrace();
            resultado.setExito(false);
            resultado.setMensaje("Error al procesar el archivo: " + e.getMessage());
        }

        return resultado;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public boolean procesarRegistro(CSVRecord record, String tipoDatos, Integer usuarioId) {
        switch (tipoDatos) {
            case "tratos":
                return procesarTrato(record, usuarioId);
            case "empresas":
                return procesarEmpresa(record, usuarioId);
            case "contactos":
                return procesarContacto(record, usuarioId);
            case "correoContactos":
                return procesarCorreoContacto(record);
            case "modelos":
                return procesarModelo(record);
            case "proveedores":
                return procesarProveedor(record);
            case "equipos":
                return procesarEquipo(record);
            case "sims":
                return procesarSim(record);
            case "historialSaldos":
                return procesarHistorialSaldo(record);
            default:
                return false;
        }
    }

    private boolean procesarCorreoContacto(CSVRecord record) {
        try {
            CorreoContacto correo = new CorreoContacto();

            Integer contactoId = Integer.parseInt(record.get("contacto_id"));
            Optional<Contacto> contactoOpt = contactosRepository.findById(contactoId);
            if (contactoOpt.isEmpty()) {
                throw new RuntimeException("Contacto no encontrado con ID: " + contactoId);
            }

            correo.setContacto(contactoOpt.get());
            correo.setCorreo(record.get("correo"));

            correoContactoRepository.save(correo);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean procesarModelo(CSVRecord record) {
        try {
            ModeloEquipo modelo = new ModeloEquipo();
            modelo.setNombre(record.get("nombre"));
            modelo.setImagenUrl(record.get("imagen_url"));
            modelo.setUso(UsoModeloEquipoEnum.valueOf(record.get("uso")));

            modeloRepository.save(modelo);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean procesarProveedor(CSVRecord record) {
        try {
            ProveedorEquipo proveedor = new ProveedorEquipo();
            proveedor.setNombre(record.get("nombre"));
            proveedor.setContactoNombre(record.get("contacto_nombre"));
            proveedor.setTelefono(record.get("telefono"));
            proveedor.setCorreo(record.get("correo"));
            proveedor.setSitioWeb(record.get("sitio_web"));

            proveedorRepository.save(proveedor);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean procesarEquipo(CSVRecord record) {
        try {
            Equipo equipo = new Equipo();
            equipo.setImei(record.get("imei"));
            equipo.setNombre(record.get("nombre"));
            equipo.setModeloId(Integer.parseInt(record.get("modelo_id")));

            String clienteIdStr = record.get("cliente_id");
            if (clienteIdStr != null && !clienteIdStr.isEmpty()) {
                equipo.setClienteId(Integer.parseInt(clienteIdStr));
            }

            equipo.setClienteDefault(record.get("cliente_default"));
            equipo.setProveedorId(Integer.parseInt(record.get("proveedor_id")));
            equipo.setTipo(TipoEquipoEnum.valueOf(record.get("tipo")));
            equipo.setEstatus(EstatusEquipoEnum.valueOf(record.get("estatus")));

            String tipoActivacionStr = record.get("tipo_activacion");
            if (tipoActivacionStr != null && !tipoActivacionStr.isEmpty()) {
                equipo.setTipoActivacion(TipoActivacionEquipoEnum.valueOf(tipoActivacionStr));
            }

            String plataformaStr = record.get("plataforma");
            if (plataformaStr != null && !plataformaStr.isEmpty()) {
                String nombrePlataforma = null;
                switch (plataformaStr) {
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
                    Plataforma plataforma = null;
                    for (Plataforma p : plataformas) {
                        if (nombrePlataforma.equals(p.getNombrePlataforma())) {
                            plataforma = p;
                            break;
                        }
                    }
                    equipo.setPlataforma(plataforma);
                }
            }

            String fechaActivacionStr = record.get("fecha_activacion");
            if (fechaActivacionStr != null && !fechaActivacionStr.isEmpty()) {
                equipo.setFechaActivacion(Date.valueOf(fechaActivacionStr));
            }

            String fechaExpiracionStr = record.get("fecha_expiracion");
            if (fechaExpiracionStr != null && !fechaExpiracionStr.isEmpty()) {
                equipo.setFechaExpiracion(Date.valueOf(fechaExpiracionStr));
            }

            equipoRepository.save(equipo);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean procesarSim(CSVRecord record) {
        try {
            Sim sim = new Sim();
            sim.setNumero(record.get("numero"));
            sim.setTarifa(TarifaSimEnum.valueOf(record.get("tarifa")));

            String vigenciaStr = record.get("vigencia");
            if (vigenciaStr != null && !vigenciaStr.isEmpty()) {
                sim.setVigencia(Date.valueOf(vigenciaStr));
            }

            String recargaStr = record.get("recarga");
            if (recargaStr != null && !recargaStr.isEmpty()) {
                sim.setRecarga(new BigDecimal(recargaStr));
            }

            sim.setResponsable(ResponsableSimEnum.valueOf(record.get("responsable")));
            sim.setPrincipal(PrincipalSimEnum.valueOf(record.get("principal")));

            String grupoStr = record.get("grupo");
            if (grupoStr != null && !grupoStr.isEmpty()) {
                sim.setGrupo(Integer.parseInt(grupoStr));
            }

            String equipoImeiStr = record.get("equipo_imei");
            if (equipoImeiStr != null && !equipoImeiStr.isEmpty()) {
                Optional<Equipo> equipoOpt = equipoRepository.findByImei(equipoImeiStr);
                if (equipoOpt.isPresent()) {
                    sim.setEquipo(equipoOpt.get());
                }
            }

            sim.setContrasena(record.get("contrasena"));

            simRepository.save(sim);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean procesarHistorialSaldo(CSVRecord record) {
        try {
            HistorialSaldosSim historial = new HistorialSaldosSim();

            // Buscar SIM por número en lugar de ID
            String simNumero = record.get("sim_numero");
            Optional<Sim> simOpt = simRepository.findByNumero(simNumero);
            if (simOpt.isEmpty()) {
                throw new RuntimeException("SIM no encontrada con número: " + simNumero);
            }

            historial.setSim(simOpt.get());

            String saldoStr = record.get("saldo_actual");
            if (saldoStr != null && !saldoStr.isEmpty()) {
                historial.setSaldoActual(new BigDecimal(saldoStr));
            }

            String datosStr = record.get("datos");
            if (datosStr != null && !datosStr.isEmpty()) {
                historial.setDatos(new BigDecimal(datosStr));
            }

            String fechaStr = record.get("fecha");
            if (fechaStr != null && !fechaStr.trim().isEmpty()) {
                historial.setFecha(Date.valueOf(fechaStr));
            } else {
                historial.setFecha(Date.valueOf(LocalDate.now()));
            }

            historialSaldoRepository.save(historial);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    private boolean procesarTrato(CSVRecord record, Integer usuarioId) {
        try {
            Trato trato = new Trato();

            // nombre
            String nombre = record.get("nombre");
            if (nombre == null || nombre.trim().isEmpty()) {
                throw new RuntimeException("El campo 'nombre' es obligatorio y está vacío");
            }
            trato.setNombre(nombre.trim());

            // empresa_id
            String empresaIdStr = record.get("empresa_id");
            if (empresaIdStr == null || empresaIdStr.trim().isEmpty()) {
                throw new RuntimeException("El campo 'empresa_id' es obligatorio y está vacío");
            }
            try {
                trato.setEmpresaId(Integer.parseInt(empresaIdStr.trim()));
            } catch (NumberFormatException e) {
                throw new RuntimeException("'empresa_id' debe ser un número entero, valor recibido: '" + empresaIdStr + "'");
            }

            // contacto_id
            String contactoIdStr = record.get("contacto_id");
            if (contactoIdStr == null || contactoIdStr.trim().isEmpty()) {
                throw new RuntimeException("El campo 'contacto_id' es obligatorio y está vacío");
            }
            Integer contactoId;
            try {
                contactoId = Integer.parseInt(contactoIdStr.trim());
            } catch (NumberFormatException e) {
                throw new RuntimeException("'contacto_id' debe ser un número entero, valor recibido: '" + contactoIdStr + "'");
            }
            Optional<Contacto> contactoOpt = contactosRepository.findById(contactoId);
            if (contactoOpt.isEmpty()) {
                throw new RuntimeException("No existe un contacto con ID: " + contactoId);
            }
            trato.setContacto(contactoOpt.get());

            // propietario_id (opcional, usa usuarioId por defecto)
            Integer propietarioId = usuarioId;
            try {
                String propietarioIdStr = record.get("propietario_id");
                if (propietarioIdStr != null && !propietarioIdStr.trim().isEmpty()) {
                    propietarioId = Integer.parseInt(propietarioIdStr.trim());
                }
            } catch (NumberFormatException e) {
                throw new RuntimeException("'propietario_id' debe ser un número entero o estar vacío");
            }
            Optional<Usuario> propietarioOpt = usuarioRepository.findById(propietarioId);
            if (propietarioOpt.isEmpty()) {
                throw new RuntimeException("No existe un usuario con ID: " + propietarioId);
            }
            trato.setPropietarioId(propietarioId);

            // numero_unidades (opcional)
            String unidadesStr = record.get("numero_unidades");
            if (unidadesStr != null && !unidadesStr.trim().isEmpty() && !unidadesStr.equalsIgnoreCase("null")) {
                try {
                    trato.setNumeroUnidades(Integer.parseInt(unidadesStr.trim()));
                } catch (NumberFormatException e) {
                    throw new RuntimeException("'numero_unidades' debe ser un número entero, valor recibido: '" + unidadesStr + "'");
                }
            }

            // ingresos_esperados (opcional)
            String ingresosStr = record.get("ingresos_esperados");
            if (ingresosStr != null && !ingresosStr.trim().isEmpty() && !ingresosStr.equalsIgnoreCase("null")) {
                try {
                    trato.setIngresosEsperados(new BigDecimal(ingresosStr.trim()));
                } catch (NumberFormatException e) {
                    throw new RuntimeException("'ingresos_esperados' debe ser un número decimal, valor recibido: '" + ingresosStr + "'");
                }
            }

            // descripcion (opcional)
            String descripcion = record.get("descripcion");
            if (descripcion != null && !descripcion.trim().isEmpty() && !descripcion.equalsIgnoreCase("null")) {
                trato.setDescripcion(descripcion.trim());
            }

            // fecha_cierre (opcional, default +60 días)
            String fechaCierreStr = record.get("fecha_cierre");
            if (fechaCierreStr == null || fechaCierreStr.trim().isEmpty() || fechaCierreStr.equalsIgnoreCase("null")) {
                trato.setFechaCierre(LocalDateTime.now().plusDays(60));
            } else {
                try {
                    trato.setFechaCierre(LocalDateTime.parse(fechaCierreStr.trim()));
                } catch (Exception e1) {
                    try {
                        trato.setFechaCierre(LocalDate.parse(fechaCierreStr.trim()).atStartOfDay());
                    } catch (Exception e2) {
                        throw new RuntimeException("'fecha_cierre' tiene formato inválido: '" + fechaCierreStr + "'. Use yyyy-MM-ddTHH:mm:ss o yyyy-MM-dd");
                    }
                }
            }

            // no_trato (opcional, pero único)
            String noTrato = record.get("no_trato");
            if (noTrato != null && !noTrato.trim().isEmpty() && !noTrato.equalsIgnoreCase("null")) {
                trato.setNoTrato(noTrato.trim());
            }

            // probabilidad (opcional, default 0)
            String probabilidadStr = record.get("probabilidad");
            if (probabilidadStr == null || probabilidadStr.trim().isEmpty() || probabilidadStr.equalsIgnoreCase("null")) {
                trato.setProbabilidad(0);
            } else {
                try {
                    int prob = Integer.parseInt(probabilidadStr.trim());
                    if (prob < 0 || prob > 100) {
                        throw new RuntimeException("'probabilidad' debe estar entre 0 y 100, valor recibido: " + prob);
                    }
                    trato.setProbabilidad(prob);
                } catch (NumberFormatException e) {
                    throw new RuntimeException("'probabilidad' debe ser un número entero, valor recibido: '" + probabilidadStr + "'");
                }
            }

            // fase
            String fase = record.get("fase");
            if (fase == null || fase.trim().isEmpty() || fase.equalsIgnoreCase("null")) {
                trato.setFase("PRIMER_CONTACTO");
            } else {
                String faseValor = fase.trim();
                List<String> fasesValidas = Arrays.asList(
                        "CLASIFICACION",
                        "PRIMER_CONTACTO",
                        "ENVIO_DE_INFORMACION",
                        "REUNION",
                        "COTIZACION_PROPUESTA_PRACTICA",
                        "NEGOCIACION_REVISION",
                        "CERRADO_GANADO",
                        "RESPUESTA_POR_CORREO",
                        "INTERES_FUTURO",
                        "CERRADO_PERDIDO",
                        "SEGUIMIENTO"
                );
                if (!fasesValidas.contains(faseValor)) {
                    throw new RuntimeException("'fase' tiene valor inválido: '" + faseValor + "'. Valores válidos: " + fasesValidas);
                }
                trato.setFase(faseValor);
            }

            trato.setFechaCreacion(Instant.now());
            trato.setFechaModificacion(Instant.now());
            trato.setFechaUltimaActividad(Instant.now());
            tratosRepository.save(trato);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            String mensajeError = e.getMessage() != null ? e.getMessage() : "Error desconocido";
            // Detectar duplicado de no_trato
            Throwable causa = e;
            while (causa != null) {
                String msg = causa.getMessage();
                if (msg != null && (msg.toLowerCase().contains("unique") || msg.toLowerCase().contains("duplicate"))) {
                    throw new RuntimeException("El 'no_trato' ya existe en la base de datos: " + record.get("no_trato"), e);
                }
                causa = causa.getCause();
            }
            throw new RuntimeException(mensajeError, e);
        }
    }

    private boolean procesarContacto(CSVRecord record, Integer usuarioId) {
        try {
            Contacto contacto = new Contacto();
            contacto.setNombre(record.get("nombre"));

            // Buscar empresa por ID
            Integer empresaId = Integer.parseInt(record.get("empresa_id"));
            Optional<Empresa> empresaOpt = empresasRepository.findById(empresaId);
            if (empresaOpt.isEmpty()) {
                throw new RuntimeException("Empresa no encontrada con ID: " + empresaId);
            }
            contacto.setEmpresa(empresaOpt.get());

            contacto.setRol(RolContactoEnum.valueOf(record.get("rol")));
            contacto.setCelular(record.get("celular"));

            Integer propietarioId = usuarioId;
            if (record.isMapped("propietario_id")) {
                String propStr = record.get("propietario_id");
                if (propStr != null && !propStr.trim().isEmpty()) {
                    propietarioId = Integer.parseInt(propStr);
                }
            }

            Optional<Usuario> propietarioOpt = usuarioRepository.findById(propietarioId);
            if (propietarioOpt.isEmpty()) throw new RuntimeException("Usuario propietario no encontrado");
            contacto.setPropietario(propietarioOpt.get());

            contacto.setCreadoPor(record.get("creado_por"));

            // Campos de fecha obligatorios
            String fechaCreacion = record.get("fecha_creacion");
            if (fechaCreacion != null && !fechaCreacion.trim().isEmpty()) {
                contacto.setFechaCreacion(Instant.parse(fechaCreacion));
            } else {
                contacto.setFechaCreacion(Instant.now());
            }

            contacto.setFechaModificacion(Instant.now());
            contacto.setFechaUltimaActividad(Instant.now());

            // Campo opcional modificado_por
            String modificadoPor = record.get("modificado_por");
            if (modificadoPor != null && !modificadoPor.trim().isEmpty()) {
                contacto.setModificadoPor(modificadoPor);
            }

            contactosRepository.save(contacto);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Error al procesar contacto: " + e.getMessage(), e);
        }
    }


    private boolean procesarEmpresa(CSVRecord record, Integer usuarioId) {
        try {
            Empresa empresa = new Empresa();
            empresa.setNombre(record.get("nombre"));
            empresa.setEstatus(EstatusEmpresaEnum.valueOf(record.get("estatus")));
            empresa.setDomicilioFisico(record.get("domicilio_fisico"));

            // Buscar propietario por ID (campo obligatorio)
            Integer propietarioId = usuarioId;
            if (record.isMapped("propietario_id")) {
                String propStr = record.get("propietario_id");
                if (propStr != null && !propStr.trim().isEmpty()) {
                    propietarioId = Integer.parseInt(propStr);
                }
            }
            Optional<Usuario> propietarioOpt = usuarioRepository.findById(propietarioId);
            if (propietarioOpt.isEmpty()) throw new RuntimeException("Propietario no encontrado");
            empresa.setPropietario(propietarioOpt.get());

            empresa.setCreadoPor(record.get("creado_por"));

            String fechaCreacion = record.get("fecha_creacion");
            if (fechaCreacion != null && !fechaCreacion.trim().isEmpty()) {
                empresa.setFechaCreacion(Instant.parse(fechaCreacion));
            } else {
                empresa.setFechaCreacion(Instant.now());
            }

            empresa.setFechaModificacion(Instant.now());
            empresa.setFechaUltimaActividad(Instant.now());

            String sitioWeb = record.get("sitio_web");
            if (sitioWeb != null && !sitioWeb.trim().isEmpty()) {
                empresa.setSitioWeb(sitioWeb);
            }

            String sectorStr = record.get("sector");
            if (sectorStr != null && !sectorStr.trim().isEmpty()) {
                Optional<Sector> sectorOpt = sectorRepository.findByNombreSectorIgnoreCase(sectorStr.trim());
                if (sectorOpt.isPresent()) {
                    empresa.setSector(sectorOpt.get());
                } else {
                    // Crear el sector si no existe
                    Sector nuevoSector = new Sector();
                    nuevoSector.setNombreSector(sectorStr.trim());
                    nuevoSector.setCreadoPor("SISTEMA_IMPORTACION");
                    nuevoSector.setModificadoPor("SISTEMA_IMPORTACION");
                    Sector sectorGuardado = sectorRepository.save(nuevoSector);
                    empresa.setSector(sectorGuardado);
                }
            }

            String domicilioFiscal = record.get("domicilio_fiscal");
            if (domicilioFiscal != null && !domicilioFiscal.trim().isEmpty()) {
                empresa.setDomicilioFiscal(domicilioFiscal);
            }

            String rfc = record.get("rfc");
            if (rfc != null && !rfc.trim().isEmpty()) {
                empresa.setRfc(rfc);
            }

            String razonSocial = record.get("razon_social");
            if (razonSocial != null && !razonSocial.trim().isEmpty()) {
                empresa.setRazonSocial(razonSocial);
            }

            String regimenFiscal = record.get("regimen_fiscal");
            if (regimenFiscal != null && !regimenFiscal.trim().isEmpty()) {
                empresa.setRegimenFiscal(regimenFiscal);
            }

            String modificadoPor = record.get("modificado_por");
            if (modificadoPor != null && !modificadoPor.trim().isEmpty()) {
                empresa.setModificadoPor(modificadoPor);
            }

            empresasRepository.save(empresa);
            return true;

        } catch (Exception e) {
            throw new RuntimeException("Error al procesar empresa: " + e.getMessage(), e);
        }
    }

    @Transactional
    public ResultadoExportacionDTO exportarDatos(SolicitudExportacionDTO solicitud, Integer usuarioId) {
        ResultadoExportacionDTO resultado = new ResultadoExportacionDTO();

        try {
            long conteoRegistros = contarRegistros(solicitud.getTipoDatos());
            final int LIMITE_MAXIMO = 100000;

            if (conteoRegistros > LIMITE_MAXIMO) {
                resultado.setExito(false);
                resultado.setMensaje("Hay " + conteoRegistros + " registros. El límite es " +
                        LIMITE_MAXIMO + ". Por favor usa filtros de fecha.");
                return resultado;
            }
            // Crear directorio si no existe
            Path directorioExportaciones = Paths.get(rutaExportaciones);
            if (!Files.exists(directorioExportaciones)) {
                Files.createDirectories(directorioExportaciones);
            }

            // Generar nombre de archivo
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String nombreArchivo = "exportacion_" + solicitud.getTipoDatos() + "_" + timestamp + "." + solicitud.getFormatoExportacion();
            Path rutaArchivo = directorioExportaciones.resolve(nombreArchivo);

            // Obtener datos según el tipo
            List<Object> datos = obtenerDatosParaExportacion(solicitud.getTipoDatos(),
                    solicitud.getFechaInicio(),
                    solicitud.getFechaFin());

            // Exportar según el formato
            int totalRegistros = 0;
            if ("csv".equalsIgnoreCase(solicitud.getFormatoExportacion())) {
                totalRegistros = exportarCSV(datos, rutaArchivo, solicitud.getTipoDatos());
            } else if ("pdf".equalsIgnoreCase(solicitud.getFormatoExportacion())) {
                totalRegistros = exportarPDF(datos, rutaArchivo, solicitud.getTipoDatos());
            }

            // Calcular tamaño del archivo
            long tamañoBytes = Files.size(rutaArchivo);
            String tamañoFormateado = formatearTamaño(tamañoBytes);

            // Guardar en historial
            HistorialExportacion historial = new HistorialExportacion();
            historial.setUsuarioId(usuarioId);
            historial.setTipoDatos(solicitud.getTipoDatos());
            historial.setFormatoExportacion(solicitud.getFormatoExportacion());
            historial.setNombreArchivo(nombreArchivo);
            historial.setRutaArchivo(rutaArchivo.toString());
            historial.setTamañoArchivo(tamañoFormateado);
            historial.setTotalRegistros(totalRegistros);

            if (solicitud.getFechaInicio() != null && !solicitud.getFechaInicio().isEmpty()) {
                historial.setFechaInicio(LocalDate.parse(solicitud.getFechaInicio()));
            }
            if (solicitud.getFechaFin() != null && !solicitud.getFechaFin().isEmpty()) {
                historial.setFechaFin(LocalDate.parse(solicitud.getFechaFin()));
            }

            historial = historialExportacionRepository.save(historial);

            // Configurar resultado
            resultado.setExito(true);
            resultado.setMensaje("Exportación completada exitosamente");
            resultado.setNombreArchivo(nombreArchivo);
            resultado.setRutaArchivo(rutaArchivo.toString());
            resultado.setTamañoArchivo(tamañoFormateado);
            resultado.setTotalRegistros(totalRegistros);
            resultado.setHistorialId(historial.getId().longValue());

        } catch (Exception e) {
            resultado.setExito(false);
            resultado.setMensaje("Error al exportar datos: " + e.getMessage());
        }

        return resultado;
    }

    private List<Object> obtenerDatosParaExportacion(String tipoDatos, String fechaInicio, String fechaFin) {
        List<Object> datos;
        boolean filtrar = fechaInicio != null && !fechaInicio.isEmpty()
                && fechaFin != null && !fechaFin.isEmpty();

        Instant startInstant = null;
        Instant endInstant = null;
        java.sql.Date startDateSql = null;
        java.sql.Date endDateSql = null;

        if (filtrar) {
            try {
                LocalDate start = LocalDate.parse(fechaInicio);
                LocalDate end = LocalDate.parse(fechaFin);

                startInstant = start.atStartOfDay(ZoneId.systemDefault()).toInstant();
                endInstant = end.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();

                startDateSql = java.sql.Date.valueOf(start);
                endDateSql = java.sql.Date.valueOf(end);
            } catch (Exception e) {
                System.err.println("Error parseando fechas, se exportará todo: " + e.getMessage());
                filtrar = false;
            }
        }
        switch (tipoDatos) {
            case "tratos":
                if (filtrar) {
                    datos = tratosRepository.findByFechaCreacionBetween(startInstant, endInstant)
                            .stream().map(Object.class::cast).collect(Collectors.toList());
                } else {
                    datos = tratosRepository.findAll()
                            .stream().map(Object.class::cast).collect(Collectors.toList());
                }
                break;

            case "empresas":
                if (filtrar) {
                    datos = empresasRepository.findByFechaCreacionBetween(startInstant, endInstant)
                            .stream().map(Object.class::cast).collect(Collectors.toList());
                } else {
                    datos = empresasRepository.findAll()
                            .stream().map(Object.class::cast).collect(Collectors.toList());
                }
                break;

            case "contactos":
                if (filtrar) {
                    datos = contactosRepository.findByFechaCreacionBetween(startInstant, endInstant)
                            .stream().map(Object.class::cast).collect(Collectors.toList());
                } else {
                    datos = contactosRepository.findAll()
                            .stream().map(Object.class::cast).collect(Collectors.toList());
                }
                break;

            case "historialSaldos":
                if (filtrar) {
                    datos = historialSaldoRepository.findByFechaBetween(startDateSql, endDateSql)
                            .stream().map(Object.class::cast).collect(Collectors.toList());
                } else {
                    datos = historialSaldoRepository.findAll()
                            .stream().map(Object.class::cast).collect(Collectors.toList());
                }
                break;

            case "auditoria":
                if (filtrar) {
                    datos = auditoriaRepository.findByFechaBetween(startInstant, endInstant)
                            .stream().map(Object.class::cast).collect(Collectors.toList());
                } else {
                    datos = auditoriaRepository.findAll()
                            .stream().map(Object.class::cast).collect(Collectors.toList());
                }
                break;

            case "equipos":
                datos = equipoRepository.findAll().stream()
                        .map(Object.class::cast).collect(Collectors.toList());
                break;

            case "correoContactos":
                datos = correoContactoRepository.findAll().stream()
                        .map(Object.class::cast).collect(Collectors.toList());
                break;

            case "modelos":
                datos = modeloRepository.findAll().stream()
                        .map(Object.class::cast).collect(Collectors.toList());
                break;

            case "proveedores":
                datos = proveedorRepository.findAll().stream()
                        .map(Object.class::cast).collect(Collectors.toList());
                break;

            case "sims":
                datos = simRepository.findAllWithEquipo().stream()
                        .map(Object.class::cast).collect(Collectors.toList());
                break;

            default:
                datos = new ArrayList<>();
        }

        return datos;
    }

    private long contarRegistros(String tipoDatos) {
        switch (tipoDatos) {
            case "tratos":
                return tratosRepository.count();
            case "empresas":
                return empresasRepository.count();
            case "contactos":
                return contactosRepository.count();
            case "correoContactos":
                return correoContactoRepository.count();
            case "modelos":
                return modeloRepository.count();
            case "proveedores":
                return proveedorRepository.count();
            case "equipos":
                return equipoRepository.count();
            case "sims":
                return simRepository.count();
            case "historialSaldos":
                return historialSaldoRepository.count();
            case "auditoria":
                return auditoriaRepository.count();
            default:
                return 0;
        }
    }

    private int exportarCSV(List<Object> datos, Path rutaArchivo, String tipoDatos) throws IOException {
        Optional<PlantillaImportacion> plantillaOpt = plantillaRepository.findByTipoDatosAndActivoTrue(tipoDatos);
        if (plantillaOpt.isEmpty()) {
            throw new RuntimeException("Plantilla no encontrada para el tipo: " + tipoDatos);
        }

        String[] columnas = plantillaOpt.get().getCamposCsv().split(",");

        try (FileOutputStream fos = new FileOutputStream(rutaArchivo.toFile());
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             PrintWriter writer = new PrintWriter(osw)) {

            writer.write('\ufeff');

            try (CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(columnas))) {
                for (Object dato : datos) {
                    List<String> valores = extraerValores(dato, tipoDatos);
                    csvPrinter.printRecord(valores);
                }
            }
        }

        return datos.size();
    }

    private int exportarPDF(List<Object> datos, Path rutaArchivo, String tipoDatos) throws IOException {
        try {
            // Obtener configuración desde la base de datos
            Optional<PlantillaImportacion> plantillaOpt = plantillaRepository.findByTipoDatosAndActivoTrue(tipoDatos);
            if (plantillaOpt.isEmpty()) {
                throw new RuntimeException("Plantilla no encontrada para el tipo: " + tipoDatos);
            }

            PlantillaImportacion plantilla = plantillaOpt.get();
            String[] columnas = plantilla.getCamposCsv().split(",");

            Document document = new Document(PageSize.A4, 36, 36, 54, 54);
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(rutaArchivo.toFile()));
            document.open();

            // Título
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA, 18, Font.BOLD);
            Paragraph title = new Paragraph(plantilla.getTipoDatos(), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // Información adicional
            Font infoFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL);
            Paragraph info = new Paragraph("Generado el: " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")), infoFont);
            info.setAlignment(Element.ALIGN_RIGHT);
            info.setSpacingAfter(10);
            document.add(info);

            // Crear tabla
            PdfPTable table = new PdfPTable(columnas.length);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);

            // Configurar fuentes
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.BOLD);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL);

            // Agregar encabezados
            for (String columna : columnas) {
                PdfPCell headerCell = new PdfPCell(new Phrase(columna.trim(), headerFont));
                headerCell.setBackgroundColor(new Color(230, 230, 230));
                headerCell.setPadding(8);
                headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(headerCell);
            }

            // Agregar datos
            for (Object dato : datos) {
                List<String> valores = extraerValores(dato, tipoDatos);
                for (String valor : valores) {
                    PdfPCell cell = new PdfPCell(new Phrase(valor != null ? valor : "", cellFont));
                    cell.setPadding(5);
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    table.addCell(cell);
                }
            }

            document.add(table);

            // Pie de página con total de registros
            Paragraph footer = new Paragraph("Total de registros: " + datos.size(), infoFont);
            footer.setAlignment(Element.ALIGN_RIGHT);
            footer.setSpacingBefore(20);
            document.add(footer);

            document.close();
            writer.close();

            return datos.size();
        } catch (Exception e) {
            throw new IOException("Error al generar PDF: " + e.getMessage(), e);
        }
    }

    private List<String> extraerValores(Object objeto, String tipoDatos) {
        List<String> valores = new ArrayList<>();

        switch (tipoDatos) {
            case "tratos":
                Trato trato = (Trato) objeto;
                valores.add(trato.getNombre());
                valores.add(trato.getEmpresaId() != null ? trato.getEmpresaId().toString() : "");
                valores.add(trato.getContacto() != null ? trato.getContacto().getId().toString() : "");
                valores.add(trato.getPropietarioId() != null ? trato.getPropietarioId().toString() : "");
                valores.add(trato.getNumeroUnidades() != null ? trato.getNumeroUnidades().toString() : "");
                valores.add(trato.getIngresosEsperados() != null ? trato.getIngresosEsperados().toString() : "");
                valores.add(trato.getDescripcion() != null ? trato.getDescripcion() : "");
                valores.add(trato.getFechaCierre() != null ? trato.getFechaCierre().toString() : "");
                valores.add(trato.getNoTrato() != null ? trato.getNoTrato() : "");
                valores.add(trato.getProbabilidad() != null ? trato.getProbabilidad().toString() : "");
                valores.add(trato.getFase() != null ? trato.getFase() : "");
                break;

            case "empresas":
                Empresa empresa = (Empresa) objeto;
                valores.add(empresa.getNombre());
                valores.add(empresa.getPropietario() != null ? empresa.getPropietario().getId().toString() : "");
                valores.add(empresa.getEstatus().toString());
                valores.add(empresa.getSitioWeb() != null ? empresa.getSitioWeb() : "");
                valores.add(empresa.getSector() != null ? empresa.getSector().getNombreSector() : "");
                valores.add(empresa.getDomicilioFisico() != null ? empresa.getDomicilioFisico() : "");
                valores.add(empresa.getDomicilioFiscal() != null ? empresa.getDomicilioFiscal() : "");
                valores.add(empresa.getRfc() != null ? empresa.getRfc() : "");
                valores.add(empresa.getRazonSocial() != null ? empresa.getRazonSocial() : "");
                valores.add(empresa.getRegimenFiscal() != null ? empresa.getRegimenFiscal() : "");
                break;

            case "contactos":
            Contacto contacto = (Contacto) objeto;
            valores.add(contacto.getNombre());
            valores.add(contacto.getEmpresa() != null ? contacto.getEmpresa().getId().toString() : "");
            valores.add(contacto.getRol().toString());
            valores.add(contacto.getCelular() != null ? contacto.getCelular() : "");
            valores.add(contacto.getPropietario() != null ? contacto.getPropietario().getId().toString() : "");
            break;

            case "correoContactos":
                CorreoContacto correo = (CorreoContacto) objeto;
                valores.add(correo.getContacto() != null ? correo.getContacto().getId().toString() : "");
                valores.add(correo.getCorreo());
                break;

            case "modelos":
                ModeloEquipo modelo = (ModeloEquipo) objeto;
                valores.add(modelo.getNombre());
                valores.add(modelo.getImagenUrl() != null ? modelo.getImagenUrl() : "");
                valores.add(modelo.getUso().toString());
                break;

            case "proveedores":
                ProveedorEquipo proveedor = (ProveedorEquipo) objeto;
                valores.add(proveedor.getNombre());
                valores.add(proveedor.getContactoNombre());
                valores.add(proveedor.getTelefono());
                valores.add(proveedor.getCorreo());
                valores.add(proveedor.getSitioWeb() != null ? proveedor.getSitioWeb() : "");
                break;

            case "equipos":
                Equipo equipo = (Equipo) objeto;
                valores.add(equipo.getImei());
                valores.add(equipo.getNombre());
                valores.add(equipo.getModeloId().toString());
                valores.add(equipo.getClienteId() != null ? equipo.getClienteId().toString() : "");
                valores.add(equipo.getClienteDefault() != null ? equipo.getClienteDefault() : "");
                valores.add(equipo.getProveedorId().toString());
                valores.add(equipo.getTipo().toString());
                valores.add(equipo.getEstatus().toString());
                valores.add(equipo.getTipoActivacion() != null ? equipo.getTipoActivacion().toString() : "");
                valores.add(equipo.getPlataforma() != null ? equipo.getPlataforma().getNombrePlataforma() : "");
                valores.add(equipo.getFechaActivacion() != null ? equipo.getFechaActivacion().toString() : "");
                valores.add(equipo.getFechaExpiracion() != null ? equipo.getFechaExpiracion().toString() : "");
                break;

            case "sims":
                Sim sim = (Sim) objeto;
                valores.add(sim.getNumero());
                valores.add(sim.getTarifa().toString());
                valores.add(sim.getVigencia() != null ? sim.getVigencia().toString() : "");
                valores.add(sim.getRecarga() != null ? sim.getRecarga().toString() : "");
                valores.add(sim.getResponsable().toString());
                valores.add(sim.getPrincipal().toString());
                valores.add(sim.getGrupo() != null ? sim.getGrupo().toString() : "");
                valores.add(sim.getContrasena() != null ? sim.getContrasena() : "");
                valores.add(sim.getEquipoImei() != null ? sim.getEquipoImei() : "");
                break;

            case "historialSaldos":
                HistorialSaldosSim historial = (HistorialSaldosSim) objeto;
                valores.add(historial.getSaldoActual() != null ? historial.getSaldoActual().toString() : "");
                valores.add(historial.getDatos() != null ? historial.getDatos().toString() : "");
                valores.add(historial.getFecha() != null ? historial.getFecha().toString() : "");
                valores.add(historial.getSim() != null ? historial.getSim().getNumero() : "");
                break;

            case "auditoria":
                Auditoria audit = (Auditoria) objeto;
                valores.add(audit.getId().toString());
                valores.add(audit.getTabla());
                valores.add(audit.getAccion());
                valores.add(audit.getRegistroId() != null ? audit.getRegistroId().toString() : "");
                valores.add(audit.getNombreUsuario() != null ? audit.getNombreUsuario() : "SISTEMA");
                valores.add(audit.getDetalleAccion() != null ? audit.getDetalleAccion() : "");
                valores.add(audit.getDatosAnteriores() != null ? audit.getDatosAnteriores().replace("\n", " ") : "");
                valores.add(audit.getDatosNuevos() != null ? audit.getDatosNuevos().replace("\n", " ") : "");
               if (audit.getFecha() != null) {
                    LocalDateTime fechaLocal = LocalDateTime.ofInstant(
                            audit.getFecha(),
                            ZoneId.of("America/Mexico_City")
                    );
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                    valores.add(fechaLocal.format(formatter));
                } else {
                    valores.add("");
                }
                break;

            default:
                Optional<PlantillaImportacion> plantillaOpt = plantillaRepository.findByTipoDatosAndActivoTrue(tipoDatos);
                if (plantillaOpt.isPresent()) {
                    String[] columnas = plantillaOpt.get().getCamposCsv().split(",");
                    for (int i = 0; i < columnas.length; i++) {
                        valores.add("");
                    }
                }
                break;
        }
        return valores;
    }

    private String formatearTamaño(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public List<HistorialExportacionDTO> obtenerHistorialExportaciones(Integer usuarioId) {
        return historialExportacionRepository.findByUsuarioIdOrderByFechaCreacionDesc(usuarioId)
                .stream()
                .map(this::convertirADTO)
                .collect(Collectors.toList());
    }

    private HistorialExportacionDTO convertirADTO(HistorialExportacion historial) {
        HistorialExportacionDTO dto = new HistorialExportacionDTO();
        dto.setId(historial.getId());
        dto.setUsuarioId(historial.getUsuarioId());
        dto.setTipoDatos(historial.getTipoDatos());
        dto.setFormatoExportacion(historial.getFormatoExportacion());
        dto.setNombreArchivo(historial.getNombreArchivo());
        dto.setRutaArchivo(historial.getRutaArchivo());
        dto.setTamañoArchivo(historial.getTamañoArchivo());
        dto.setFechaInicio(historial.getFechaInicio());
        dto.setFechaFin(historial.getFechaFin());
        dto.setTotalRegistros(historial.getTotalRegistros());
        dto.setFechaCreacion(historial.getFechaCreacion());
        return dto;
    }
    public Resource descargarExportacion(Integer exportacionId, Integer usuarioId) {
        Optional<HistorialExportacion> exportacionOpt = historialExportacionRepository.findById(exportacionId);

        if (exportacionOpt.isEmpty() || !exportacionOpt.get().getUsuarioId().equals(usuarioId)) {
            throw new RuntimeException("Exportación no encontrada");
        }

        HistorialExportacion exportacion = exportacionOpt.get();
        Path rutaArchivo = Paths.get(exportacion.getRutaArchivo());

        if (!Files.exists(rutaArchivo)) {
            throw new RuntimeException("Archivo de exportación no encontrado");
        }

        return resourceLoader.getResource("file:" + rutaArchivo.toString());
    }

    @Transactional
    public void eliminarExportacion(Integer exportacionId, Integer usuarioId) {
        Optional<HistorialExportacion> exportacionOpt = historialExportacionRepository.findById(exportacionId);
        if (exportacionOpt.isEmpty() || !exportacionOpt.get().getUsuarioId().equals(usuarioId)) {
            throw new RuntimeException("Exportación no encontrada");
        }
        HistorialExportacion exportacion = exportacionOpt.get();
        // Eliminar archivo físico
        try {
            Files.deleteIfExists(Paths.get(exportacion.getRutaArchivo()));
        } catch (IOException e) {
            // Log el error pero continuar con la eliminación del registro
        }
        // Eliminar registro de la base de datos
        historialExportacionRepository.delete(exportacion);
    }

    public List<HistorialImportacionDTO> obtenerHistorialImportaciones(Integer usuarioId) {
        return historialImportacionRepository.findByUsuarioIdOrderByFechaCreacionDesc(usuarioId)
                .stream()
                .map(this::convertirImportacionADTO)
                .collect(Collectors.toList());
    }

    private HistorialImportacionDTO convertirImportacionADTO(HistorialImportacion historial) {
        HistorialImportacionDTO dto = new HistorialImportacionDTO();
        dto.setId(historial.getId());
        dto.setUsuarioId(historial.getUsuarioId());
        dto.setTipoDatos(historial.getTipoDatos());
        dto.setNombreArchivo(historial.getNombreArchivo());
        dto.setRegistrosProcesados(historial.getRegistrosProcesados());
        dto.setRegistrosExitosos(historial.getRegistrosExitosos());
        dto.setRegistrosFallidos(historial.getRegistrosFallidos());
        dto.setErrores(historial.getErrores());
        dto.setFechaCreacion(historial.getFechaCreacion());
        return dto;
    }

    private String limpiarBOM(String texto) {
        if (texto == null) return null;
        // Eliminar BOM UTF-8 (EF BB BF)
        if (texto.startsWith("\uFEFF")) {
            return texto.substring(1);
        }
        return texto;
    }

    private String convertirXLSXaCSV(Path rutaXLSX) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = Files.newInputStream(rutaXLSX);
             XSSFWorkbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                List<String> celdas = new ArrayList<>();
                for (Cell cell : row) {
                    String valor = "";
                    switch (cell.getCellType()) {
                        case STRING:
                            valor = cell.getStringCellValue()
                                    .replace("\r\n", " ")
                                    .replace("\n", " ")
                                    .replace("\r", " ")
                                    .trim();
                            break;
                        case NUMERIC:
                            if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                                valor = cell.getLocalDateTimeCellValue().toLocalDate().toString();
                            } else {
                                double d = cell.getNumericCellValue();
                                valor = (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(d);
                            }
                            break;
                        case BOOLEAN:
                            valor = String.valueOf(cell.getBooleanCellValue());
                            break;
                        default:
                            valor = "";
                    }
                    if (valor.contains(",") || valor.contains("\"") || valor.contains("\n")) {
                        valor = "\"" + valor.replace("\"", "\"\"") + "\"";
                    }
                    celdas.add(valor);
                }
                sb.append(String.join(",", celdas)).append("\n");
            }
        }
        return sb.toString();
    }
}