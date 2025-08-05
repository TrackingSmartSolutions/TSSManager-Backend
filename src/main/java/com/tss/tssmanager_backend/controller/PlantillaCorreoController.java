package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.PlantillaCorreoDTO;
import com.tss.tssmanager_backend.entity.PlantillaCorreo;
import com.tss.tssmanager_backend.service.PlantillaCorreoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/plantillas")
public class PlantillaCorreoController {

    @Autowired
    private PlantillaCorreoService servicio;

    @GetMapping
    public ResponseEntity<List<PlantillaCorreo>> obtenerTodasLasPlantillas() {
        return ResponseEntity.ok(servicio.obtenerTodasLasPlantillas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlantillaCorreo> obtenerPlantillaPorId(@PathVariable Integer id) {
        return servicio.obtenerPlantillaPorId(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crearPlantilla(
            @RequestPart("plantilla") PlantillaCorreo plantilla,
            @RequestPart(value = "adjuntos", required = false) MultipartFile[] adjuntos) {
        try {
            PlantillaCorreo savedPlantilla = servicio.guardarPlantilla(plantilla, adjuntos);
            return ResponseEntity.ok(savedPlantilla);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error al procesar los archivos");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarPlantilla(
            @PathVariable Integer id,
            @RequestPart("plantilla") PlantillaCorreo plantilla,
            @RequestPart(value = "adjuntos", required = false) MultipartFile[] adjuntos,
            @RequestPart(value = "adjuntosToRemove", required = false) String adjuntosToRemove) {
        try {
            PlantillaCorreoDTO updatedPlantilla = servicio.actualizarPlantilla(id, plantilla, adjuntos, adjuntosToRemove);
            return ResponseEntity.ok(updatedPlantilla);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error al procesar los archivos");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarPlantilla(@PathVariable Integer id) {
        servicio.eliminarPlantilla(id);
        return ResponseEntity.noContent().build();
    }
}