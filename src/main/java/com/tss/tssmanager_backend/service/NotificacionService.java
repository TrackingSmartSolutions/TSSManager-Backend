package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.EstatusNotificacionEnum;
import com.tss.tssmanager_backend.enums.EstatusUsuarioEnum;
import com.tss.tssmanager_backend.enums.RolUsuarioEnum;
import com.tss.tssmanager_backend.repository.*;
import com.tss.tssmanager_backend.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class NotificacionService {

    private static final Logger logger = LoggerFactory.getLogger(NotificacionService.class);

    @Autowired
    private NotificacionRepository notificacionRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ActividadRepository actividadRepository;
    @Autowired
    private TratoRepository tratoRepository;
    @Autowired
    private SimRepository simRepository;
    @Autowired
    private CuentaPorCobrarRepository cuentaPorCobrarRepository;
    @Autowired
    private CuentaPorPagarRepository cuentaPorPagarRepository;
    @Autowired
    private EmpresaRepository empresaRepository;

    @Transactional
    public void generarNotificacionCambiarContrasena(String usuarioNombre, String usuarioCorreo) {
        List<Usuario> admins = usuarioRepository.findByRolAndEstatusOrderById(RolUsuarioEnum.ADMINISTRADOR, EstatusUsuarioEnum.ACTIVO);
        String mensaje = String.format("Solicitud de cambio de contraseña para %s (%s)", usuarioNombre, usuarioCorreo);

        admins.forEach(admin -> {
            // Verificar si ya existe una notificación similar reciente
            if (!existeNotificacionReciente(admin.getId(), "CAMBIO_CONTRASENA", mensaje)) {
                crearNotificacion(admin, "CAMBIO_CONTRASENA", mensaje);
                logger.info("Notificación de cambio de contraseña enviada al admin: {}", admin.getNombre());
            }
        });
    }

    @Transactional
    public void generarNotificacionActividad(Actividad actividad) {
        Integer asignadoAId = actividad.getAsignadoAId();
        Integer tratoId = actividad.getTratoId();

        try {
            Trato trato = tratoRepository.findById(tratoId)
                    .orElseThrow(() -> new RuntimeException("Trato no encontrado"));
            Usuario asignadoA = usuarioRepository.findById(asignadoAId)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            Usuario propietario = usuarioRepository.findById(trato.getPropietarioId())
                    .orElseThrow(() -> new RuntimeException("Propietario no encontrado"));

            LocalDate fechaActividad = actividad.getFechaLimite();
            LocalDate hoy = LocalDate.now();
            LocalDate manana = hoy.plusDays(1);

            // Verificar si es el día antes o el mismo día
            if (fechaActividad.equals(manana) || fechaActividad.equals(hoy)) {
                String tipoMensaje = fechaActividad.equals(manana) ? "mañana" : "hoy";
                String mensaje = String.format("Actividad %s programada para %s: %s, Fecha: %s, Hora: %s, Descripción: %s",
                        actividad.getTipo().name(),
                        tipoMensaje,
                        trato.getNombre(),
                        fechaActividad,
                        actividad.getHoraInicio() != null ? actividad.getHoraInicio().toLocalTime() : "Todo el día",
                        actividad.getFinalidad());

                // Notificar al asignado (evitar duplicados)
                if (!existeNotificacionReciente(asignadoA.getId(), "ACTIVIDAD", mensaje)) {
                    crearNotificacion(asignadoA, "ACTIVIDAD", mensaje);
                }

                // Notificar al propietario (evitar duplicados y auto-notificación)
                if (!propietario.getId().equals(asignadoA.getId()) &&
                        !existeNotificacionReciente(propietario.getId(), "ACTIVIDAD", mensaje)) {
                    crearNotificacion(propietario, "ACTIVIDAD", mensaje);
                }

                logger.info("Notificaciones de actividad enviadas para: {}", trato.getNombre());
            }
        } catch (Exception e) {
            logger.error("Error al generar notificación de actividad: {}", e.getMessage());
        }
    }

    @Transactional
    public void generarNotificacionTratoGanado(Trato trato) {
        if ("CERRADO_GANADO".equals(trato.getFase())) {
            List<Usuario> admins = usuarioRepository.findByRolAndEstatusOrderById(RolUsuarioEnum.ADMINISTRADOR, EstatusUsuarioEnum.ACTIVO);
            String mensaje = String.format("Trato ganado: %s, Empresa: %s",
                    trato.getNombre(),
                    trato.getEmpresaId() != null ?
                            empresaRepository.findById(trato.getEmpresaId()).map(Empresa::getNombre).orElse("Sin empresa") :
                            "Sin empresa");

            admins.forEach(admin -> {
                if (!existeNotificacionReciente(admin.getId(), "TRATO_GANADO", mensaje)) {
                    crearNotificacion(admin, "TRATO_GANADO", mensaje);
                }
            });

            logger.info("Notificaciones de trato ganado enviadas para: {}", trato.getNombre());
        }
    }

    @Transactional
    public void generarNotificacionEscalamiento(Trato trato, Integer adminId) {
        try {
            Usuario admin = usuarioRepository.findById(adminId)
                    .orElseThrow(() -> new RuntimeException("Administrador no encontrado"));
            String mensaje = String.format("Escalamiento de trato: %s, Empresa: %s, Fase: %s",
                    trato.getNombre(),
                    trato.getEmpresaId() != null ?
                            empresaRepository.findById(trato.getEmpresaId()).map(Empresa::getNombre).orElse("Sin empresa") :
                            "Sin empresa",
                    trato.getFase());

            if (!existeNotificacionReciente(admin.getId(), "ESCALAMIENTO", mensaje)) {
                crearNotificacion(admin, "ESCALAMIENTO", mensaje);
                logger.info("Notificación de escalamiento enviada al admin: {}", admin.getNombre());
            }
        } catch (Exception e) {
            logger.error("Error al generar notificación de escalamiento: {}", e.getMessage());
        }
    }

    // Método programado para ejecutarse todos los días a las 8:00 AM
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void verificarNotificacionesProgramadas() {
        logger.info("Iniciando verificación de notificaciones programadas");
        generarNotificacionCuentasYSims();
        verificarActividadesProximas();
        logger.info("Verificación de notificaciones programadas completada");
    }

    // Método separado para verificar actividades próximas
    @Transactional
    public void verificarActividadesProximas() {
        LocalDate hoy = LocalDate.now();
        LocalDate manana = hoy.plusDays(1);

        // Buscar actividades para hoy y mañana
        List<Actividad> actividadesHoy = actividadRepository.findByFechaLimite(hoy);
        List<Actividad> actividadesManana = actividadRepository.findByFechaLimite(manana);

        // Procesar actividades de hoy
        actividadesHoy.forEach(this::generarNotificacionActividad);

        // Procesar actividades de mañana
        actividadesManana.forEach(this::generarNotificacionActividad);
    }

    @Transactional
    public void generarNotificacionCuentasYSims() {
        LocalDate hoy = LocalDate.now();
        LocalDate manana = hoy.plusDays(1);

        try {
            // Cuentas por cobrar
            procesarCuentasPorCobrar(hoy, manana);

            // Cuentas por pagar
            procesarCuentasPorPagar(hoy, manana);

            // SIMs
            procesarRecargarSims(hoy, manana);

        } catch (Exception e) {
            logger.error("Error al generar notificaciones de cuentas y SIMs: {}", e.getMessage());
        }
    }

    private void procesarCuentasPorCobrar(LocalDate hoy, LocalDate manana) {
        cuentaPorCobrarRepository.findAll().stream()
                .filter(cuenta -> cuenta.getFechaPago() != null)
                .filter(cuenta -> cuenta.getFechaPago().equals(hoy) || cuenta.getFechaPago().equals(manana))
                .forEach(cuenta -> {
                    String tipoMensaje = cuenta.getFechaPago().equals(manana) ? "mañana" : "hoy";
                    String mensaje = String.format("Cuenta por cobrar vence %s: %s, Cliente: %s, Fecha: %s",
                            tipoMensaje, cuenta.getFolio(), cuenta.getCliente().getNombre(), cuenta.getFechaPago());

                    notificarAdministradores("CUENTA_COBRAR", mensaje);
                });
    }

    private void procesarCuentasPorPagar(LocalDate hoy, LocalDate manana) {
        cuentaPorPagarRepository.findAll().stream()
                .filter(cuenta -> cuenta.getFechaPago() != null)
                .filter(cuenta -> cuenta.getFechaPago().equals(hoy) || cuenta.getFechaPago().equals(manana))
                .forEach(cuenta -> {
                    String tipoMensaje = cuenta.getFechaPago().equals(manana) ? "mañana" : "hoy";
                    String mensaje = String.format("Cuenta por pagar vence %s: %s, Cuenta: %s, Fecha: %s",
                            tipoMensaje, cuenta.getFolio(), cuenta.getCuenta().getNombre(), cuenta.getFechaPago());

                    notificarAdministradores("CUENTA_PAGAR", mensaje);
                });
    }

    private void procesarRecargarSims(LocalDate hoy, LocalDate manana) {
        simRepository.findAll().stream()
                .filter(sim -> sim.getVigencia() != null)
                .filter(sim -> {
                    LocalDate vigenciaDate = sim.getVigencia().toLocalDate();
                    return vigenciaDate.equals(hoy) || vigenciaDate.equals(manana);
                })
                .forEach(sim -> {
                    LocalDate vigenciaDate = sim.getVigencia().toLocalDate();
                    String tipoMensaje = vigenciaDate.equals(manana) ? "mañana" : "hoy";
                    String mensaje = String.format("Recarga de SIM vence %s: %s, Equipo: %s, Fecha: %s",
                            tipoMensaje, sim.getNumero(),
                            sim.getEquipo() != null ? sim.getEquipo().getImei() : "Sin equipo",
                            vigenciaDate);

                    // Cambiar para notificar a todos los usuarios activos
                    notificarTodosLosUsuarios("RECARGA", mensaje);
                });
    }

    // Nuevo método helper para notificar a todos los usuarios activos
    private void notificarTodosLosUsuarios(String tipo, String mensaje) {
        List<Usuario> todosUsuarios = usuarioRepository.findByEstatusOrderById(EstatusUsuarioEnum.ACTIVO);
        todosUsuarios.forEach(usuario -> {
            if (!existeNotificacionReciente(usuario.getId(), tipo, mensaje)) {
                crearNotificacion(usuario, tipo, mensaje);
            }
        });
    }

    private void notificarAdministradores(String tipo, String mensaje) {
        List<Usuario> admins = usuarioRepository.findByRolAndEstatusOrderById(RolUsuarioEnum.ADMINISTRADOR, EstatusUsuarioEnum.ACTIVO);
        admins.forEach(admin -> {
            if (!existeNotificacionReciente(admin.getId(), tipo, mensaje)) {
                crearNotificacion(admin, tipo, mensaje);
            }
        });
    }

    // Método para verificar si existe una notificación reciente similar
    private boolean existeNotificacionReciente(Integer usuarioId, String tipo, String mensaje) {
        Instant hace24Horas = Instant.now().minusSeconds(24 * 60 * 60);
        return notificacionRepository.existsByUsuarioIdAndTipoNotificacionAndMensajeAndFechaCreacionAfter(
                usuarioId, tipo, mensaje, hace24Horas
        );
    }

    // Método helper para crear notificaciones
    private void crearNotificacion(Usuario usuario, String tipo, String mensaje) {
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(usuario);
        notificacion.setTipoNotificacion(tipo);
        notificacion.setMensaje(mensaje);
        notificacion.setFechaCreacion(Instant.now());
        notificacion.setEstatus(EstatusNotificacionEnum.NO_LEIDA);
        notificacionRepository.save(notificacion);
    }

    @Transactional(readOnly = true)
    public List<Notificacion> listarNotificacionesPorUsuario() {
        Integer userId = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
        // Ordenar por fecha de creación descendente (más recientes primero)
        return notificacionRepository.findByUsuarioIdOrderByFechaCreacionDesc(userId);
    }

    @Transactional(readOnly = true)
    public Integer contarNotificacionesNoLeidas() {
        Integer userId = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
        return notificacionRepository.countByUsuarioIdAndEstatus(userId, EstatusNotificacionEnum.NO_LEIDA);
    }

    @Transactional
    public void marcarComoLeida(Integer notificacionId) {
        try {
            Notificacion notificacion = notificacionRepository.findById(notificacionId)
                    .orElseThrow(() -> new RuntimeException("Notificación no encontrada"));
            notificacion.setEstatus(EstatusNotificacionEnum.LEIDA);
            notificacion.setFechaLeida(Instant.now());
            notificacionRepository.save(notificacion);
            logger.info("Notificación {} marcada como leída", notificacionId);
        } catch (Exception e) {
            logger.error("Error al marcar notificación como leída: {}", e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void marcarTodasComoLeidas() {
        Integer userId = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
        List<Notificacion> notificacionesNoLeidas = notificacionRepository.findByUsuarioIdAndEstatus(userId, EstatusNotificacionEnum.NO_LEIDA);

        Instant ahora = Instant.now();
        notificacionesNoLeidas.forEach(notificacion -> {
            notificacion.setEstatus(EstatusNotificacionEnum.LEIDA);
            notificacion.setFechaLeida(ahora);
            notificacionRepository.save(notificacion);
        });

        logger.info("Todas las notificaciones del usuario {} marcadas como leídas", userId);
    }

    @Scheduled(cron = "0 0 2 * * *") // Ejecutar a las 2:00 AM todos los días
    @Transactional
    public void limpiarNotificacionesLeidas() {
        logger.info("Iniciando limpieza de notificaciones leídas");
        try {
            Instant hace24Horas = Instant.now().minusSeconds(24 * 60 * 60);
            List<Notificacion> notificacionesParaEliminar = notificacionRepository
                    .findByEstatusAndFechaLeidaBefore(EstatusNotificacionEnum.LEIDA, hace24Horas);

            if (!notificacionesParaEliminar.isEmpty()) {
                notificacionRepository.deleteAll(notificacionesParaEliminar);
                logger.info("Eliminadas {} notificaciones leídas", notificacionesParaEliminar.size());
            }
        } catch (Exception e) {
            logger.error("Error al limpiar notificaciones leídas: {}", e.getMessage());
        }
    }
}