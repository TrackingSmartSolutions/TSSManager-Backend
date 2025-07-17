package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.service.CorreosSeguimientoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/correos-seguimiento")
public class CorreosSeguimientoController {

    @Autowired
    private CorreosSeguimientoService correosSeguimientoService;

    @PostMapping("/activar/{tratoId}")
    public ResponseEntity<String> activarCorreosSeguimiento(@PathVariable Integer tratoId) {
        try {
            correosSeguimientoService.activarCorreosSeguimiento(tratoId);
            return ResponseEntity.ok("Correos de seguimiento activados exitosamente");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error interno del servidor");
        }
    }

    @PostMapping("/desactivar/{tratoId}")
    public ResponseEntity<String> desactivarCorreosSeguimiento(@PathVariable Integer tratoId) {
        try {
            correosSeguimientoService.desactivarCorreosSeguimiento(tratoId);
            return ResponseEntity.ok("Correos de seguimiento desactivados exitosamente");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error interno del servidor");
        }
    }

    @GetMapping("/estado/{tratoId}")
    public ResponseEntity<Boolean> obtenerEstadoCorreosSeguimiento(@PathVariable Integer tratoId) {
        boolean activo = correosSeguimientoService.estanActivosCorreosSeguimiento(tratoId);
        return ResponseEntity.ok(activo);
    }

    @PostMapping("/procesar-manual")
    public ResponseEntity<Void> procesarCorreosSeguimientoManual() {
        try {
            correosSeguimientoService.procesarCorreosSeguimiento();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/verificar-pendientes")
    public ResponseEntity<String> verificarCorreosPendientes() {
        try {
            correosSeguimientoService.verificarCorreosPendientes();
            return ResponseEntity.ok("Verificación de correos pendientes completada");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al verificar correos pendientes");
        }
    }
}