package com.tss.tssmanager_backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ConceptoCuentaDTO {
    private Integer id;
    private Integer cantidad;
    private String unidad;
    private String concepto;
    private BigDecimal precioUnitario;
    private BigDecimal importeTotal;
    private BigDecimal descuento;
}