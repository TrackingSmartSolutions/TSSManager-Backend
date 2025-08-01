package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.ReporteDTO;
import com.tss.tssmanager_backend.entity.Actividad;
import com.tss.tssmanager_backend.repository.ActividadRepository;
import com.tss.tssmanager_backend.repository.NotaTratoRepository;
import com.tss.tssmanager_backend.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReporteService {

    @Autowired
    private ActividadRepository actividadRepository;
    @Autowired
    private NotaTratoRepository notaTratoRepository;

    public ReporteDTO generarReporteActividades(LocalDate startDate, LocalDate endDate) {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Integer userId = userDetails.getId();

        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endDate.atStartOfDay(ZoneId.systemDefault()).plusDays(1).toInstant();

        // Obtener actividades completadas
        List<Actividad> actividades = actividadRepository.findByUsuarioCompletadoIdAndFechaCompletadoBetween(
                userId, start, end);
        ReporteDTO reporte = new ReporteDTO();

        // Gráfica de Actividades Realizadas
        reporte.setActividades(actividades.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getTipo().name(),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                )).entrySet().stream()
                .map(e -> new ReporteDTO.ActividadCount(e.getKey(), e.getValue(), getColor(e.getKey())))
                .collect(Collectors.toList()));

        // Gráfica de Empresas Contactada
        reporte.setEmpresas(actividades.stream()
                .collect(Collectors.groupingBy(
                        a -> actividadRepository.findEmpresaNameByTratoId(a.getTratoId()),
                        Collectors.counting()
                )).entrySet().stream()
                .map(e -> new ReporteDTO.EmpresaCount(e.getKey(), e.getValue().intValue()))
                .collect(Collectors.toList()));

        // Notas de Interacciones (usando actividades completadas)
        reporte.setNotas(actividades.stream()
                .filter(a -> a.getEstatus() == com.tss.tssmanager_backend.enums.EstatusActividadEnum.CERRADA)
                .map(a -> new ReporteDTO.Nota(
                        actividadRepository.findEmpresaNameByTratoId(a.getTratoId()),
                        a.getNotas() != null ? a.getNotas() : "Sin notas",
                        a.getRespuesta() != null ? a.getRespuesta().name() : "No",
                        a.getInteres() != null ? a.getInteres().name() : "Bajo"
                ))
                .collect(Collectors.toList()));

        reporte.setStartDate(startDate.toString());
        reporte.setEndDate(endDate.toString());

        return reporte;
    }

    private String getColor(String type) {
        switch (type) {
            case "REUNIONES": return "#ff6b6b";
            case "LLAMADAS": return "#4ecdc4";
            case "CORREOS": return "#45b7d1";
            case "MENSAJES": return "#96ceb4";
            default: return "#45b7d1";
        }
    }
}