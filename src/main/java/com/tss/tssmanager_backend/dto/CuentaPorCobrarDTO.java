package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.EsquemaCobroEnum;
import com.tss.tssmanager_backend.enums.EstatusPagoEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CuentaPorCobrarDTO {
    private Integer id;
    private String folio;
    private LocalDate fechaPago;
    private String clienteNombre;
    private Integer clienteId;
    private EstatusPagoEnum estatus;
    private BigDecimal cantidadCobrar;
    private EsquemaCobroEnum esquema;
    private Integer noEquipos;
    private List<String> conceptos;
    private String comprobantePagoUrl;
    private LocalDate fechaRealPago;
    private Integer cotizacionId;
    private BigDecimal montoPagado;
    private BigDecimal saldoPendiente;
    private BigDecimal montoPago;
}