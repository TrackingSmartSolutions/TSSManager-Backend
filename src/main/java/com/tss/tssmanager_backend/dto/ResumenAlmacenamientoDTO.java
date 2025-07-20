package com.tss.tssmanager_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumenAlmacenamientoDTO {
    private BigDecimal espacioTotalMb;
    private BigDecimal espacioRecuperableMb;
    private Long totalRegistros;
    private Long registrosAntiguos;
    private Integer totalTablas;
}
