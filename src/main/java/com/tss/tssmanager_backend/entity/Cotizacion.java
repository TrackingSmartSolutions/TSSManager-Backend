package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.EstatusCotizacionEnum;
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

    @Column(name = "trato_id")
    private Integer tratoId;

    @Column(name = "usuario_creador_id")
    private Integer usuarioCreadorId;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false, length = 20)
    private EstatusCotizacionEnum estatus = EstatusCotizacionEnum.PENDIENTE;

    @OneToMany(mappedBy = "cotizacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UnidadCotizacion> unidades;

    @OneToMany(mappedBy = "cotizacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CuentaPorCobrar> cuentasPorCobrar;

    @Column(name = "notas_comerciales_nombre")
    private String notasComercialesNombre;

    @Lob
    @Column(name = "notas_comerciales_contenido")
    private byte[] notasComercialesContenido;

    @Column(name = "notas_comerciales_tipo")
    private String notasComercialesTopo;

    // Agregar nuevos campos para Ficha Técnica
    @Column(name = "ficha_tecnica_nombre")
    private String fichaTecnicaNombre;

    @Lob
    @Column(name = "ficha_tecnica_contenido")
    private byte[] fichaTecnicaContenido;

    @Column(name = "ficha_tecnica_tipo")
    private String fichaTecnicaTipo;

}