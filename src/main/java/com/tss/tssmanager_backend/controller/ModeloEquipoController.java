package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.entity.ModeloEquipo;
import com.tss.tssmanager_backend.service.ModeloEquipoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/modelos")
public class ModeloEquipoController {

    @Autowired
    private ModeloEquipoService service;

    @PostMapping
    public ResponseEntity<ModeloEquipo> crearModelo(
            @RequestPart("modelo") ModeloEquipo modelo,
            @RequestPart(value = "imagen", required = false) MultipartFile imagen) throws IOException {
        ModeloEquipo savedModelo = service.guardarModelo(modelo, imagen);
        return ResponseEntity.ok(savedModelo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ModeloEquipo> actualizarModelo(
            @PathVariable Integer id,
            @RequestPart("modelo") ModeloEquipo modelo,
            @RequestPart(value = "imagen", required = false) MultipartFile imagen) throws IOException {
        ModeloEquipo updatedModelo = service.actualizarModelo(id, modelo, imagen);
        return ResponseEntity.ok(updatedModelo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarModelo(@PathVariable Integer id) {
        service.eliminarModelo(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModeloEquipo> obtenerModelo(@PathVariable Integer id) {
        return ResponseEntity.ok(service.obtenerModelo(id));
    }

    @GetMapping
    public ResponseEntity<Iterable<ModeloEquipo>> obtenerTodosLosModelos() {
        return ResponseEntity.ok(service.obtenerTodosLosModelos());
    }
}