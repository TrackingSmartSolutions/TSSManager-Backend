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
    private ResponsableSimEnum responsable;
    private PrincipalSimEnum principal;
    private Integer grupo;
    private BigDecimal recarga;
    private Date vigencia;
    private String contrasena;
    private Integer equipoId;

}