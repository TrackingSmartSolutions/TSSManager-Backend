package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.*;
import jakarta.persistence.*;
import java.sql.Date;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "\"Actividades\"")
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

    // Getters y setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getTratoId() { return tratoId; }
    public void setTratoId(Integer tratoId) { this.tratoId = tratoId; }
    public TipoActividadEnum getTipo() { return tipo; }
    public void setTipo(TipoActividadEnum tipo) { this.tipo = tipo; }
    public SubtipoTareaEnum getSubtipoTarea() { return subtipoTarea; }
    public void setSubtipoTarea(SubtipoTareaEnum subtipoTarea) { this.subtipoTarea = subtipoTarea; }
    public Integer getAsignadoAId() { return asignadoAId; }
    public void setAsignadoAId(Integer asignadoAId) { this.asignadoAId = asignadoAId; }
    public LocalDate getFechaLimite() { return fechaLimite; }
    public void setFechaLimite(LocalDate fechaLimite) { this.fechaLimite = fechaLimite; }
    public Time getHoraInicio() { return horaInicio; }
    public void setHoraInicio(Time horaInicio) { this.horaInicio = horaInicio; }
    public String getDuracion() { return duracion; }
    public void setDuracion(String duracion) { this.duracion = duracion; }
    public ModalidadActividadEnum getModalidad() { return modalidad; }
    public void setModalidad(ModalidadActividadEnum modalidad) { this.modalidad = modalidad; }
    public String getLugarReunion() { return lugarReunion; }
    public void setLugarReunion(String lugarReunion) { this.lugarReunion = lugarReunion; }
    public MedioReunionEnum getMedio() { return medio; }
    public void setMedio(MedioReunionEnum medio) { this.medio = medio; }
    public String getEnlaceReunion() { return enlaceReunion; }
    public void setEnlaceReunion(String enlaceReunion) { this.enlaceReunion = enlaceReunion; }
    public FinalidadActividadEnum getFinalidad() { return finalidad; }
    public void setFinalidad(FinalidadActividadEnum finalidad) { this.finalidad = finalidad; }
    public EstatusActividadEnum getEstatus() { return estatus; }
    public void setEstatus(EstatusActividadEnum estatus) { this.estatus = estatus; }
    public Instant getFechaCompletado() { return fechaCompletado; }
    public void setFechaCompletado(Instant fechaCompletado) { this.fechaCompletado = fechaCompletado; }
    public Integer getUsuarioCompletadoId() { return usuarioCompletadoId; }
    public void setUsuarioCompletadoId(Integer usuarioCompletadoId) { this.usuarioCompletadoId = usuarioCompletadoId; }
    public RespuestaEnum getRespuesta() { return respuesta; }
    public void setRespuesta(RespuestaEnum respuesta) { this.respuesta = respuesta; }
    public InteresEnum getInteres() { return interes; }
    public void setInteres(InteresEnum interes) { this.interes = interes; }
    public InformacionEnum getInformacion() { return informacion; }
    public void setInformacion(InformacionEnum informacion) { this.informacion = informacion; }
    public SiguienteAccionEnum getSiguienteAccion() { return siguienteAccion; }
    public void setSiguienteAccion(SiguienteAccionEnum siguienteAccion) { this.siguienteAccion = siguienteAccion; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public Instant getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(Instant fechaModificacion) { this.fechaModificacion = fechaModificacion; }
    public Trato getTrato() { return trato; }
    public void setTrato(Trato trato) { this.trato = trato; }
    public Integer getContactoId() { return contactoId; }
    public void setContactoId(Integer contactoId) { this.contactoId = contactoId; }
    public Contacto getContacto() { return contacto; }
    public void setContacto(Contacto contacto) { this.contacto = contacto; }
}