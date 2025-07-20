package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.*;
import com.tss.tssmanager_backend.entity.ConfiguracionAlmacenamiento;
import com.tss.tssmanager_backend.entity.HistorialLimpieza;
import com.tss.tssmanager_backend.repository.AlmacenamientoRepository;
import com.tss.tssmanager_backend.repository.ConfiguracionAlmacenamientoRepository;
import com.tss.tssmanager_backend.repository.HistorialLimpiezaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlmacenamientoService {

    @Autowired
    private AlmacenamientoRepository almacenamientoRepository;

    @Autowired
    private ConfiguracionAlmacenamientoRepository configuracionRepository;

    @Autowired
    private HistorialLimpiezaRepository historialRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // Mapeo de nombres de tablas a nombres de módulos más amigables
    private final Map<String, String> tablaToModulo = Map.of(
            "Tratos", "Tratos",
            "Empresas", "Empresas",
            "Contactos", "Contactos",
            "Notas_Tratos", "Notas",
            "Actividades", "Actividades",
            "Email_records", "Correos electrónicos",
            "Notificaciones", "Notificaciones",
            "Auditoria", "Auditoría",
            "Facturas", "Facturas",
            "Cotizaciones", "Cotizaciones"
    );

    public List<EstadisticasAlmacenamientoDTO> obtenerEstadisticasAlmacenamiento() {
        try {
            List<Object[]> resultados = almacenamientoRepository.obtenerEstadisticasAlmacenamiento();

            return resultados.stream()
                    .map(this::mapearEstadisticas)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error al obtener estadísticas de almacenamiento", e);
            return new ArrayList<>();
        }
    }

    public ResumenAlmacenamientoDTO obtenerResumenAlmacenamiento() {
        try {
            List<EstadisticasAlmacenamientoDTO> estadisticas = obtenerEstadisticasAlmacenamiento();

            BigDecimal espacioTotal = estadisticas.stream()
                    .map(EstadisticasAlmacenamientoDTO::getTamanoMb)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal espacioRecuperable = estadisticas.stream()
                    .map(EstadisticasAlmacenamientoDTO::getEspacioRecuperableMb)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Long totalRegistros = estadisticas.stream()
                    .mapToLong(EstadisticasAlmacenamientoDTO::getTotalRegistros)
                    .sum();

            Long registrosAntiguos = estadisticas.stream()
                    .mapToLong(EstadisticasAlmacenamientoDTO::getRegistrosAntiguos)
                    .sum();

            return new ResumenAlmacenamientoDTO(
                    espacioTotal,
                    espacioRecuperable,
                    totalRegistros,
                    registrosAntiguos,
                    estadisticas.size()
            );
        } catch (Exception e) {
            log.error("Error al obtener resumen de almacenamiento", e);
            return new ResumenAlmacenamientoDTO(BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L, 0);
        }
    }

    public List<ConfiguracionAlmacenamientoDTO> obtenerConfiguracionTablasHabilitadas() {
        try {
            List<ConfiguracionAlmacenamiento> configuraciones = configuracionRepository.findAllEnabledOrderByTableName();

            return configuraciones.stream()
                    .map(this::mapearConfiguracion)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error al obtener configuración de tablas habilitadas", e);
            return new ArrayList<>();
        }
    }

    public EstadisticasAlmacenamientoDTO obtenerEstadisticasTablaEspecifica(String tablaNombre, Integer diasAntiguedad) {
        try {
            // Obtener estadísticas básicas
            Double tamanoMb = almacenamientoRepository.calcularTamanoTabla(tablaNombre);
            Long totalRegistros = contarRegistrosTabla(tablaNombre);
            Long registrosAntiguos = contarRegistrosAntiguos(tablaNombre, diasAntiguedad);

            // Calcular espacio recuperable estimado
            BigDecimal espacioRecuperable = BigDecimal.valueOf(tamanoMb != null ? tamanoMb : 0.0)
                    .multiply(BigDecimal.valueOf(registrosAntiguos))
                    .divide(BigDecimal.valueOf(Math.max(totalRegistros, 1)), 2, BigDecimal.ROUND_HALF_UP);

            return new EstadisticasAlmacenamientoDTO(
                    tablaNombre,
                    totalRegistros,
                    BigDecimal.valueOf(tamanoMb != null ? tamanoMb : 0.0),
                    registrosAntiguos,
                    espacioRecuperable
            );
        } catch (Exception e) {
            log.error("Error al obtener estadísticas para tabla: " + tablaNombre, e);
            return new EstadisticasAlmacenamientoDTO(tablaNombre, 0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO);
        }
    }

    @Transactional
    public ResultadoLimpiezaDTO ejecutarLimpiezaManual(SolicitudLimpiezaDTO solicitud, Integer usuarioId) {
        try {
            log.info("Iniciando limpieza manual para tabla: {}", solicitud.getTablaNombre());

            // Verificar que la tabla esté habilitada para limpieza
            Optional<ConfiguracionAlmacenamiento> config = configuracionRepository.findByTablaNombre(solicitud.getTablaNombre());
            if (!config.isPresent() || !config.get().getHabilitadoLimpieza()) {
                return new ResultadoLimpiezaDTO(
                        solicitud.getTablaNombre(),
                        0,
                        BigDecimal.ZERO,
                        "La tabla no está habilitada para limpieza automática",
                        false
                );
            }

            // Calcular tamaño antes de la limpieza
            Double tamanoAntes = almacenamientoRepository.calcularTamanoTabla(solicitud.getTablaNombre());

            // Ejecutar limpieza según el tipo de tabla
            Integer registrosEliminados = ejecutarLimpiezaEspecifica(solicitud);

            // Calcular tamaño después de la limpieza
            Double tamanoDesues = almacenamientoRepository.calcularTamanoTabla(solicitud.getTablaNombre());
            BigDecimal espacioLiberado = BigDecimal.valueOf(
                    (tamanoAntes != null ? tamanoAntes : 0.0) - (tamanoDesues != null ? tamanoDesues : 0.0)
            );

            // Registrar en historial
            HistorialLimpieza historial = new HistorialLimpieza();
            historial.setTablaNombre(solicitud.getTablaNombre());
            historial.setRegistrosEliminados(registrosEliminados);
            historial.setEspacioLiberadoMb(espacioLiberado);
            historial.setTipoLimpieza("MANUAL");
            historial.setUsuarioId(usuarioId);
            historial.setDescripcion("Limpieza manual - " + solicitud.getCriterioEliminacion());
            historialRepository.save(historial);

            log.info("Limpieza completada. Registros eliminados: {}, Espacio liberado: {} MB",
                    registrosEliminados, espacioLiberado);

            return new ResultadoLimpiezaDTO(
                    solicitud.getTablaNombre(),
                    registrosEliminados,
                    espacioLiberado,
                    "Limpieza completada exitosamente",
                    true
            );

        } catch (Exception e) {
            log.error("Error durante la limpieza manual", e);
            return new ResultadoLimpiezaDTO(
                    solicitud.getTablaNombre(),
                    0,
                    BigDecimal.ZERO,
                    "Error durante la limpieza: " + e.getMessage(),
                    false
            );
        }
    }

    @Transactional
    public Integer ejecutarLimpiezaAutomatica() {
        try {
            log.info("Iniciando limpieza automática de todas las tablas");

            Integer totalEliminados = almacenamientoRepository.limpiarRegistrosAntiguos("Tratos", null, "AUTOMATICA");
            log.info("Limpieza automática completada. Registros eliminados: {}", totalEliminados);

            return totalEliminados;
        } catch (Exception e) {
            log.error("Error durante la limpieza automática", e);
            return 0;
        }
    }

    public List<HistorialLimpieza> obtenerHistorialLimpieza(Integer dias) {
        try {
            LocalDateTime fechaDesde = LocalDateTime.now().minusDays(dias != null ? dias : 30);
            return historialRepository.findRecentCleaningHistory(fechaDesde);
        } catch (Exception e) {
            log.error("Error al obtener historial de limpieza", e);
            return new ArrayList<>();
        }
    }

    private EstadisticasAlmacenamientoDTO mapearEstadisticas(Object[] resultado) {
        return new EstadisticasAlmacenamientoDTO(
                (String) resultado[0],           // tabla_nombre
                (Long) resultado[1],             // total_registros
                (BigDecimal) resultado[2],       // tamano_mb
                (Long) resultado[3],             // registros_antiguos
                (BigDecimal) resultado[4]        // espacio_recuperable_mb
        );
    }

    private ConfiguracionAlmacenamientoDTO mapearConfiguracion(ConfiguracionAlmacenamiento config) {
        return new ConfiguracionAlmacenamientoDTO(
                config.getId(),
                config.getTablaNombre(),
                config.getHabilitadoLimpieza(),
                config.getDiasRetencion()
        );
    }

    private Long contarRegistrosTabla(String tablaNombre) {
        try {
            String sql = "SELECT COUNT(*) FROM \"" + tablaNombre + "\"";
            Query query = entityManager.createNativeQuery(sql);
            return ((Number) query.getSingleResult()).longValue();
        } catch (Exception e) {
            log.error("Error al contar registros de tabla: " + tablaNombre, e);
            return 0L;
        }
    }

    private Long contarRegistrosAntiguos(String tablaNombre, Integer dias) {
        try {
            String sql;
            switch (tablaNombre) {
                case "Facturas":
                    sql = "SELECT COUNT(*) FROM \"Facturas\" f WHERE EXISTS (" +
                            "SELECT 1 FROM \"Solicitudes_Factura_Nota\" sfn WHERE sfn.id = f.solicitud_id " +
                            "AND sfn.fecha_emision < CURRENT_DATE - INTERVAL '" + dias + " days')";
                    break;
                case "Solicitudes_Factura_Nota":
                    sql = "SELECT COUNT(*) FROM \"Solicitudes_Factura_Nota\" WHERE fecha_emision < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Unidades_Cotizacion":
                    sql = "SELECT COUNT(*) FROM \"Unidades_Cotizacion\" WHERE EXISTS (" +
                            "SELECT 1 FROM \"Cotizaciones\" c WHERE c.id = cotizacion_id " +
                            "AND c.fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days')";
                    break;
                case "Cotizaciones":
                    sql = "SELECT COUNT(*) FROM \"Cotizaciones\" WHERE fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Cuentas_por_Cobrar":
                    sql = "SELECT COUNT(*) FROM \"Cuentas_por_Cobrar\" WHERE fecha_pago < CURRENT_DATE - INTERVAL '" + dias + " days' AND estatus = 'PAGADA'";
                    break;
                case "Emisores":
                    sql = "SELECT COUNT(*) FROM \"Emisores\" WHERE (SELECT COUNT(*) FROM \"Solicitudes_Factura_Nota\" sfn WHERE sfn.emisor_id = id) = 0";
                    break;
                case "Cuentas_por_Pagar":
                    sql = "SELECT COUNT(*) FROM \"Cuentas_por_Pagar\" WHERE fecha_pago < CURRENT_DATE - INTERVAL '" + dias + " days' AND estatus = 'PAGADA'";
                    break;
                case "Transacciones":
                    sql = "SELECT COUNT(*) FROM \"Transacciones\" WHERE fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Cuentas_Transacciones":
                    sql = "SELECT COUNT(*) FROM \"Cuentas_Transacciones\" WHERE (SELECT COUNT(*) FROM \"Transacciones\" t WHERE t.cuenta_id = id) = 0";
                    break;
                case "Categorias_Transacciones":
                    sql = "SELECT COUNT(*) FROM \"Categorias_Transacciones\" WHERE (SELECT COUNT(*) FROM \"Cuentas_Transacciones\" ct WHERE ct.categoria_id = id) = 0";
                    break;
                case "Historial_Saldos_SIMs":
                    sql = "SELECT COUNT(*) FROM \"Historial_Saldos_SIMs\" WHERE fecha < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Equipos_Estatus":
                    sql = "SELECT COUNT(*) FROM \"Equipos_Estatus\" WHERE fecha_check < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Equipos":
                    sql = "SELECT COUNT(*) FROM \"Equipos\" WHERE fecha_activacion < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "SIMs":
                    sql = "SELECT COUNT(*) FROM \"SIMs\" WHERE vigencia < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Modelos_Equipos":
                    sql = "SELECT COUNT(*) FROM \"Modelos_Equipos\" WHERE (SELECT COUNT(*) FROM \"Equipos\" e WHERE e.modelo_id = id) = 0";
                    break;
                case "Proveedores":
                    sql = "SELECT COUNT(*) FROM \"Proveedores\" WHERE (SELECT COUNT(*) FROM \"Equipos\" e WHERE e.proveedor_id = id) = 0";
                    break;
                case "Actividades":
                    sql = "SELECT COUNT(*) FROM \"Actividades\" WHERE fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Notas_Tratos":
                    sql = "SELECT COUNT(*) FROM \"Notas_Tratos\" WHERE fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Tratos":
                    sql = "SELECT COUNT(*) FROM \"Tratos\" WHERE fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days' AND fase = 'CERRADO_PERDIDO'";
                    break;
                case "Secuencias_Tratos":
                    sql = "SELECT COUNT(*) FROM \"Secuencias_Tratos\" WHERE (SELECT COUNT(*) FROM \"Tratos\" t WHERE t.empresa_id = empresa_id) = 0";
                    break;
                case "Telefonos_Contactos":
                    sql = "SELECT COUNT(*) FROM \"Telefonos_Contactos\" WHERE EXISTS (" +
                            "SELECT 1 FROM \"Contactos\" c WHERE c.id = contacto_id " +
                            "AND c.fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days')";
                    break;
                case "Contactos":
                    sql = "SELECT COUNT(*) FROM \"Contactos\" WHERE fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Correos_Contactos":
                    sql = "SELECT COUNT(*) FROM \"Correos_Contactos\" WHERE fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Notificaciones":
                    sql = "SELECT COUNT(*) FROM \"Notificaciones\" WHERE fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Empresas":
                    sql = "SELECT COUNT(*) FROM \"Empresas\" WHERE fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Usuarios":
                    sql = "SELECT COUNT(*) FROM \"Usuarios\" WHERE fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days' AND estatus = 'INACTIVO'";
                    break;
                case "Auditoria":
                    sql = "SELECT COUNT(*) FROM \"Auditoria\" WHERE fecha < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Plantillas_Correos":
                    sql = "SELECT COUNT(*) FROM \"Plantillas_Correos\" WHERE fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Plantillas_Correos_Adjuntos":
                    sql = "SELECT COUNT(*) FROM \"Plantillas_Correos_Adjuntos\" WHERE EXISTS (" +
                            "SELECT 1 FROM \"Plantillas_Correos\" pc WHERE pc.id = plantilla_id " +
                            "AND pc.fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days')";
                    break;
                case "Configuracion_Empresa":
                    sql = "SELECT COUNT(*) FROM \"Configuracion_Empresa\" WHERE fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Copias_Seguridad":
                    sql = "SELECT COUNT(*) FROM \"Copias_Seguridad\" WHERE fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Email_records":
                    sql = "SELECT COUNT(*) FROM \"Email_records\" WHERE fecha_envio < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Plantillas_Importacion":
                    sql = "SELECT COUNT(*) FROM \"Plantillas_Importacion\" WHERE fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Historial_Exportaciones":
                    sql = "SELECT COUNT(*) FROM \"Historial_Exportaciones\" WHERE fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                case "Historial_Importaciones":
                    sql = "SELECT COUNT(*) FROM \"Historial_Importaciones\" WHERE fecha_creacion < CURRENT_DATE - INTERVAL '" + dias + " days'";
                    break;
                default:
                    return 0L;
            }
            Query query = entityManager.createNativeQuery(sql);
            return ((Number) query.getSingleResult()).longValue();
        } catch (Exception e) {
            log.error("Error al contar registros antiguos de tabla: " + tablaNombre, e);
            return 0L;
        }
    }

    private Integer ejecutarLimpiezaEspecifica(SolicitudLimpiezaDTO solicitud) {
        try {
            String tablaNombre = solicitud.getTablaNombre();
            return almacenamientoRepository.limpiarRegistrosAntiguos(tablaNombre, null, "MANUAL");
        } catch (Exception e) {
            log.error("Error al ejecutar limpieza específica para tabla: " + solicitud.getTablaNombre(), e);
            return 0;
        }
    }

    @Scheduled(cron = "0 0 2 * * *") // Ejecutar diariamente a las 2:00 AM
    public void ejecutarLimpiezaAutomaticaDiaria() {
        log.info("Iniciando limpieza automática diaria de tratos CERRADO_PERDIDO");

        try {
            Integer tratosEliminados = ejecutarLimpiezaAutomatica();

            if (tratosEliminados > 0) {
                log.info("Limpieza automática completada. Tratos eliminados: {}", tratosEliminados);
            } else {
                log.debug("Limpieza automática ejecutada. No se encontraron tratos para eliminar");
            }

        } catch (Exception e) {
            log.error("Error durante la limpieza automática programada", e);
        }
    }


    @Scheduled(cron = "0 0 3 * * SUN") // Ejecutar los domingos a las 3:00 AM
    public void generarReporteEstadisticasSemanales() {
        log.info("Generando reporte de estadísticas de almacenamiento semanales");

        try {
            var resumen = obtenerResumenAlmacenamiento();

            log.info("Reporte semanal de almacenamiento:");
            log.info("- Espacio total usado: {} MB", resumen.getEspacioTotalMb());
            log.info("- Espacio recuperable: {} MB", resumen.getEspacioRecuperableMb());
            log.info("- Total de registros: {}", resumen.getTotalRegistros());
            log.info("- Registros antiguos: {}", resumen.getRegistrosAntiguos());
            log.info("- Tablas monitoreadas: {}", resumen.getTotalTablas());

        } catch (Exception e) {
            log.error("Error al generar reporte de estadísticas semanales", e);
        }
    }


}