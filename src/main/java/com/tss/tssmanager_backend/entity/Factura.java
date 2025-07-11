package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "\"Facturas\"")
@Data
public class Factura {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, name = "folio_fiscal")
    private String folioFiscal;

    @Column(nullable = false, name = "no_solicitud")
    private String noSolicitud;

    @Column(nullable = false, name = "archivo_url")
    private String archivoUrl;

    @Column(name = "solicitud_id")
    private Integer solicitudId;

    @ManyToOne
    @JoinColumn(name = "solicitud_id", insertable = false, updatable = false)
    private SolicitudFacturaNota solicitud;
}