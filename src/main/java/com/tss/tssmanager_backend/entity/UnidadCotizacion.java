package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Data
@Table(name = "\"Unidades_Cotizacion\"")
public class UnidadCotizacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "cotizacion_id", nullable = false, foreignKey = @ForeignKey(name = "fk_unidad_cotizacion"))
    private Cotizacion cotizacion;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "unidad", nullable = false, length = 50)
    private String unidad;

    @Column(name = "concepto", nullable = false)
    private String concepto;

    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "descuento", nullable = false, precision = 5, scale = 2)
    private BigDecimal descuento;

    @Column(name = "importe_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal importeTotal;

}