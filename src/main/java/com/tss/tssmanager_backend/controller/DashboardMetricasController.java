package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.DashboardMetricasDTO;
import com.tss.tssmanager_backend.service.DashboardMetricasService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DashboardMetricasController {

    @Autowired
    private DashboardMetricasService dashboardMetricasService;

    @GetMapping("/metricas")
    public ResponseEntity<Map<String, Object>> obtenerMetricasDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer usuario) {

        try {
            // Establecer fechas por defecto si no se proporcionan
            if (startDate == null) {
                startDate = LocalDate.now().withDayOfMonth(1); // Primer día del mes actual
            }
            if (endDate == null) {
                endDate = LocalDate.now(); // Día actual
            }

            // Validar que startDate no sea posterior a endDate
            if (startDate.isAfter(endDate)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "La fecha de inicio no puede ser posterior a la fecha de fin");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Obtener métricas del dashboard
            DashboardMetricasDTO metricas = dashboardMetricasService.obtenerMetricasDashboard(startDate, endDate, usuario);

            // Preparar respuesta exitosa
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", metricas);
            Map<String, Object> filtros = new HashMap<>();
            filtros.put("startDate", startDate);
            filtros.put("endDate", endDate);
            filtros.put("usuario", usuario);
            response.put("filtros", filtros);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error obteniendo métricas del dashboard: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Error interno del servidor al obtener las métricas");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}