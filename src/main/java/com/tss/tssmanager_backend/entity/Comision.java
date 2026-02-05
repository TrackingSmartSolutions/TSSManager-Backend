package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.EstatusComisionEnum;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "\"Comisiones\"")
public class Comision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "cuenta_por_cobrar_id", nullable = false)
    private CuentaPorCobrar cuentaPorCobrar;

    @ManyToOne
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne
    @JoinColumn(name = "trato_id", nullable = false)
    private Trato trato;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @Column(name = "monto_base", nullable = false, precision = 19, scale = 2)
    private BigDecimal montoBase;

    @ManyToOne
    @JoinColumn(name = "vendedor_cuenta_id", nullable = false)
    private CuentasTransacciones vendedorCuenta;

    @Column(name = "porcentaje_venta", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeVenta;

    @Column(name = "monto_comision_venta", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoComisionVenta;

    @Column(name = "saldo_pendiente_venta", nullable = false, precision = 10, scale = 2)
    private BigDecimal saldoPendienteVenta;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus_venta", nullable = false)
    private EstatusComisionEnum estatusVenta;

    @ManyToOne
    @JoinColumn(name = "proyecto_cuenta_id", nullable = false)
    private CuentasTransacciones proyectoCuenta;

    @Column(name = "porcentaje_proyecto", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeProyecto;

    @Column(name = "monto_comision_proyecto", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoComisionProyecto;

    @Column(name = "saldo_pendiente_proyecto", nullable = false, precision = 10, scale = 2)
    private BigDecimal saldoPendienteProyecto;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus_proyecto", nullable = false)
    private EstatusComisionEnum estatusProyecto;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    @PrePersist
    protected void onCreate() {
        fechaCreacion = LocalDateTime.now();
        fechaModificacion = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        fechaModificacion = LocalDateTime.now();
    }
}