package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.PrincipalSimEnum;
import com.tss.tssmanager_backend.enums.ResponsableSimEnum;
import com.tss.tssmanager_backend.enums.TarifaSimEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.sql.Date;

@Data
public class SimDTO {
    private Integer id;
    private String numero;
    private TarifaSimEnum tarifa;
    private Date vigencia;
    private BigDecimal recarga;
    private ResponsableSimEnum responsable;
    private PrincipalSimEnum principal;
    private Integer grupo;
    private String contrasena;
    private String equipoImei;

    @Deprecated
    private Integer equipoId;

    private String equipoNombre;
}