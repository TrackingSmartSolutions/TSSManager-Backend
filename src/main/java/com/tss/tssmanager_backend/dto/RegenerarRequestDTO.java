package com.tss.tssmanager_backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RegenerarRequestDTO {
    private Integer transaccionId;
    private LocalDate fechaUltimoPago;
    private BigDecimal nuevoMonto;
    private String formaPago;

    public RegenerarRequestDTO() {}

    public RegenerarRequestDTO(Integer transaccionId, LocalDate fechaUltimoPago) {
        this.transaccionId = transaccionId;
        this.fechaUltimoPago = fechaUltimoPago;
    }

    public RegenerarRequestDTO(Integer transaccionId, LocalDate fechaUltimoPago, BigDecimal nuevoMonto) {
        this.transaccionId = transaccionId;
        this.fechaUltimoPago = fechaUltimoPago;
        this.nuevoMonto = nuevoMonto;
    }

    public RegenerarRequestDTO(Integer transaccionId, String formaPago, BigDecimal nuevoMonto, LocalDate fechaUltimoPago) {
        this.transaccionId = transaccionId;
        this.formaPago = formaPago;
        this.nuevoMonto = nuevoMonto;
        this.fechaUltimoPago = fechaUltimoPago;
    }
}