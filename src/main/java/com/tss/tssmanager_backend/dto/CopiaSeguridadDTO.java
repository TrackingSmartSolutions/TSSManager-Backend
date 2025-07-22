package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.TipoCopiaSeguridadEnum;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CopiaSeguridadDTO {
    private Integer id;
    private TipoCopiaSeguridadEnum tipoDatos;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaEliminacion;
    private String estado;
    private String tamañoArchivo;
    private String frecuencia;
    private String archivoPdfUrl;
    private String archivoCsvUrl;
}