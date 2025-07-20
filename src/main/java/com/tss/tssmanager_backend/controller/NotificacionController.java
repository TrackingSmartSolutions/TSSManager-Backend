package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.entity.Notificacion;
import com.tss.tssmanager_backend.service.NotificacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    @Autowired
    private NotificacionService notificacionService;

    // Obtener todas las notificaciones del usuario actual
    @GetMapping("/user")
    public ResponseEntity<List<Notificacion>> obtenerNotificacionesUsuario() {
        return ResponseEntity.ok(notificacionService.listarNotificacionesPorUsuario());
    }

    // Obtener contador de notificaciones no leídas
    @GetMapping("/user/contador-no-leidas")
    public ResponseEntity<Map<String, Integer>> obtenerContadorNoLeidas() {
        Integer contador = notificacionService.contarNotificacionesNoLeidas();
        return ResponseEntity.ok(Map.of("count", contador));
    }

    // Marcar una notificación específica como leída
    @PostMapping("/{id}/marcar-leida")
    public ResponseEntity<Void> marcarComoLeida(@PathVariable Integer id) {
        notificacionService.marcarComoLeida(id);
        return ResponseEntity.ok().build();
    }

    // Marcar todas las notificaciones como leídas
    @PostMapping("/marcar-todas-leidas")
    public ResponseEntity<Void> marcarTodasComoLeidas() {
        notificacionService.marcarTodasComoLeidas();
        return ResponseEntity.ok().build();
    }

    // Forzar verificación de notificaciones programadas (para testing)
    @PostMapping("/verificar-programadas")
    public ResponseEntity<Map<String, String>> forzarVerificacionProgramada() {
        notificacionService.verificarNotificacionesProgramadas();
        return ResponseEntity.ok(Map.of("mensaje", "Verificación de notificaciones ejecutada"));
    }

    // Ejecutar limpieza de notificaciones automática
    @PostMapping("/limpiar-leidas")
    public ResponseEntity<Map<String, String>> limpiarNotificacionesLeidas() {
        notificacionService.limpiarNotificacionesLeidas();
        return ResponseEntity.ok(Map.of("mensaje", "Limpieza de notificaciones ejecutada"));
    }

    @PostMapping("/limpiar-leidas-manual")
    public ResponseEntity<Map<String, Object>> limpiarNotificacionesLeidasManual() {
        try {
            int eliminadas = notificacionService.limpiarNotificacionesLeidasManual();
            return ResponseEntity.ok(Map.of(
                    "mensaje", "Limpieza manual ejecutada exitosamente",
                    "eliminadas", eliminadas
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "mensaje", "Error durante la limpieza manual: " + e.getMessage(),
                    "eliminadas", 0
            ));
        }
    }
}