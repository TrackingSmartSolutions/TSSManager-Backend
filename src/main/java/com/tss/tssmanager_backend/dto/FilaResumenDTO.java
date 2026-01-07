package com.tss.tssmanager_backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FilaResumenDTO {
    private LocalDate fecha;
    private String categoria;
    private String cliente;
    private Integer totalCuentas;
    private BigDecimal monto;

    public FilaResumenDTO(LocalDate fecha, String categoria, String cliente,
                          Integer totalCuentas, BigDecimal monto) {
        this.fecha = fecha;
        this.categoria = categoria;
        this.cliente = cliente;
        this.totalCuentas = totalCuentas;
        this.monto = monto;
    }
}
