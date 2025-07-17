package com.tss.tssmanager_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoImportacionDTO {
    private boolean exito;
    private String mensaje;
    private int registrosProcesados;
    private int registrosExitosos;
    private int registrosFallidos;
    private String errores;
}