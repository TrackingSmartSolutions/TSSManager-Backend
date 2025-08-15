package com.tss.tssmanager_backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CuentaPorPagarDTO {
    private Integer id;
    private LocalDate fechaPago;
    private BigDecimal monto;
    private String formaPago;
    private Integer usuarioId;
    private String nota;
    private BigDecimal montoPagado;
    private BigDecimal saldoPendiente;
    private BigDecimal montoPago;

}
