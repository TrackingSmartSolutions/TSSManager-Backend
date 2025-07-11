package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.entity.ConfiguracionEmpresa;
import com.tss.tssmanager_backend.service.ConfiguracionEmpresaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/configuracion")
public class ConfiguracionEmpresaController {

    @Autowired
    private ConfiguracionEmpresaService configuracionEmpresaService;

    @GetMapping("/empresa")
    public ResponseEntity<ConfiguracionEmpresa> obtenerConfiguracion() {
        return ResponseEntity.ok(configuracionEmpresaService.obtenerConfiguracion());
    }

    @PostMapping("/empresa")
    public ResponseEntity<ConfiguracionEmpresa> guardarConfiguracion(
            @RequestPart("configuracion") ConfiguracionEmpresa configuracion,
            @RequestPart(value = "logo", required = false) MultipartFile logo) throws IOException {
        try {
            ConfiguracionEmpresa savedConfig = configuracionEmpresaService.guardarConfiguracion(configuracion, logo);
            return ResponseEntity.ok(savedConfig);
        } catch (Exception e) {
            if (e.getCause() instanceof org.hibernate.StaleObjectStateException) {
                return ResponseEntity.status(409).body(null);
            }
            return ResponseEntity.status(500).body(null);
        }
    }
}