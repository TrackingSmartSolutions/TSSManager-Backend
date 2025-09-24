package com.tss.tssmanager_backend.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class SectorDTO {
    private Integer id;
    private String nombreSector;
    private String creadoPor;
    private String modificadoPor;
    private Instant fechaCreacion;
    private Instant fechaModificacion;
}