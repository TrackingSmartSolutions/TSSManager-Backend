package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "\"Tratos\"")
public class Trato {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contacto_id", nullable = false)
    private Contacto contacto;

    @Column(name = "numero_unidades")
    private Integer numeroUnidades;

    @Column(name = "ingresos_esperados")
    private BigDecimal ingresosEsperados;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "propietario_id", nullable = false)
    private Integer propietarioId;

    @Column(name = "fecha_cierre", nullable = false)
    private LocalDateTime fechaCierre;

    @Column(name = "no_trato", unique = true)
    private String noTrato;

    @Column(name = "probabilidad", nullable = false)
    private Integer probabilidad;

    @Column(name = "fase", columnDefinition = "varchar(50)", nullable = false)
    private String fase;

    @Column(name = "correos_automaticos_activos")
    private Boolean correosAutomaticosActivos;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_modificacion", nullable = false)
    private Instant fechaModificacion;

    @Column(name = "fecha_ultima_actividad", nullable = false)
    private Instant fechaUltimaActividad;

    @OneToMany(mappedBy = "tratoId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Actividad> actividades;

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public Integer getEmpresaId() { return empresaId; }
    public void setEmpresaId(Integer empresaId) { this.empresaId = empresaId; }
    public Contacto getContacto() { return contacto; } // Correct method
    public void setContacto(Contacto contacto) { this.contacto = contacto; } // Correct method
    public Integer getNumeroUnidades() { return numeroUnidades; }
    public void setNumeroUnidades(Integer numeroUnidades) { this.numeroUnidades = numeroUnidades; }
    public BigDecimal getIngresosEsperados() { return ingresosEsperados; }
    public void setIngresosEsperados(BigDecimal ingresosEsperados) { this.ingresosEsperados = ingresosEsperados; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Integer getPropietarioId() { return propietarioId; }
    public void setPropietarioId(Integer propietarioId) { this.propietarioId = propietarioId; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }
    public String getNoTrato() { return noTrato; }
    public void setNoTrato(String noTrato) { this.noTrato = noTrato; }
    public Integer getProbabilidad() { return probabilidad; }
    public void setProbabilidad(Integer probabilidad) { this.probabilidad = probabilidad; }
    public String getFase() { return fase; }
    public void setFase(String fase) { this.fase = fase; }
    public Boolean getCorreosAutomaticosActivos() { return correosAutomaticosActivos; }
    public void setCorreosAutomaticosActivos(Boolean correosAutomaticosActivos) { this.correosAutomaticosActivos = correosAutomaticosActivos; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public Instant getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(Instant fechaModificacion) { this.fechaModificacion = fechaModificacion; }
    public Instant getFechaUltimaActividad() { return fechaUltimaActividad; }
    public void setFechaUltimaActividad(Instant fechaUltimaActividad) { this.fechaUltimaActividad = fechaUltimaActividad; }
    public List<Actividad> getActividades() { return actividades; }
    public void setActividades(List<Actividad> actividades) { this.actividades = actividades; }
}