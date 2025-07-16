package com.tss.tssmanager_backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TratoDTO {
    private Integer id;
    private String nombre;
    private Integer empresaId;
    private Integer contactoId;
    private Integer numeroUnidades;
    private BigDecimal ingresosEsperados;
    private String descripcion;
    private Integer propietarioId;
    private LocalDateTime fechaCierre;
    private String noTrato;
    private Integer probabilidad;
    private String fase;
    private Boolean correosAutomaticosActivos;
    private Instant fechaCreacion;
    private Instant fechaModificacion;
    private Instant fechaUltimaActividad;
    private String propietarioNombre;
    private String empresaNombre;
    private List<ActividadDTO> actividades;
    private List<FaseDTO> fases;
    private List<ActividadDTO> historialInteracciones;
    private List<NotaTratoDTO> notas;
    private ActividadesAbiertasDTO actividadesAbiertas;
    private ContactoDTO contacto;
    private String domicilio;
    private String sitioWeb;
    private String sector;

    private boolean escalado;
    private String nuevoAdministradorNombre;
}

