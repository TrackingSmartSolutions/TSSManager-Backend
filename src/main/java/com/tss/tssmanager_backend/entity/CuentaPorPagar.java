package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "\"Cuentas_por_Pagar\"")
public class CuentaPorPagar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "folio", nullable = false)
    private String folio;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @ManyToOne
    @JoinColumn(name = "cuenta_id", nullable = false)
    private CuentasTransacciones cuenta;

    @Column(name = "monto", nullable = false)
    private BigDecimal monto;

    @Column(name = "forma_pago", nullable = false)
    private String formaPago;

    @Column(name = "estatus", nullable = false)
    private String estatus;

    @Column(name = "nota")
    private String nota;

    @Column(name = "numero_pago")
    private Integer numeroPago;

    @Column(name = "total_pagos")
    private Integer totalPagos;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @ManyToOne
    @JoinColumn(name = "transaccion_id", nullable = false)
    private Transaccion transaccion;
}