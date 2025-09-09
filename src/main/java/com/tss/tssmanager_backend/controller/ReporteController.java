package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.ReporteDTO;
import com.tss.tssmanager_backend.service.ReporteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    @Autowired
    private ReporteService reporteService;

    @GetMapping("/actividades")
    public ReporteDTO getActividadesReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String usuario) {
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now();
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        return reporteService.generarReporteActividades(start, end, usuario);
    }
}