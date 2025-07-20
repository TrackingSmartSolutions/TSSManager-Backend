package com.tss.tssmanager_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionAlmacenamientoDTO {
    private Integer id;
    private String tablaNombre;
    private Boolean habilitadoLimpieza;
    private Integer diasRetencion;
}
