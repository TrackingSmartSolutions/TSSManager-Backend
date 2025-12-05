package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.*;
import lombok.Data;
import java.sql.Time;
import java.time.LocalDate;

@Data
public class ActividadConEmpresaDTO {
    private Integer id;
    private Integer tratoId;
    private TipoActividadEnum tipo;
    private SubtipoTareaEnum subtipoTarea;
    private Integer asignadoAId;
    private LocalDate fechaLimite;
    private Time horaInicio;
    private String duracion;
    private ModalidadActividadEnum modalidad;
    private String lugarReunion;
    private MedioReunionEnum medio;
    private String enlaceReunion;
    private EstatusActividadEnum estatus;
    private Integer contactoId;
    private String contactoNombre;
    private String empresaNombre;
    private Integer empresaId;
}