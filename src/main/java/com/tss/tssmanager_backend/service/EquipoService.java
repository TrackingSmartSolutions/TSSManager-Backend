package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.EquiposEstatusDTO;
import com.tss.tssmanager_backend.entity.Equipo;
import com.tss.tssmanager_backend.entity.EquiposEstatus;
import com.tss.tssmanager_backend.enums.EstatusEquipoEnum;
import com.tss.tssmanager_backend.enums.EstatusReporteEquipoEnum;
import com.tss.tssmanager_backend.enums.TipoActivacionEquipoEnum;
import com.tss.tssmanager_backend.repository.EquipoRepository;
import com.tss.tssmanager_backend.repository.EquiposEstatusRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EquipoService {

    @Autowired
    private EquipoRepository repository;

    @Autowired
    private EquiposEstatusRepository equiposEstatusRepository;

    public Iterable<Equipo> obtenerTodosLosEquipos() {
        return repository.findAllWithSims();
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

    @CacheEvict(value = {"equipos", "dashboard-stats"}, allEntries = true)
    public Equipo guardarEquipo(Equipo equipo) {
        if (equipo.getEstatus() == EstatusEquipoEnum.ACTIVO) {
            equipo.setFechaActivacion(Date.valueOf(LocalDate.now()));
            equipo.setFechaExpiracion(calculateExpirationDate(equipo.getTipoActivacion()));
        }
        if (equipo.getClienteDefault() != null) {
            System.out.println("Guardando equipo con cliente default: " + equipo.getClienteDefault());
        }
        return repository.save(equipo);
    }

    @CacheEvict(value = {"equipos", "dashboard-stats"}, allEntries = true)
    public Equipo actualizarEquipo(Integer id, Equipo equipoDetails) {
        Equipo equipo = obtenerEquipo(id);
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

        if (equipo.getEstatus() == EstatusEquipoEnum.ACTIVO) {
            equipo.setFechaActivacion(Date.valueOf(LocalDate.now()));
            equipo.setFechaExpiracion(calculateExpirationDate(equipo.getTipoActivacion()));
        } else if (equipo.getEstatus() == EstatusEquipoEnum.INACTIVO) {
            equipo.setFechaActivacion(null);
            equipo.setFechaExpiracion(null);
        } else if (isExpired(equipo)) {
            equipo.setEstatus(EstatusEquipoEnum.EXPIRADO);
        }

        if (equipo.getClienteDefault() != null) {
            System.out.println("Actualizando equipo con cliente default: " + equipo.getClienteDefault());
        }

        return repository.save(equipo);
    }

    @CacheEvict(value = {"equipos", "dashboard-stats"}, allEntries = true)
    public void eliminarEquipo(Integer id) {
        Equipo equipo = obtenerEquipo(id);
        if (equipo.getSimReferenciada() != null) {
            throw new IllegalStateException("No se puede eliminar el equipo porque tiene una SIM vinculada. Desvincule la SIM primero.");
        }
        repository.delete(equipo);
    }

    private Date calculateExpirationDate(TipoActivacionEquipoEnum tipoActivacion) {
        LocalDate today = LocalDate.now();
        return tipoActivacion == TipoActivacionEquipoEnum.ANUAL
                ? Date.valueOf(today.plusYears(1))
                : Date.valueOf(today.plusYears(10));
    }

    @CacheEvict(value = {"equipos", "dashboard-stats"}, allEntries = true)
    public void activarEquipo(Integer id) {
        Equipo equipo = obtenerEquipo(id);
        if (equipo.getEstatus() == EstatusEquipoEnum.INACTIVO) {
            equipo.setEstatus(EstatusEquipoEnum.ACTIVO);
            equipo.setFechaActivacion(Date.valueOf(LocalDate.now()));
            equipo.setFechaExpiracion(calculateExpirationDate(equipo.getTipoActivacion()));
            repository.save(equipo);
        }
    }

    @CacheEvict(value = {"equipos", "dashboard-stats"}, allEntries = true)
    public void renovarEquipo(Integer id) {
        Equipo equipo = obtenerEquipo(id);
        if (needsRenewal(equipo)) {
            equipo.setEstatus(EstatusEquipoEnum.ACTIVO);
            equipo.setFechaActivacion(Date.valueOf(LocalDate.now()));
            equipo.setFechaExpiracion(calculateExpirationDate(equipo.getTipoActivacion()));
            repository.save(equipo);
        }
    }

    private boolean needsRenewal(Equipo equipo) {
        if (equipo.getFechaExpiracion() == null || equipo.getEstatus() != EstatusEquipoEnum.ACTIVO) return false;
        LocalDate today = LocalDate.now();
        LocalDate expirationDate = equipo.getFechaExpiracion().toLocalDate();
        long daysUntilExpiration = expirationDate.toEpochDay() - today.toEpochDay();
        return daysUntilExpiration <= 30 && daysUntilExpiration >= 0;
    }

    private boolean isExpired(Equipo equipo) {
        if (equipo.getFechaExpiracion() == null) return false;
        LocalDate today = LocalDate.now();
        return equipo.getFechaExpiracion().toLocalDate().isBefore(today) && equipo.getEstatus() == EstatusEquipoEnum.ACTIVO;
    }

    public void checkExpiredEquipos() {
        List<Equipo> equipos = repository.findAll();
        LocalDate today = LocalDate.now();
        for (Equipo equipo : equipos) {
            if (isExpired(equipo)) {
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
    public void guardarEstatus(List<Map<String, Object>> estatusList) {
        // Crear fecha solo con año, mes y día (sin hora)
        LocalDate today = LocalDate.now();
        Date fechaActual = Date.valueOf(today);

        // Eliminar registros anteriores del mismo día
        equiposEstatusRepository.deleteByFechaCheck(fechaActual);

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
        LocalDate fechaDesde = LocalDate.now().minusDays(30);
        Date fechaDesdeSql = Date.valueOf(fechaDesde);
        return equiposEstatusRepository.findRecentEstatusOptimized(fechaDesdeSql);
    }

    public Page<EquiposEstatusDTO> obtenerEstatusPaginado(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return equiposEstatusRepository.findAllEstatusOptimizedPaged(pageable);
    }

    @Cacheable(value = "dashboard-stats", key = "'estatus-plataforma'")
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerDashboardEstatusOptimizado() {
        Map<String, Object> result = new HashMap<>();

        Date fechaUltimoCheck = equiposEstatusRepository.findMaxFechaCheck();
        result.put("fechaUltimoCheck", fechaUltimoCheck);

        List<Object[]> dashboardData = repository.findDashboardEstatusData();

        List<Map<String, Object>> equiposOffline = procesarEquiposOfflineOptimizado(dashboardData, fechaUltimoCheck);

        result.put("estatusPorCliente", procesarEstatusPorClienteOptimizado(dashboardData));
        result.put("equiposPorPlataforma", procesarEquiposPorPlataformaOptimizado(dashboardData));
        result.put("equiposOffline", equiposOffline);
        result.put("equiposPorMotivo", procesarEquiposPorMotivo(equiposOffline));
        result.put("equiposParaCheck", repository.findEquiposParaCheck());

        return result;
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

    private List<Map<String, Object>> procesarEquiposOfflineOptimizado(List<Object[]> data, Date fechaUltimoCheck) {
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

}