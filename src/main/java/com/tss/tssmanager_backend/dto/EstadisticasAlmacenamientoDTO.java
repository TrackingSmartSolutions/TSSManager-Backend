package com.tss.tssmanager_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstadisticasAlmacenamientoDTO {
    private String tablaNombre;
    private Long totalRegistros;
    private BigDecimal tamanoMb;
    private Long registrosAntiguos;
    private BigDecimal espacioRecuperableMb;
}
