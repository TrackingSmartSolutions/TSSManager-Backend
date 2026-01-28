package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.EquiposEstatusDTO;
import com.tss.tssmanager_backend.entity.Equipo;
import com.tss.tssmanager_backend.entity.EquiposEstatus;
import com.tss.tssmanager_backend.entity.Plataforma;
import com.tss.tssmanager_backend.entity.Sim;
import com.tss.tssmanager_backend.enums.*;
import com.tss.tssmanager_backend.repository.EquipoRepository;
import com.tss.tssmanager_backend.repository.EquiposEstatusRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EquipoService {

    @Autowired
    private EquipoRepository repository;

    @Autowired
    private EquiposEstatusRepository equiposEstatusRepository;

    @Autowired
    private CreditoPlataformaService creditoPlataformaService;

    @Autowired
    private SimService simService;

    private static final Logger logger = LoggerFactory.getLogger(EquipoService.class);
    public Iterable<Equipo> obtenerTodosLosEquipos() {
        return repository.findAllWithSimsOrderedByExpiration();
    }

    @Cacheable(value = "equipos", key = "#id")
    public Equipo findById(Integer id) {
        return repository.findById(id).orElse(null);
    }

    @Cacheable(value = "equipos", key = "#imei")
    public Optional<Equipo> findByImei(String imei) {
        return repository.findByImei(imei);
    }

    @Cacheable(value = "equipos", key = "'cliente_' + #clienteId")
    public List<Equipo> findByClienteId(Long clienteId) {
        return repository.findByClienteId(clienteId);
    }

    @Cacheable(value = "equipos", key = "'tipo_' + #tipo + '_estatus_' + #estatus")
    public List<Equipo> findByTipoAndEstatus(String tipo, String estatus) {
        return repository.findByTipoAndEstatus(tipo, estatus);
    }

    @CacheEvict(value = "equipos", key = "#equipo.id")
    public Equipo save(Equipo equipo) {
        return repository.save(equipo);
    }

    public Equipo obtenerEquipo(Integer id) {
        Optional<Equipo> equipo = repository.findById(id);
        if (equipo.isPresent()) {
            return equipo.get();
        }
        throw new EntityNotFoundException("Equipo no encontrado con ID: " + id);
    }

    @Transactional
    @CacheEvict(value = {"equipos", "dashboard-stats"}, allEntries = true)
    public Equipo guardarEquipo(Equipo equipo) {
        boolean debeRegistrarCargo = false;
        Integer creditosARegistrar = 0;

        if (equipo.getEstatus() == EstatusEquipoEnum.ACTIVO) {
            equipo.setFechaActivacion(Date.valueOf(LocalDate.now()));
            equipo.setFechaExpiracion(calculateExpirationDate(equipo.getTipoActivacion(), null));

            if (equipo.getCreditosUsados() != null &&
                    equipo.getCreditosUsados() > 0 &&
                    (equipo.getPlataforma() != null &&
                            (equipo.getPlataforma().getId().equals(1) || equipo.getPlataforma().getId().equals(2)))) {
                debeRegistrarCargo = true;
                creditosARegistrar = equipo.getCreditosUsados();
            }
        }

        if (equipo.getCreditosUsados() == null) {
            equipo.setCreditosUsados(0);
        }
        if (equipo.getClienteDefault() != null) {
            System.out.println("Guardando equipo con cliente default: " + equipo.getClienteDefault());
        }

        Equipo equipoGuardado = repository.save(equipo);

        if (debeRegistrarCargo) {
            String subtipo = null;
            if (equipoGuardado.getPlataforma() != null && equipo.getPlataforma().getId().equals(2)) {
                subtipo = equipoGuardado.getTipoActivacion() == TipoActivacionEquipoEnum.ANUAL ? "ANUAL" : "VITALICIA";
            }

            creditoPlataformaService.registrarCargoConSubtipo(
                    equipoGuardado.getPlataforma(),
                    ConceptoCreditoEnum.INTEGRACION,
                    new BigDecimal(creditosARegistrar),
                    "Integración de equipo: " + equipoGuardado.getNombre(),
                    equipoGuardado.getId(),
                    subtipo
            );
        }

        if (equipoGuardado.getPlataforma() != null &&
                equipoGuardado.getEstatus() == EstatusEquipoEnum.ACTIVO &&
                (equipoGuardado.getPlataforma().getId().equals(5) || equipoGuardado.getPlataforma().getId().equals(6))) {

            creditoPlataformaService.registrarAsignacionLicencia(
                    equipoGuardado.getPlataforma(),
                    "Asignación de licencia - Equipo: " + equipoGuardado.getNombre(),
                    equipoGuardado.getId()
            );
        }

        return equipoGuardado;
    }

    @Transactional
    @CacheEvict(value = {"equipos", "dashboard-stats"}, allEntries = true)
    public Equipo actualizarEquipo(Integer id, Equipo equipoDetails) {
        Equipo equipo = obtenerEquipo(id);

        EstatusEquipoEnum estatusAnterior = equipo.getEstatus();
        Integer creditosAnteriores = equipo.getCreditosUsados();
        Sim simAnterior = equipo.getSimReferenciada();

        boolean debeRegistrarCargo = false;
        Integer creditosARegistrar = 0;

        // Actualizar campos básicos
        equipo.setImei(equipoDetails.getImei());
        equipo.setNombre(equipoDetails.getNombre());
        equipo.setModeloId(equipoDetails.getModeloId());
        equipo.setClienteId(equipoDetails.getClienteId());
        equipo.setProveedorId(equipoDetails.getProveedorId());
        equipo.setTipo(equipoDetails.getTipo());

        equipo.setEstatus(equipoDetails.getEstatus());

        equipo.setTipoActivacion(equipoDetails.getTipoActivacion());
        equipo.setPlataforma(equipoDetails.getPlataforma());
        equipo.setSimReferenciada(equipoDetails.getSimReferenciada());
        equipo.setClienteDefault(equipoDetails.getClienteDefault());
        equipo.setCreditosUsados(equipoDetails.getCreditosUsados() != null ? equipoDetails.getCreditosUsados() : 0);

        if (equipo.getEstatus() == EstatusEquipoEnum.ACTIVO) {
            if (equipoDetails.getFechaActivacion() != null) {
                equipo.setFechaActivacion(equipoDetails.getFechaActivacion());
            } else if (equipo.getFechaActivacion() == null) {
                equipo.setFechaActivacion(Date.valueOf(LocalDate.now()));
            }
            equipo.setFechaExpiracion(calculateExpirationDate(equipo.getTipoActivacion(), equipo.getFechaActivacion()));

            if (isExpired(equipo)) {
                equipo.setEstatus(EstatusEquipoEnum.EXPIRADO);
            }

            if (equipo.getEstatus() == EstatusEquipoEnum.ACTIVO) {
                if (estatusAnterior != EstatusEquipoEnum.ACTIVO &&
                        equipo.getCreditosUsados() != null &&
                        equipo.getCreditosUsados() > 0 &&
                        (creditosAnteriores == null || creditosAnteriores == 0) &&
                        (equipo.getPlataforma() != null &&
                                (equipo.getPlataforma().getId().equals(1) || equipo.getPlataforma().getId().equals(2)))) {
                    debeRegistrarCargo = true;
                    creditosARegistrar = equipo.getCreditosUsados();
                } else if (estatusAnterior == EstatusEquipoEnum.ACTIVO &&
                        creditosAnteriores != null &&
                        equipo.getCreditosUsados() != null &&
                        !equipo.getCreditosUsados().equals(creditosAnteriores) &&
                        equipo.getCreditosUsados() > creditosAnteriores &&
                        (equipo.getPlataforma() != null &&
                                (equipo.getPlataforma().getId().equals(1) || equipo.getPlataforma().getId().equals(2)))) {
                    debeRegistrarCargo = true;
                    creditosARegistrar = equipo.getCreditosUsados() - creditosAnteriores;
                }
            }
        } else if (equipo.getEstatus() == EstatusEquipoEnum.INACTIVO) {
            equipo.setFechaActivacion(null);
            equipo.setFechaExpiracion(null);
        } else if (equipo.getEstatus() == EstatusEquipoEnum.EXPIRADO) {
        }

        if (equipo.getClienteDefault() != null) {
            System.out.println("Actualizando equipo con cliente default: " + equipo.getClienteDefault());
        }

        Equipo equipoActualizado = repository.save(equipo);

        Sim simParaValidar = equipoActualizado.getSimReferenciada();
        if (simParaValidar == null) {
            simParaValidar = simAnterior;
        }

        if (simParaValidar != null && simParaValidar.getTarifa() == TarifaSimEnum.POR_SEGUNDO) {
            BigDecimal recargaActual = simParaValidar.getRecarga();
            BigDecimal recargaDiez = new BigDecimal("10.00");
            BigDecimal recargaCincuenta = new BigDecimal("50.00");

            if (equipoActualizado.getEstatus() == EstatusEquipoEnum.EXPIRADO) {
                if (recargaActual == null || recargaActual.compareTo(recargaDiez) != 0) {
                    try {
                        simService.actualizarRecargaPorCambioEstatusEquipo(simParaValidar.getId(), recargaDiez);
                    } catch (Exception e) {
                        System.err.println("Error actualizando SIM: " + e.getMessage());
                    }
                }
            }
            else if (equipoActualizado.getEstatus() == EstatusEquipoEnum.ACTIVO ||
                    equipoActualizado.getEstatus() == EstatusEquipoEnum.INACTIVO) {
                if (recargaActual == null || recargaActual.compareTo(recargaCincuenta) != 0) {
                    try {
                        simService.actualizarRecargaPorCambioEstatusEquipo(simParaValidar.getId(), recargaCincuenta);
                    } catch (Exception e) {
                        System.err.println("Error actualizando SIM: " + e.getMessage());
                    }
                }
            }
        }

        if (debeRegistrarCargo) {
            String subtipo = null;
            if (equipoActualizado.getPlataforma() != null && equipo.getPlataforma().getId().equals(2)) {
                subtipo = equipoActualizado.getTipoActivacion() == TipoActivacionEquipoEnum.ANUAL ? "ANUAL" : "VITALICIA";
            }

            creditoPlataformaService.registrarCargoConSubtipo(
                    equipoActualizado.getPlataforma(),
                    ConceptoCreditoEnum.INTEGRACION,
                    new BigDecimal(creditosARegistrar),
                    "Integración de equipo: " + equipoActualizado.getNombre(),
                    equipoActualizado.getId(),
                    subtipo
            );
        }

        Plataforma plataformaAnterior = equipo.getPlataforma(); // Ya tenemos el equipo anterior guardado al inicio del método

        boolean cambioPlataforma = plataformaAnterior != null &&
                equipoActualizado.getPlataforma() != null &&
                !plataformaAnterior.getId().equals(equipoActualizado.getPlataforma().getId());

        boolean esPlataformaLicenciaAnterior = plataformaAnterior != null &&
                (plataformaAnterior.getId().equals(5) || plataformaAnterior.getId().equals(6));

        boolean esPlataformaLicenciaNueva = equipoActualizado.getPlataforma() != null &&
                (equipoActualizado.getPlataforma().getId().equals(5) || equipoActualizado.getPlataforma().getId().equals(6));

        // Si cambió DESDE una plataforma de licencias
        if (cambioPlataforma && esPlataformaLicenciaAnterior && estatusAnterior == EstatusEquipoEnum.ACTIVO) {
            creditoPlataformaService.registrarLiberacionLicencia(
                    plataformaAnterior,
                    "Liberación de licencia - Equipo movido: " + equipoActualizado.getNombre(),
                    equipoActualizado.getId()
            );
        }

        // Si cambió HACIA una plataforma de licencias y está activo
        if (esPlataformaLicenciaNueva && equipoActualizado.getEstatus() == EstatusEquipoEnum.ACTIVO) {
            if (cambioPlataforma || estatusAnterior != EstatusEquipoEnum.ACTIVO) {
                creditoPlataformaService.registrarAsignacionLicencia(
                        equipoActualizado.getPlataforma(),
                        "Asignación de licencia - Equipo: " + equipoActualizado.getNombre(),
                        equipoActualizado.getId()
                );
            }
        }

        // Si se desactivó un equipo con licencia
        if (esPlataformaLicenciaNueva &&
                estatusAnterior == EstatusEquipoEnum.ACTIVO &&
                equipoActualizado.getEstatus() == EstatusEquipoEnum.INACTIVO) {
            creditoPlataformaService.registrarLiberacionLicencia(
                    equipoActualizado.getPlataforma(),
                    "Liberación de licencia - Equipo desactivado: " + equipoActualizado.getNombre(),
                    equipoActualizado.getId()
            );
        }

        return equipoActualizado;
    }

    @CacheEvict(value = {"equipos", "dashboard-stats"}, allEntries = true)
    public void eliminarEquipo(Integer id) {
        Equipo equipo = obtenerEquipo(id);
        if (equipo.getSimReferenciada() != null) {
            throw new IllegalStateException("No se puede eliminar el equipo porque tiene una SIM vinculada. Desvincule la SIM primero.");
        }

        if (equipo.getPlataforma() != null &&
                (equipo.getPlataforma().getId().equals(5) || equipo.getPlataforma().getId().equals(6)) &&
                equipo.getEstatus() == EstatusEquipoEnum.ACTIVO) {

            creditoPlataformaService.registrarLiberacionLicencia(
                    equipo.getPlataforma(),
                    "Liberación de licencia - Equipo eliminado: " + equipo.getNombre(),
                    equipo.getId()
            );
        }

        repository.delete(equipo);
    }

    private Date calculateExpirationDate(TipoActivacionEquipoEnum tipoActivacion, Date fechaBase) {
        LocalDate baseDate = fechaBase != null ? fechaBase.toLocalDate() : LocalDate.now();
        return tipoActivacion == TipoActivacionEquipoEnum.ANUAL
                ? Date.valueOf(baseDate.plusYears(1))
                : Date.valueOf(baseDate.plusYears(10));
    }

    @CacheEvict(value = {"equipos", "dashboard-stats"}, allEntries = true)
    public void activarEquipo(Integer id) {
        Equipo equipo = obtenerEquipo(id);
        if (equipo.getEstatus() == EstatusEquipoEnum.INACTIVO) {
            equipo.setEstatus(EstatusEquipoEnum.ACTIVO);
            equipo.setFechaActivacion(Date.valueOf(LocalDate.now()));
            equipo.setFechaExpiracion(calculateExpirationDate(equipo.getTipoActivacion(), null));
            equipo.setUsuarioActivacion(obtenerUsuarioActual());
            repository.save(equipo);

            if (equipo.getSimReferenciada() != null &&
                    equipo.getSimReferenciada().getTarifa() == TarifaSimEnum.POR_SEGUNDO) {
                simService.actualizarRecargaPorCambioEstatusEquipo(
                        equipo.getSimReferenciada().getId(),
                        new BigDecimal("50.00")
                );
            }
        }
    }

    @CacheEvict(value = {"equipos", "dashboard-stats"}, allEntries = true)
    public void renovarEquipo(Integer id) {
        Equipo equipo = obtenerEquipo(id);
        if (needsRenewal(equipo)) {
            equipo.setEstatus(EstatusEquipoEnum.ACTIVO);
            equipo.setFechaActivacion(Date.valueOf(LocalDate.now()));

            Date fechaBase;
            if (equipo.getFechaExpiracion() != null && !isExpired(equipo)) {
                fechaBase = equipo.getFechaExpiracion();
            } else {
                fechaBase = null;
            }

            equipo.setFechaExpiracion(calculateExpirationDate(equipo.getTipoActivacion(), fechaBase));
            equipo.setUsuarioRenovacion(obtenerUsuarioActual());
            repository.save(equipo);
            if (equipo.getSimReferenciada() != null &&
                    equipo.getSimReferenciada().getTarifa() == TarifaSimEnum.POR_SEGUNDO) {
                simService.actualizarRecargaPorCambioEstatusEquipo(
                        equipo.getSimReferenciada().getId(),
                        new BigDecimal("50.00")
                );
            }
        }
    }

    private boolean needsRenewal(Equipo equipo) {
        if (equipo.getFechaExpiracion() == null) return false;
        LocalDate today = LocalDate.now();
        LocalDate expirationDate = equipo.getFechaExpiracion().toLocalDate();
        long daysUntilExpiration = expirationDate.toEpochDay() - today.toEpochDay();

        return (equipo.getEstatus() == EstatusEquipoEnum.ACTIVO && daysUntilExpiration <= 30) ||
                (equipo.getEstatus() == EstatusEquipoEnum.EXPIRADO);
    }

    private boolean isExpired(Equipo equipo) {
        if (equipo.getFechaExpiracion() == null) return false;
        LocalDate today = LocalDate.now();
        return equipo.getFechaExpiracion().toLocalDate().isBefore(today);
    }

    public void checkExpiredEquipos() {
        List<Equipo> equipos = repository.findAll();
        LocalDate today = LocalDate.now();
        for (Equipo equipo : equipos) {
            if (equipo.getFechaExpiracion() != null &&
                    equipo.getFechaExpiracion().toLocalDate().isBefore(today) &&
                    equipo.getEstatus() == EstatusEquipoEnum.ACTIVO) {
                equipo.setEstatus(EstatusEquipoEnum.EXPIRADO);
                repository.save(equipo);
            }
        }
    }

    public Map<Integer, Long> contarEquiposPorModelo() {
        Map<Integer, Long> conteoPorModelo = new HashMap<>();
        repository.findAll().forEach(equipo -> {
            conteoPorModelo.merge(equipo.getModeloId(), 1L, Long::sum);
        });
        return conteoPorModelo;
    }

    @Transactional
    @CacheEvict(value = {"equipos", "dashboard-stats"}, allEntries = true) // Añadir esta línea
    public void guardarEstatus(List<Map<String, Object>> estatusList) {
        Timestamp fechaActual = new Timestamp(System.currentTimeMillis());

        estatusList.forEach(estatus -> {
            EquiposEstatus es = new EquiposEstatus();
            es.setEquipo(repository.findById((Integer) estatus.get("equipoId"))
                    .orElseThrow(() -> new EntityNotFoundException("Equipo no encontrado con ID: " + estatus.get("equipoId"))));
            es.setEstatus(EstatusReporteEquipoEnum.valueOf((String) estatus.get("status")));
            es.setMotivo((String) estatus.get("motivo"));
            es.setFechaCheck(fechaActual);

            equiposEstatusRepository.save(es);
        });
    }

    public List<EquiposEstatusDTO> obtenerEstatus() {
        LocalDateTime fechaDesde = LocalDateTime.now().minusDays(30);
        Timestamp fechaDesdeSql = Timestamp.valueOf(fechaDesde);
        return equiposEstatusRepository.findRecentEstatusOptimized(fechaDesdeSql);
    }

    public Page<EquiposEstatusDTO> obtenerEstatusPaginado(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return equiposEstatusRepository.findAllEstatusOptimizedPaged(pageable);
    }

    @CacheEvict(value = "dashboard-stats", key = "'estatus-plataforma'")
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerDashboardEstatusOptimizado() {
        logger.info("Iniciando obtención de datos para dashboard de estatus");
        Map<String, Object> result = new HashMap<>();

        try {
            Timestamp fechaUltimoCheck = equiposEstatusRepository.findMaxFechaCheck();
            result.put("fechaUltimoCheck", fechaUltimoCheck);
            logger.debug("Fecha último check: {}", fechaUltimoCheck);

            List<Object[]> dashboardData = repository.findDashboardEstatusData();
            logger.debug("Datos obtenidos de findDashboardEstatusData: {} filas", dashboardData.size());

            List<Map<String, Object>> equiposOffline = procesarEquiposOfflineOptimizado(dashboardData, fechaUltimoCheck);
            logger.debug("Equipos offline procesados: {} equipos", equiposOffline.size());

            result.put("estatusPorCliente", procesarEstatusPorClienteOptimizado(dashboardData));
            logger.debug("Estatus por cliente procesado");

            result.put("equiposPorPlataforma", procesarEquiposPorPlataformaOptimizado(dashboardData));
            logger.debug("Equipos por plataforma procesado");

            result.put("equiposOffline", equiposOffline);
            result.put("equiposPorMotivo", procesarEquiposPorMotivo(equiposOffline));
            logger.debug("Motivos procesados");

            List<Equipo> paraCheck = repository.findEquiposParaCheck();
            logger.debug("Equipos para check: {} equipos", paraCheck.size());
            result.put("equiposParaCheck", paraCheck);

            logger.info("Dashboard de estatus procesado exitosamente");
            return result;
        } catch (Exception e) {
            logger.error("Error al procesar dashboard de estatus: {}", e.getMessage(), e);
            throw new RuntimeException("Error al obtener datos del dashboard: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> procesarEstatusPorClienteOptimizado(List<Object[]> data) {
        Map<String, Map<String, Integer>> clienteStats = new HashMap<>();

        for (Object[] row : data) {
            String cliente = (String) row[0];
            String estatus = (String) row[1];

            clienteStats.computeIfAbsent(cliente, k -> new HashMap<>())
                    .merge(estatus, 1, Integer::sum);
        }

        return clienteStats.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("cliente", entry.getKey());
                    item.put("enLinea", entry.getValue().getOrDefault("ONLINE", 0));
                    item.put("fueraLinea", entry.getValue().getOrDefault("OFFLINE", 0));
                    return item;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> procesarEquiposPorPlataformaOptimizado(List<Object[]> data) {
        Map<String, Integer> plataformaCount = new HashMap<>();

        for (Object[] row : data) {
            String plataforma = (String) row[2];
            if (plataforma != null) {
                plataformaCount.merge(plataforma, 1, Integer::sum);
            }
        }

        return plataformaCount.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("plataforma", entry.getKey());
                    item.put("cantidad", entry.getValue());
                    return item;
                })
                .sorted((a, b) -> Integer.compare((Integer) b.get("cantidad"), (Integer) a.get("cantidad")))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> procesarEquiposOfflineOptimizado(List<Object[]> data, Timestamp fechaUltimoCheck) {
        List<Map<String, Object>> equiposOffline = new ArrayList<>();

        for (Object[] row : data) {
            String estatusReporte = (String) row[1];

            if (!"ONLINE".equals(estatusReporte)) {
                Map<String, Object> equipo = new HashMap<>();
                equipo.put("cliente", row[0] != null ? row[0] : "Sin Cliente");
                equipo.put("nombre", row[3]);
                equipo.put("plataforma", row[2]);
                equipo.put("imei", row[4]);
                equipo.put("motivo", row[5] != null ? row[5] : "Sin reporte de estatus");
                equipo.put("fechaCheck", row[6]);
                equipo.put("reportando", false);

                equiposOffline.add(equipo);
            }
        }

        return equiposOffline;
    }

    private List<Map<String, Object>> procesarEquiposPorMotivo(List<Map<String, Object>> equiposOffline) {
        Map<String, Integer> motivoCount = new HashMap<>();

        for (Map<String, Object> equipo : equiposOffline) {
            String motivo = (String) equipo.get("motivo");
            if (motivo != null && !motivo.trim().isEmpty()) {
                motivoCount.merge(motivo, 1, Integer::sum);
            }
        }

        return motivoCount.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("motivo", entry.getKey());
                    item.put("cantidad", entry.getValue());
                    return item;
                })
                .sorted((a, b) -> Integer.compare((Integer) b.get("cantidad"), (Integer) a.get("cantidad")))
                .collect(Collectors.toList());
    }

    @CacheEvict(value = {"equipos", "dashboard-stats"}, allEntries = true)
    public void activarEquipoConCreditos(Integer id, Integer creditosUsados) {
        Equipo equipo = obtenerEquipo(id);
        if (equipo.getEstatus() == EstatusEquipoEnum.INACTIVO) {
            equipo.setEstatus(EstatusEquipoEnum.ACTIVO);
            equipo.setFechaActivacion(Date.valueOf(LocalDate.now()));
            equipo.setFechaExpiracion(calculateExpirationDate(equipo.getTipoActivacion(), null));
            equipo.setCreditosUsados(creditosUsados != null ? creditosUsados : 0);
            equipo.setUsuarioActivacion(obtenerUsuarioActual());
            repository.save(equipo);

            // Registrar cargo en créditos plataforma
            if (creditosUsados != null && creditosUsados > 0 &&
                    (equipo.getPlataforma() != null &&
                            (equipo.getPlataforma().getId().equals(1) || equipo.getPlataforma().getId().equals(2)))) {

                String subtipo = null;
                if (equipo.getPlataforma() != null && equipo.getPlataforma().getId().equals(2)) {
                    subtipo = equipo.getTipoActivacion() == TipoActivacionEquipoEnum.ANUAL ? "ANUAL" : "VITALICIA";
                }

                creditoPlataformaService.registrarCargoConSubtipo(
                        equipo.getPlataforma(),
                        ConceptoCreditoEnum.INTEGRACION,
                        new BigDecimal(creditosUsados),
                        "Integración de equipo: " + equipo.getNombre(),
                        equipo.getId(),
                        subtipo
                );
            }
        }
    }

    @CacheEvict(value = {"equipos", "dashboard-stats"}, allEntries = true)
    public void renovarEquipoConCreditos(Integer id, Integer creditosUsados) {
        Equipo equipo = obtenerEquipo(id);
        if (needsRenewal(equipo)) {
            equipo.setEstatus(EstatusEquipoEnum.ACTIVO);
            equipo.setFechaActivacion(Date.valueOf(LocalDate.now()));

            Date fechaBase;
            if (equipo.getFechaExpiracion() != null && !isExpired(equipo)) {
                fechaBase = equipo.getFechaExpiracion();
            } else {
                fechaBase = null;
            }

            equipo.setFechaExpiracion(calculateExpirationDate(equipo.getTipoActivacion(), fechaBase));
            equipo.setCreditosUsados(creditosUsados != null ? creditosUsados : 0);
            equipo.setUsuarioRenovacion(obtenerUsuarioActual());
            repository.save(equipo);

            if (equipo.getSimReferenciada() != null &&
                    equipo.getSimReferenciada().getTarifa() == TarifaSimEnum.POR_SEGUNDO) {
                simService.actualizarRecargaPorCambioEstatusEquipo(
                        equipo.getSimReferenciada().getId(),
                        new BigDecimal("50.00")
                );
            }

            if (creditosUsados != null && creditosUsados > 0 &&
                    (equipo.getPlataforma() != null &&
                            (equipo.getPlataforma().getId().equals(1) || equipo.getPlataforma().getId().equals(2)))) {

                ConceptoCreditoEnum concepto;
                if (equipo.getPlataforma() != null && equipo.getPlataforma().getId().equals(2)) {
                    concepto = equipo.getTipoActivacion() == TipoActivacionEquipoEnum.ANUAL
                            ? ConceptoCreditoEnum.RENOVACION_ANUAL
                            : ConceptoCreditoEnum.RENOVACION_VITALICIA;
                } else {
                    concepto = equipo.getTipoActivacion() == TipoActivacionEquipoEnum.ANUAL
                            ? ConceptoCreditoEnum.RENOVACION_ANUAL
                            : ConceptoCreditoEnum.RENOVACION_VITALICIA;
                }

                String subtipo = null;
                if (equipo.getPlataforma() != null && equipo.getPlataforma().getId().equals(2)) {
                    subtipo = equipo.getTipoActivacion() == TipoActivacionEquipoEnum.ANUAL ? "ANUAL" : "VITALICIA";
                }

                creditoPlataformaService.registrarCargoConSubtipo(
                        equipo.getPlataforma(),
                        concepto,
                        new BigDecimal(creditosUsados),
                        "Renovación " + concepto.name().toLowerCase().replace("_", " ") + " - Equipo: " + equipo.getNombre(),
                        equipo.getId(),
                        subtipo
                );
            }
        }
    }

    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    @CacheEvict(value = {"equipos", "dashboard-stats"}, allEntries = true)
    public void actualizarEquiposExpirados() {
        List<Equipo> equipos = repository.findAllWithSims();
        LocalDate today = LocalDate.now();

        for (Equipo equipo : equipos) {
            if (equipo.getFechaExpiracion() != null &&
                    equipo.getFechaExpiracion().toLocalDate().isBefore(today) &&
                    equipo.getEstatus() == EstatusEquipoEnum.ACTIVO) {
                equipo.setEstatus(EstatusEquipoEnum.EXPIRADO);
                repository.save(equipo);
            }
        }

        for (Equipo equipo : equipos) {
            if (equipo.getEstatus() == EstatusEquipoEnum.EXPIRADO &&
                    equipo.getSimReferenciada() != null &&
                    equipo.getSimReferenciada().getTarifa() == TarifaSimEnum.POR_SEGUNDO) {

                BigDecimal precioActual = equipo.getSimReferenciada().getRecarga();

                if (precioActual == null || precioActual.compareTo(new BigDecimal("10.00")) != 0) {
                    try {
                        simService.actualizarRecargaPorCambioEstatusEquipo(
                                equipo.getSimReferenciada().getId(),
                                new BigDecimal("10.00")
                        );
                    } catch (Exception e) {
                        System.err.println("Error al actualizar recarga de SIM: " + e.getMessage());
                    }
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Equipo> obtenerEquiposProximosAExpirar() {
        logger.info("Obteniendo equipos próximos a expirar dentro de 30 días");
        return repository.findEquiposProximosAExpirar();
    }

    // Método auxiliar para obtener el nombre del usuario logueado
    private String obtenerUsuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "SISTEMA";
    }
}