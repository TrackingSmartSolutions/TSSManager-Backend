package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp; // Importante para la fecha
import java.time.Instant;

@Data
@Entity
@Table(
        name = "notificacion_popup_mostrada",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"actividad_id", "usuario_id"})
        }
)
public class NotificacionPopupMostrada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "actividad_id", nullable = false)
    private Integer actividadId;

    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @CreationTimestamp
    @Column(name = "fecha_mostrado", nullable = false, updatable = false)
    private Instant fechaMostrado;
}