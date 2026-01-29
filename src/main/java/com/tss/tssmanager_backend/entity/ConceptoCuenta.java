package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Data
@Table(name = "\"Conceptos_Cuentas_Cobrar\"")
public class ConceptoCuenta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_por_cobrar_id")
    private CuentaPorCobrar cuentaPorCobrar;

    private Integer cantidad;
    private String unidad;
    private String concepto;
    @Column(name = "descuento")
    private BigDecimal descuento;
    @Column(name = "precio_unitario")
    private BigDecimal precioUnitario;
    @Column(name = "importe_total")
    private BigDecimal importeTotal;
}
