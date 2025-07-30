package com.tss.tssmanager_backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TratoBasicoDTO {
    private Integer id;
    private String nombre;
    private String propietarioNombre;
    private LocalDateTime fechaCierre;
    private String empresaNombre;
    private String noTrato;
    private BigDecimal ingresoEsperado;
    private String fase;
    private Integer propietarioId;
    private Instant fechaUltimaActividad;
    private Instant fechaCreacion;
    private Instant fechaModificacion;

    // Datos para la lógica de actividades
    private Boolean isNeglected;
    private Boolean hasActivities;
    private Integer actividadesAbiertasCount;
    private String proximaActividadTipo;
    private LocalDate proximaActividadFecha;
    private Integer contactoId;
}
