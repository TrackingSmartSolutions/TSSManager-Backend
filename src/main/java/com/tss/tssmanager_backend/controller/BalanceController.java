package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.BalanceResumenDTO;
import com.tss.tssmanager_backend.repository.CuentaPorCobrarRepository;
import com.tss.tssmanager_backend.repository.TransaccionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/balance")
@CrossOrigin(origins = "*")
public class BalanceController {

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private CuentaPorCobrarRepository cuentaPorCobrarRepository;

    @GetMapping("/resumen")
    public BalanceResumenDTO obtenerBalance(
            @RequestParam(required = false) Integer anio,
            @RequestParam(required = false) Integer mes) {

        BalanceResumenDTO dto = new BalanceResumenDTO();

        dto.setTotalIngresos(transaccionRepository.sumIngresosByFecha(anio, mes));
        dto.setTotalGastos(transaccionRepository.sumGastosByFecha(anio, mes));

        // Manejo de nulos por si no hay transacciones
        if (dto.getTotalIngresos() == null) dto.setTotalIngresos(BigDecimal.ZERO);
        if (dto.getTotalGastos() == null) dto.setTotalGastos(BigDecimal.ZERO);

        dto.setUtilidadPerdida(dto.getTotalIngresos().subtract(dto.getTotalGastos()));

        dto.setAniosDisponibles(transaccionRepository.findDistinctYears());

        dto.setAcumuladoCuentas(transaccionRepository.findAcumuladoPorCuenta(anio, mes));

        List<BalanceResumenDTO.EquipoVendidoDTO> equiposVendidos =
                cuentaPorCobrarRepository.findEquiposVendidosReporte(anio, mes);

        equiposVendidos = equiposVendidos.stream()
                .filter(e -> e.getNumeroEquipos() != null && e.getNumeroEquipos() > 0)
                .collect(Collectors.toList());

        dto.setEquiposVendidos(equiposVendidos);
        List<Object[]> rawGrafico = transaccionRepository.findDatosGrafico(anio, mes);
        Map<String, BigDecimal[]> mapaGrafico = new LinkedHashMap<>();

        if (anio == null) {
            // Vista por años
            for (Object[] row : rawGrafico) {
                LocalDate fecha = (LocalDate) row[0];
                if(fecha == null) continue;
                String key = String.valueOf(fecha.getYear());
                procesarFilaGrafico(mapaGrafico, key, row);
            }
        } else if (mes != null) {
            // Vista por días del mes seleccionado
            LocalDate primerDia = LocalDate.of(anio, mes, 1);
            int diasEnMes = primerDia.lengthOfMonth();

            for(int d = 1; d <= diasEnMes; d++) {
                mapaGrafico.put(String.valueOf(d), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            }

            for (Object[] row : rawGrafico) {
                LocalDate fecha = (LocalDate) row[0];
                if(fecha == null) continue;
                String key = String.valueOf(fecha.getDayOfMonth());
                procesarFilaGrafico(mapaGrafico, key, row);
            }
        } else {
            // Vista por meses del año seleccionado
            String[] meses = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};
            for(String m : meses) mapaGrafico.put(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

            for (Object[] row : rawGrafico) {
                LocalDate fecha = (LocalDate) row[0];
                if(fecha == null) continue;
                String key = meses[fecha.getMonthValue() - 1];
                procesarFilaGrafico(mapaGrafico, key, row);
            }
        }

        List<BalanceResumenDTO.GraficoDataDTO> listaGrafico = new ArrayList<>();
        mapaGrafico.forEach((k, v) -> listaGrafico.add(new BalanceResumenDTO.GraficoDataDTO(k, v[0], v[1])));
        dto.setGraficoMensual(listaGrafico);

        return dto;
    }

    private void procesarFilaGrafico(Map<String, BigDecimal[]> mapa, String key, Object[] row) {
        BigDecimal monto = (BigDecimal) row[1];
        String tipo = row[2].toString();
        String notas = (String) row[3];

        mapa.putIfAbsent(key, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

        if ("INGRESO".equals(tipo)) {
            mapa.get(key)[0] = mapa.get(key)[0].add(monto);
        } else if ("GASTO".equals(tipo) && notas != null && notas.contains("Cuentas por Pagar")) {
            mapa.get(key)[1] = mapa.get(key)[1].add(monto);
        }
    }
}