package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.EventoCalendarioDTO;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.EstatusActividadEnum;
import com.tss.tssmanager_backend.enums.EstatusPagoEnum;
import com.tss.tssmanager_backend.enums.RolUsuarioEnum;
import com.tss.tssmanager_backend.repository.*;
import com.tss.tssmanager_backend.security.CustomUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CalendarioService {

    @Autowired
    private ActividadRepository actividadRepository;
    @Autowired
    private CuentaPorCobrarRepository cuentaPorCobrarRepository;
    @Autowired
    private CuentaPorPagarRepository cuentaPorPagarRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private TratoRepository tratoRepository;
    @Autowired
    private NotificacionService notificacionService;

    private static final Logger logger = LoggerFactory.getLogger(CalendarioService.class);
    private static final ZoneId MEXICO_ZONE = ZoneId.of("America/Mexico_City");

    @Cacheable(value = "calendario-eventos", key = "'eventos_' + #startDate + '_' + #endDate + '_' + #usuario + '_' + T(org.springframework.security.core.context.SecurityContextHolder).context.authentication.authorities.iterator().next().authority")
    @Transactional(readOnly = true)
    public List<EventoCalendarioDTO> obtenerEventos(Instant startDate, Instant endDate, String usuario) {
        String userRol = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .findFirst().map(authority -> authority.getAuthority()).orElse("ROLE_EMPLEADO");

        Integer userId = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
        LocalDate start = startDate != null ? startDate.atZone(ZoneId.systemDefault()).toLocalDate() : LocalDate.now().minusMonths(1);
        LocalDate end = endDate != null ? endDate.atZone(ZoneId.systemDefault()).toLocalDate() : LocalDate.now().plusMonths(1);

        List<EventoCalendarioDTO> eventos = new ArrayList<>();

        // Obtener actividades con una sola consulta optimizada
        List<Actividad> actividades = obtenerActividadesOptimizada(userRol, userId, usuario, start, end);

        // Cargar usuarios y tratos en batch para evitar N+1
        Set<Integer> userIds = actividades.stream().map(Actividad::getAsignadoAId).collect(Collectors.toSet());
        Set<Integer> tratoIds = actividades.stream().map(Actividad::getTratoId).collect(Collectors.toSet());

        Map<Integer, Usuario> usuariosMap = usuarioRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));
        Map<Integer, Trato> tratosMap = tratoRepository.findAllById(tratoIds).stream()
                .collect(Collectors.toMap(Trato::getId, Function.identity()));

        // Convertir actividades a eventos
        eventos.addAll(actividades.stream()
                .map(actividad -> convertActividadToEventoOptimizada(actividad, usuariosMap, tratosMap))
                .collect(Collectors.toList()));

        // Solo cargar cuentas para administradores o usuarios específicos
        boolean shouldShowCuentas = determinarSiMostrarCuentas(userRol, usuario);
        if (shouldShowCuentas) {
            // Cargar cuentas con consultas optimizadas
            eventos.addAll(obtenerCuentasPorCobrarOptimizada(start, end));
            eventos.addAll(obtenerCuentasPorPagarOptimizada(start, end));
        }

        return eventos;
    }

    private List<Actividad> obtenerActividadesOptimizada(String userRol, Integer userId, String usuario, LocalDate start, LocalDate end) {
        if ("ROLE_EMPLEADO".equals(userRol)) {
            return actividadRepository.findByAsignadoAIdAndFechaLimiteBetweenAndEstatusNot(userId, start, end, EstatusActividadEnum.CERRADA);
        } else if ("ROLE_ADMINISTRADOR".equals(userRol)) {
            if (usuario == null || usuario.equals("Todos los usuarios")) {
                return actividadRepository.findByFechaLimiteBetweenAndEstatusNot(start, end, EstatusActividadEnum.CERRADA);
            } else {
                Usuario assignedUser = usuarioRepository.findByNombre(usuario);
                if (assignedUser == null) {
                    throw new RuntimeException("Usuario no encontrado: " + usuario);
                }
                return actividadRepository.findByAsignadoAIdAndFechaLimiteBetweenAndEstatusNot(assignedUser.getId(), start, end, EstatusActividadEnum.CERRADA);
            }
        }
        return actividadRepository.findByAsignadoAIdAndFechaLimiteBetweenAndEstatusNot(userId, start, end, EstatusActividadEnum.CERRADA);
    }

    private boolean determinarSiMostrarCuentas(String userRol, String usuario) {
        if ("ROLE_EMPLEADO".equals(userRol)) {
            return false;
        } else if ("ROLE_ADMINISTRADOR".equals(userRol)) {
            if (usuario == null || usuario.equals("Todos los usuarios")) {
                return true;
            } else {
                Usuario assignedUser = usuarioRepository.findByNombre(usuario);
                return assignedUser != null && assignedUser.getRol() == RolUsuarioEnum.ADMINISTRADOR;
            }
        }
        return false;
    }

    private EventoCalendarioDTO convertActividadToEventoOptimizada(Actividad actividad, Map<Integer, Usuario> usuariosMap, Map<Integer, Trato> tratosMap) {
        Usuario asignadoA = usuariosMap.get(actividad.getAsignadoAId());
        Trato trato = tratosMap.get(actividad.getTratoId());

        if (asignadoA == null || trato == null) {
            logger.warn("Datos faltantes para actividad {}: usuario={}, trato={}",
                    actividad.getId(), asignadoA != null, trato != null);
            return null;
        }

        String title = actividad.getTipo().name() + " - " + trato.getNombre();
        Instant inicio;
        Instant fin = null;
        boolean allDay = actividad.getHoraInicio() == null;

        if (allDay) {
            inicio = actividad.getFechaLimite().atStartOfDay().atZone(MEXICO_ZONE).toInstant();
        } else {
            inicio = actividad.getFechaLimite().atTime(actividad.getHoraInicio().toLocalTime()).atZone(MEXICO_ZONE).toInstant();
            fin = inicio.plus(10, ChronoUnit.MINUTES);
        }

        return EventoCalendarioDTO.builder()
                .titulo(title)
                .inicio(inicio)
                .fin(fin)
                .allDay(allDay)
                .color(getColorByType(actividad.getTipo().name()))
                .tipo(actividad.getTipo().name())
                .asignadoA(asignadoA.getNombre())
                .trato(trato.getNombre())
                .tratoId(trato.getId())
                .modalidad(actividad.getModalidad() != null ? actividad.getModalidad().name() : null)
                .medio(actividad.getMedio() != null ? actividad.getMedio().name() : null)
                .build();
    }

    private List<EventoCalendarioDTO> obtenerCuentasPorCobrarOptimizada(LocalDate start, LocalDate end) {
        return cuentaPorCobrarRepository.findByFechaPagoBetweenAndEstatusNot(start, end, EstatusPagoEnum.PAGADO)
                .stream()
                .map(cuenta -> EventoCalendarioDTO.builder()
                        .titulo("Cuenta por Cobrar - " + cuenta.getFolio() + " - " + cuenta.getCliente().getNombre())
                        .inicio(cuenta.getFechaPago().atStartOfDay().atZone(MEXICO_ZONE).toInstant())
                        .allDay(true)
                        .color("#ef4444")
                        .tipo("Cuenta por Cobrar")
                        .numeroCuenta(cuenta.getFolio())
                        .cliente(cuenta.getCliente().getNombre())
                        .estado(cuenta.getEstatus().name())
                        .esquema(cuenta.getEsquema().name())
                        .build())
                .collect(Collectors.toList());
    }

    private List<EventoCalendarioDTO> obtenerCuentasPorPagarOptimizada(LocalDate start, LocalDate end) {
        return cuentaPorPagarRepository.findByFechaPagoBetweenAndEstatusNot(start, end, "Pagado")
                .stream()
                .map(cuenta -> {
                    EventoCalendarioDTO.EventoCalendarioDTOBuilder builder = EventoCalendarioDTO.builder()
                            .titulo(cuenta.getCuenta().getCategoria().getDescripcion() + " - " + cuenta.getCuenta().getNombre() +
                                    (cuenta.getSim() != null ? " - " + cuenta.getSim().getId() : ""))
                            .inicio(cuenta.getFechaPago().atStartOfDay().atZone(MEXICO_ZONE).toInstant())
                            .allDay(true)
                            .color("#8b5cf6")
                            .tipo("Cuenta por Pagar")
                            .numeroCuenta(cuenta.getFolio())
                            .cliente(cuenta.getCuenta().getNombre())
                            .estado(cuenta.getEstatus())
                            .monto(cuenta.getMonto())
                            .nota(cuenta.getNota())
                            .id(cuenta.getId().toString());

                    if (cuenta.getSim() != null) {
                        builder.numeroSim(cuenta.getSim().getNumero());
                    }
                    return builder.build();
                })
                .collect(Collectors.toList());
    }

    private EventoCalendarioDTO convertActividadToEvento(Actividad actividad) {
        Usuario asignadoA = usuarioRepository.findById(actividad.getAsignadoAId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        String title = actividad.getTipo().name() + " - " + actividad.getTrato().getNombre();

        Instant inicio;
        Instant fin;
        boolean allDay = false;

        if (actividad.getHoraInicio() == null) {
            inicio = actividad.getFechaLimite().atStartOfDay().atZone(MEXICO_ZONE).toInstant();
            fin = null;
            allDay = true;
        } else {
            inicio = actividad.getFechaLimite().atTime(actividad.getHoraInicio().toLocalTime()).atZone(MEXICO_ZONE).toInstant();
            // Agregar 10 minutos a la hora de inicio para crear la hora de fin
            fin = actividad.getFechaLimite().atTime(actividad.getHoraInicio().toLocalTime().plusMinutes(10)).atZone(MEXICO_ZONE).toInstant();
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
                .tratoId(actividad.getTrato().getId())
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

    @CacheEvict(value = "calendario-eventos", allEntries = true)
    public void invalidarCacheCalendario() {
        logger.info("Cache del calendario invalidado");
    }
}