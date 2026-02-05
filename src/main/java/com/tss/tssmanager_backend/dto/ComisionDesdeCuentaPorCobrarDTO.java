package com.tss.tssmanager_backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ComisionDesdeCuentaPorCobrarDTO {
    private Integer vendedorCuentaId;
    private String vendedorNuevoNombre;
    private BigDecimal porcentajeVenta;
    private BigDecimal porcentajeProyecto;
    private String notas;
}