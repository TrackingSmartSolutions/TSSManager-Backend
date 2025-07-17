package com.tss.tssmanager_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistorialExportacionDTO {
    private Integer id;
    private Integer usuarioId;
    private String tipoDatos;
    private String formatoExportacion;
    private String nombreArchivo;
    private String rutaArchivo;
    private String tamañoArchivo;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Integer totalRegistros;
    private LocalDateTime fechaCreacion;
}

