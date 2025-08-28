package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.EmpresaConCoordenadasDTO;
import com.tss.tssmanager_backend.entity.Empresa;
import com.tss.tssmanager_backend.repository.EmpresaRepository;
import com.tss.tssmanager_backend.service.GeocodingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class GeocodingController {

    @Autowired
    private GeocodingService geocodingService;
    @Autowired
    private EmpresaRepository empresaRepository;

    @GetMapping("/coordenadas/empresas")
    public ResponseEntity<List<EmpresaConCoordenadasDTO>> getEmpresasConCoordenadas() {
        try {
            List<EmpresaConCoordenadasDTO> empresas = geocodingService.getEmpresasConCoordenadas();
            return ResponseEntity.ok(empresas);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/coordenadas/preprocess")
    public ResponseEntity<String> preprocessAddresses() {
        try {
            // Ejecutar en thread separado para no bloquear
            new Thread(() -> geocodingService.preprocessAddresses()).start();
            return ResponseEntity.ok("Preprocesamiento iniciado en background");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/coordenadas/debug")
    public ResponseEntity<Map<String, Object>> debugEmpresas() {
        try {
            // Query simple para verificar datos
            List<Empresa> todasEmpresas = empresaRepository.findAll();
            long conDomicilio = todasEmpresas.stream()
                    .filter(e -> e.getDomicilioFisico() != null && !e.getDomicilioFisico().trim().isEmpty())
                    .count();

            Map<String, Object> debug = new HashMap<>();
            debug.put("totalEmpresas", todasEmpresas.size());
            debug.put("conDomicilioFisico", conDomicilio);

            // Mostrar primeros 5 domicilios para verificar
            List<String> ejemplos = todasEmpresas.stream()
                    .filter(e -> e.getDomicilioFisico() != null && !e.getDomicilioFisico().trim().isEmpty())
                    .limit(5)
                    .map(Empresa::getDomicilioFisico)
                    .toList();
            debug.put("ejemplosDomicilios", ejemplos);

            return ResponseEntity.ok(debug);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.ok(error);
        }
    }
}