package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.RolContactoEnum;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
@Table(name = "\"Contactos\"")
@Data
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

}