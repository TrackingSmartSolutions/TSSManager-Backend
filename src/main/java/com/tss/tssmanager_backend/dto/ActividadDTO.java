package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.*;
import lombok.Data;
import java.sql.Date;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalDate;

@Data
public class ActividadDTO {
    private Integer id;
    private Integer tratoId;
    private TipoActividadEnum tipo;
    private SubtipoTareaEnum subtipoTarea;
    private Integer asignadoAId;
    private String asignadoANombre;
    private LocalDate fechaLimite;
    private Time horaInicio;
    private String duracion;
    private ModalidadActividadEnum modalidad;
    private String lugarReunion;
    private MedioReunionEnum medio;
    private String enlaceReunion;
    private FinalidadActividadEnum finalidad;
    private EstatusActividadEnum estatus;
    private Instant fechaCompletado;
    private Integer usuarioCompletadoId;
    private RespuestaEnum respuesta;
    private InteresEnum interes;
    private InformacionEnum informacion;
    private SiguienteAccionEnum siguienteAccion;
    private String notas;
    private Instant fechaCreacion;
    private Instant fechaModificacion;
    private Integer contactoId;

}