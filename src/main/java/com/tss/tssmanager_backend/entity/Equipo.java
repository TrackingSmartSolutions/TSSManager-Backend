package com.tss.tssmanager_backend.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.tss.tssmanager_backend.enums.EstatusEquipoEnum;
import com.tss.tssmanager_backend.enums.PlataformaEquipoEnum;
import com.tss.tssmanager_backend.enums.TipoActivacionEquipoEnum;
import com.tss.tssmanager_backend.enums.TipoEquipoEnum;
import jakarta.persistence.*;
import lombok.Data;

import java.sql.Date;

@Entity
@Table(name = "\"Equipos\"")
@Data
public class Equipo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "imei", nullable = false)
    private String imei;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "modelo_id", nullable = false)
    private Integer modeloId;

    @Column(name = "cliente_id")
    private Integer clienteId;

    @Column(name = "proveedor_id", nullable = false)
    private Integer proveedorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoEquipoEnum tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false)
    private EstatusEquipoEnum estatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_activacion")
    private TipoActivacionEquipoEnum tipoActivacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "plataforma")
    private PlataformaEquipoEnum plataforma;

    @OneToOne(mappedBy = "equipo", cascade = CascadeType.ALL)
    @JsonManagedReference
    private Sim simReferenciada;

    @Column(name = "fecha_activacion")
    private java.sql.Date fechaActivacion;

    @Column(name = "fecha_expiracion")
    private java.sql.Date fechaExpiracion;

    @Column(name = "cliente_default", length = 10)
    private String clienteDefault;

    @Column(name = "creditos_usados", nullable = false)
    private Integer creditosUsados = 0;

}