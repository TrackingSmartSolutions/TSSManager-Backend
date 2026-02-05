package com.tss.tssmanager_backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CrearComisionDTO {
    private Integer cuentaPorCobrarId;
    private Integer empresaId;
    private Integer tratoId;
    private Integer vendedorCuentaId;
    private String vendedorNuevoNombre;
    private BigDecimal porcentajeVenta;
    private Integer proyectoCuentaId;
    private String proyectoNuevoNombre;

    private BigDecimal porcentajeProyecto;

    private String notas;
}