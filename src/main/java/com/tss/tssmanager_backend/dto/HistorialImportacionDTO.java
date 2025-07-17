package com.tss.tssmanager_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistorialImportacionDTO {
    private Integer id;
    private Integer usuarioId;
    private String tipoDatos;
    private String nombreArchivo;
    private Integer registrosProcesados;
    private Integer registrosExitosos;
    private Integer registrosFallidos;
    private String errores;
    private LocalDateTime fechaCreacion;

}