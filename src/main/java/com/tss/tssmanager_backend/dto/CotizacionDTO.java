package com.tss.tssmanager_backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class CotizacionDTO {
    private Integer id;
    private String clienteNombre;
    private BigDecimal subtotal;
    private BigDecimal iva;
    private BigDecimal isrEstatal;
    private BigDecimal isrFederal;
    private BigDecimal total;
    private String importeLetra;
    private Instant fechaCreacion;
    private String fecha;
    private List<UnidadCotizacionDTO> unidades;
    private Integer cantidadTotal;
    private Integer conceptosCount;
    private EmpresaDTO empresaData;
}