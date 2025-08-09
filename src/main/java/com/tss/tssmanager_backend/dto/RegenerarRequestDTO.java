package com.tss.tssmanager_backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RegenerarRequestDTO {
    private Integer transaccionId;
    private LocalDate fechaUltimoPago;
    private BigDecimal nuevoMonto;

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
}