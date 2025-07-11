package com.tss.tssmanager_backend.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class NotaTratoDTO {
    private Integer id;
    private Integer tratoId;
    private Integer usuarioId;
    private String nota;
    private Instant fechaCreacion;
    private Integer editadoPor;
    private Instant fechaEdicion;
    private String autorNombre;
    private String editadoPorNombre;

}