package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "\"Historial_Limpieza\"")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistorialLimpieza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tabla_nombre", nullable = false, length = 100)
    private String tablaNombre;

    @Column(name = "registros_eliminados")
    private Integer registrosEliminados = 0;

    @Column(name = "espacio_liberado_mb", precision = 10, scale = 2)
    private BigDecimal espacioLiberadoMb = BigDecimal.ZERO;

    @Column(name = "tipo_limpieza", nullable = false, length = 50)
    private String tipoLimpieza;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "fecha_limpieza")
    private LocalDateTime fechaLimpieza;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @PrePersist
    protected void onCreate() {
        fechaLimpieza = LocalDateTime.now();
    }
}
