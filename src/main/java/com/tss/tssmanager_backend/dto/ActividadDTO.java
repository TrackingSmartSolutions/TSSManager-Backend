package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.*;
import java.sql.Date;
import java.sql.Time;
import java.time.Instant;

public class ActividadDTO {
    private Integer id;
    private Integer tratoId;
    private TipoActividadEnum tipo;
    private SubtipoTareaEnum subtipoTarea;
    private Integer asignadoAId;
    private Date fechaLimite;
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
    public Date getFechaLimite() { return fechaLimite; }
    public void setFechaLimite(Date fechaLimite) { this.fechaLimite = fechaLimite; }
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
}