package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.RolContactoEnum;
import java.time.Instant;
import java.util.List;

public class ContactoDTO {
    private Integer id;
    private String nombre;
    private RolContactoEnum rol;
    private String celular;
    private Instant fechaCreacion;
    private Instant fechaModificacion;
    private Instant fechaUltimaActividad;
    private List<CorreoDTO> correos;
    private List<TelefonoDTO> telefonos;
    private String creadoPor;
    private String modificadoPor;
    private PropietarioDTO propietario;
    private Integer propietarioId;
    private String telefono;
    private String whatsapp;
    private String email;

    public ContactoDTO() {
    }

    public ContactoDTO(String nombre, String telefono, String whatsapp, String email) {
        this.nombre = nombre;
        this.telefono = telefono;
        this.whatsapp = whatsapp;
        this.email = email;
    }

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public RolContactoEnum getRol() { return rol; }
    public void setRol(RolContactoEnum rol) { this.rol = rol; }
    public String getCelular() { return celular; }
    public void setCelular(String celular) { this.celular = celular; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public Instant getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(Instant fechaModificacion) { this.fechaModificacion = fechaModificacion; }
    public Instant getFechaUltimaActividad() { return fechaUltimaActividad; }
    public void setFechaUltimaActividad(Instant fechaUltimaActividad) { this.fechaUltimaActividad = fechaUltimaActividad; }
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
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getWhatsapp() { return whatsapp; }
    public void setWhatsapp(String whatsapp) { this.whatsapp = whatsapp; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}