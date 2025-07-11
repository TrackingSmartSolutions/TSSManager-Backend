package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.UsoModeloEquipoEnum;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "\"Modelos_Equipos\"")
@Data
public class ModeloEquipo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "imagen_url")
    private String imagenUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "uso", nullable = false)
    private UsoModeloEquipoEnum uso;
}