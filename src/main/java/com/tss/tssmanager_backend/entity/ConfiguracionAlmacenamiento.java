package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "\"Configuracion_Almacenamiento\"")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionAlmacenamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tabla_nombre", nullable = false, length = 100)
    private String tablaNombre;

    @Column(name = "habilitado_limpieza")
    private Boolean habilitadoLimpieza = true;

    @Column(name = "dias_retencion")
    private Integer diasRetencion = 180;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

