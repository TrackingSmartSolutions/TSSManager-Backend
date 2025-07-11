package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "\"Emisores\"")
@Data
public class Emisor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, name = "razon_social")
    private String razonSocial;

    @Column(nullable = false)
    private String direccion;

    @Column(nullable = false)
    private String rfc;

    @Column(nullable = false)
    private String telefono;

    @Column(name = "constancia_regimen_fiscal_url")
    private String constanciaRegimenFiscalUrl;
}