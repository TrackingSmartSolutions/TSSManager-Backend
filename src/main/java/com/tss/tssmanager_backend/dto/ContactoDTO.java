package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.RolContactoEnum;
import java.time.LocalDateTime;
import java.util.List;

public class ContactoDTO {
    private Integer id;
    private String nombre;
    private RolContactoEnum rol;
    private String celular;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;
    private LocalDateTime fechaUltimaActividad;
    private List<CorreoDTO> correos;
    private List<TelefonoDTO> telefonos;
    private String creadoPor;
    private String modificadoPor;
    private PropietarioDTO propietario;
    private Integer propietarioId;

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public RolContactoEnum getRol() { return rol; }
    public void setRol(RolContactoEnum rol) { this.rol = rol; }
    public String getCelular() { return celular; }
    public void setCelular(String celular) { this.celular = celular; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(LocalDateTime fechaModificacion) { this.fechaModificacion = fechaModificacion; }
    public LocalDateTime getFechaUltimaActividad() { return fechaUltimaActividad; }
    public void setFechaUltimaActividad(LocalDateTime fechaUltimaActividad) { this.fechaUltimaActividad = fechaUltimaActividad; }
    public List<CorreoDTO> getCorreos() { return correos; }
    public void setCorreos(List<CorreoDTO> correos) { this.correos = correos; }
    public List<TelefonoDTO> getTelefonos() { return telefonos; }
    public void setTelefonos(List<TelefonoDTO> telefonos) { this.telefonos = telefonos; }
    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }
    public String getModificadoPor() { return modificadoPor; }
    public void setModificadoPor(String modificadoPor) { this.modificadoPor = modificadoPor; }
    public PropietarioDTO getPropietario() { return propietario; }
    public void setPropietario(PropietarioDTO propietario) { this.propietario = propietario; }
    public Integer getPropietarioId() { return propietarioId; }
    public void setPropietarioId(Integer propietarioId) { this.propietarioId = propietarioId; }
}