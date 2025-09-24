package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.entity.Plataforma;
import com.tss.tssmanager_backend.enums.ConceptoCreditoEnum;
import com.tss.tssmanager_backend.enums.PlataformaEquipoEnum;
import com.tss.tssmanager_backend.enums.TipoCreditoEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditoPlataformaDTO {
    private Integer id;
    private LocalDateTime fecha;
    private String plataforma;
    private ConceptoCreditoEnum concepto;
    private TipoCreditoEnum tipo;
    private BigDecimal monto;
    private String nota;
    private Integer equipoId;
    private String equipoNombre;
}