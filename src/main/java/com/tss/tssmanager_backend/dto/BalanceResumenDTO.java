package com.tss.tssmanager_backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class BalanceResumenDTO {
    private BigDecimal totalIngresos;
    private BigDecimal totalGastos;
    private BigDecimal utilidadPerdida;
    private List<GraficoDataDTO> graficoMensual;
    private List<AcumuladoCuentaDTO> acumuladoCuentas;
    private List<EquipoVendidoDTO> equiposVendidos;
    private List<Integer> aniosDisponibles;

    @Data
    public static class GraficoDataDTO {
        public String mes;
        public BigDecimal ingresos;
        public BigDecimal gastos;
        public GraficoDataDTO(String mes, BigDecimal ingresos, BigDecimal gastos) {
            this.mes = mes;
            this.ingresos = ingresos;
            this.gastos = gastos;
        }
    }

    @Data
    public static class AcumuladoCuentaDTO {
        public String categoria;
        public String cuenta;
        public BigDecimal monto;
        public AcumuladoCuentaDTO(String categoria, String cuenta, BigDecimal monto) {
            this.categoria = categoria;
            this.cuenta = cuenta;
            this.monto = monto;
        }
    }

    @Data
    public static class EquipoVendidoDTO {
        public String cliente;
        public java.time.LocalDate fechaPago;
        public Integer numeroEquipos;
        public EquipoVendidoDTO(String cliente, java.time.LocalDate fechaPago, Long numeroEquipos) {
            this.cliente = cliente;
            this.fechaPago = fechaPago;
            this.numeroEquipos = numeroEquipos != null ? numeroEquipos.intValue() : 0;
        }
    }
}