package com.tss.tssmanager_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoLimpiezaDTO {
    private String tablaNombre;
    private Integer registrosEliminados;
    private BigDecimal espacioLiberadoMb;
    private String mensaje;
    private Boolean exito;
}

