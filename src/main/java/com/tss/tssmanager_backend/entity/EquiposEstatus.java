package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.EstatusReporteEquipoEnum;
import jakarta.persistence.*;
import lombok.Data;

import java.sql.Date;

@Entity
@Table(name = "\"Equipos_Estatus\"")
@Data
public class EquiposEstatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_id", nullable = false)
    private Equipo equipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false)
    private EstatusReporteEquipoEnum estatus;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "fecha_check", nullable = false)
    private Date fechaCheck;
}