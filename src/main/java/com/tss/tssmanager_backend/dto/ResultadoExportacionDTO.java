package com.tss.tssmanager_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoExportacionDTO {
    private boolean exito;
    private String mensaje;
    private String nombreArchivo;
    private String rutaArchivo;
    private String tamañoArchivo;
    private int totalRegistros;
    private Long historialId;
}