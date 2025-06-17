package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "\"Notas_Tratos\"")
public class NotaTrato {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "trato_id", nullable = false)
    private Integer tratoId;

    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(name = "nota", nullable = false)
    private String nota;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "editado_por")
    private Integer editadoPor;

    @Column(name = "fecha_edicion")
    private Instant fechaEdicion;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getTratoId() { return tratoId; }
    public void setTratoId(Integer tratoId) { this.tratoId = tratoId; }
    public Integer getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Integer usuarioId) { this.usuarioId = usuarioId; }
    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public Integer getEditadoPor() { return editadoPor; }
    public void setEditadoPor(Integer editadoPor) { this.editadoPor = editadoPor; }
    public Instant getFechaEdicion() { return fechaEdicion; }
    public void setFechaEdicion(Instant fechaEdicion) { this.fechaEdicion = fechaEdicion; }
}