package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.EstatusReporteEquipoEnum;
import lombok.Data;

import java.sql.Date;

@Data
public class EquiposEstatusDTO {
    private Integer id;
    private Integer equipoId;
    private EstatusReporteEquipoEnum estatus;
    private String motivo;
    private Date fechaCheck;

}