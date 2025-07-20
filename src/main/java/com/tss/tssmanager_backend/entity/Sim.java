package com.tss.tssmanager_backend.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.tss.tssmanager_backend.enums.PrincipalSimEnum;
import com.tss.tssmanager_backend.enums.ResponsableSimEnum;
import com.tss.tssmanager_backend.enums.TarifaSimEnum;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.sql.Date;

@Entity
@Table(name = "\"SIMs\"")
@Data
public class Sim {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "numero", nullable = false)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(name = "tarifa", nullable = false)
    private TarifaSimEnum tarifa;

    @Column(name = "vigencia")
    @DateTimeFormat(pattern = "yyyy-MM-dd", iso = DateTimeFormat.ISO.DATE)
    private Date vigencia;

    @Column(name = "recarga")
    private BigDecimal recarga;

    @Enumerated(EnumType.STRING)
    @Column(name = "responsable", nullable = false)
    private ResponsableSimEnum responsable;

    @Enumerated(EnumType.STRING)
    @Column(name = "principal", nullable = false)
    private PrincipalSimEnum principal;

    @Column(name = "grupo")
    private Integer grupo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_imei", referencedColumnName = "imei")
    @JsonBackReference
    private Equipo equipo;

    @Column(name = "contrasena")
    private String contrasena;

    public String getEquipoImei() {
        return equipo != null ? equipo.getImei() : null;
    }

    public void setEquipoByImei(String imei) {
        if (equipo == null && imei != null) {
            equipo = new Equipo();
            equipo.setImei(imei);
        } else if (equipo != null && imei != null) {
            equipo.setImei(imei);
        } else if (imei == null) {
            equipo = null;
        }
    }
}