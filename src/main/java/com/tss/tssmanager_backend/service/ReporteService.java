package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.ReporteDTO;
import com.tss.tssmanager_backend.entity.Actividad;
import com.tss.tssmanager_backend.entity.Usuario;
import com.tss.tssmanager_backend.repository.ActividadRepository;
import com.tss.tssmanager_backend.repository.NotaTratoRepository;
import com.tss.tssmanager_backend.repository.UsuarioRepository;
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
    @Autowired
    private UsuarioRepository usuarioRepository;

    public ReporteDTO generarReporteActividades(LocalDate startDate, LocalDate endDate, String nombreUsuario) {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Integer userId;

        // Si se especifica un usuario y el usuario actual es ADMINISTRADOR
        if (nombreUsuario != null && userDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMINISTRADOR") ||
                        auth.getAuthority().equals("ROLE_GESTOR"))) {
            Usuario usuario = usuarioRepository.findByNombre(nombreUsuario);
            System.out.println("Buscando usuario: '" + nombreUsuario + "'"); // Debug
            System.out.println("Usuario encontrado: " + (usuario != null ? usuario.getNombre() : "null")); // Debug

            if (usuario == null) {
                // Intentar búsqueda case-insensitive
                List<Usuario> todosUsuarios = usuarioRepository.findAll();
                usuario = todosUsuarios.stream()
                        .filter(u -> u.getNombre().equalsIgnoreCase(nombreUsuario.trim()))
                        .findFirst()
                        .orElse(null);

                if (usuario == null) {
                    throw new RuntimeException("Usuario no encontrado: '" + nombreUsuario + "'. Usuarios disponibles: " +
                            todosUsuarios.stream().map(Usuario::getNombre).collect(Collectors.toList()));
                }
            }
            userId = usuario.getId();
        } else {
            userId = userDetails.getId();
        }

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
                        Collectors.groupingBy(
                                a -> getMedio(a),
                                Collectors.counting()
                        )
                )).entrySet().stream()
                .flatMap(tipoEntry ->
                        tipoEntry.getValue().entrySet().stream()
                                .map(medioEntry -> new ReporteDTO.ActividadCount(
                                        medioEntry.getKey(),
                                        medioEntry.getValue().intValue(),
                                        getColor(tipoEntry.getKey()),
                                        tipoEntry.getKey()
                                ))
                )
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

    private String getMedio(Actividad actividad) {
        String tipo = actividad.getTipo().name();
        String medio = actividad.getMedio() != null ? actividad.getMedio().name() : "SIN_MEDIO";

        if ("TAREAS".equals(tipo)) {
            switch (medio) {
                case "ACTIVIDAD": return "Actividad";
                case "CORREO": return "Correo";
                case "MENSAJE": return "Mensaje";
                default: return "Otros";
            }
        }
        else if ("LLAMADAS".equals(tipo)) {
            switch (medio) {
                case "TELEFONO": return "Teléfono";
                case "WHATSAPP": return "WhatsApp";
                default: return "Otros";
            }
        }
        else if ("REUNIONES".equals(tipo)) {
            switch (medio) {
                case "VIRTUAL": return "Virtual";
                case "PRESENCIAL": return "Presencial";
                default: return "Otros";
            }
        }

        return medio;
    }
}