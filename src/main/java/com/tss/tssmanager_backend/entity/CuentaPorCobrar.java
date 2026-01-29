package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.EsquemaCobroEnum;
import com.tss.tssmanager_backend.enums.EstatusPagoEnum;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Data
@Table(name = "\"Cuentas_por_Cobrar\"")
public class CuentaPorCobrar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "folio", nullable = false, length = 50)
    private String folio;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private Empresa cliente;

    @ManyToOne
    @JoinColumn(name = "cotizacion_id", nullable = false)
    private Cotizacion cotizacion;

    @Column(name = "cantidadCobrar", nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidadCobrar;

    @Column(name = "monto_pagado", precision = 10, scale = 2)
    private BigDecimal montoPagado = BigDecimal.ZERO;

    @Column(name = "saldo_pendiente", precision = 10, scale = 2)
    private BigDecimal saldoPendiente;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false)
    private EstatusPagoEnum estatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "esquema", nullable = false)
    private EsquemaCobroEnum esquema;

    @Column(name = "no_equipos", nullable = false)
    private Integer noEquipos;

    @OneToMany(mappedBy = "cuentaPorCobrar", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConceptoCuenta> conceptos;

    @Column(name = "comprobante_pago_url", length = 255)
    private String comprobantePagoUrl;

    @Column(name = "fecha_real_pago")
    private LocalDate fechaRealPago;

    @OneToMany(mappedBy = "cuentaPorCobrar", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SolicitudFacturaNota> solicitudesFacturasNotas;
}