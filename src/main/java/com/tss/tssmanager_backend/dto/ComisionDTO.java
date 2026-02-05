package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.EstatusComisionEnum;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ComisionDTO {
    private Integer id;
    private Integer cuentaPorCobrarId;
    private String folioCuentaPorCobrar;
    private Integer empresaId;
    private String empresaNombre;
    private Integer tratoId;
    private String tratoNombre;
    private LocalDate fechaPago;
    private BigDecimal montoBase;
    private Integer vendedorCuentaId;
    private String vendedorNombre;
    private BigDecimal porcentajeVenta;
    private BigDecimal montoComisionVenta;
    private BigDecimal saldoPendienteVenta;
    private EstatusComisionEnum estatusVenta;
    private Integer proyectoCuentaId;
    private String proyectoNombre;
    private BigDecimal porcentajeProyecto;
    private BigDecimal montoComisionProyecto;
    private BigDecimal saldoPendienteProyecto;
    private EstatusComisionEnum estatusProyecto;
    private String notas;
}