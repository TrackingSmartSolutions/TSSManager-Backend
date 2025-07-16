package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.EventoCalendarioDTO;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.EstatusActividadEnum;
import com.tss.tssmanager_backend.enums.RolUsuarioEnum;
import com.tss.tssmanager_backend.repository.*;
import com.tss.tssmanager_backend.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CalendarioService {

    @Autowired
    private ActividadRepository actividadRepository;
    @Autowired
    private SimRepository simRepository;
    @Autowired
    private CuentaPorCobrarRepository cuentaPorCobrarRepository;
    @Autowired
    private CuentaPorPagarRepository cuentaPorPagarRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private NotificacionService notificacionService;

    private static final ZoneId MEXICO_ZONE = ZoneId.of("America/Mexico_City");

    @Transactional(readOnly = true)
    public List<EventoCalendarioDTO> obtenerEventos(Instant startDate, Instant endDate, String usuario) {
        String userRol = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .findFirst().map(authority -> authority.getAuthority()).orElse("ROLE_EMPLEADO");

        Integer userId = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
        LocalDate start = startDate != null ? Instant.ofEpochMilli(startDate.toEpochMilli()).atZone(java.time.ZoneId.systemDefault()).toLocalDate() : LocalDate.now().minusMonths(1);
        LocalDate end = endDate != null ? Instant.ofEpochMilli(endDate.toEpochMilli()).atZone(java.time.ZoneId.systemDefault()).toLocalDate() : LocalDate.now().plusMonths(1);

        // Consulta personalizada para Actividad
        List<Actividad> actividades;
        boolean shouldShowRecargasAndCuentas = false;
        boolean shouldShowCuentas = false;

        if ("ROLE_EMPLEADO".equals(userRol)) {
            actividades = actividadRepository.findByAsignadoAIdAndFechaLimiteBetween(userId, start, end);
            shouldShowRecargasAndCuentas = true;
            shouldShowCuentas = false;
        } else if ("ROLE_ADMINISTRADOR".equals(userRol)) {
            // ADMINISTRADOR
            if (usuario == null || usuario.equals("Todos los usuarios")) {
                actividades = actividadRepository.findByFechaLimiteBetween(start, end);
                shouldShowRecargasAndCuentas = true;
                shouldShowCuentas = true;
            } else {
                Usuario assignedUser = usuarioRepository.findByNombre(usuario);
                if (assignedUser == null) {
                    throw new RuntimeException("Usuario no encontrado: " + usuario);
                }
                actividades = actividadRepository.findByAsignadoAIdAndFechaLimiteBetween(assignedUser.getId(), start, end);
                shouldShowRecargasAndCuentas = true;
                shouldShowCuentas = assignedUser.getRol() == RolUsuarioEnum.ADMINISTRADOR;

            }
        } else {
            actividades = actividadRepository.findByAsignadoAIdAndFechaLimiteBetween(userId, start, end);
        }
        List<EventoCalendarioDTO> eventos = actividades.stream()
                .map(this::convertActividadToEvento)
                .collect(Collectors.toList());

        // Solo agregar eventos adicionales (recargas, cuentas) si es administrador
        if (shouldShowRecargasAndCuentas) {
            eventos.addAll(simRepository.findAll().stream()
                    .filter(sim -> sim.getVigencia() != null &&
                            !sim.getVigencia().toLocalDate().isBefore(start) &&
                            !sim.getVigencia().toLocalDate().isAfter(end))
                    .map(sim -> EventoCalendarioDTO.builder()
                            .titulo("Recarga - " + sim.getNumero() + " - " + (sim.getEquipo() != null ? sim.getEquipo().getImei() : "Sin IMEI"))
                            .inicio(sim.getVigencia().toLocalDate().atStartOfDay().atZone(MEXICO_ZONE).toInstant())
                            .fin(null)
                            .allDay(true)
                            .color("#f59e0b")
                            .tipo("Recarga")
                            .numeroSim(sim.getNumero())
                            .imei(sim.getEquipo() != null ? sim.getEquipo().getImei() : null)
                            .build())
                    .collect(Collectors.toList()));

            if (shouldShowCuentas) {
                // Agregar cuentas por cobrar - configuradas como eventos de todo el día
                eventos.addAll(cuentaPorCobrarRepository.findAll().stream()
                        .filter(cuenta -> cuenta.getFechaPago() != null &&
                                !cuenta.getFechaPago().isBefore(start) &&
                                !cuenta.getFechaPago().isAfter(end))
                        .map(cuenta -> EventoCalendarioDTO.builder()
                                .titulo("Cuenta por Cobrar - " + cuenta.getFolio() + " - " + cuenta.getCliente().getNombre())
                                .inicio(cuenta.getFechaPago().atStartOfDay().atZone(MEXICO_ZONE).toInstant())
                                .fin(null)
                                .allDay(true)
                                .color("#ef4444")
                                .tipo("Cuenta por Cobrar")
                                .numeroCuenta(cuenta.getFolio())
                                .cliente(cuenta.getCliente().getNombre())
                                .estado(cuenta.getEstatus().name())
                                .esquema(cuenta.getEsquema().name())
                                .build())
                        .collect(Collectors.toList()));

                // Agregar cuentas por pagar - configuradas como eventos de todo el día
                eventos.addAll(cuentaPorPagarRepository.findAll().stream()
                        .filter(cuenta -> cuenta.getFechaPago() != null &&
                                !cuenta.getFechaPago().isBefore(start) &&
                                !cuenta.getFechaPago().isAfter(end))
                        .map(cuenta -> EventoCalendarioDTO.builder()
                                .titulo("Cuenta por Pagar - " + cuenta.getFolio() + " - " + cuenta.getCuenta().getNombre())
                                .inicio(cuenta.getFechaPago().atStartOfDay().atZone(MEXICO_ZONE).toInstant())
                                .fin(null)
                                .allDay(true)
                                .color("#8b5cf6")
                                .tipo("Cuenta por Pagar")
                                .numeroCuenta(cuenta.getFolio())
                                .cliente(cuenta.getCuenta().getNombre())
                                .estado(cuenta.getEstatus())
                                .build())
                        .collect(Collectors.toList()));
            }
        }

        notificacionService.generarNotificacionCuentasYSims();
        return eventos;
    }

    private EventoCalendarioDTO convertActividadToEvento(Actividad actividad) {
        Usuario asignadoA = usuarioRepository.findById(actividad.getAsignadoAId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        String title = actividad.getTipo().name() + " - " + actividad.getTrato().getNombre();

        Instant inicio = actividad.getFechaLimite().atStartOfDay().atZone(MEXICO_ZONE).toInstant();
        Instant fin;
        boolean allDay = false;

        if (actividad.getHoraInicio() == null) {
            fin = null;
            allDay = true;
        } else {
            fin = actividad.getFechaLimite().atTime(actividad.getHoraInicio().toLocalTime()).atZone(MEXICO_ZONE).toInstant();
            allDay = false;
        }

        return EventoCalendarioDTO.builder()
                .titulo(title)
                .inicio(inicio)
                .fin(fin)
                .allDay(allDay)
                .color(getColorByType(actividad.getTipo().name()))
                .tipo(actividad.getTipo().name())
                .asignadoA(asignadoA.getNombre())
                .trato(actividad.getTrato().getNombre())
                .modalidad(actividad.getModalidad() != null ? actividad.getModalidad().name() : null)
                .medio(actividad.getMedio() != null ? actividad.getMedio().name() : null)
                .build();
    }

    private String getColorByType(String type) {
        return switch (type) {
            case "REUNION" -> "#3b82f6";
            case "LLAMADA" -> "#10b981";
            case "TAREA" -> "#f50bc3";
            default -> "#d1d5db";
        };
    }
}