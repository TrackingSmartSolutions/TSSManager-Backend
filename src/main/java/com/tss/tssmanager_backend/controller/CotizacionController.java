package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.CotizacionDTO;
import com.tss.tssmanager_backend.entity.Cotizacion;
import com.tss.tssmanager_backend.exception.ResourceNotFoundException;
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

    @GetMapping("/{id}")
    public ResponseEntity<CotizacionDTO> obtenerCotizacionPorId(@PathVariable Integer id) {
        try {
            logger.debug("Solicitud para obtener cotización con ID: {}", id);

            CotizacionDTO dto = cotizacionService.obtenerCotizacionPorId(id);

            return ResponseEntity.ok(dto);

        } catch (ResourceNotFoundException e) {
            logger.warn("Cotización no encontrada con ID {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error al obtener cotización con ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body(null);
        }
    }

    @GetMapping("/{id}/download-pdf")
    public ResponseEntity<ByteArrayResource> descargarCotizacionPDF(
            @PathVariable Integer id,
            @RequestParam(value = "incluirArchivos", defaultValue = "false") boolean incluirArchivos) {

        try {
            logger.info("Solicitud para descargar PDF de cotización con ID: {} (incluirArchivos: {})", id, incluirArchivos);

            ByteArrayResource resource = cotizacionService.generateCotizacionPDF(id, incluirArchivos);
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

        } catch (OutOfMemoryError e) {
            logger.error("Memoria insuficiente al generar PDF para cotización ID {}: {}", id, e.getMessage());
            System.gc();
            return ResponseEntity.status(507)
                    .header("X-Error-Message", "Archivo demasiado grande. Intenta sin incluir archivos adicionales")
                    .body(null);
        } catch (Exception e) {
            logger.error("Error al descargar PDF de cotización ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body(null);
        }
    }

    @PostMapping("/{id}/upload-archivos")
    public ResponseEntity<Map<String, String>> subirArchivos(
            @PathVariable Integer id,
            @RequestParam(value = "notasComerciales", required = false) MultipartFile notasComerciales,
            @RequestParam(value = "fichaTecnica", required = false) MultipartFile fichaTecnica) {
        try {
            // Validar que si se sube un archivo, se suban ambos
            boolean tieneNotas = notasComerciales != null && !notasComerciales.isEmpty();
            boolean tieneFicha = fichaTecnica != null && !fichaTecnica.isEmpty();

            if ((tieneNotas && !tieneFicha) || (!tieneNotas && tieneFicha)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Si subes un archivo, debes subir ambos: Notas Comerciales y Ficha Técnica"));
            }

            // Validar formatos
            if (tieneNotas) {
                String tipoNotas = notasComerciales.getContentType();
                if (!"application/pdf".equals(tipoNotas) && !"image/png".equals(tipoNotas)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Notas Comerciales: Solo se permiten archivos PDF y PNG"));
                }
            }

            if (tieneFicha) {
                String tipoFicha = fichaTecnica.getContentType();
                if (!"application/pdf".equals(tipoFicha) && !"image/png".equals(tipoFicha)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Ficha Técnica: Solo se permiten archivos PDF y PNG"));
                }
            }

            cotizacionService.subirArchivosAdicionales(id, notasComerciales, fichaTecnica);
            return ResponseEntity.ok(Map.of("mensaje", "Archivos subidos exitosamente"));

        } catch (Exception e) {
            logger.error("Error al subir archivos: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al subir los archivos: " + e.getMessage()));
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