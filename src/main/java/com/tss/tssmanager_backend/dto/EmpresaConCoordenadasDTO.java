package com.tss.tssmanager_backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EmpresaConCoordenadasDTO {
    private Integer id;
    private String nombre;
    private String domicilioFisico;
    private String sector;
    private String estatus;
    private String sitioWeb;
    private BigDecimal lat;
    private BigDecimal lng;

    // Constructors
    public EmpresaConCoordenadasDTO() {}

    public EmpresaConCoordenadasDTO(Integer id, String nombre, String domicilioFisico,
                                    String sector, String estatus, String sitioWeb,
                                    BigDecimal lat, BigDecimal lng) {
        this.id = id;
        this.nombre = nombre;
        this.domicilioFisico = domicilioFisico;
        this.sector = sector;
        this.estatus = estatus;
        this.sitioWeb = sitioWeb;
        this.lat = lat;
        this.lng = lng;
    }

}