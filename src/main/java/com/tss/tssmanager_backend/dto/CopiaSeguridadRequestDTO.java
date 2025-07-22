package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.TipoCopiaSeguridadEnum;
import lombok.Data;

import java.util.List;

@Data
public class CopiaSeguridadRequestDTO {
    private List<TipoCopiaSeguridadEnum> tiposDatos;
    private String frecuencia;
    private Boolean subirADrive;
}
