package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.CotizacionDTO;
import com.tss.tssmanager_backend.entity.Cotizacion;
import com.tss.tssmanager_backend.service.CotizacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cotizaciones")
public class CotizacionController {

    private static final Logger logger = LoggerFactory.getLogger(CotizacionController.class);

    @Autowired
    private CotizacionService cotizacionService;

    @PostMapping
    public ResponseEntity<CotizacionDTO> crearCotizacion(@RequestBody CotizacionDTO cotizacionDTO) {
        try {
            logger.debug("Solicitud para crear cotización: {}", cotizacionDTO);
            CotizacionDTO nuevaCotizacion = cotizacionService.crearCotizacion(cotizacionDTO);
            return ResponseEntity.ok(nuevaCotizacion);
        } catch (Exception e) {
            logger.error("Error al crear cotización: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(null);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<CotizacionDTO> actualizarCotizacion(@PathVariable Integer id, @RequestBody CotizacionDTO cotizacionDTO) {
        try {
            logger.debug("Solicitud para actualizar cotización con ID: {}", id);
            CotizacionDTO updatedCotizacion = cotizacionService.actualizarCotizacion(id, cotizacionDTO);
            return ResponseEntity.ok(updatedCotizacion);
        } catch (Exception e) {
            logger.error("Error al actualizar cotización con ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCotizacion(@PathVariable Integer id) {
        try {
            logger.debug("Solicitud para eliminar cotización con ID: {}", id);
            cotizacionService.eliminarCotizacion(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error al eliminar cotización con ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<CotizacionDTO>> listarCotizaciones() {
        try {
            logger.debug("Solicitud para listar cotizaciones");
            List<CotizacionDTO> cotizaciones = cotizacionService.listarCotizaciones();
            return ResponseEntity.ok(cotizaciones);
        } catch (Exception e) {
            logger.error("Error al listar cotizaciones: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(null);
        }
    }

    @GetMapping("/{id}/download-pdf")
    public ResponseEntity<ByteArrayResource> descargarCotizacionPDF(
            @PathVariable Integer id,
            @RequestParam(value = "incluirArchivo", defaultValue = "true") boolean incluirArchivo) throws Exception {

        logger.info("Solicitud para descargar PDF de cotización con ID: {}", id);
        ByteArrayResource resource = cotizacionService.generateCotizacionPDF(id, incluirArchivo);
        Cotizacion cotizacion = cotizacionService.findById(id);


        HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        String fileName = "COTIZACION_" + cotizacion.getId() + "_" +
                cotizacion.getFechaCreacion().toString().replace(":", "-") + ".pdf";
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setContentType(MediaType.APPLICATION_PDF);

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(resource.contentLength())
                .body(resource);
    }

    @PostMapping("/{id}/upload-archivo")
    public ResponseEntity<Map<String, String>> subirArchivo(
            @PathVariable Integer id,
            @RequestParam("archivo") MultipartFile archivo) {
        try {
            if (archivo.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El archivo no puede estar vacío"));
            }

            if (!"application/pdf".equals(archivo.getContentType())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Solo se permiten archivos PDF"));
            }

            cotizacionService.subirArchivoAdicional(id, archivo);
            return ResponseEntity.ok(Map.of("mensaje", "Archivo subido exitosamente"));

        } catch (Exception e) {
            logger.error("Error al subir archivo: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al subir el archivo"));
        }
    }

    @GetMapping("/{id}/check-vinculada")
    public ResponseEntity<Map<String, Boolean>> checkCotizacionVinculada(@PathVariable Integer id) {
        logger.info("Verificando si cotización con ID {} está vinculada a cuenta por cobrar", id);
        try {
            boolean vinculada = cotizacionService.isCotizacionVinculadaACuentaPorCobrar(id);
            return ResponseEntity.ok(Map.of("vinculada", vinculada));
        } catch (Exception e) {
            logger.error("Error al verificar vinculación de cotización con ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", true));
        }
    }
}