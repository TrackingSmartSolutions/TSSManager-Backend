package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Table(name = "\"Correos_Contactos\"")
public class CorreoContacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "contacto_id", nullable = false)
    @JsonBackReference
    private Contacto contacto;

    @Column(nullable = false)
    private String correo;

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Contacto getContacto() { return contacto; }
    public void setContacto(Contacto contacto) { this.contacto = contacto; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
}