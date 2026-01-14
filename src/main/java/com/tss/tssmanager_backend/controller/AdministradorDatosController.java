package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.*;
import com.tss.tssmanager_backend.service.AdministradorDatosService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/administrador-datos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdministradorDatosController {

    private final AdministradorDatosService administradorDatosService;


    @GetMapping("/descargar-plantilla/{tipoDatos}")
    public ResponseEntity<Resource> descargarPlantilla(@PathVariable String tipoDatos) {
        try {
            Resource plantilla = administradorDatosService.descargarPlantilla(tipoDatos);

            String nombreArchivo = "plantilla_" + tipoDatos + ".xlsx";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                    .body(plantilla);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/tipos-datos")
    public ResponseEntity<List<TipoDatosDTO>> obtenerTiposDatos() {
        try {
            List<TipoDatosDTO> tipos = administradorDatosService.obtenerTiposDatos();
            return ResponseEntity.ok(tipos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/importar-datos")
    public ResponseEntity<ResultadoImportacionDTO> importarDatos(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("tipoDatos") String tipoDatos,
            @RequestParam("usuarioId") Integer usuarioId) {

        try {
            System.out.println("=== SOLICITUD DE IMPORTACIÓN ===");
            System.out.println("Archivo: " + archivo.getOriginalFilename());
            System.out.println("Tamaño: " + archivo.getSize() + " bytes");
            System.out.println("Tipo de datos: " + tipoDatos);
            System.out.println("Usuario ID: " + usuarioId);

            ResultadoImportacionDTO resultado = administradorDatosService.importarDatos(archivo, tipoDatos, usuarioId);

            System.out.println("=== RESPUESTA IMPORTACIÓN ===");
            System.out.println("Éxito: " + resultado.isExito());
            System.out.println("Mensaje: " + resultado.getMensaje());
            System.out.println("Exitosos: " + resultado.getRegistrosExitosos());
            System.out.println("Fallidos: " + resultado.getRegistrosFallidos());

            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            System.err.println("=== ERROR EN CONTROLADOR ===");
            System.err.println("Mensaje: " + e.getMessage());
            e.printStackTrace();

            ResultadoImportacionDTO error = new ResultadoImportacionDTO();
            error.setExito(false);
            error.setMensaje("Error al importar datos: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/exportar-datos")
    public ResponseEntity<ResultadoExportacionDTO> exportarDatos(
            @RequestBody SolicitudExportacionDTO solicitud) {
        try {
            Integer usuarioId = solicitud.getUsuarioId();
            ResultadoExportacionDTO resultado = administradorDatosService.exportarDatos(solicitud, usuarioId);
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            ResultadoExportacionDTO error = new ResultadoExportacionDTO();
            error.setExito(false);
            error.setMensaje("Error al exportar datos: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/historial-exportaciones/{usuarioId}")
    public ResponseEntity<List<HistorialExportacionDTO>> obtenerHistorialExportaciones(
            @PathVariable Integer usuarioId) {

        try {
            List<HistorialExportacionDTO> historial = administradorDatosService.obtenerHistorialExportaciones(usuarioId);
            return ResponseEntity.ok(historial);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/descargar-exportacion/{exportacionId}")
    public ResponseEntity<Resource> descargarExportacion(
            @PathVariable Integer exportacionId,
            @RequestParam("usuarioId") Integer usuarioId) {

        try {
            Resource archivo = administradorDatosService.descargarExportacion(exportacionId, usuarioId);

            // Obtener información del archivo para establecer el nombre correcto
            String nombreArchivo = archivo.getFilename();

            // Determinar el tipo de contenido según la extensión
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (nombreArchivo != null) {
                if (nombreArchivo.endsWith(".csv")) {
                    mediaType = MediaType.parseMediaType("text/csv");
                } else if (nombreArchivo.endsWith(".pdf")) {
                    mediaType = MediaType.APPLICATION_PDF;
                }
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                    .body(archivo);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/eliminar-exportacion/{exportacionId}")
    public ResponseEntity<String> eliminarExportacion(
            @PathVariable Integer exportacionId,
            @RequestParam("usuarioId") Integer usuarioId) {

        try {
            administradorDatosService.eliminarExportacion(exportacionId, usuarioId);
            return ResponseEntity.ok("Exportación eliminada correctamente");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al eliminar exportación: " + e.getMessage());
        }
    }

    @GetMapping("/historial-importaciones/{usuarioId}")
    public ResponseEntity<List<HistorialImportacionDTO>> obtenerHistorialImportaciones(
            @PathVariable Integer usuarioId) {
        try {
            List<HistorialImportacionDTO> historial = administradorDatosService.obtenerHistorialImportaciones(usuarioId);
            return ResponseEntity.ok(historial);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

}