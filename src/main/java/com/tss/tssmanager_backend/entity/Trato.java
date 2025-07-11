package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Entity
@Table(name = "\"Tratos\"")
@Data
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


}