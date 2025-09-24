package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.entity.Plataforma;
import com.tss.tssmanager_backend.service.PlataformaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plataformas")
public class PlataformaController {

    @Autowired
    private PlataformaService service;

    @GetMapping
    public ResponseEntity<List<Plataforma>> obtenerTodasLasPlataformas() {
        return ResponseEntity.ok(service.obtenerTodasLasPlataformas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Plataforma> obtenerPlataforma(@PathVariable Integer id) {
        return ResponseEntity.ok(service.obtenerPlataforma(id));
    }

    @PostMapping
    public ResponseEntity<Plataforma> crearPlataforma(@RequestBody Plataforma plataforma) {
        return ResponseEntity.ok(service.guardarPlataforma(plataforma));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Plataforma> actualizarPlataforma(@PathVariable Integer id, @RequestBody Plataforma plataforma) {
        return ResponseEntity.ok(service.actualizarPlataforma(id, plataforma));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarPlataforma(@PathVariable Integer id) {
        service.eliminarPlataforma(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/check-associations")
    public ResponseEntity<Map<String, Object>> verificarAsociaciones(@PathVariable Integer id) {
        return ResponseEntity.ok(service.verificarAsociaciones(id));
    }
}