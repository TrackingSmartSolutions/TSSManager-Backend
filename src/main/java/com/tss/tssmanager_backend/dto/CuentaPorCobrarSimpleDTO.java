package com.tss.tssmanager_backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CuentaPorCobrarSimpleDTO {
    private Integer id;
    private String folio;
    private BigDecimal montoPagado;
    private String estatus;

    public CuentaPorCobrarSimpleDTO(Integer id, String folio, BigDecimal montoPagado, String estatus) {
        this.id = id;
        this.folio = folio;
        this.montoPagado = montoPagado;
        this.estatus = estatus;
    }
}