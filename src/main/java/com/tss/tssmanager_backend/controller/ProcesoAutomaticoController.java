package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.ProcesoAutomaticoDTO;
import com.tss.tssmanager_backend.service.ProcesoAutomaticoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/procesos-automaticos")
public class ProcesoAutomaticoController {

    @Autowired
    private ProcesoAutomaticoService service;

    @GetMapping
    public ResponseEntity<List<ProcesoAutomaticoDTO>> obtenerTodos() {
        return ResponseEntity.ok(service.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProcesoAutomaticoDTO> obtenerPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(service.obtenerPorId(id));
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody ProcesoAutomaticoDTO dto) {
        try {
            return ResponseEntity.ok(service.crear(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody ProcesoAutomaticoDTO dto) {
        try {
            return ResponseEntity.ok(service.actualizar(id, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}