package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.SectorDTO;
import com.tss.tssmanager_backend.exception.ResourceNotFoundException;
import com.tss.tssmanager_backend.service.SectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sectores")
public class SectorController {

    private static final Logger logger = LoggerFactory.getLogger(SectorController.class);

    @Autowired
    private SectorService sectorService;

    @PostMapping
    public ResponseEntity<SectorDTO> crearSector(@RequestBody SectorDTO sectorDTO) {
        try {
            logger.debug("Solicitud para crear sector: {}", sectorDTO.getNombreSector());
            SectorDTO nuevoSector = sectorService.crearSector(sectorDTO);
            return ResponseEntity.ok(nuevoSector);
        } catch (IllegalArgumentException e) {
            logger.error("Error al crear sector: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            logger.error("Error interno al crear sector: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<SectorDTO> actualizarSector(@PathVariable Integer id, @RequestBody SectorDTO sectorDTO) {
        try {
            logger.debug("Solicitud para actualizar sector con ID: {}", id);
            SectorDTO sectorActualizado = sectorService.actualizarSector(id, sectorDTO);
            return ResponseEntity.ok(sectorActualizado);
        } catch (ResourceNotFoundException e) {
            logger.error("Sector no encontrado con ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            logger.error("Error al actualizar sector: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            logger.error("Error interno al actualizar sector: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarSector(@PathVariable Integer id) {
        try {
            logger.debug("Solicitud para eliminar sector con ID: {}", id);
            sectorService.eliminarSector(id);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            logger.error("Sector no encontrado con ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            logger.error("Error al eliminar sector: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error interno al eliminar sector: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<SectorDTO>> listarSectores() {
        try {
            logger.debug("Solicitud para listar sectores");
            List<SectorDTO> sectores = sectorService.listarSectores();
            return ResponseEntity.ok(sectores);
        } catch (Exception e) {
            logger.error("Error interno al listar sectores: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<SectorDTO> obtenerSectorPorId(@PathVariable Integer id) {
        try {
            logger.debug("Solicitud para obtener sector con ID: {}", id);
            SectorDTO sector = sectorService.obtenerSectorPorId(id);
            return ResponseEntity.ok(sector);
        } catch (ResourceNotFoundException e) {
            logger.error("Sector no encontrado con ID: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error interno al obtener sector: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/{id}/check-associations")
    public ResponseEntity<Map<String, Boolean>> checkAssociations(@PathVariable Integer id) {
        try {
            logger.debug("Verificando asociaciones para sector con ID: {}", id);
            boolean hasAssociations = sectorService.checkAssociations(id);
            Map<String, Boolean> response = new HashMap<>();
            response.put("hasAssociations", hasAssociations);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error al verificar asociaciones del sector: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}