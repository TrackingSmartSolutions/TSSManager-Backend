package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.EstatusNotificacionEnum;
import com.tss.tssmanager_backend.enums.EstatusUsuarioEnum;
import com.tss.tssmanager_backend.enums.RolUsuarioEnum;
import com.tss.tssmanager_backend.repository.*;
import com.tss.tssmanager_backend.security.CustomUserDetails;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class NotificacionService {

    private static final Logger logger = LoggerFactory.getLogger(NotificacionService.class);

    private static final ZoneId ZONE_ID = ZoneId.of("America/Mexico_City");

    @Autowired
    private NotificacionRepository notificacionRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ActividadRepository actividadRepository;
    @Autowired
    private TratoRepository tratoRepository;
    @Autowired
    private CuentaPorCobrarRepository cuentaPorCobrarRepository;
    @Autowired
    private CuentaPorPagarRepository cuentaPorPagarRepository;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private EmailRecordRepository emailRecordRepository;
    @Autowired
    private EmailService emailService;

    @PostConstruct
    public void inicializarNotificaciones() {
        logger.info("Inicializando verificación de notificaciones al arrancar la aplicación");
        try {
            verificarNotificacionesProgramadas();
            limpiarNotificacionesLeidas();
        } catch (Exception e) {
            logger.error("Error durante la inicialización de notificaciones: {}", e.getMessage());
        }
    }

    private Instant obtenerInstantLocal() {
        return ZonedDateTime.now(ZONE_ID).toInstant();
    }

    private ZonedDateTime convertirAZonaLocal(Instant instant) {
        return instant.atZone(ZONE_ID);
    }

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
            LocalDate hoy = LocalDate.now(ZONE_ID);
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

    // Método programado para ejecutarse cada 6 horas
    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void verificarNotificacionesProgramadas() {
        logger.info("Verificación programada de respaldo ejecutada");
        generarNotificacionCuentasYSims();
        verificarActividadesProximas();
    }

    // Método separado para verificar actividades próximas
    @Transactional
    public void verificarActividadesProximas() {
        LocalDate hoy = LocalDate.now(ZONE_ID);
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
        LocalDate hoy = LocalDate.now(ZONE_ID);
        LocalDate manana = hoy.plusDays(1);

        try {
            // Cuentas por cobrar
            procesarCuentasPorCobrar(hoy, manana);

            // Cuentas por pagar
            procesarCuentasPorPagar(hoy, manana);

        } catch (Exception e) {
            logger.error("Error al generar notificaciones de cuentas y SIMs: {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generarNotificacionCuentasYSimsEnTransaccionSeparada() {
        // Este método simplemente llama al método original pero en una transacción independiente
        generarNotificacionCuentasYSims();
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

                    // Enviar correo solo si vence mañana
                    if (cuenta.getFechaPago().equals(manana)) {
                        enviarCorreoCuentaPorCobrar(cuenta);
                    }
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

                    // Enviar correo solo si vence mañana
                    if (cuenta.getFechaPago().equals(manana)) {
                        enviarCorreoCuentaPorPagar(cuenta);
                    }
                });
    }

    private void enviarCorreoCuentaPorCobrar(CuentaPorCobrar cuenta) {
        try {
            List<Usuario> admins = usuarioRepository.findByRolAndEstatusOrderById(
                    RolUsuarioEnum.ADMINISTRADOR, EstatusUsuarioEnum.ACTIVO);

            String asunto = "Recordatorio: Cuenta por Cobrar vence mañana";
            String cuerpo = construirCuerpoCorreoCuentaPorCobrar(cuenta);

            for (Usuario admin : admins) {
                // Verificar si ya se envió correo en las últimas 24 horas para evitar duplicados
                if (!existeCorreoRecienteCuentaPorCobrar(admin.getId(), cuenta.getFolio())) {
                    emailService.enviarCorreo(
                            admin.getCorreoElectronico(),
                            asunto,
                            cuerpo,
                            null, // Sin adjuntos
                            null  // Sin trato asociado
                    );
                    logger.info("Correo de cuenta por cobrar enviado a: {}", admin.getCorreoElectronico());
                }
            }
        } catch (Exception e) {
            logger.error("Error al enviar correo de cuenta por cobrar: {}", e.getMessage());
        }
    }

    private void enviarCorreoCuentaPorPagar(CuentaPorPagar cuenta) {
        try {
            List<Usuario> admins = usuarioRepository.findByRolAndEstatusOrderById(
                    RolUsuarioEnum.ADMINISTRADOR, EstatusUsuarioEnum.ACTIVO);

            String asunto = "Recordatorio: Cuenta por Pagar vence mañana";
            String cuerpo = construirCuerpoCorreoCuentaPorPagar(cuenta);

            for (Usuario admin : admins) {
                // Verificar si ya se envió correo en las últimas 24 horas para evitar duplicados
                if (!existeCorreoRecienteCuentaPorPagar(admin.getId(), cuenta.getFolio())) {
                    emailService.enviarCorreo(
                            admin.getCorreoElectronico(),
                            asunto,
                            cuerpo,
                            null, // Sin adjuntos
                            null  // Sin trato asociado
                    );
                    logger.info("Correo de cuenta por pagar enviado a: {}", admin.getCorreoElectronico());
                }
            }
        } catch (Exception e) {
            logger.error("Error al enviar correo de cuenta por pagar: {}", e.getMessage());
        }
    }

    private String construirCuerpoCorreoCuentaPorCobrar(CuentaPorCobrar cuenta) {
        return String.format("""
            <html>
            <body>
                <h2>Recordatorio: Cuenta por Cobrar</h2>
                <p>Estimado administrador,</p>
                <p>Le recordamos que la siguiente cuenta por cobrar <strong>vence mañana</strong>:</p>
                <ul>
                    <li><strong>Folio:</strong> %s</li>
                    <li><strong>Cliente:</strong> %s</li>
                    <li><strong>Fecha de Vencimiento:</strong> %s</li>
                    <li><strong>Monto:</strong> $%s</li>
                </ul>
                <p>Por favor, tome las acciones necesarias.</p>
                <br>
                <p>Saludos cordiales,<br>Sistema TSS Manager</p>
            </body>
            </html>
            """,
                cuenta.getFolio(),
                cuenta.getCliente().getNombre(),
                cuenta.getFechaPago(),
                cuenta.getCantidadCobrar()
        );
    }

    private String construirCuerpoCorreoCuentaPorPagar(CuentaPorPagar cuenta) {
        return String.format("""
            <html>
            <body>
                <h2>Recordatorio: Cuenta por Pagar</h2>
                <p>Estimado administrador,</p>
                <p>Le recordamos que la siguiente cuenta por pagar <strong>vence mañana</strong>:</p>
                <ul>
                    <li><strong>Folio:</strong> %s</li>
                    <li><strong>Cuenta:</strong> %s</li>
                    <li><strong>Fecha de Vencimiento:</strong> %s</li>
                    <li><strong>Monto:</strong> $%s</li>
                </ul>
                <p>Por favor, tome las acciones necesarias.</p>
                <br>
                <p>Saludos cordiales,<br>Sistema TSS Manager</p>
            </body>
            </html>
            """,
                cuenta.getFolio(),
                cuenta.getCuenta().getNombre(),
                cuenta.getFechaPago(),
                cuenta.getMonto()
        );
    }

    private boolean existeCorreoRecienteCuentaPorCobrar(Integer adminId, String folio) {
        try {
            Usuario admin = usuarioRepository.findById(adminId).orElse(null);
            if (admin == null || admin.getCorreoElectronico() == null) {
                return false;
            }

            String correoAdmin = admin.getCorreoElectronico();
            Instant hace24Horas = obtenerInstantLocal().minusSeconds(24 * 60 * 60);
            ZonedDateTime hace24HorasZoned = convertirAZonaLocal(hace24Horas);

            List<EmailRecord> correosRecientes = emailRecordRepository
                    .findByDestinatarioContainingAndCuerpoContainingAndFechaEnvioAfterAndExitoTrue(
                            correoAdmin, folio, hace24HorasZoned);

            return !correosRecientes.isEmpty();

        } catch (Exception e) {
            logger.error("Error al verificar correo reciente de cuenta por cobrar: {}", e.getMessage());
            return false;
        }
    }

    private boolean existeCorreoRecienteCuentaPorPagar(Integer adminId, String folio) {
        try {
            Usuario admin = usuarioRepository.findById(adminId).orElse(null);
            if (admin == null || admin.getCorreoElectronico() == null) {
                return false;
            }

            String correoAdmin = admin.getCorreoElectronico();
            Instant hace24Horas = obtenerInstantLocal().minusSeconds(24 * 60 * 60);
            ZonedDateTime hace24HorasZoned = convertirAZonaLocal(hace24Horas);

            List<EmailRecord> correosRecientes = emailRecordRepository
                    .findByDestinatarioContainingAndCuerpoContainingAndFechaEnvioAfterAndExitoTrue(
                            correoAdmin, folio, hace24HorasZoned);

            return !correosRecientes.isEmpty();

        } catch (Exception e) {
            logger.error("Error al verificar correo reciente de cuenta por pagar: {}", e.getMessage());
            return false;
        }
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
        List<Usuario> adminsYGestores = new ArrayList<>();
        adminsYGestores.addAll(usuarioRepository.findByRolAndEstatusOrderById(RolUsuarioEnum.ADMINISTRADOR, EstatusUsuarioEnum.ACTIVO));
        adminsYGestores.addAll(usuarioRepository.findByRolAndEstatusOrderById(RolUsuarioEnum.GESTOR, EstatusUsuarioEnum.ACTIVO));

        adminsYGestores.forEach(admin -> {
            if (!existeNotificacionReciente(admin.getId(), tipo, mensaje)) {
                crearNotificacion(admin, tipo, mensaje);
            }
        });
    }

    // Método para verificar si existe una notificación reciente similar
    private boolean existeNotificacionReciente(Integer usuarioId, String tipo, String mensaje) {
        Instant hace24Horas = obtenerInstantLocal().minusSeconds(24 * 60 * 60);
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
        notificacion.setFechaCreacion(obtenerInstantLocal());
        notificacion.setEstatus(EstatusNotificacionEnum.NO_LEIDA);
        notificacionRepository.save(notificacion);
    }

    @Transactional(readOnly = true)
    public List<Notificacion> listarNotificacionesPorUsuario() {
        Integer userId = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
        return notificacionRepository.findByUsuarioIdOrderByFechaCreacionDesc(userId);
    }

    // Crear un método separado para verificar y listar
    @Transactional
    public List<Notificacion> listarNotificacionesConVerificacion() {
        verificarNotificacionesProgramadas();
        Integer userId = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
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
            notificacion.setFechaLeida(obtenerInstantLocal()); // Corregido: usar hora local
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

        Instant ahora = obtenerInstantLocal();
        notificacionesNoLeidas.forEach(notificacion -> {
            notificacion.setEstatus(EstatusNotificacionEnum.LEIDA);
            notificacion.setFechaLeida(ahora);
            notificacionRepository.save(notificacion);
        });

        logger.info("Todas las notificaciones del usuario {} marcadas como leídas", userId);
    }

    @Scheduled(cron = "0 0 */12 * * *")
    @Transactional
    public void limpiarNotificacionesLeidas() {
        logger.info("Limpieza programada de notificaciones ejecutada");
        try {
            Instant hace24Horas = obtenerInstantLocal().minusSeconds(24 * 60 * 60);

            // Mejorar la consulta para mejor rendimiento
            List<Notificacion> notificacionesParaEliminar = notificacionRepository
                    .findByEstatusAndFechaLeidaBefore(EstatusNotificacionEnum.LEIDA, hace24Horas);

            if (!notificacionesParaEliminar.isEmpty()) {
                logger.info("Encontradas {} notificaciones para eliminar", notificacionesParaEliminar.size());

                // Log de algunas notificaciones para debugging
                notificacionesParaEliminar.stream()
                        .limit(5)
                        .forEach(notif -> logger.debug("Eliminando notificación ID: {}, Fecha leída: {}",
                                notif.getId(), notif.getFechaLeida()));

                notificacionRepository.deleteAll(notificacionesParaEliminar);
                logger.info("Eliminadas {} notificaciones leídas con más de 24 horas", notificacionesParaEliminar.size());
            } else {
                logger.info("No se encontraron notificaciones para eliminar");
            }
        } catch (Exception e) {
            logger.error("Error al limpiar notificaciones leídas: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void verificarNotificacionesSilenciosa() {
        try {
            generarNotificacionCuentasYSims();
            verificarActividadesProximas();
        } catch (Exception e) {
            logger.error("Error en verificación silenciosa: {}", e.getMessage());
        }
    }

    @Transactional
    public int limpiarNotificacionesLeidasManual() {
        logger.info("Limpieza manual de notificaciones ejecutada");
        try {
            Instant hace24Horas = obtenerInstantLocal().minusSeconds(24 * 60 * 60);

            List<Notificacion> notificacionesParaEliminar = notificacionRepository
                    .findByEstatusAndFechaLeidaBefore(EstatusNotificacionEnum.LEIDA, hace24Horas);

            if (!notificacionesParaEliminar.isEmpty()) {
                logger.info("Limpieza manual: Encontradas {} notificaciones para eliminar", notificacionesParaEliminar.size());

                // Log detallado para debugging
                notificacionesParaEliminar.forEach(notif ->
                        logger.debug("Eliminando notificación ID: {}, Usuario: {}, Fecha leída: {}, Hace 24h: {}",
                                notif.getId(), notif.getUsuario().getId(), notif.getFechaLeida(), hace24Horas));

                notificacionRepository.deleteAll(notificacionesParaEliminar);
                logger.info("Limpieza manual: Eliminadas {} notificaciones leídas", notificacionesParaEliminar.size());
                return notificacionesParaEliminar.size();
            } else {
                logger.info("Limpieza manual: No se encontraron notificaciones para eliminar");
                return 0;
            }
        } catch (Exception e) {
            logger.error("Error en limpieza manual de notificaciones: {}", e.getMessage(), e);
            throw e;
        }
    }
}