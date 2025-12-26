package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.sql.Date;

@Entity
@Table(name = "\"Historial_Saldos_SIMs\"")
@Data
public class HistorialSaldosSim {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sim_numero", referencedColumnName = "numero", nullable = false)
    private Sim sim;

    @Column(name = "saldo_actual")
    private BigDecimal saldoActual;

    @Column(name = "datos")
    private BigDecimal datos;

    @Column(name = "fecha", nullable = false)
    private Date fecha;

    @Column(columnDefinition = "boolean default false")
    private Boolean revisado = false;
}