package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "\"Proveedores\"")
@Data
public class ProveedorEquipo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "contacto_nombre")
    private String contactoNombre;

    @Column(name = "telefono")
    private String telefono;

    @Column(name = "correo")
    private String correo;

    @Column(name = "sitio_web")
    private String sitioWeb;

}