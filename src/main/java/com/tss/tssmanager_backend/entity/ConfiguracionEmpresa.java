package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "\"Configuracion_Empresa\"")
@Data
public class ConfiguracionEmpresa {
    @Id
    private Integer id;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "eslogan")
    private String eslogan;

    @Column(name = "correo_contacto")
    private String correoContacto;

    @Column(name = "telefono_movil")
    private String telefonoMovil;

    @Column(name = "telefono_fijo")
    private String telefonoFijo;

    @Column(name = "direccion_principal")
    private String direccionPrincipal;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "fecha_creacion")
    private LocalDateTime fechacreacion;

    @UpdateTimestamp
    @Column(name = "fecha_modificacion")
    private LocalDateTime fechamodificacion;
}