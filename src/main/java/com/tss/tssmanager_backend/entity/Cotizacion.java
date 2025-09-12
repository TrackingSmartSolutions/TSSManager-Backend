package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Data
@Table(name = "\"Cotizaciones\"")
public class Cotizacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cotizacion_cliente"))
    private Empresa cliente;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "iva", nullable = false, precision = 10, scale = 2)
    private BigDecimal iva;

    @Column(name = "isr_estatal", nullable = false, precision = 10, scale = 2)
    private BigDecimal isrEstatal;

    @Column(name = "isr_federal", nullable = false, precision = 10, scale = 2)
    private BigDecimal isrFederal;

    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "importe_letra", nullable = false, length = 500)
    private String importeLetra;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @OneToMany(mappedBy = "cotizacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UnidadCotizacion> unidades;

    @Column(name = "archivo_adicional_nombre")
    private String archivoAdicionalNombre;

    @Lob
    @Column(name = "archivo_adicional_contenido")
    private byte[] archivoAdicionalContenido;

    @Column(name = "archivo_adicional_tipo")
    private String archivoAdicionalTipo;


}