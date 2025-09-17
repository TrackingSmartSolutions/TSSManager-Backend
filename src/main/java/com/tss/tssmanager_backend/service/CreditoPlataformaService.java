package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.CreditoPlataformaDTO;
import com.tss.tssmanager_backend.dto.DashboardCreditosDTO;
import com.tss.tssmanager_backend.entity.CreditoPlataforma;
import com.tss.tssmanager_backend.entity.Equipo;
import com.tss.tssmanager_backend.enums.ConceptoCreditoEnum;
import com.tss.tssmanager_backend.enums.PlataformaEquipoEnum;
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

    public DashboardCreditosDTO getDashboardData(LocalDate fechaInicio, LocalDate fechaFin, String filtroPlataforma) {
        LocalDateTime fechaInicioDateTime = fechaInicio.atStartOfDay();
        LocalDateTime fechaFinDateTime = fechaFin.atTime(LocalTime.MAX);

        List<CreditoPlataforma> creditos;

        if ("Todos".equals(filtroPlataforma)) {
            creditos = repository.findByFechaBetween(fechaInicioDateTime, fechaFinDateTime);
        } else {
            PlataformaEquipoEnum plataforma = PlataformaEquipoEnum.valueOf(filtroPlataforma.replace(" ", "_").toUpperCase());
            creditos = repository.findByPlataformaAndFechaBetween(plataforma, fechaInicioDateTime, fechaFinDateTime);
        }

        // Convertir a DTOs
        List<CreditoPlataformaDTO> estadoCuenta = convertToDTO(creditos);

        // Calcular saldos
        Map<String, BigDecimal> saldos = calcularSaldos();

        // Calcular historial
        List<Map<String, Object>> historial = calcularHistorialSaldos(fechaInicioDateTime, fechaFinDateTime);

        return new DashboardCreditosDTO(saldos, estadoCuenta, historial);
    }

    private List<CreditoPlataformaDTO> convertToDTO(List<CreditoPlataforma> creditos) {
        return creditos.stream().map(credito -> {
            CreditoPlataformaDTO dto = new CreditoPlataformaDTO();
            dto.setId(credito.getId());
            dto.setFecha(credito.getFecha());
            dto.setPlataforma(credito.getPlataforma());
            dto.setConcepto(credito.getConcepto());
            dto.setTipo(credito.getTipo());
            dto.setMonto(credito.getMonto());
            dto.setNota(credito.getNota());
            dto.setEquipoId(credito.getEquipoId());

            // Buscar nombre del equipo si existe
            if (credito.getEquipoId() != null) {
                equipoRepository.findById(credito.getEquipoId())
                        .ifPresent(equipo -> dto.setEquipoNombre(equipo.getNombre()));
            }

            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void registrarAbonoConSubtipo(PlataformaEquipoEnum plataforma, ConceptoCreditoEnum concepto,
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
    public void registrarAbonoConSubtipo(PlataformaEquipoEnum plataforma, ConceptoCreditoEnum concepto,
                                         BigDecimal monto, String nota, Integer transaccionId,
                                         Integer cuentaPorPagarId, String subtipo) {
        registrarAbonoConSubtipo(plataforma, concepto, monto, nota, transaccionId,
                cuentaPorPagarId, subtipo, null);
    }

    @Transactional
    public void registrarCargoConSubtipo(PlataformaEquipoEnum plataforma, ConceptoCreditoEnum concepto,
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

        Map<String, BigDecimal> saldos = new HashMap<>();

        // Inicializar con 0
        saldos.put("TRACK_SOLID", BigDecimal.ZERO);
        saldos.put("WHATSGPS", BigDecimal.ZERO);
        saldos.put("WHATSGPS_ANUAL", BigDecimal.ZERO);
        saldos.put("WHATSGPS_VITALICIA", BigDecimal.ZERO);

        for (Object[] row : saldosData) {
            PlataformaEquipoEnum plataforma = (PlataformaEquipoEnum) row[0];
            BigDecimal saldo = (BigDecimal) row[1];
            saldos.put(plataforma.name(), saldo);
        }

        // Calcular saldos por subtipo de WhatsGPS
        for (Object[] row : saldosSubtipos) {
            PlataformaEquipoEnum plataforma = (PlataformaEquipoEnum) row[0];
            String subtipo = (String) row[1];
            BigDecimal saldo = (BigDecimal) row[2];

            if (plataforma == PlataformaEquipoEnum.WHATSGPS && subtipo != null) {
                saldos.put("WHATSGPS_" + subtipo, saldo);
            }
        }

        return saldos;
    }

    private List<Map<String, Object>> calcularHistorialSaldos(LocalDateTime fechaInicio, LocalDateTime fechaFin) {
        // Obtener todos los movimientos hasta la fecha fin para calcular saldos acumulados
        List<CreditoPlataforma> todosLosCreditos = repository.findByFechaLessThanEqualOrderByFecha(fechaFin);

        // Agrupar por fecha y plataforma/subtipo
        Map<LocalDate, Map<String, BigDecimal>> saldosPorFechaYPlataforma = new TreeMap<>();

        for (CreditoPlataforma credito : todosLosCreditos) {
            LocalDate fecha = credito.getFecha().toLocalDate();

            String plataformaKey;
            if (credito.getPlataforma() == PlataformaEquipoEnum.WHATSGPS && credito.getSubtipo() != null) {
                plataformaKey = "WHATSGPS_" + credito.getSubtipo();
            } else {
                plataformaKey = credito.getPlataforma().name();
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
            for (String plataforma : Arrays.asList("TRACK_SOLID", "WHATSGPS_ANUAL", "WHATSGPS_VITALICIA")) {
                Map<String, Object> item = new HashMap<>();
                item.put("fecha", fecha.toString());
                item.put("plataforma", plataforma);
                item.put("saldo", saldosAcumulados.get(plataforma));
                resultado.add(item);
            }
        }

        return resultado;
    }

    @Transactional
    public void registrarCargo(PlataformaEquipoEnum plataforma, ConceptoCreditoEnum concepto,
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
    public void registrarAbono(PlataformaEquipoEnum plataforma, ConceptoCreditoEnum concepto,
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
}