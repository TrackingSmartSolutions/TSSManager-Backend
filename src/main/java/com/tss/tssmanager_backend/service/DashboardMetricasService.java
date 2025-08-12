package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.*;
import com.tss.tssmanager_backend.enums.RolUsuarioEnum;
import com.tss.tssmanager_backend.repository.DashboardMetricasRepository;
import com.tss.tssmanager_backend.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DashboardMetricasService {

    @Autowired
    private DashboardMetricasRepository dashboardMetricasRepository;

    @Transactional(readOnly = true)
    public DashboardMetricasDTO obtenerMetricasDashboard(LocalDate startDate, LocalDate endDate, Integer usuarioId) {
        // Aplicar filtro de empleado si es necesario
        Integer usuarioFiltrado = aplicarFiltroEmpleado(usuarioId);

        // Convertir fechas a Instant
        Instant startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        // Obtener datos de las consultas
        List<EmpresasCreadasDTO> empresasCreadas = obtenerEmpresasCreadas(startInstant, endInstant, usuarioFiltrado);
        List<TasaRespuestaDTO> tasaRespuesta = obtenerTasaRespuesta(startInstant, endInstant, usuarioFiltrado);
        List<TasaConversionDTO> tasaConversion = obtenerTasaConversion(startInstant, endInstant, usuarioFiltrado);
        ResumenEjecutivoDTO resumenEjecutivo = obtenerResumenEjecutivo(startDate, endDate);

        return new DashboardMetricasDTO(resumenEjecutivo, empresasCreadas, tasaRespuesta, tasaConversion);
    }

    private Integer aplicarFiltroEmpleado(Integer usuarioId) {
        Integer currentUserId = getCurrentUserId();
        RolUsuarioEnum currentUserRole = getCurrentUserRole();

        // Si es empleado, solo puede ver sus propias métricas
        if (RolUsuarioEnum.EMPLEADO.equals(currentUserRole)) {
            return currentUserId;
        }

        // Si es administrador, puede ver las métricas del usuario especificado o todas
        return usuarioId;
    }

    private List<EmpresasCreadasDTO> obtenerEmpresasCreadas(Instant startDate, Instant endDate, Integer usuarioId) {
        List<Object[]> results = dashboardMetricasRepository.findEmpresasCreadasPorUsuario(startDate, endDate, usuarioId);

        if (results == null) {
            return new ArrayList<>();
        }

        return results.stream().map(row -> {
            Integer idUsuario = row[0] != null ? (Integer) row[0] : 0;
            String nombreCompleto = row[1] != null ? (String) row[1] : "Sin nombre";
            Long nuevas = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            Long contactadas = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            Long infoEnviada = row[4] != null ? ((Number) row[4]).longValue() : 0L;

            return new EmpresasCreadasDTO(
                    idUsuario,
                    nombreCompleto,
                    nuevas.intValue(),
                    contactadas.intValue(),
                    infoEnviada.intValue()
            );
        }).collect(Collectors.toList());
    }

    private List<TasaRespuestaDTO> obtenerTasaRespuesta(Instant startDate, Instant endDate, Integer usuarioId) {
        List<Object[]> results = dashboardMetricasRepository.findTasaRespuestaPorUsuario(startDate, endDate, usuarioId);

        if (results == null) {
            return new ArrayList<>();
        }

        return results.stream().map(row -> {
            Integer idUsuario = row[0] != null ? (Integer) row[0] : 0;
            String nombreCompleto = row[1] != null ? (String) row[1] : "Sin nombre";
            Long totalLlamadas = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            Long llamadasExitosas = row[3] != null ? ((Number) row[3]).longValue() : 0L;

            Double tasa = totalLlamadas > 0 ? (llamadasExitosas * 100.0) / totalLlamadas : 0.0;

            return new TasaRespuestaDTO(
                    idUsuario,
                    nombreCompleto,
                    totalLlamadas.intValue(),
                    llamadasExitosas.intValue(),
                    Math.round(tasa * 100.0) / 100.0
            );
        }).collect(Collectors.toList());
    }

    private List<TasaConversionDTO> obtenerTasaConversion(Instant startDate, Instant endDate, Integer usuarioId) {
        List<Object[]> results = dashboardMetricasRepository.findTasaConversionPorUsuario(startDate, endDate, usuarioId);

        if (results == null) {
            return new ArrayList<>();
        }

        return results.stream().map(row -> {
            Integer idUsuario = row[0] != null ? (Integer) row[0] : 0;
            String nombreCompleto = row[1] != null ? (String) row[1] : "Sin nombre";
            Long contactadas = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            Long respuestaPositiva = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            Long interesMedio = row[4] != null ? ((Number) row[4]).longValue() : 0L;
            Long reuniones = row[5] != null ? ((Number) row[5]).longValue() : 0L;

            // Calcular tasas de conversión
            Double tasaRespuesta = contactadas > 0 ? (respuestaPositiva * 100.0) / contactadas : 0.0;
            Double tasaInteres = respuestaPositiva > 0 ? (interesMedio * 100.0) / respuestaPositiva : 0.0;
            Double tasaReuniones = interesMedio > 0 ? (reuniones * 100.0) / interesMedio : 0.0;

            return new TasaConversionDTO(
                    idUsuario,
                    nombreCompleto,
                    contactadas.intValue(),
                    respuestaPositiva.intValue(),
                    interesMedio.intValue(),
                    reuniones.intValue(),
                    Math.round(tasaRespuesta * 100.0) / 100.0,
                    Math.round(tasaInteres * 100.0) / 100.0,
                    Math.round(tasaReuniones * 100.0) / 100.0
            );
        }).collect(Collectors.toList());
    }

    private ResumenEjecutivoDTO obtenerResumenEjecutivo(LocalDate startDate, LocalDate endDate) {
        Instant startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        // Obtener métricas actuales
        List<Object[]> currentResultsList = dashboardMetricasRepository.findResumenEjecutivo(startInstant, endInstant);

        // Calcular período anterior equivalente
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        LocalDate startDatePrev = startDate.minusDays(daysBetween);
        LocalDate endDatePrev = endDate.minusDays(daysBetween);

        Instant startInstantPrev = startDatePrev.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endInstantPrev = endDatePrev.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        // Obtener métricas del período anterior
        List<Object[]> previousResultsList = dashboardMetricasRepository.findResumenEjecutivoPeriodoAnterior(startInstantPrev, endInstantPrev);

        // Verificar que las listas no estén vacías y obtener el primer (y único) elemento
        Object[] currentResults = (currentResultsList != null && !currentResultsList.isEmpty()) ? currentResultsList.get(0) : new Object[4];
        Object[] previousResults = (previousResultsList != null && !previousResultsList.isEmpty()) ? previousResultsList.get(0) : new Object[4];

        // Extraer valores actuales - Verificar que no sean null
        Integer totalEmpresas = currentResults[0] != null ? ((Number) currentResults[0]).intValue() : 0;
        Double promedioContacto = currentResults[1] != null ? ((Number) currentResults[1]).doubleValue() : 0.0;
        Double tasaRespuestaGlobal = currentResults[2] != null ? ((Number) currentResults[2]).doubleValue() : 0.0;
        Double tasaConversionGlobal = currentResults[3] != null ? ((Number) currentResults[3]).doubleValue() : 0.0;

        // Extraer valores anteriores - Verificar que no sean null
        Integer totalEmpresasPrev = previousResults[0] != null ? ((Number) previousResults[0]).intValue() : 0;
        Double promedioContactoPrev = previousResults[1] != null ? ((Number) previousResults[1]).doubleValue() : 0.0;
        Double tasaRespuestaGlobalPrev = previousResults[2] != null ? ((Number) previousResults[2]).doubleValue() : 0.0;
        Double tasaConversionGlobalPrev = previousResults[3] != null ? ((Number) previousResults[3]).doubleValue() : 0.0;

        // Calcular tendencias
        TendenciasDTO tendencias = new TendenciasDTO(
                calcularTendencia(totalEmpresas, totalEmpresasPrev),
                calcularTendencia(promedioContacto, promedioContactoPrev),
                calcularTendencia(tasaRespuestaGlobal, tasaRespuestaGlobalPrev),
                calcularTendencia(tasaConversionGlobal, tasaConversionGlobalPrev)
        );

        return new ResumenEjecutivoDTO(
                totalEmpresas,
                Math.round(promedioContacto * 100.0) / 100.0,
                Math.round(tasaRespuestaGlobal * 100.0) / 100.0,
                Math.round(tasaConversionGlobal * 100.0) / 100.0,
                tendencias
        );
    }

    private String calcularTendencia(Double valorActual, Double valorAnterior) {
        if (valorAnterior == null || valorAnterior == 0.0) {
            return valorActual != null && valorActual > 0.0 ? "up" : "stable";
        }

        if (valorActual == null) {
            return "down";
        }

        double diferencia = Math.abs(valorActual - valorAnterior);
        // Considerar estable si la diferencia es menor al 1%
        if (diferencia < 0.01) {
            return "stable";
        }

        if (valorActual > valorAnterior) {
            return "up";
        } else {
            return "down";
        }
    }

    private String calcularTendencia(Integer valorActual, Integer valorAnterior) {
        if (valorAnterior == null || valorAnterior == 0) {
            return valorActual != null && valorActual > 0 ? "up" : "stable";
        }

        if (valorActual == null) {
            return "down";
        }

        if (valorActual > valorAnterior) {
            return "up";
        } else if (valorActual < valorAnterior) {
            return "down";
        } else {
            return "stable";
        }
    }

    private Integer getCurrentUserId() {
        return ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
    }

    private RolUsuarioEnum getCurrentUserRole() {
        return ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getRol();
    }
}