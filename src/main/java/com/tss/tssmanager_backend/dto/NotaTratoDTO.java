package com.tss.tssmanager_backend.dto;

import java.time.Instant;

public class NotaTratoDTO {
    private Integer id;
    private Integer tratoId;
    private Integer usuarioId;
    private String nota;
    private Instant fechaCreacion;
    private Integer editadoPor;
    private Instant fechaEdicion;
    private String autorNombre; // Nuevo campo
    private String editadoPorNombre; // Nuevo campo

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
    public String getAutorNombre() { return autorNombre; }
    public void setAutorNombre(String autorNombre) { this.autorNombre = autorNombre; }
    public String getEditadoPorNombre() { return editadoPorNombre; }
    public void setEditadoPorNombre(String editadoPorNombre) { this.editadoPorNombre = editadoPorNombre; }
}