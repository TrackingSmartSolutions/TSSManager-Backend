package com.tss.tssmanager_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudExportacionDTO {
    private String tipoDatos;
    private String formatoExportacion;
    private String fechaInicio;
    private String fechaFin;
    private Integer usuarioId;
}

