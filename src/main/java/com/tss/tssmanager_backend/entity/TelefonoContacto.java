package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Table(name = "\"Telefonos_Contactos\"")
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

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Contacto getContacto() { return contacto; }
    public void setContacto(Contacto contacto) { this.contacto = contacto; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
}