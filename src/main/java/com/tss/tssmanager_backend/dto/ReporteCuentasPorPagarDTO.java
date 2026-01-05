package com.tss.tssmanager_backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class ReporteCuentasPorPagarDTO {
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String filtroEstatus;
    private BigDecimal montoTotal;
    private BigDecimal montoSaldoAcumulado = BigDecimal.ZERO;
    private Map<LocalDate, BigDecimal> montoPorDia;
    private Map<LocalDate, List<CuentaReporteDTO>> cuentasPorDia;

    // Constructores
    public ReporteCuentasPorPagarDTO() {}

    public ReporteCuentasPorPagarDTO(LocalDate fechaInicio, LocalDate fechaFin, String filtroEstatus) {
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.filtroEstatus = filtroEstatus;
    }

    // Clase interna para el detalle de cada cuenta
    @Data
    public static class CuentaReporteDTO {
        private String folio;
        private String cuenta;
        private BigDecimal monto;
        private String formaPago;
        private String estatus;
        private String categoria;
        private String numeroSim;

        // Constructor
        public CuentaReporteDTO(String folio, String cuenta, BigDecimal monto, String formaPago,
                                String estatus, String categoria, String numeroSim) {
            this.folio = folio;
            this.cuenta = cuenta;
            this.monto = monto;
            this.formaPago = formaPago;
            this.estatus = estatus;
            this.categoria = categoria;
            this.numeroSim = numeroSim;
        }
    }
}