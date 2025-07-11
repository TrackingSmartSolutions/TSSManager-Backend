package com.tss.tssmanager_backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class UnidadCotizacionDTO {
    private Integer id;
    private Integer cantidad;
    private String unidad;
    private String concepto;
    private BigDecimal precioUnitario;
    private BigDecimal descuento;
    private BigDecimal importeTotal;

}