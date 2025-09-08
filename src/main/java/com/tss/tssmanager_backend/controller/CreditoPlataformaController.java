package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.DashboardCreditosDTO;
import com.tss.tssmanager_backend.service.CreditoPlataformaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/creditos-plataforma")
public class CreditoPlataformaController {

    @Autowired
    private CreditoPlataformaService service;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardCreditosDTO> getDashboardData(
            @RequestParam String fechaInicio,
            @RequestParam String fechaFin,
            @RequestParam(defaultValue = "Todos") String plataforma) {

        LocalDate inicio = LocalDate.parse(fechaInicio);
        LocalDate fin = LocalDate.parse(fechaFin);

        DashboardCreditosDTO data = service.getDashboardData(inicio, fin, plataforma);
        return ResponseEntity.ok(data);
    }
}