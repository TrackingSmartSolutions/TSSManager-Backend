package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.*;
import jakarta.persistence.*;
import lombok.Data;
import java.sql.Date;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "\"Actividades\"")
@Data
public class Actividad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "trato_id", nullable = false)
    private Integer tratoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoActividadEnum tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "subtipo_tarea")
    private SubtipoTareaEnum subtipoTarea;

    @Column(name = "asignado_a_id", nullable = false)
    private Integer asignadoAId;

    @Column(name = "fecha_limite", nullable = false)
    private LocalDate fechaLimite;

    @Column(name = "hora_inicio")
    private Time horaInicio;

    @Column(name = "duracion")
    private String duracion;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad")
    private ModalidadActividadEnum modalidad;

    @Column(name = "lugar_reunion")
    private String lugarReunion;

    @Enumerated(EnumType.STRING)
    @Column(name = "medio")
    private MedioReunionEnum medio;

    @Column(name = "enlace_reunion")
    private String enlaceReunion;

    @Enumerated(EnumType.STRING)
    @Column(name = "finalidad", nullable = false)
    private FinalidadActividadEnum finalidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false)
    private EstatusActividadEnum estatus;

    @Column(name = "fecha_completado")
    private Instant fechaCompletado;

    @Column(name = "usuario_completado_id")
    private Integer usuarioCompletadoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "respuesta")
    private RespuestaEnum respuesta;

    @Enumerated(EnumType.STRING)
    @Column(name = "interes")
    private InteresEnum interes;

    @Enumerated(EnumType.STRING)
    @Column(name = "informacion")
    private InformacionEnum informacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "siguiente_accion")
    private SiguienteAccionEnum siguienteAccion;

    @Column(name = "notas")
    private String notas;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_modificacion", nullable = false)
    private Instant fechaModificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trato_id", insertable = false, updatable = false)
    private Trato trato;

    @Column(name = "contacto_id")
    private Integer contactoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contacto_id", insertable = false, updatable = false)
    private Contacto contacto;

}