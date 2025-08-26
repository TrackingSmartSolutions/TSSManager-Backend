package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.EstatusReporteEquipoEnum;
import jakarta.persistence.*;
import lombok.Data;

import java.sql.Date;
import java.sql.Timestamp;

@Entity
@Table(name = "\"Equipos_Estatus\"")
@Data
public class EquiposEstatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_equipos_estatus_equipo"))
    private Equipo equipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false, length = 20)
    private EstatusReporteEquipoEnum estatus;

    @Column(name = "motivo", length = 500)
    private String motivo;

    @Column(name = "fecha_check", nullable = false)
    private Timestamp fechaCheck;
}