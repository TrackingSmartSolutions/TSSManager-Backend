package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.EventoCalendarioDTO;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.RolUsuarioEnum;
import com.tss.tssmanager_backend.repository.*;
import com.tss.tssmanager_backend.security.CustomUserDetails;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    private NotificacionService notificacionService;

    private static final ZoneId MEXICO_ZONE = ZoneId.of("America/Mexico_City");
    private final Map<Integer, Usuario> usuarioCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initUsuarioCache() {
        usuarioRepository.findAll().forEach(usuario ->
                usuarioCache.put(usuario.getId(), usuario));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "calendario-eventos",
            key = "#startDate + '_' + #endDate + '_' + #usuario",
            unless = "#result.size() > 1000")
    public List<EventoCalendarioDTO> obtenerEventos(Instant startDate, Instant endDate, String usuario) {
        String userRol = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .findFirst().map(authority -> authority.getAuthority()).orElse("ROLE_EMPLEADO");

        Integer userId = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
        LocalDate start = startDate != null ? Instant.ofEpochMilli(startDate.toEpochMilli()).atZone(java.time.ZoneId.systemDefault()).toLocalDate() : LocalDate.now().minusMonths(1);
        LocalDate end = endDate != null ? Instant.ofEpochMilli(endDate.toEpochMilli()).atZone(java.time.ZoneId.systemDefault()).toLocalDate() : LocalDate.now().plusMonths(1);

        List<EventoCalendarioDTO> eventos = new ArrayList<>();

        if ("ROLE_EMPLEADO".equals(userRol)) {
            List<Actividad> actividades = actividadRepository.findByAsignadoAIdAndFechaLimiteBetween(userId, start, end);
            eventos.addAll(processActividadesInTransaction(actividades));
        } else if ("ROLE_ADMINISTRADOR".equals(userRol)) {
            if (usuario == null || usuario.equals("Todos los usuarios")) {
                // Cargar todo en paralelo
                CompletableFuture<List<Actividad>> actividadesFuture = CompletableFuture.supplyAsync(() ->
                        actividadRepository.findByFechaLimiteBetween(start, end));
                CompletableFuture<List<CuentaPorCobrar>> cuentasCobrarFuture = CompletableFuture.supplyAsync(() ->
                        cuentaPorCobrarRepository.findByFechaPagoBetween(start, end));
                CompletableFuture<List<CuentaPorPagar>> cuentasPagarFuture = CompletableFuture.supplyAsync(() ->
                        cuentaPorPagarRepository.findByFechaPagoBetween(start, end));

                // Esperar resultados y procesarlos dentro de la transacción
                List<Actividad> actividades = actividadesFuture.join();
                List<CuentaPorCobrar> cuentasCobrar = cuentasCobrarFuture.join();
                List<CuentaPorPagar> cuentasPagar = cuentasPagarFuture.join();

                eventos.addAll(processActividadesInTransaction(actividades));
                eventos.addAll(convertCuentasPorCobrar(cuentasCobrar));
                eventos.addAll(convertCuentasPorPagar(cuentasPagar));
            } else {
                Usuario assignedUser = usuarioRepository.findByNombre(usuario);
                if (assignedUser == null) {
                    throw new RuntimeException("Usuario no encontrado: " + usuario);
                }
                List<Actividad> actividades = actividadRepository.findByAsignadoAIdAndFechaLimiteBetween(assignedUser.getId(), start, end);
                eventos.addAll(processActividadesInTransaction(actividades));

                if (assignedUser.getRol() == RolUsuarioEnum.ADMINISTRADOR) {
                    eventos.addAll(convertCuentasPorCobrar(cuentaPorCobrarRepository.findByFechaPagoBetween(start, end)));
                    eventos.addAll(convertCuentasPorPagar(cuentaPorPagarRepository.findByFechaPagoBetween(start, end)));
                }
            }
        }

        return eventos;
    }

    private List<EventoCalendarioDTO> processActividadesInTransaction(List<Actividad> actividades) {
        return actividades.stream()
                .map(actividad -> {
                    // Forzar la carga de la relación lazy aquí, dentro de la transacción
                    String tratoNombre = actividad.getTrato().getNombre(); // Esto fuerza la carga
                    return convertActividadToEvento(actividad);
                })
                .collect(Collectors.toList());
    }

    private List<EventoCalendarioDTO> convertCuentasPorCobrar(List<CuentaPorCobrar> cuentas) {
        return cuentas.stream()
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
                .collect(Collectors.toList());
    }

    private List<EventoCalendarioDTO> convertCuentasPorPagar(List<CuentaPorPagar> cuentas) {
        return cuentas.stream()
                .map(cuenta -> {
                    EventoCalendarioDTO.EventoCalendarioDTOBuilder builder = EventoCalendarioDTO.builder()
                            .titulo(cuenta.getCuenta().getCategoria().getDescripcion() + " - " + cuenta.getCuenta().getNombre())
                            .inicio(cuenta.getFechaPago().atStartOfDay().atZone(MEXICO_ZONE).toInstant())
                            .fin(null)
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
        Usuario asignadoA = usuarioCache.computeIfAbsent(actividad.getAsignadoAId(),
                id -> usuarioRepository.findById(id).orElseThrow(() -> new RuntimeException("Usuario no encontrado")));

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