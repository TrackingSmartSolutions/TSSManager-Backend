package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.EstatusEmpresaEnum;
import com.tss.tssmanager_backend.enums.SectorEmpresaEnum;
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
@Table(name = "\"Empresas\"")
@Data
@EntityListeners(AuditingEntityListener.class)
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @ManyToOne
    @JoinColumn(name = "propietario_id", nullable = false)
    private Usuario propietario;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false)
    private EstatusEmpresaEnum estatus;

    @Column(name = "sitio_web")
    private String sitioWeb;

    @Column(name = "sector")
    @Enumerated(EnumType.STRING)
    private SectorEmpresaEnum sector;

    @Column(name = "domicilio_fisico", nullable = false)
    private String domicilioFisico;

    @Column(name = "domicilio_fiscal")
    private String domicilioFiscal;

    @Column(name = "rfc")
    private String rfc;

    @Column(name = "razon_social")
    private String razonSocial;

    @Column(name = "regimen_fiscal")
    private String regimenFiscal;

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

    @OneToMany(mappedBy = "empresa", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<Contacto> contactos = new ArrayList<>();
}