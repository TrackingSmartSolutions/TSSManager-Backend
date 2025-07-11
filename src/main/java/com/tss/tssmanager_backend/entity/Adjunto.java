package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "\"Plantillas_Correos_Adjuntos\"")
@Data
public class Adjunto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plantilla_id", nullable = false)
    @JsonIgnore
    private PlantillaCorreo plantilla;

    @Column(name = "adjunto_url", nullable = false)
    private String adjuntoUrl;
}