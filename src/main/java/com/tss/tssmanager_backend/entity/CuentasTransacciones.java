package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "\"Cuentas_Transacciones\"",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_nombre_categoria",
                columnNames = {"nombre", "categoria_id"}
        ))
public class CuentasTransacciones {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @ManyToOne
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaTransacciones categoria;

    @Transient
    private Integer categoriaId;
}