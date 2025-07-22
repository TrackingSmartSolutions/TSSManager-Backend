package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.CopiaSeguridadDTO;
import com.tss.tssmanager_backend.dto.ConfiguracionCopiasDTO;
import com.tss.tssmanager_backend.enums.TipoCopiaSeguridadEnum;
import com.tss.tssmanager_backend.service.CopiaSeguridadService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/copias-seguridad")
@RequiredArgsConstructor
public class CopiaSeguridadController {

    private final CopiaSeguridadService copiaSeguridadService;

    // Configuración de copias
    @GetMapping("/configuracion/{usuarioId}")
    public ResponseEntity<ConfiguracionCopiasDTO> obtenerConfiguracion(@PathVariable Integer usuarioId) {
        try {
            ConfiguracionCopiasDTO config = copiaSeguridadService.obtenerConfiguracion(usuarioId);
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/configuracion")
    public ResponseEntity<ConfiguracionCopiasDTO> guardarConfiguracion(@RequestBody ConfiguracionCopiasDTO configuracion) {
        try {
            ConfiguracionCopiasDTO config = copiaSeguridadService.guardarConfiguracion(configuracion);
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Autenticación Google Drive
    @GetMapping("/google-drive/auth-url/{usuarioId}")
    public ResponseEntity<Map<String, String>> obtenerUrlAutenticacion(@PathVariable Integer usuarioId) {
        try {
            String authUrl = copiaSeguridadService.obtenerUrlAutenticacionGoogleDrive(usuarioId);
            return ResponseEntity.ok(Map.of("authUrl", authUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/google-drive/callback")
    public ResponseEntity<Map<String, String>> procesarCallbackGoogleDrive(
            @RequestParam String code,
            @RequestParam String state) {
        try {
            Integer usuarioId = Integer.parseInt(state);
            boolean success = copiaSeguridadService.procesarCallbackGoogleDrive(usuarioId, code);

            if (success) {
                return ResponseEntity.ok(Map.of("message", "Google Drive vinculado correctamente"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Error al vincular Google Drive"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Error en el proceso de autenticación"));
        }
    }



    @DeleteMapping("/google-drive/desvincular/{usuarioId}")
    public ResponseEntity<Map<String, String>> desvincularGoogleDrive(@PathVariable Integer usuarioId) {
        try {
            copiaSeguridadService.desvincularGoogleDrive(usuarioId);
            return ResponseEntity.ok(Map.of("message", "Google Drive desvinculado correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Error al desvincular Google Drive"));
        }
    }

    // Gestión de copias
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<CopiaSeguridadDTO>> obtenerCopiasByUsuario(@PathVariable Integer usuarioId) {
        try {
            List<CopiaSeguridadDTO> copias = copiaSeguridadService.obtenerCopiasByUsuario(usuarioId);
            return ResponseEntity.ok(copias);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/generar/{usuarioId}")
    public ResponseEntity<Map<String, String>> generarCopiaInstantanea(
            @PathVariable Integer usuarioId,
            @RequestBody List<TipoCopiaSeguridadEnum> tiposDatos) {
        try {
            copiaSeguridadService.generarCopiaInstantanea(usuarioId, tiposDatos);
            return ResponseEntity.ok(Map.of("message", "Copia de seguridad generada correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Error al generar la copia de seguridad"));
        }
    }

    @PostMapping("/restaurar/{copiaId}")
    public ResponseEntity<Map<String, String>> restaurarCopia(@PathVariable Integer copiaId) {
        try {
            copiaSeguridadService.restaurarCopia(copiaId);
            return ResponseEntity.ok(Map.of("message", "Datos restaurados correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Error al restaurar la copia de seguridad"));
        }
    }

    @DeleteMapping("/{copiaId}")
    public ResponseEntity<Map<String, String>> eliminarCopia(@PathVariable Integer copiaId) {
        try {
            copiaSeguridadService.eliminarCopia(copiaId);
            return ResponseEntity.ok(Map.of("message", "Copia eliminada correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Error al eliminar la copia"));
        }
    }

    // Descarga de archivos
    @GetMapping("/descargar/{copiaId}/pdf")
    public ResponseEntity<Resource> descargarPDF(@PathVariable Integer copiaId) {
        try {
            Resource resource = copiaSeguridadService.descargarArchivo(copiaId, "PDF");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"copia_" + copiaId + ".pdf\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/descargar/{copiaId}/csv")
    public ResponseEntity<Resource> descargarCSV(@PathVariable Integer copiaId) {
        try {
            Resource resource = copiaSeguridadService.descargarArchivo(copiaId, "CSV");

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"copia_" + copiaId + ".csv\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Estadísticas
    @GetMapping("/estadisticas/{usuarioId}")
    public ResponseEntity<Map<String, Object>> obtenerEstadisticas(@PathVariable Integer usuarioId) {
        try {
            Map<String, Object> estadisticas = copiaSeguridadService.obtenerEstadisticas(usuarioId);
            return ResponseEntity.ok(estadisticas);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Validar estado del sistema
    @GetMapping("/estado-sistema/{usuarioId}")
    public ResponseEntity<Map<String, Object>> validarEstadoSistema(@PathVariable Integer usuarioId) {
        try {
            Map<String, Object> estado = copiaSeguridadService.validarEstadoSistema(usuarioId);
            return ResponseEntity.ok(estado);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}