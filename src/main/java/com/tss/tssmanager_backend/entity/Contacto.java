package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.RolContactoEnum;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
@Table(name = "\"Contactos\"")
@EntityListeners(AuditingEntityListener.class)
public class Contacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "empresa_id", nullable = false)
    @JsonBackReference
    private Empresa empresa;

    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RolContactoEnum rol;

    private String celular;

    @ManyToOne
    @JoinColumn(name = "propietario_id", nullable = false)
    private Usuario propietario;

    @CreatedBy
    @Column(name = "creado_por", nullable = false, updatable = false)
    private String creadoPor;

    @LastModifiedBy
    @Column(name = "modificado_por")
    private String modificadoPor;

    @CreatedDate
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @LastModifiedDate
    @Column(name = "fecha_modificacion", nullable = false)
    private Instant fechaModificacion;

    @LastModifiedDate
    @Column(name = "fecha_ultima_actividad", nullable = false)
    private Instant fechaUltimaActividad;

    @OneToMany(mappedBy = "contacto", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<CorreoContacto> correos = new ArrayList<>();

    @OneToMany(mappedBy = "contacto", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<TelefonoContacto> telefonos = new ArrayList<>();

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public RolContactoEnum getRol() { return rol; }
    public void setRol(RolContactoEnum rol) { this.rol = rol; }

    public String getCelular() { return celular; }
    public void setCelular(String celular) { this.celular = celular; }

    public Usuario getPropietario() { return propietario; }
    public void setPropietario(Usuario propietario) { this.propietario = propietario; }

    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }

    public String getModificadoPor() { return modificadoPor; }
    public void setModificadoPor(String modificadoPor) { this.modificadoPor = modificadoPor; }

    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public Instant getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(Instant fechaModificacion) { this.fechaModificacion = fechaModificacion; }

    public Instant getFechaUltimaActividad() { return fechaUltimaActividad; }
    public void setFechaUltimaActividad(Instant fechaUltimaActividad) { this.fechaUltimaActividad = fechaUltimaActividad; }

    public List<CorreoContacto> getCorreos() { return correos; }
    public void setCorreos(List<CorreoContacto> correos) {
        this.correos.clear();
        if (correos != null) {
            correos.forEach(correo -> correo.setContacto(this));
            this.correos.addAll(correos);
        }
    }

    public List<TelefonoContacto> getTelefonos() { return telefonos; }
    public void setTelefonos(List<TelefonoContacto> telefonos) {
        this.telefonos.clear();
        if (telefonos != null) {
            telefonos.forEach(telefono -> telefono.setContacto(this));
            this.telefonos.addAll(telefonos);
        }
    }

    public void addCorreo(CorreoContacto correo) {
        correo.setContacto(this);
        this.correos.add(correo);
    }

    public void addTelefono(TelefonoContacto telefono) {
        telefono.setContacto(this);
        this.telefonos.add(telefono);
    }
}