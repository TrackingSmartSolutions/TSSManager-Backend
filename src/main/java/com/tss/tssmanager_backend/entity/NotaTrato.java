package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Entity
@Table(name = "\"Notas_Tratos\"")
@Data
public class NotaTrato {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "trato_id", nullable = false)
    private Integer tratoId;

    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(name = "nota", nullable = false)
    private String nota;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "editado_por")
    private Integer editadoPor;

    @Column(name = "fecha_edicion")
    private Instant fechaEdicion;


}