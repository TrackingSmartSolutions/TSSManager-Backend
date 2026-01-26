package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.EstatusNotificacionEnum;
import com.tss.tssmanager_backend.enums.EstatusPagoEnum;
import com.tss.tssmanager_backend.enums.EstatusUsuarioEnum;
import com.tss.tssmanager_backend.enums.RolUsuarioEnum;
import com.tss.tssmanager_backend.repository.*;
import com.tss.tssmanager_backend.security.CustomUserDetails;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

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
    private CreditoPlataformaRepository creditoPlataformaRepository;
    @Autowired
    private EmailService emailService;
    @Autowired
    private EquipoService equipoService;
    @Autowired
    private NotificacionPopupMostradaRepository notificacionPopupMostradaRepository;

/*
    @PostConstruct
    @Transactional
    public void inicializarNotificaciones() {
        logger.info("Inicializando verificación de notificaciones al arrancar la aplicación");
        try {
            Thread.sleep(2000);
            verificarActividadesProximas();
            Thread.sleep(1000);
            limpiarNotificacionesLeidas();
            Thread.sleep(1000);
            verificarYEnviarAlertaEquiposPendiente();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Inicialización interrumpida: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Error durante la inicialización de notificaciones: {}", e.getMessage());
        }
    }
*/
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void inicializarNotificacionesAlArranque() {
        logger.info("Ejecutando tareas de inicialización (ApplicationReadyEvent)...");
        try {
            logger.info("Verificando actividades próximas al arranque...");
            verificarActividadesProximas();
            logger.info("Limpiando notificaciones leídas al arranque...");
            limpiarNotificacionesLeidas();
            logger.info("Inicialización de notificaciones completada.");
        } catch (Exception e) {
            logger.error("Error durante la inicialización de notificaciones al arranque: {}", e.getMessage());
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
        /*Integer asignadoAId = actividad.getAsignadoAId();
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
                String mensaje = String.format("Actividad %s programada para %s: %s, Fecha: %s, Hora: %s",
                        actividad.getTipo().name(),
                        tipoMensaje,
                        trato.getNombre(),
                        fechaActividad,
                        actividad.getHoraInicio() != null ? actividad.getHoraInicio().toLocalTime() : "Todo el día");


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
        } */
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
    @Scheduled(cron = "0 0 */6 * * *", zone = "America/Mexico_City")
    public void verificarNotificacionesProgramadas() {
        logger.info("Verificación programada de respaldo ejecutada");
        verificarActividadesProximas();
    }

    @Scheduled(cron = "0 0 10 * * *", zone = "America/Mexico_City")
    @Transactional
    public void verificarNotificacionesMatutina() {
        logger.info("Verificación matutina de las 10 AM ejecutada");
        generarNotificacionCuentasYSims();
        verificarActividadesProximas();
    }

    // Método separado para verificar actividades próximas
    @Transactional
    public void verificarActividadesProximas() {
        /*LocalDate hoy = LocalDate.now(ZONE_ID);
        LocalDate manana = hoy.plusDays(1);

        // Buscar actividades para hoy y mañana
        List<Actividad> actividadesHoy = actividadRepository.findByFechaLimite(hoy);
        List<Actividad> actividadesManana = actividadRepository.findByFechaLimite(manana);

        // Procesar actividades de hoy
        actividadesHoy.forEach(this::generarNotificacionActividad);

        // Procesar actividades de mañana
        actividadesManana.forEach(this::generarNotificacionActividad);
        */
    }

    @Transactional
    public void generarNotificacionCuentasYSims() {
        LocalDate hoy = LocalDate.now(ZONE_ID);
        LocalDate manana = hoy.plusDays(1);

        try {
            List<CuentaPorCobrar> cuentasVencenHoy = obtenerCuentasPorCobrarVencen(hoy);
            List<CuentaPorCobrar> cuentasVencenManana = obtenerCuentasPorCobrarVencen(manana);
            List<CuentaPorPagar> cuentasPagarHoy = obtenerCuentasPorPagarVencen(hoy);
            List<CuentaPorPagar> cuentasPagarManana = obtenerCuentasPorPagarVencen(manana);
            List<CuentaPorCobrar> cuentasCobrarVencidas = obtenerCuentasPorCobrarVencidas();
            List<CuentaPorPagar> cuentasPagarVencidas = obtenerCuentasPorPagarVencidas();

            procesarCuentasPorCobrar(hoy, manana, cuentasVencenHoy, cuentasVencenManana, cuentasCobrarVencidas);
            procesarCuentasPorPagar(hoy, manana, cuentasPagarHoy, cuentasPagarManana, cuentasPagarVencidas);

        } catch (Exception e) {
            logger.error("Error al generar notificaciones de cuentas y SIMs: {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generarNotificacionCuentasYSimsEnTransaccionSeparada() {
        // Este método simplemente llama al método original pero en una transacción independiente
        generarNotificacionCuentasYSims();
    }

    private void procesarCuentasPorCobrar(LocalDate hoy, LocalDate manana,
                                          List<CuentaPorCobrar> cuentasVencenHoy,
                                          List<CuentaPorCobrar> cuentasVencenManana,
                                          List<CuentaPorCobrar> cuentasVencidas) {

        // Consolidar cuentas que vencen HOY (pendientes + en proceso que vencen hoy + vencidas)
        List<CuentaPorCobrar> cuentasConsolidadasHoy = new ArrayList<>();
        cuentasConsolidadasHoy.addAll(cuentasVencenHoy);
        cuentasConsolidadasHoy.addAll(cuentasVencidas);

        if (!cuentasConsolidadasHoy.isEmpty()) {
            cuentasConsolidadasHoy.forEach(cuenta -> {
                String mensaje = String.format("Cuenta por cobrar %s: %s, Cliente: %s, Fecha: %s",
                        cuenta.getEstatus() == EstatusPagoEnum.VENCIDA ? "VENCIDA" : "vence hoy",
                        cuenta.getFolio(), cuenta.getCliente().getNombre(), cuenta.getFechaPago());

                notificarAdministradores("CUENTA_COBRAR", mensaje);
            });

            if (!yaSeEnvioCorreoConsolidadoHoy("CUENTAS_COBRAR_HOY", hoy)) {
                enviarCorreoConsolidadoCuentasPorCobrar(cuentasConsolidadasHoy, "HOY", hoy);
            } else {
                logger.info("Saltando envío de correo de cuentas por cobrar (HOY) - Ya enviado");
            }
        }

        // Consolidar cuentas que vencen MAÑANA
        if (!cuentasVencenManana.isEmpty()) {
            cuentasVencenManana.forEach(cuenta -> {
                String mensaje = String.format("Cuenta por cobrar vence mañana: %s, Cliente: %s, Fecha: %s",
                        cuenta.getFolio(), cuenta.getCliente().getNombre(), cuenta.getFechaPago());

                notificarAdministradores("CUENTA_COBRAR", mensaje);
            });

            if (!yaSeEnvioCorreoConsolidadoHoy("CUENTAS_COBRAR_MANANA", manana)) {
                enviarCorreoConsolidadoCuentasPorCobrar(cuentasVencenManana, "MAÑANA", manana);
            } else {
                logger.info("Saltando envío de correo de cuentas por cobrar (MAÑANA) - Ya enviado");
            }
        }
    }


    private void procesarCuentasPorPagar(LocalDate hoy, LocalDate manana,
                                         List<CuentaPorPagar> cuentasVencenHoy,
                                         List<CuentaPorPagar> cuentasVencenManana,
                                         List<CuentaPorPagar> cuentasVencidas) {

        // Consolidar cuentas que vencen HOY (pendientes + en proceso que vencen hoy + vencidas)
        List<CuentaPorPagar> cuentasConsolidadasHoy = new ArrayList<>();
        cuentasConsolidadasHoy.addAll(cuentasVencenHoy);
        cuentasConsolidadasHoy.addAll(cuentasVencidas);

        if (!cuentasConsolidadasHoy.isEmpty()) {
            cuentasConsolidadasHoy.forEach(cuenta -> {
                String mensaje = String.format("Cuenta por pagar %s: %s, Cuenta: %s, Fecha: %s",
                        "Vencida".equals(cuenta.getEstatus()) ? "VENCIDA" : "vence hoy",
                        cuenta.getFolio(), cuenta.getCuenta().getNombre(), cuenta.getFechaPago());

                notificarAdministradores("CUENTA_PAGAR", mensaje);
            });

            if (!yaSeEnvioCorreoConsolidadoHoy("CUENTAS_PAGAR_HOY", hoy)) {
                enviarCorreoConsolidadoCuentasPorPagar(cuentasConsolidadasHoy, "HOY", hoy);
            } else {
                logger.info("Saltando envío de correo de cuentas por pagar (HOY) - Ya enviado");
            }
        }

        // Consolidar cuentas que vencen MAÑANA
        if (!cuentasVencenManana.isEmpty()) {
            cuentasVencenManana.forEach(cuenta -> {
                String mensaje = String.format("Cuenta por pagar vence mañana: %s, Cuenta: %s, Fecha: %s",
                        cuenta.getFolio(), cuenta.getCuenta().getNombre(), cuenta.getFechaPago());

                notificarAdministradores("CUENTA_PAGAR", mensaje);
            });

            if (!yaSeEnvioCorreoConsolidadoHoy("CUENTAS_PAGAR_MANANA", manana)) {
                enviarCorreoConsolidadoCuentasPorPagar(cuentasVencenManana, "MAÑANA", manana);
            } else {
                logger.info("Saltando envío de correo de cuentas por pagar (MAÑANA) - Ya enviado");
            }
        }
    }

    private void enviarCorreoConsolidadoCuentasPorCobrar(List<CuentaPorCobrar> cuentas, String cuandoVence, LocalDate fechaVencimiento) {
        try {
            List<Usuario> adminsYGestores = obtenerAdminsYGestoresActivos();

            if (adminsYGestores.isEmpty()) {
                logger.warn("No hay administradores o gestores activos para enviar correos de Cuentas por Cobrar.");
                return;
            }
            String asunto = cuandoVence.equals("VENCIDAS")
                    ? "Recordatorio: Cuentas por Cobrar - Están VENCIDAS"
                    : "Recordatorio: Cuentas por Cobrar - Vencen " + cuandoVence;
            String cuerpo = construirCuerpoCorreoConsolidadoCuentasPorCobrar(cuentas, cuandoVence);
            String tipoCorreo = "CUENTAS_COBRAR_" + cuandoVence;

            for (Usuario admin : adminsYGestores) {
                try {
                    emailService.enviarCorreo(
                            admin.getCorreoElectronico(),
                            asunto,
                            cuerpo,
                            null,
                            null,
                            null,
                            tipoCorreo
                    );
                    Thread.sleep(1000);
                    logger.info("Correo consolidado de cuentas por cobrar (vencen {}, fecha {}) enviado a: {}",
                            cuandoVence, fechaVencimiento, admin.getCorreoElectronico());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Envío de correo interrumpido: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error al enviar correo consolidado de cuentas por cobrar: {}", e.getMessage());
        }
    }

    private void enviarCorreoConsolidadoCuentasPorPagar(List<CuentaPorPagar> cuentas, String cuandoVence, LocalDate fechaVencimiento) {
        try {
            List<Usuario> adminsYGestores = obtenerAdminsYGestoresActivos();

            if (adminsYGestores.isEmpty()) {
                logger.warn("No hay administradores o gestores activos para enviar correos de Cuentas por Pagar.");
                return;
            }

            String asunto = cuandoVence.equals("VENCIDAS")
                    ? "Recordatorio: Cuentas por Pagar - Están VENCIDAS"
                    : "Recordatorio: Cuentas por Pagar - Vencen " + cuandoVence;
            String cuerpo = construirCuerpoCorreoConsolidadoCuentasPorPagar(cuentas, cuandoVence);
            String tipoCorreo = "CUENTAS_PAGAR_" + cuandoVence;

            for (Usuario admin : adminsYGestores) {
                try {
                    emailService.enviarCorreo(
                            admin.getCorreoElectronico(),
                            asunto,
                            cuerpo,
                            null,
                            null,
                            null,
                            tipoCorreo
                    );
                    Thread.sleep(1000);
                    logger.info("Correo consolidado de cuentas por pagar (vencen {}, fecha {}) enviado a: {}",
                            cuandoVence, fechaVencimiento, admin.getCorreoElectronico());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Envío de correo interrumpido: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error al enviar correo consolidado de cuentas por pagar: {}", e.getMessage());
        }
    }

    private String construirCuerpoCorreoConsolidadoCuentasPorCobrar(List<CuentaPorCobrar> cuentas, String cuandoVence) {
        BigDecimal montoTotal = cuentas.stream()
                .map(CuentaPorCobrar::getCantidadCobrar)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate fechaVencimiento = cuentas.get(0).getFechaPago();
        String textoVencimiento = cuandoVence.equals("HOY") ? "vencen hoy" :
                cuandoVence.equals("MAÑANA") ? "vencen mañana" :
                        "están VENCIDAS";

        StringBuilder listadoCuentas = new StringBuilder();
        for (CuentaPorCobrar cuenta : cuentas) {
            String estatusDisplay = cuenta.getEstatus().name();

            listadoCuentas.append(String.format(
                    "<tr><td>%s</td><td>%s</td><td>$%s</td><td style=\"font-weight: bold; color: %s;\">%s</td></tr>",
                    cuenta.getFolio(),
                    cuenta.getCliente().getNombre(),
                    cuenta.getCantidadCobrar(),
                    obtenerColorEstatus(cuenta.getEstatus()),
                    formatearEstatus(estatusDisplay)
            ));
        }

        String tituloEvento = cuandoVence.equals("VENCIDAS")
                ? String.format("⚠️ URGENTE: %d Cuentas por Cobrar VENCIDAS - Total $%s", cuentas.size(), montoTotal)
                : String.format("Recordatorio: %d Cuentas por Cobrar - Total $%s", cuentas.size(), montoTotal);
        String descripcionEvento = cuandoVence.equals("VENCIDAS")
                ? String.format(
                "⚠️ ALERTA: Cuentas por cobrar VENCIDAS:%n" +
                        "Total de cuentas: %d%n" +
                        "Monto total: $%s%n" +
                        "Requiere atención inmediata",
                cuentas.size(), montoTotal
        )
                : String.format(
                "Recordatorio de cuentas por cobrar:%n" +
                        "Total de cuentas: %d%n" +
                        "Monto total: $%s%n" +
                        "Fecha de vencimiento: %s",
                cuentas.size(), montoTotal, fechaVencimiento
        );

        String urlCalendario = generarUrlGoogleCalendar(tituloEvento, descripcionEvento, fechaVencimiento);

        return String.format("""
    <html>
    <head>
        <style>
            .calendar-button {
                display: inline-block;
                padding: 12px 24px;
                background-color: #4285F4;
                color: white;
                text-decoration: none;
                border-radius: 5px;
                font-weight: bold;
                margin: 20px 0;
            }
            .calendar-button:hover {
                background-color: #357AE8;
            }
        </style>
    </head>
    <body>
        <h2>Recordatorio: Cuentas por Cobrar</h2>
        <p>Estimado administrador,</p>
        <p>Le recordamos que las siguientes cuentas por cobrar <strong>%s</strong>:</p>
        <ul>
            <li><strong>Total de cuentas:</strong> %d</li>
            <li><strong>Monto total:</strong> $%s</li>
        </ul>
        
        <div style="text-align: center; margin: 20px 0;">
            <a href="%s" class="calendar-button" target="_blank">
                📅 Agregar Recordatorio a Google Calendar
            </a>
        </div>
        
        <p>A continuación se muestra el listado individual de las cuentas:</p>
        <table border="1" style="border-collapse: collapse; width: 100%%;">
            <thead>
                <tr style="background-color: #f2f2f2;">
                    <th style="padding: 8px;">Folio</th>
                    <th style="padding: 8px;">Cliente</th>
                    <th style="padding: 8px;">Monto</th>
                     <th style="padding: 8px;">Estatus</th>
                </tr>
            </thead>
            <tbody>
                %s
            </tbody>
        </table>
        <p>Por favor, tome las acciones necesarias.</p>
        <br>
        <p>Saludos cordiales,<br>Sistema TSS Manager</p>
    </body>
    </html>
    """,
                textoVencimiento,
                cuentas.size(),
                montoTotal,
                urlCalendario != null ? urlCalendario : "#",
                listadoCuentas.toString()
        );
    }

    private String construirCuerpoCorreoConsolidadoCuentasPorPagar(List<CuentaPorPagar> cuentas, String cuandoVence) {
        BigDecimal montoTotal = cuentas.stream()
                .map(CuentaPorPagar::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate fechaVencimiento = cuentas.get(0).getFechaPago();
        String textoVencimiento = cuandoVence.equals("HOY") ? "vencen hoy" :
                cuandoVence.equals("MAÑANA") ? "vencen mañana" :
                        "están VENCIDAS";
        StringBuilder listadoCuentas = new StringBuilder();
        for (CuentaPorPagar cuenta : cuentas) {
            String categoria = cuenta.getTransaccion() != null && cuenta.getTransaccion().getCategoria() != null
                    ? cuenta.getTransaccion().getCategoria().getDescripcion() : "Sin categoría";
            String cuentaNombre = cuenta.getCuenta() != null ? cuenta.getCuenta().getNombre() : "Sin cuenta";
            String simNumero = cuenta.getSim() != null ? cuenta.getSim().getNumero() : "N/A";

            String estatusDisplay = cuenta.getEstatus();

            listadoCuentas.append(String.format(
                    "<tr><td>%s</td><td>%s</td><td>%s</td><td>$%s</td><td style=\"font-weight: bold; color: %s;\">%s</td></tr>",
                    categoria,
                    cuentaNombre,
                    simNumero,
                    cuenta.getMonto(),
                    obtenerColorEstatusPagar(estatusDisplay),
                    formatearEstatusPagar(estatusDisplay)
            ));
        }

        String tituloEvento = cuandoVence.equals("VENCIDAS")
                ? String.format("⚠️ URGENTE: %d Cuentas por Pagar VENCIDAS - Total $%s", cuentas.size(), montoTotal)
                : String.format("Recordatorio: %d Cuentas por Pagar - Total $%s", cuentas.size(), montoTotal);
        String descripcionEvento = cuandoVence.equals("VENCIDAS")
                ? String.format(
                "⚠️ ALERTA: Cuentas por pagar VENCIDAS:%n" +
                        "Total de cuentas: %d%n" +
                        "Monto total: $%s%n" +
                        "Requiere atención inmediata",
                cuentas.size(), montoTotal
        )
                : String.format(
                "Recordatorio de cuentas por pagar:%n" +
                        "Total de cuentas: %d%n" +
                        "Monto total: $%s%n" +
                        "Fecha de vencimiento: %s",
                cuentas.size(), montoTotal, fechaVencimiento
        );

        String urlCalendario = generarUrlGoogleCalendar(tituloEvento, descripcionEvento, fechaVencimiento);

        return String.format("""
                        <html>
                        <head>
                            <style>
                                .calendar-button {
                                    display: inline-block;
                                    padding: 12px 24px;
                                    background-color: #4285F4;
                                    color: white;
                                    text-decoration: none;
                                    border-radius: 5px;
                                    font-weight: bold;
                                    margin: 20px 0;
                                }
                                .calendar-button:hover {
                                    background-color: #357AE8;
                                }
                            </style>
                        </head>
                        <body>
                            <h2>Recordatorio: Cuentas por Pagar</h2>
                            <p>Estimado administrador,</p>
                            <p>Le recordamos que las siguientes cuentas por pagar <strong>%s</strong>:</p>
                            <ul>
                                <li><strong>Total de cuentas:</strong> %d</li>
                                <li><strong>Monto total:</strong> $%s</li>
                            </ul>
                        
                            <div style="text-align: center; margin: 20px 0;">
                                <a href="%s" class="calendar-button" target="_blank">
                                    📅 Agregar Recordatorio a Google Calendar
                                </a>
                            </div>
                        
                            <p>A continuación se muestra el listado individual de las cuentas:</p>
                            <table border="1" style="border-collapse: collapse; width: 100%%;">
                                <thead>
                                    <tr style="background-color: #f2f2f2;">
                                        <th style="padding: 8px;">Categoría</th>
                                        <th style="padding: 8px;">Cuenta</th>
                                        <th style="padding: 8px;">SIM</th>
                                        <th style="padding: 8px;">Monto</th>
                                        <th style="padding: 8px;">Estatus</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    %s
                                </tbody>
                            </table>
                            <p>Por favor, tome las acciones necesarias.</p>
                            <br>
                            <p>Saludos cordiales,<br>Sistema TSS Manager</p>
                        </body>
                        </html>
                        """,
                textoVencimiento,
                cuentas.size(),
                montoTotal,
                urlCalendario != null ? urlCalendario : "#",
                listadoCuentas.toString()
        );
    }

    private String generarUrlGoogleCalendar(String titulo, String descripcion, LocalDate fecha) {
        try {
            // Formato de fecha para Google Calendar: yyyyMMdd
            String fechaInicio = fecha.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String fechaFin = fecha.plusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            // Construir la URL de Google Calendar para evento de todo el día
            String baseUrl = "https://calendar.google.com/calendar/render?action=TEMPLATE";
            String text = "&text=" + URLEncoder.encode(titulo, StandardCharsets.UTF_8);
            String dates = "&dates=" + fechaInicio + "/" + fechaFin;
            String details = "&details=" + URLEncoder.encode(descripcion, StandardCharsets.UTF_8);

            return baseUrl + text + dates + details;
        } catch (Exception e) {
            logger.error("Error generando URL de Google Calendar: {}", e.getMessage());
            return null;
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

    @Transactional
    public void notificarAdministradores(String tipo, String mensaje) {
        List<Usuario> adminsYGestores = obtenerAdminsYGestoresActivos();

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

    @Scheduled(cron = "0 0 */12 * * *", zone = "America/Mexico_City")
    @Transactional
    public void limpiarNotificacionesLeidas() {
        logger.info("Limpieza programada de notificaciones ejecutada");
        try {
            Instant hace24Horas = obtenerInstantLocal().minusSeconds(24 * 60 * 60);

            int eliminadas = notificacionRepository.deleteByEstatusAndFechaLeidaBefore(
                    EstatusNotificacionEnum.LEIDA, hace24Horas);

            if (eliminadas > 0) {
                logger.info("Eliminadas {} notificaciones leídas con más de 24 horas (vía @Modifying)", eliminadas);
            } else {
                logger.info("No se encontraron notificaciones para eliminar");
            }
        } catch (Exception e) {
            logger.error("Error al limpiar notificaciones leídas: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public int limpiarNotificacionesLeidasManual() {
        logger.info("Limpieza manual de notificaciones ejecutada");
        try {
            Instant hace24Horas = obtenerInstantLocal().minusSeconds(24 * 60 * 60);

            int eliminadas = notificacionRepository.deleteByEstatusAndFechaLeidaBefore(
                    EstatusNotificacionEnum.LEIDA, hace24Horas);

            if (eliminadas > 0) {
                logger.info("Limpieza manual: Eliminadas {} notificaciones leídas (vía @Modifying)", eliminadas);
            } else {
                logger.info("Limpieza manual: No se encontraron notificaciones para eliminar");
            }
            return eliminadas;

        } catch (Exception e) {
            logger.error("Error en limpieza manual de notificaciones: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Scheduled(cron = "0 0 9 * * MON", zone = "America/Mexico_City")
    public void verificarExpiracionEquipos() {
        logger.info("Iniciando verificación semanal de expiración de equipos (Lunes 9:00 AM)");
        try {
            LocalDate lunesEstaSemana = LocalDate.now(ZONE_ID)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

            if (seEnvioAlertaEquiposEstaSemana(lunesEstaSemana)) {
                logger.info("Alerta de equipos ya enviada esta semana - SALTANDO");
                return;
            }
            List<Equipo> equiposProximosAExpirar = equipoService.obtenerEquiposProximosAExpirar();

            if (!equiposProximosAExpirar.isEmpty()) {
                List<Usuario> adminsYGestores = obtenerAdminsYGestoresActivos();

                enviarAlertaExpiracionEquipos(equiposProximosAExpirar, adminsYGestores);

                logger.info("Alerta de expiración enviada. Total de equipos: {}", equiposProximosAExpirar.size());
            } else {
                logger.info("No hay equipos próximos a expirar en los próximos 30 días");
            }
        } catch (Exception e) {
            logger.error("Error al verificar expiración de equipos: {}", e.getMessage(), e);
        }
    }

    private void enviarAlertaExpiracionEquipos(List<Equipo> equipos, List<Usuario> adminsYGestores) {
        try {

            if (adminsYGestores.isEmpty()) {
                logger.warn("No hay administradores o gestores activos para enviar alertas");
                return;
            }

            String asunto = "Alerta: Equipos próximos a expirar";

            String cuerpo = construirCuerpoAlertaExpiracion(equipos);

            for (Usuario admin : adminsYGestores) {
                try {
                    String tipoCorreo = "ALERTA_EQUIPOS_SEMANAL";
                    emailService.enviarCorreo(
                            admin.getCorreoElectronico(),
                            asunto,
                            cuerpo,
                            null,
                            null,
                            null,
                            tipoCorreo
                    );
                    Thread.sleep(1000);
                    logger.info("Alerta de expiración enviada a: {}", admin.getCorreoElectronico());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Envío de alerta de expiración interrumpido: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error al enviar alerta de expiración de equipos: {}", e.getMessage(), e);
        }
    }

    private String construirCuerpoAlertaExpiracion(List<Equipo> equipos) {
        LocalDate hoy = LocalDate.now(ZONE_ID);

        // 1. Lógica del Resumen por Plataforma (Sin cambios mayores)
        Map<String, List<Equipo>> equiposPorPlataforma = equipos.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getPlataforma() != null ? e.getPlataforma().getNombrePlataforma() : "Sin Plataforma"
                ));

        StringBuilder resumenPlataformas = new StringBuilder();
        for (Map.Entry<String, List<Equipo>> entry : equiposPorPlataforma.entrySet()) {
            String plataforma = entry.getKey();
            List<Equipo> equiposPlataforma = entry.getValue();

            BigDecimal creditosRequeridos = equiposPlataforma.stream()
                    .map(e -> new BigDecimal(e.getCreditosUsados() != null ? e.getCreditosUsados() : 0))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal saldoActual = obtenerSaldoPlataforma(plataforma);
            BigDecimal creditosFaltantes = saldoActual.subtract(creditosRequeridos);

            String estadoCreditos = creditosFaltantes.compareTo(BigDecimal.ZERO) >= 0
                    ? "✓ Suficientes"
                    : "✗ Insuficientes";

            resumenPlataformas.append(String.format(
                    "<tr>" +
                            "<td style=\"padding: 10px; border: 1px solid #ddd;\">%s</td>" +
                            "<td style=\"padding: 10px; border: 1px solid #ddd; text-align: center;\">%d</td>" +
                            "<td style=\"padding: 10px; border: 1px solid #ddd; text-align: right;\">%s</td>" +
                            "<td style=\"padding: 10px; border: 1px solid #ddd; text-align: right;\">%s</td>" +
                            "<td style=\"padding: 10px; border: 1px solid #ddd; text-align: center;\">%s</td>" +
                            "</tr>",
                    plataforma,
                    equiposPlataforma.size(),
                    creditosRequeridos,
                    saldoActual,
                    estadoCreditos
            ));
        }

        StringBuilder tablaEquipos = new StringBuilder();

        for (Equipo equipo : equipos) {
            long diasRestantes = ChronoUnit.DAYS.between(hoy, equipo.getFechaExpiracion().toLocalDate());
            String plataforma = equipo.getPlataforma() != null ? equipo.getPlataforma().getNombrePlataforma() : "N/A";

            boolean esExpirado = diasRestantes < 0 || "EXPIRADO".equals(equipo.getEstatus().name());

            String textoColumnaDias;
            String estiloTextoDias;
            String estiloFila;

            if (esExpirado) {
                textoColumnaDias = "EXPIRADO";
                estiloTextoDias = "color: #d32f2f; font-weight: bold;";
                estiloFila = "background-color: #fff5f5;";
            } else {
                textoColumnaDias = diasRestantes + " días";
                estiloTextoDias = "color: #333; font-weight: bold;"; // Color normal
                estiloFila = "";
            }

            tablaEquipos.append(String.format(
                    "<tr style=\"%s\">" +
                            "<td style=\"padding: 10px; border: 1px solid #ddd;\">%s</td>" +
                            "<td style=\"padding: 10px; border: 1px solid #ddd;\">%s</td>" +
                            "<td style=\"padding: 10px; border: 1px solid #ddd; text-align: center;\">%d</td>" +
                            "<td style=\"padding: 10px; border: 1px solid #ddd; text-align: center;\">%s</td>" +
                            // Aquí inyectamos el estilo y el texto condicional
                            "<td style=\"padding: 10px; border: 1px solid #ddd; text-align: center; %s\">%s</td>" +
                            "</tr>",
                    estiloFila,
                    equipo.getNombre(),
                    plataforma,
                    equipo.getCreditosUsados() != null ? equipo.getCreditosUsados() : 0,
                    equipo.getFechaExpiracion(),
                    estiloTextoDias,
                    textoColumnaDias
            ));
        }

        return String.format("""
        <html>
        <head>
            <style>
                body {
                    font-family: Arial, sans-serif;
                    color: #333;
                    background-color: #f9f9f9;
                }
                .container {
                    background-color: white;
                    padding: 20px;
                    border-radius: 8px;
                    margin: 10px;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                }
                .section-title {
                    background-color: #e74c3c;
                    color: white;
                    padding: 12px;
                    border-radius: 4px;
                    margin-top: 20px;
                    margin-bottom: 10px;
                    font-weight: bold;
                }
                table {
                    width: 100%%;
                    border-collapse: collapse;
                    margin-top: 10px;
                    margin-bottom: 20px;
                }
                th {
                    background-color: #34495e;
                    color: white;
                    padding: 12px;
                    text-align: left;
                    border: 1px solid #bdc3c7;
                }
                .alert-box {
                    background-color: #fff3cd;
                    border: 1px solid #ffc107;
                    border-radius: 4px;
                    padding: 15px;
                    margin: 15px 0;
                }
                .footer {
                    margin-top: 30px;
                    font-size: 12px;
                    color: #7f8c8d;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <h2 style="color: #e74c3c;">⚠️ Alerta de Expiración de Equipos</h2>
                
                <div class="alert-box">
                    <strong>Atención:</strong> Existen %d equipos que requieren atención (Vencidos o próximos a vencer).
                    Por favor, revise la información a continuación y planifique las renovaciones necesarias.
                </div>

                <div class="section-title">📊 Resumen por Plataforma</div>
                <table>
                    <thead>
                        <tr>
                            <th>Plataforma</th>
                            <th style="text-align: center;">Equipos</th>
                            <th style="text-align: right;">Créditos Requeridos</th>
                            <th style="text-align: right;">Saldo Actual</th>
                            <th style="text-align: center;">Estado</th>
                        </tr>
                    </thead>
                    <tbody>
                        %s
                    </tbody>
                </table>

                <div class="section-title">📋 Detalle de Equipos</div>
                <table>
                    <thead>
                        <tr>
                            <th>Nombre del Equipo</th>
                            <th>Plataforma</th>
                            <th style="text-align: center;">Créditos Requeridos</th>
                            <th style="text-align: center;">Fecha de Expiración</th>
                            <th style="text-align: center;">Días Restantes / Estatus</th>
                        </tr>
                    </thead>
                    <tbody>
                        %s
                    </tbody>
                </table>

                <div class="footer">
                    <p><strong>Nota:</strong> Este es un mensaje automático generado por TSS Manager.</p>
                    <p>Por favor, tome las acciones necesarias para renovar los equipos antes de su vencimiento.</p>
                </div>
            </div>
        </body>
        </html>
        """,
                equipos.size(),
                resumenPlataformas.toString(),
                tablaEquipos.toString()
        );
    }

    private BigDecimal obtenerSaldoPlataforma(String nombrePlataforma) {
        try {
            if ("WhatsGPS".equals(nombrePlataforma)) {
                List<Object[]> saldosPorSubtipo = creditoPlataformaRepository.getSaldosPorPlataformaYSubtipo();
                BigDecimal total = BigDecimal.ZERO;
                for (Object[] row : saldosPorSubtipo) {
                    if ("WhatsGPS".equals(row[0]) && "ANUAL".equals(row[1])) {
                        total = total.add((BigDecimal) row[2]);
                    }
                }
                return total;
            } else {
                List<Object[]> saldos = creditoPlataformaRepository.getSaldosPorPlataforma();
                for (Object[] row : saldos) {
                    if (nombrePlataforma.equals(row[0])) {
                        return (BigDecimal) row[1];
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error al obtener saldo de plataforma: {}", e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    @Transactional
    public void verificarYEnviarAlertaEquiposPendiente() {
        try {
            LocalDate hoy = LocalDate.now(ZONE_ID);
            ZonedDateTime ahora = ZonedDateTime.now(ZONE_ID);

            LocalDate lunesEstaSemana = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            ZonedDateTime lunesA9AM = lunesEstaSemana.atTime(9, 0).atZone(ZONE_ID);

            if (ahora.isAfter(lunesA9AM)) {
                if (!seEnvioAlertaEquiposEstaSemana(lunesEstaSemana)) {
                    logger.info("No se ha enviado la alerta de equipos esta semana. Enviando ahora...");
                    verificarExpiracionEquipos();
                } else {
                    logger.info("La alerta de equipos ya fue enviada esta semana");
                }
            } else {
                logger.info("Aún no es lunes a las 9 AM de esta semana. No se envía alerta.");
            }
        } catch (Exception e) {
            logger.error("Error al verificar alerta de equipos pendiente: {}", e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public boolean seEnvioAlertaEquiposEstaSemana(LocalDate lunesEstaSemana) {
        try {
            ZonedDateTime inicioSemana = lunesEstaSemana.atStartOfDay(ZONE_ID);
            String asunto = "Alerta: Equipos próximos a expirar";

            boolean yaEnviado = emailRecordRepository.existsByAsuntoContainingAndFechaEnvioAfterAndExitoTrue(
                    asunto,
                    inicioSemana
            );

            if (yaEnviado) {
                logger.debug("Verificación en BD: Alerta de equipos ya enviada esta semana");
            }
            return yaEnviado;

        } catch (Exception e) {
            logger.error("Error al verificar si se envió alerta de equipos: {}", e.getMessage());
            return false;
        }
    }

    @Transactional(readOnly = true)
    public boolean yaSeEnvioCorreoConsolidadoHoy(String tipoCorreo, LocalDate fechaVencimiento) {
        try {
            ZonedDateTime inicioDelDia = LocalDate.now(ZONE_ID).atStartOfDay(ZONE_ID);

            boolean yaEnviado = emailRecordRepository.existsByTipoCorreoConsolidadoAndExitoTrueAndFechaEnvioAfter(
                    tipoCorreo, inicioDelDia);

            if (yaEnviado) {
                logger.info("✓ Correo {} YA ENVIADO HOY. (Verificado en BD)", tipoCorreo);
            } else {
                logger.info("✗ Correo {} NO enviado hoy. Procederá a enviar.", tipoCorreo);
            }
            return yaEnviado;

        } catch (Exception e) {
            logger.error("Error al verificar correo consolidado: {}", e.getMessage());
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<CuentaPorCobrar> obtenerCuentasPorCobrarVencen(LocalDate fecha) {
        List<CuentaPorCobrar> cuentas = cuentaPorCobrarRepository.findByFechaPagoAndEstatusIn(
                fecha,
                List.of(EstatusPagoEnum.PENDIENTE, EstatusPagoEnum.EN_PROCESO)
        );

        cuentas.forEach(c -> {
            if (c.getConceptos() != null) {
                c.getConceptos().length();
            }
            if (c.getCliente() != null) {
                c.getCliente().getNombre();
            }
        });

        return cuentas;
    }

    @Transactional(readOnly = true)
    public List<CuentaPorPagar> obtenerCuentasPorPagarVencen(LocalDate fecha) {
        List<CuentaPorPagar> cuentas = cuentaPorPagarRepository.findByFechaPagoAndEstatusIn(
                fecha,
                List.of("Pendiente", "En proceso")
        );

        cuentas.forEach(c -> {
            if (c.getNota() != null) {
                c.getNota().length();
            }
            if (c.getTransaccion() != null && c.getTransaccion().getCategoria() != null) {
                String desc = c.getTransaccion().getCategoria().getDescripcion();
                if (desc != null) {
                    desc.length();
                }
            }
            if (c.getCuenta() != null) {
                c.getCuenta().getNombre();
            }
        });

        return cuentas;
    }

    @Transactional(readOnly = true)
    public List<CuentaPorCobrar> obtenerCuentasPorCobrarVencidas() {
        List<CuentaPorCobrar> todas = cuentaPorCobrarRepository.findByEstatusIn(
                List.of(EstatusPagoEnum.VENCIDA, EstatusPagoEnum.PENDIENTE, EstatusPagoEnum.EN_PROCESO)
        );

        LocalDate hoy = LocalDate.now(ZONE_ID);
        List<CuentaPorCobrar> filtradas = todas.stream()
                .filter(c -> c.getFechaPago() != null && c.getFechaPago().isBefore(hoy))
                .collect(Collectors.toList());

        filtradas.forEach(c -> {
            if (c.getConceptos() != null) {
                c.getConceptos().length();
            }
            if (c.getCliente() != null) {
                c.getCliente().getNombre();
            }
        });

        return filtradas;
    }

    @Transactional(readOnly = true)
    public List<CuentaPorPagar> obtenerCuentasPorPagarVencidas() {
        List<CuentaPorPagar> todas = cuentaPorPagarRepository.findByEstatusIn(
                List.of("Vencida", "Pendiente", "En proceso")
        );

        LocalDate hoy = LocalDate.now(ZONE_ID);
        List<CuentaPorPagar> filtradas = todas.stream()
                .filter(c -> c.getFechaPago() != null && c.getFechaPago().isBefore(hoy))
                .collect(Collectors.toList());

        filtradas.forEach(c -> {
            if (c.getNota() != null) {
                c.getNota().length();
            }
            if (c.getTransaccion() != null && c.getTransaccion().getCategoria() != null) {
                String desc = c.getTransaccion().getCategoria().getDescripcion();
                if (desc != null) {
                    desc.length();
                }
            }
            if (c.getCuenta() != null) {
                c.getCuenta().getNombre();
            }
        });

        return filtradas;
    }

    @Transactional(readOnly = true)
    public List<Usuario> obtenerAdminsYGestoresActivos() {
        List<Usuario> adminsYGestores = new ArrayList<>();
        adminsYGestores.addAll(usuarioRepository.findByRolAndEstatusOrderById(RolUsuarioEnum.ADMINISTRADOR, EstatusUsuarioEnum.ACTIVO));
        adminsYGestores.addAll(usuarioRepository.findByRolAndEstatusOrderById(RolUsuarioEnum.GESTOR, EstatusUsuarioEnum.ACTIVO));

        return filtrarUsuariosActivos(adminsYGestores);
    }

    private List<Usuario> filtrarUsuariosActivos(List<Usuario> usuarios) {
        return usuarios.stream()
                .filter(usuario -> usuarioRepository.existsByCorreoElectronicoAndEstatus(
                        usuario.getCorreoElectronico(),
                        EstatusUsuarioEnum.ACTIVO))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> obtenerActividadesProximasUsuario() {
        try {
            Integer userId = ((CustomUserDetails) SecurityContextHolder.getContext()
                    .getAuthentication().getPrincipal()).getId();

            ZonedDateTime ahora = ZonedDateTime.now(ZONE_ID);
            LocalDate hoy = ahora.toLocalDate();
            LocalTime horaActual = ahora.toLocalTime();

            // Buscar actividades de hoy del usuario
            List<Actividad> actividadesHoy = actividadRepository
                    .findByAsignadoAIdAndFechaLimiteAndEstatus(
                            userId, hoy, com.tss.tssmanager_backend.enums.EstatusActividadEnum.ABIERTA);

            List<Map<String, Object>> actividadesProximas = new ArrayList<>();

            for (Actividad actividad : actividadesHoy) {
                // Solo reuniones y llamadas
                if (actividad.getTipo() != com.tss.tssmanager_backend.enums.TipoActividadEnum.REUNION &&
                        actividad.getTipo() != com.tss.tssmanager_backend.enums.TipoActividadEnum.LLAMADA) {
                    continue;
                }

                if (actividad.getHoraInicio() != null) {
                    LocalTime horaActividad = actividad.getHoraInicio().toLocalTime();
                    long segundosRestantes = ChronoUnit.SECONDS.between(horaActual, horaActividad);
                    long minutosRestantes = (segundosRestantes + 59) / 60;

                    boolean mostrarNotificacion = false;

                    if (actividad.getTipo() == com.tss.tssmanager_backend.enums.TipoActividadEnum.LLAMADA) {
                        // LLAMADAS: Mostrar SOLO cuando minutosRestantes es exactamente 0
                        mostrarNotificacion = (minutosRestantes == 0);
                    } else {
                        // REUNIONES: Mostrar SOLO cuando minutosRestantes es exactamente 30
                        mostrarNotificacion = (minutosRestantes == 30);
                    }

                    if (mostrarNotificacion) {
                        Map<String, Object> actividadMap = new HashMap<>();
                        actividadMap.put("id", actividad.getId());
                        actividadMap.put("tratoId", actividad.getTratoId());
                        actividadMap.put("tipo", actividad.getTipo().name());
                        actividadMap.put("horaInicio", actividad.getHoraInicio().toString());
                        actividadMap.put("duracion", actividad.getDuracion());
                        actividadMap.put("modalidad", actividad.getModalidad() != null ?
                                actividad.getModalidad().name() : null);
                        actividadMap.put("enlaceReunion", actividad.getEnlaceReunion());
                        actividadMap.put("lugarReunion", actividad.getLugarReunion());

                        // Obtener nombre del trato
                        if (actividad.getTratoId() != null) {
                            tratoRepository.findById(actividad.getTratoId()).ifPresent(trato -> {
                                actividadMap.put("tratoNombre", trato.getNombre());
                                // Obtener nombre de la empresa
                                if (trato.getEmpresaId() != null) {
                                    empresaRepository.findById(trato.getEmpresaId()).ifPresent(empresa -> {
                                        actividadMap.put("empresaNombre", empresa.getNombre());
                                    });
                                }
                            });
                        }

                        actividadMap.put("minutosRestantes", minutosRestantes);

                        // VERIFICAR si ya se mostró este popup
                        if (!notificacionPopupMostradaRepository.existsByActividadIdAndUsuarioId(
                                actividad.getId(), userId)) {

                            // Registrar que se va a mostrar
                            NotificacionPopupMostrada registro = new NotificacionPopupMostrada();
                            registro.setActividadId(actividad.getId());
                            registro.setUsuarioId(userId);
                            registro.setFechaMostrado(obtenerInstantLocal());
                            notificacionPopupMostradaRepository.save(registro);

                            actividadesProximas.add(actividadMap);
                            logger.info("Popup programado para actividad {} - Usuario {}", actividad.getId(), userId);
                        } else {
                            logger.debug("Popup ya mostrado para actividad {} - Usuario {}", actividad.getId(), userId);
                        }
                    }
                }
            }

            logger.info("Actividades próximas encontradas para usuario {}: {}", userId, actividadesProximas.size());
            return actividadesProximas;

        } catch (Exception e) {
            logger.error("Error al obtener actividades próximas: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // Se ejecuta a las 10:00 AM todos los lunes
    @Scheduled(cron = "0 0 10 * * MON", zone = "America/Mexico_City")
    @Transactional(readOnly = true)
    public void enviarReporteSemanalTratosDesatendidos() {
        logger.info("Iniciando reporte semanal de tratos desatendidos...");

        Instant hace7Dias = ZonedDateTime.now(ZONE_ID).minusDays(7).toInstant();

        List<Trato> tratosDesatendidos = tratoRepository.findTratosDesatendidos(hace7Dias);

        if (tratosDesatendidos.isEmpty()) {
            logger.info("No se encontraron tratos desatendidos para reportar.");
            return;
        }

        Map<Integer, List<Trato>> tratosPorUsuario = tratosDesatendidos.stream()
                .collect(Collectors.groupingBy(Trato::getPropietarioId));

        tratosPorUsuario.forEach((usuarioId, listaTratos) -> {
            usuarioRepository.findById(usuarioId).ifPresent(usuario -> {
                if (usuario.getCorreoElectronico() != null && !usuario.getCorreoElectronico().isEmpty()) {

                    // Ordenamos la lista: Nulls primero (nunca tocados), luego fechas ascendentes (más viejos arriba)
                    listaTratos.sort(Comparator.comparing(
                            Trato::getFechaUltimaActividad,
                            Comparator.nullsFirst(Comparator.naturalOrder())
                    ));

                    String asunto = "Reporte Semanal: Tratos sin interacción programada";
                    String cuerpo = construirCuerpoCorreoDesatendidos(usuario.getNombre(), listaTratos);

                    try {
                        emailService.enviarCorreo(
                                usuario.getCorreoElectronico(),
                                asunto,
                                cuerpo,
                                null,
                                null,
                                null,
                                "REPORTE_DESATENDIDOS"
                        );
                        logger.info("Reporte de desatendidos enviado a: {}", usuario.getCorreoElectronico());

                        Thread.sleep(500);
                    } catch (Exception e) {
                        logger.error("Error enviando reporte a usuario {}: {}", usuarioId, e.getMessage());
                    }
                }
            });
        });
    }

    private String construirCuerpoCorreoDesatendidos(String nombreUsuario, List<Trato> tratos) {
        StringBuilder filasTabla = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                .withZone(ZONE_ID);

        for (Trato t : tratos) {
            String nombreCliente = "Sin contacto";
            if (t.getContacto() != null) {
                nombreCliente = t.getContacto().getNombre();
            } else if (t.getEmpresaId() != null) {
                nombreCliente = "Empresa (ID: " + t.getEmpresaId() + ")";
            }

            String fechaUltima = t.getFechaUltimaActividad() != null
                    ? formatter.format(t.getFechaUltimaActividad())
                    : "Sin actividad registrada";

            String etapa = formatearNombreFase(t.getFase());

            filasTabla.append(String.format(
                    "<tr>" +
                            "<td style='padding: 8px; border-bottom: 1px solid #ddd;'>%s</td>" + // Nombre Trato
                            "<td style='padding: 8px; border-bottom: 1px solid #ddd;'>%s</td>" + // Cliente
                            "<td style='padding: 8px; border-bottom: 1px solid #ddd;'>%s</td>" + // Etapa (NUEVO)
                            "<td style='padding: 8px; border-bottom: 1px solid #ddd; text-align: center;'>%s</td>" + // Fecha
                            "</tr>",
                    t.getNombre(),
                    nombreCliente,
                    etapa,
                    fechaUltima
            ));
        }

        return String.format("""
    <html>
    <body style="font-family: Arial, sans-serif; color: #333;">
        <h2 style="color: #d32f2f;">⚠️ Tratos Desatendidos</h2>
        <p>Hola <strong>%s</strong>,</p>
        <p>Los siguientes tratos no han tenido una interacción programada en los últimos 7 días. Te recomendamos revisarlos para no perder el seguimiento.</p>
        
        <table style="width: 100%%; border-collapse: collapse; margin-top: 15px;">
            <thead>
                <tr style="background-color: #f5f5f5;">
                    <th style="padding: 10px; text-align: left; border-bottom: 2px solid #ddd;">Nombre del Trato</th>
                    <th style="padding: 10px; text-align: left; border-bottom: 2px solid #ddd;">Cliente / Contacto</th>
                    <th style="padding: 10px; text-align: left; border-bottom: 2px solid #ddd;">Etapa</th>
                    <th style="padding: 10px; text-align: center; border-bottom: 2px solid #ddd;">Última Actividad</th>
                </tr>
            </thead>
            <tbody>
                %s
            </tbody>
        </table>
        
        <p style="margin-top: 20px; font-size: 12px; color: #777;">
            Este es un reporte automático generado por TSS Manager.
        </p>
    </body>
    </html>
    """, nombreUsuario, filasTabla.toString());
    }

    private String formatearEstatus(String estatus) {
        switch (estatus) {
            case "PENDIENTE":
                return "Pendiente";
            case "VENCIDA":
                return "Vencida";
            case "EN_PROCESO":
                return "En Proceso";
            default:
                return estatus;
        }
    }

    private String obtenerColorEstatus(EstatusPagoEnum estatus) {
        switch (estatus) {
            case VENCIDA:
                return "#d32f2f";
            case PENDIENTE:
                return "#f57c00";
            case EN_PROCESO:
                return "#1976d2";
            default:
                return "#666666";
        }
    }

    private String formatearEstatusPagar(String estatus) {
        return estatus;
    }

    private String obtenerColorEstatusPagar(String estatus) {
        switch (estatus.toLowerCase()) {
            case "vencida":
                return "#d32f2f";
            case "pendiente":
                return "#f57c00";
            case "en proceso":
                return "#1976d2";
            default:
                return "#666666";
        }
    }

    private String formatearNombreFase(String fase) {
        if (fase == null) return "Sin etapa";
        switch (fase) {
            case "CLASIFICACION": return "Clasificación";
            case "PRIMER_CONTACTO": return "Primer Contacto";
            case "ENVIO_DE_INFORMACION": return "Envío de Información";
            case "REUNION": return "Reunión";
            case "COTIZACION_PROPUESTA_PRACTICA": return "Cotización";
            case "NEGOCIACION_REVISION": return "Negociación";
            case "CERRADO_GANADO": return "Cerrado Ganado";
            case "RESPUESTA_POR_CORREO": return "Respuesta por Correo";
            case "INTERES_FUTURO": return "Interés Futuro";
            case "CERRADO_PERDIDO": return "Cerrado Perdido";
            default: return fase;
        }
    }
}