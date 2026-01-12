package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.tss.tssmanager_backend.enums.TipoTransaccionEnum;
import com.tss.tssmanager_backend.enums.EsquemaTransaccionEnum;
import lombok.Data;

@Entity
@Data
@Table(name = "\"Transacciones\"")
public class Transaccion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoTransaccionEnum tipo;

    @ManyToOne
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaTransacciones categoria;

    @ManyToOne
    @JoinColumn(name = "cuenta_id", nullable = false)
    private CuentasTransacciones cuenta;

    @Column(name = "monto", nullable = false)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(name = "esquema", nullable = false)
    private EsquemaTransaccionEnum esquema;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @Column(name = "forma_pago", nullable = false)
    private String formaPago;

    @Column(name = "notas")
    private String notas;

    @Column(name = "numero_pagos")
    private Integer numeroPagos;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_modificacion", nullable = false)
    private LocalDateTime fechaModificacion;

    @Transient
    private Integer categoriaId;

    @Transient
    private Integer cuentaId;

    @Transient
    private String nombreCuenta;

    @Column(name = "cuenta_por_cobrar_id")
    private Integer cuentaPorCobrarId;

    @Column(name = "cuenta_por_pagar_id")
    private Integer cuentaPorPagarId;

}
