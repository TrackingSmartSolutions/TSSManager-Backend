package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.TipoDocumentoSolicitudEnum;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "\"Solicitudes_Factura_Nota\"")
@Data
public class SolicitudFacturaNota {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String identificador;

    @Column(nullable = false, name = "fecha_emision")
    private java.sql.Date fechaEmision;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private Empresa cliente;

    @ManyToOne
    @JoinColumn(name = "cuenta_por_cobrar_id", nullable = false)
    private CuentaPorCobrar cuentaPorCobrar;

    @ManyToOne
    @JoinColumn(name = "cotizacion_id")
    private Cotizacion cotizacion;

    @Column(nullable = false, name = "metodo_pago")
    private String metodoPago;

    @Column(nullable = false, name = "forma_pago")
    private String formaPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDocumentoSolicitudEnum tipo;

    @Column(nullable = false, name = "clave_producto_servicio")
    private String claveProductoServicio;

    @Column(nullable = false, name = "clave_unidad")
    private String claveUnidad;

    @ManyToOne
    @JoinColumn(name = "emisor_id", nullable = false)
    private Emisor emisor;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(nullable = false)
    private BigDecimal iva;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(nullable = false, name = "importe_letra")
    private String importeLetra;

    @Column(name = "fecha_modificacion", nullable = false)
    private java.time.LocalDateTime fechaModificacion;

    @Column(name = "uso_cfdi")
    private String usoCfdi;

    @Column(name = "conceptos_seleccionados", columnDefinition = "TEXT")
    private String conceptosSeleccionados;

}
