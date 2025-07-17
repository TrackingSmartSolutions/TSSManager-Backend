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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final SimRepository simRepository;
    private final HistorialSaldosSimRepository historialSaldoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.archivos.exportaciones:/exportaciones}")
    private String rutaExportaciones;

    @Value("${app.archivos.importaciones:/importaciones}")
    private String rutaImportaciones;

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


    @Transactional
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
            String nombreArchivo = "importacion_" + tipoDatos + "_" + System.currentTimeMillis() + ".csv";
            Path rutaArchivo = directorioImportaciones.resolve(nombreArchivo);
            Files.copy(archivo.getInputStream(), rutaArchivo);

            // Parsear CSV
            try (Reader reader = Files.newBufferedReader(rutaArchivo);
                 CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

                List<CSVRecord> records = csvParser.getRecords();
                resultado.setRegistrosProcesados(records.size());
                int exitosos = 0;
                int fallidos = 0;

                for (CSVRecord record : records) {
                    try {
                        // Pasa el usuarioId al método procesarRegistro
                        boolean procesado = procesarRegistro(record, tipoDatos, usuarioId);
                        if (procesado) {
                            exitosos++;
                        } else {
                            fallidos++;
                        }
                    } catch (Exception e) {
                        fallidos++;
                        errores.append("Fila ").append(record.getRecordNumber()).append(": ").append(e.getMessage()).append("\n");
                    }
                }

                resultado.setRegistrosExitosos(exitosos);
                resultado.setRegistrosFallidos(fallidos);
                resultado.setErrores(errores.toString());
                resultado.setExito(exitosos > 0);
                resultado.setMensaje(exitosos > 0 ? "Importación completada" : "No se pudo importar ningún registro");

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
            resultado.setExito(false);
            resultado.setMensaje("Error al procesar el archivo: " + e.getMessage());
        }
        return resultado;
    }

    private boolean procesarRegistro(CSVRecord record, String tipoDatos, Integer usuarioId) {
        switch (tipoDatos) {
            case "tratos":
                return procesarTrato(record, usuarioId);
            case "empresas":
                return procesarEmpresa(record);
            case "contactos":
                return procesarContacto(record);
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
                equipo.setPlataforma(PlataformaEquipoEnum.valueOf(plataformaStr));
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

            String equipoIdStr = record.get("equipo_id");
            if (equipoIdStr != null && !equipoIdStr.isEmpty()) {
                Optional<Equipo> equipoOpt = equipoRepository.findById(Integer.parseInt(equipoIdStr));
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

            // Buscar SIM por ID
            Integer simId = Integer.parseInt(record.get("sim_id"));
            Optional<Sim> simOpt = simRepository.findById(simId);
            if (simOpt.isEmpty()) {
                throw new RuntimeException("SIM no encontrada con ID: " + simId);
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
            trato.setNombre(record.get("nombre"));
            trato.setEmpresaId(Integer.parseInt(record.get("empresa_id")));

            // Buscar contacto por ID
            Integer contactoId = Integer.parseInt(record.get("contacto_id"));
            Optional<Contacto> contactoOpt = contactosRepository.findById(contactoId);
            if (contactoOpt.isEmpty()) {
                throw new RuntimeException("Contacto no encontrado con ID: " + contactoId);
            }
            trato.setContacto(contactoOpt.get());

            Integer propietarioId = usuarioId;

            try {
                String propietarioIdStr = record.get("propietario_id");
                if (propietarioIdStr != null && !propietarioIdStr.trim().isEmpty()) {
                    propietarioId = Integer.parseInt(propietarioIdStr);
                }
            } catch (Exception e) {
            }

            Optional<Usuario> propietarioOpt = usuarioRepository.findById(propietarioId);
            if (propietarioOpt.isEmpty()) {
                throw new RuntimeException("Usuario propietario no encontrado con ID: " + propietarioId);
            }

            trato.setPropietarioId(propietarioId);

            String unidadesStr = record.get("numero_unidades");
            if (unidadesStr != null && !unidadesStr.isEmpty()) {
                trato.setNumeroUnidades(Integer.parseInt(unidadesStr));
            }

            String ingresosStr = record.get("ingresos_esperados");
            if (ingresosStr != null && !ingresosStr.isEmpty()) {
                trato.setIngresosEsperados(new BigDecimal(ingresosStr));
            }

            trato.setDescripcion(record.get("descripcion"));
            trato.setFechaCierre(LocalDateTime.parse(record.get("fecha_cierre")));
            trato.setNoTrato(record.get("no_trato"));
            trato.setProbabilidad(Integer.parseInt(record.get("probabilidad")));
            trato.setFase(record.get("fase"));
            trato.setFechaCreacion(Instant.now());
            trato.setFechaModificacion(Instant.now());
            trato.setFechaUltimaActividad(Instant.now());

            tratosRepository.save(trato);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    private boolean procesarContacto(CSVRecord record) {
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

            // Buscar propietario por ID (campo obligatorio)
            Integer propietarioId = Integer.parseInt(record.get("propietario_id"));
            Optional<Usuario> propietarioOpt = usuarioRepository.findById(propietarioId);
            if (propietarioOpt.isEmpty()) {
                throw new RuntimeException("Usuario propietario no encontrado con ID: " + propietarioId);
            }
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
            return false;
        }
    }


    private boolean procesarEmpresa(CSVRecord record) {
        try {
            Empresa empresa = new Empresa();

            // Campos obligatorios existentes
            empresa.setNombre(record.get("nombre"));
            empresa.setEstatus(EstatusEmpresaEnum.valueOf(record.get("estatus")));
            empresa.setDomicilioFisico(record.get("domicilio_fisico"));

            // Buscar propietario por ID (campo obligatorio)
            Integer propietarioId = Integer.parseInt(record.get("propietario_id"));
            Optional<Usuario> propietarioOpt = usuarioRepository.findById(propietarioId);
            if (propietarioOpt.isEmpty()) {
                throw new RuntimeException("Usuario propietario no encontrado con ID: " + propietarioId);
            }
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
                empresa.setSector(SectorEmpresaEnum.valueOf(sectorStr));
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
            return false;
        }
    }

    @Transactional
    public ResultadoExportacionDTO exportarDatos(SolicitudExportacionDTO solicitud, Integer usuarioId) {
        ResultadoExportacionDTO resultado = new ResultadoExportacionDTO();

        try {
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
        switch (tipoDatos) {
            case "tratos":
                return tratosRepository.findAll().stream().map(Object.class::cast).collect(Collectors.toList());
            case "empresas":
                return empresasRepository.findAll().stream().map(Object.class::cast).collect(Collectors.toList());
            case "contactos":
                return contactosRepository.findAll().stream().map(Object.class::cast).collect(Collectors.toList());
            case "correoContactos":
                return correoContactoRepository.findAll().stream().map(Object.class::cast).collect(Collectors.toList());
            case "modelos":
                return modeloRepository.findAll().stream().map(Object.class::cast).collect(Collectors.toList());
            case "proveedores":
                return proveedorRepository.findAll().stream().map(Object.class::cast).collect(Collectors.toList());
            case "equipos":
                return equipoRepository.findAll().stream().map(Object.class::cast).collect(Collectors.toList());
            case "sims":
                return simRepository.findAll().stream().map(Object.class::cast).collect(Collectors.toList());
            case "historialSaldos":
                return historialSaldoRepository.findAll().stream().map(Object.class::cast).collect(Collectors.toList());
            default:
                return new ArrayList<>();
        }
    }

    private int exportarCSV(List<Object> datos, Path rutaArchivo, String tipoDatos) throws IOException {
        // Obtener columnas desde la base de datos
        Optional<PlantillaImportacion> plantillaOpt = plantillaRepository.findByTipoDatosAndActivoTrue(tipoDatos);
        if (plantillaOpt.isEmpty()) {
            throw new RuntimeException("Plantilla no encontrada para el tipo: " + tipoDatos);
        }

        String[] columnas = plantillaOpt.get().getCamposCsv().split(",");

        try (FileWriter writer = new FileWriter(rutaArchivo.toFile());
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(columnas))) {

            for (Object dato : datos) {
                List<String> valores = extraerValores(dato, tipoDatos);
                csvPrinter.printRecord(valores);
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
                valores.add(empresa.getEstatus().toString());
                valores.add(empresa.getPropietario() != null ? empresa.getPropietario().getId().toString() : "");
                valores.add(empresa.getSitioWeb() != null ? empresa.getSitioWeb() : "");
                valores.add(empresa.getSector() != null ? empresa.getSector().toString() : "");
                valores.add(empresa.getDomicilioFisico());
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
                valores.add(equipo.getPlataforma() != null ? equipo.getPlataforma().toString() : "");
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
                valores.add(sim.getEquipo() != null ? sim.getEquipo().getId().toString() : "");
                valores.add(sim.getContrasena() != null ? sim.getContrasena() : "");
                break;

            case "historialSaldos":
                HistorialSaldosSim historial = (HistorialSaldosSim) objeto;
                valores.add(historial.getSim() != null ? historial.getSim().getId().toString() : "");
                valores.add(historial.getSaldoActual() != null ? historial.getSaldoActual().toString() : "");
                valores.add(historial.getDatos() != null ? historial.getDatos().toString() : "");
                valores.add(historial.getFecha() != null ? historial.getFecha().toString() : "");
                break;

            default:
                // Obtener columnas desde la base de datos para tipos no reconocidos
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
}