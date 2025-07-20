package com.tss.tssmanager_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudLimpiezaDTO {
    private String tablaNombre;
    private Integer diasAntiguedad;
    private Boolean confirmarEliminacion;
    private String criterioEliminacion;
}
