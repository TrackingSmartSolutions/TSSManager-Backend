package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.ConceptoCreditoEnum;
import com.tss.tssmanager_backend.enums.PlataformaEquipoEnum;
import com.tss.tssmanager_backend.enums.TipoCreditoEnum;
import com.tss.tssmanager_backend.utils.DateUtils;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "creditos_plataforma")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditoPlataforma {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plataforma_id", nullable = false, foreignKey = @ForeignKey(name = "fk_creditos_plataforma"))
    private Plataforma plataforma;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConceptoCreditoEnum concepto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCreditoEnum tipo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(length = 500)
    private String nota;

    @Column(name = "equipo_id")
    private Integer equipoId;

    @Column(name = "transaccion_id")
    private Integer transaccionId;

    @Column(name = "cuenta_por_pagar_id")
    private Integer cuentaPorPagarId;

    @Column(name = "fecha_creacion",
            nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "subtipo")
    private String subtipo;

    @Column(name = "es_licencia")
    private Boolean esLicencia = false;

    @PrePersist
    protected void onCreate() {
        fechaCreacion = DateUtils.nowInMexico();
    }
}