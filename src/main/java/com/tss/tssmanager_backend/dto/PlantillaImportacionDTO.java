package com.tss.tssmanager_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlantillaImportacionDTO {
    private Integer id;
    private String tipoDatos;
    private String nombreArchivo;
    private String rutaArchivo;
    private String camposCsv;
    private String descripcion;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private Boolean activo;
}
