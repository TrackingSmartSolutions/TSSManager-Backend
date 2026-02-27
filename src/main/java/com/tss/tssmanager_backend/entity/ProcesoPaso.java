package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "\"Proceso_Pasos\"")
public class ProcesoPaso {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "proceso_id")
    private ProcesoAutomatico proceso;

    @ManyToOne
    @JoinColumn(name = "plantilla_id")
    private PlantillaCorreo plantilla;

    private Integer dias;
    private Integer orden;

}
