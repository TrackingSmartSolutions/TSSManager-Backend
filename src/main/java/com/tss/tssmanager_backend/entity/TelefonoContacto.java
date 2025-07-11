package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.Data;

@Entity
@Table(name = "\"Telefonos_Contactos\"")
@Data
public class TelefonoContacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "contacto_id", nullable = false)
    @JsonBackReference
    private Contacto contacto;

    @Column(nullable = false)
    private String telefono;
}