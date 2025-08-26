package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.EstatusReporteEquipoEnum;
import lombok.Data;

import java.sql.Date;
import java.sql.Timestamp;

@Data
public class EquiposEstatusDTO {
    private Integer id;
    private Integer equipoId;
    private EstatusReporteEquipoEnum estatus;
    private String motivo;
    private Timestamp fechaCheck;

    public EquiposEstatusDTO(Integer id, Integer equipoId, EstatusReporteEquipoEnum estatus,
                             String motivo, Timestamp fechaCheck) {
        this.id = id;
        this.equipoId = equipoId;
        this.estatus = estatus;
        this.motivo = motivo;
        this.fechaCheck = fechaCheck;
    }

    public EquiposEstatusDTO() {}

}