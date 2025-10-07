package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.CreditoPlataformaDTO;
import com.tss.tssmanager_backend.dto.DashboardCreditosDTO;
import com.tss.tssmanager_backend.entity.CreditoPlataforma;
import com.tss.tssmanager_backend.entity.Equipo;
import com.tss.tssmanager_backend.entity.Plataforma;
import com.tss.tssmanager_backend.enums.ConceptoCreditoEnum;
import com.tss.tssmanager_backend.enums.EstatusEquipoEnum;
import com.tss.tssmanager_backend.enums.TipoCreditoEnum;
import com.tss.tssmanager_backend.repository.CreditoPlataformaRepository;
import com.tss.tssmanager_backend.repository.EquipoRepository;
import com.tss.tssmanager_backend.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CreditoPlataformaService {

    @Autowired
    private CreditoPlataformaRepository repository;

    @Autowired
    private EquipoRepository equipoRepository;

    @Autowired
    private PlataformaService plataformaService;

    @Transactional(readOnly = true)
    public DashboardCreditosDTO getDashboardData(LocalDate fechaInicio, LocalDate fechaFin, String filtroPlataforma) {
        LocalDateTime fechaInicioDateTime = fechaInicio.atStartOfDay();
        LocalDateTime fechaFinDateTime = fechaFin.atTime(LocalTime.MAX);

        List<CreditoPlataforma> creditos;

        if ("Todos".equals(filtroPlataforma)) {
            creditos = repository.findByFechaBetween(fechaInicioDateTime, fechaFinDateTime);
        } else {
            Plataforma plataforma = plataformaService.obtenerTodasLasPlataformas().stream()
                    .filter(p -> p.getNombrePlataforma().equalsIgnoreCase(filtroPlataforma))
                    .findFirst()
                    .orElse(null);

            if (plataforma != null) {
                creditos = repository.findByPlataformaAndFechaBetween(plataforma, fechaInicioDateTime, fechaFinDateTime);
            } else {
                creditos = new ArrayList<>();
            }
        }

        List<CreditoPlataformaDTO> estadoCuenta = convertToDTO(creditos);

        Map<String, BigDecimal> saldos = calcularSaldos();

        List<Map<String, Object>> historial = calcularHistorialSaldos(fechaInicioDateTime, fechaFinDateTime);

        return new DashboardCreditosDTO(saldos, estadoCuenta, historial);
    }

    private List<CreditoPlataformaDTO> convertToDTO(List<CreditoPlataforma> creditos) {
        return creditos.stream().map(credito -> {
            CreditoPlataformaDTO dto = new CreditoPlataformaDTO();
            dto.setId(credito.getId());
            dto.setFecha(credito.getFecha());
            dto.setPlataforma(credito.getPlataforma().getNombrePlataforma());
            dto.setConcepto(credito.getConcepto());
            dto.setTipo(credito.getTipo());
            dto.setMonto(credito.getMonto());
            dto.setNota(credito.getNota());
            dto.setEquipoId(credito.getEquipoId());

            if (credito.getEquipoId() != null) {
                equipoRepository.findById(credito.getEquipoId())
                        .ifPresent(equipo -> dto.setEquipoNombre(equipo.getNombre()));
            }

            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void registrarAbonoConSubtipo(Plataforma plataforma, ConceptoCreditoEnum concepto,
                                         BigDecimal monto, String nota, Integer transaccionId,
                                         Integer cuentaPorPagarId, String subtipo, LocalDateTime fechaCustom) {
        CreditoPlataforma credito = new CreditoPlataforma();

        credito.setFecha(fechaCustom != null ? fechaCustom : DateUtils.nowInMexico());

        credito.setPlataforma(plataforma);
        credito.setConcepto(concepto);
        credito.setTipo(TipoCreditoEnum.ABONO);
        credito.setMonto(monto);
        credito.setNota(nota);
        credito.setTransaccionId(transaccionId);
        credito.setCuentaPorPagarId(cuentaPorPagarId);
        credito.setSubtipo(subtipo);

        repository.save(credito);
    }

    @Transactional
    public void registrarAbonoConSubtipo(Plataforma plataforma, ConceptoCreditoEnum concepto,
                                         BigDecimal monto, String nota, Integer transaccionId,
                                         Integer cuentaPorPagarId, String subtipo) {
        registrarAbonoConSubtipo(plataforma, concepto, monto, nota, transaccionId,
                cuentaPorPagarId, subtipo, null);
    }

    @Transactional
    public void registrarCargoConSubtipo(Plataforma plataforma, ConceptoCreditoEnum concepto,
                                         BigDecimal monto, String nota, Integer equipoId, String subtipo) {
        CreditoPlataforma credito = new CreditoPlataforma();
        credito.setFecha(DateUtils.nowInMexico());
        credito.setPlataforma(plataforma);
        credito.setConcepto(concepto);
        credito.setTipo(TipoCreditoEnum.CARGO);
        credito.setMonto(monto);
        credito.setNota(nota);
        credito.setEquipoId(equipoId);
        credito.setSubtipo(subtipo);

        repository.save(credito);
    }

    private Map<String, BigDecimal> calcularSaldos() {
        List<Object[]> saldosData = repository.getSaldosPorPlataforma();
        List<Object[]> saldosSubtipos = repository.getSaldosPorPlataformaYSubtipo();
        List<Object[]> saldosLicencias = repository.getSaldosPorPlataformaLicencias();

        Map<String, BigDecimal> saldos = new HashMap<>();

        // Inicializar con las claves que espera el frontend
        saldos.put("TRACK_SOLID", BigDecimal.ZERO);
        saldos.put("WHATSGPS", BigDecimal.ZERO);
        saldos.put("WHATSGPS_ANUAL", BigDecimal.ZERO);
        saldos.put("WHATSGPS_VITALICIA", BigDecimal.ZERO);

        for (Object[] row : saldosData) {
            String nombrePlataforma = (String) row[0];
            BigDecimal saldo = (BigDecimal) row[1];

            // Mapear nombre de BD a clave esperada por frontend
            String claveParaFrontend = mapearNombrePlataformaParaFrontend(nombrePlataforma);
            saldos.put(claveParaFrontend, saldo);
        }

        // Calcular saldos por subtipo de WhatsGPS
        for (Object[] row : saldosSubtipos) {
            String nombrePlataforma = (String) row[0];
            String subtipo = (String) row[1];
            BigDecimal saldo = (BigDecimal) row[2];

            if ("WhatsGPS".equals(nombrePlataforma) && subtipo != null) {
                saldos.put("WHATSGPS_" + subtipo, saldo);
            }
        }

        // Calcular licencias para Fulltrack y F/Basic
        for (Object[] row : saldosLicencias) {
            String nombrePlataforma = (String) row[0];
            BigDecimal saldoActual = (BigDecimal) row[1];

            if ("Fulltrack".equals(nombrePlataforma)) {
                long equiposActivos = equipoRepository.countByPlataformaIdAndEstatus(6, EstatusEquipoEnum.ACTIVO);
                BigDecimal ocupadas = new BigDecimal(equiposActivos);
                BigDecimal disponibles = saldoActual;
                BigDecimal total = ocupadas.add(disponibles);

                saldos.put("FULLTRACK_OCUPADAS", ocupadas);
                saldos.put("FULLTRACK_DISPONIBLES", disponibles.max(BigDecimal.ZERO));
                saldos.put("FULLTRACK_TOTAL", total);
            } else if ("F/Basic".equals(nombrePlataforma)) {
                long equiposActivos = equipoRepository.countByPlataformaIdAndEstatus(5, EstatusEquipoEnum.ACTIVO);
                BigDecimal ocupadas = new BigDecimal(equiposActivos);
                BigDecimal disponibles = saldoActual;
                BigDecimal total = ocupadas.add(disponibles);

                saldos.put("F_BASIC_OCUPADAS", ocupadas);
                saldos.put("F_BASIC_DISPONIBLES", disponibles.max(BigDecimal.ZERO));
                saldos.put("F_BASIC_TOTAL", total);
            }
        }

        return saldos;
    }

    private List<Map<String, Object>> calcularHistorialSaldos(LocalDateTime fechaInicio, LocalDateTime fechaFin) {
        List<CreditoPlataforma> todosLosCreditos = repository.findByFechaLessThanEqualOrderByFecha(fechaFin);

        Map<LocalDate, Map<String, BigDecimal>> saldosPorFechaYPlataforma = new TreeMap<>();

        for (CreditoPlataforma credito : todosLosCreditos) {
            LocalDate fecha = credito.getFecha().toLocalDate();

            String plataformaKey;
            if ("WhatsGPS".equals(credito.getPlataforma().getNombrePlataforma()) && credito.getSubtipo() != null) {
                plataformaKey = "WHATSGPS_" + credito.getSubtipo();
            } else {
                // Mapear nombres de BD a formato esperado por frontend para gráfica
                String nombrePlataforma = credito.getPlataforma().getNombrePlataforma();
                plataformaKey = mapearNombrePlataformaParaFrontend(nombrePlataforma);
            }

            saldosPorFechaYPlataforma.computeIfAbsent(fecha, k -> new HashMap<>());

            BigDecimal montoActual = saldosPorFechaYPlataforma.get(fecha).getOrDefault(plataformaKey, BigDecimal.ZERO);

            if (credito.getTipo() == TipoCreditoEnum.ABONO) {
                montoActual = montoActual.add(credito.getMonto());
            } else { // CARGO
                montoActual = montoActual.subtract(credito.getMonto());
            }

            saldosPorFechaYPlataforma.get(fecha).put(plataformaKey, montoActual);
        }

        // Calcular saldos acumulados
        Map<String, BigDecimal> saldosAcumulados = new HashMap<>();
        saldosAcumulados.put("TRACK_SOLID", BigDecimal.ZERO);
        saldosAcumulados.put("WHATSGPS_ANUAL", BigDecimal.ZERO);
        saldosAcumulados.put("WHATSGPS_VITALICIA", BigDecimal.ZERO);
        saldosAcumulados.put("FULLTRACK", BigDecimal.ZERO);
        saldosAcumulados.put("F_BASIC", BigDecimal.ZERO);

        List<Map<String, Object>> resultado = new ArrayList<>();

        for (Map.Entry<LocalDate, Map<String, BigDecimal>> entry : saldosPorFechaYPlataforma.entrySet()) {
            LocalDate fecha = entry.getKey();

            // Solo incluir fechas dentro del rango solicitado
            if (fecha.isBefore(fechaInicio.toLocalDate()) || fecha.isAfter(fechaFin.toLocalDate())) {
                // Actualizar saldos acumulados pero no incluir en resultado si está fuera del rango
                for (Map.Entry<String, BigDecimal> plataformaEntry : entry.getValue().entrySet()) {
                    String plataforma = plataformaEntry.getKey();
                    BigDecimal cambio = plataformaEntry.getValue();
                    saldosAcumulados.put(plataforma, saldosAcumulados.getOrDefault(plataforma, BigDecimal.ZERO).add(cambio));
                }
                continue;
            }

            // Actualizar saldos acumulados con los cambios del día
            for (Map.Entry<String, BigDecimal> plataformaEntry : entry.getValue().entrySet()) {
                String plataforma = plataformaEntry.getKey();
                BigDecimal cambio = plataformaEntry.getValue();
                saldosAcumulados.put(plataforma, saldosAcumulados.getOrDefault(plataforma, BigDecimal.ZERO).add(cambio));
            }

            // Crear entradas para cada plataforma en esta fecha con su saldo acumulado
            // Crear entradas para cada plataforma en esta fecha con su saldo acumulado
            for (String plataforma : Arrays.asList("TRACK_SOLID", "WHATSGPS_ANUAL", "WHATSGPS_VITALICIA", "FULLTRACK", "F_BASIC")) {
                Map<String, Object> item = new HashMap<>();
                item.put("fecha", fecha.toString());
                item.put("plataforma", plataforma);
                item.put("saldo", saldosAcumulados.get(plataforma));
                resultado.add(item);
            }
        }

        return resultado;
    }

    private String mapearNombrePlataforma(String nombreBD) {
        switch (nombreBD) {
            case "Track Solid":
                return "TRACK_SOLID";
            case "WhatsGPS":
                return "WHATSGPS";
            case "TrackerKing":
                return "TRACKERKING";
            case "Joint Cloud":
                return "JOINTCLOUD";
            default:
                return nombreBD;
        }
    }

    private String mapearNombrePlataformaParaFrontend(String nombreBD) {
        switch (nombreBD) {
            case "Track Solid":
                return "TRACK_SOLID";
            case "WhatsGPS":
                return "WHATSGPS";
            case "TrackerKing":
                return "TRACKERKING";
            case "Joint Cloud":
                return "JOINTCLOUD";
            case "Fulltrack":
                return "FULLTRACK";
            case "F/Basic":
                return "F_BASIC";
            default:
                return nombreBD.toUpperCase().replace(" ", "_");
        }
    }

    @Transactional
    public void registrarCargo(Plataforma plataforma, ConceptoCreditoEnum concepto,
                               BigDecimal monto, String nota, Integer equipoId) {
        CreditoPlataforma credito = new CreditoPlataforma();
        credito.setFecha(LocalDateTime.now());
        credito.setPlataforma(plataforma);
        credito.setConcepto(concepto);
        credito.setTipo(TipoCreditoEnum.CARGO);
        credito.setMonto(monto);
        credito.setNota(nota);
        credito.setEquipoId(equipoId);

        repository.save(credito);
    }

    @Transactional
    public void registrarAbono(Plataforma plataforma, ConceptoCreditoEnum concepto,
                               BigDecimal monto, String nota, Integer transaccionId, Integer cuentaPorPagarId) {
        CreditoPlataforma credito = new CreditoPlataforma();
        credito.setFecha(DateUtils.nowInMexico());
        credito.setPlataforma(plataforma);
        credito.setConcepto(concepto);
        credito.setTipo(TipoCreditoEnum.ABONO);
        credito.setMonto(monto);
        credito.setNota(nota);
        credito.setTransaccionId(transaccionId);
        credito.setCuentaPorPagarId(cuentaPorPagarId);

        repository.save(credito);
    }

    @Transactional
    public void registrarCompraLicencias(Plataforma plataforma, Integer cantidadLicencias,
                                         String nota, Integer transaccionId,
                                         Integer cuentaPorPagarId, LocalDateTime fechaCustom) {
        CreditoPlataforma credito = new CreditoPlataforma();
        credito.setFecha(fechaCustom != null ? fechaCustom : DateUtils.nowInMexico());
        credito.setPlataforma(plataforma);
        credito.setConcepto(ConceptoCreditoEnum.COMPRA_LICENCIAS);
        credito.setTipo(TipoCreditoEnum.ABONO);
        credito.setMonto(new BigDecimal(cantidadLicencias));
        credito.setNota(nota);
        credito.setTransaccionId(transaccionId);
        credito.setCuentaPorPagarId(cuentaPorPagarId);
        credito.setEsLicencia(true);
        credito.setSubtipo(null);

        repository.save(credito);
    }

    @Transactional
    public void registrarAsignacionLicencia(Plataforma plataforma, String nota, Integer equipoId) {
        CreditoPlataforma credito = new CreditoPlataforma();
        credito.setFecha(DateUtils.nowInMexico());
        credito.setPlataforma(plataforma);
        credito.setConcepto(ConceptoCreditoEnum.ASIGNACION_LICENCIA);
        credito.setTipo(TipoCreditoEnum.CARGO);
        credito.setMonto(BigDecimal.ONE);
        credito.setNota(nota);
        credito.setEquipoId(equipoId);
        credito.setEsLicencia(true);

        repository.save(credito);
    }

    @Transactional
    public void registrarLiberacionLicencia(Plataforma plataforma, String nota, Integer equipoId) {
        CreditoPlataforma credito = new CreditoPlataforma();
        credito.setFecha(DateUtils.nowInMexico());
        credito.setPlataforma(plataforma);
        credito.setConcepto(ConceptoCreditoEnum.LIBERACION_LICENCIA);
        credito.setTipo(TipoCreditoEnum.ABONO);
        credito.setMonto(BigDecimal.ONE);
        credito.setNota(nota);
        credito.setEquipoId(equipoId);
        credito.setEsLicencia(true);

        repository.save(credito);
    }
}