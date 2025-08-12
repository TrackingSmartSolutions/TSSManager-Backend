package com.tss.tssmanager_backend.dto;

import lombok.Data;

@Data
public class ResumenEjecutivoDTO {
    private Integer totalEmpresas;
    private Double promedioContacto;
    private Double tasaRespuestaGlobal;
    private Double tasaConversionGlobal;
    private TendenciasDTO tendencias;

    // Constructores
    public ResumenEjecutivoDTO() {
    }

    public ResumenEjecutivoDTO(Integer totalEmpresas, Double promedioContacto,
                               Double tasaRespuestaGlobal, Double tasaConversionGlobal,
                               TendenciasDTO tendencias) {
        this.totalEmpresas = totalEmpresas;
        this.promedioContacto = promedioContacto;
        this.tasaRespuestaGlobal = tasaRespuestaGlobal;
        this.tasaConversionGlobal = tasaConversionGlobal;
        this.tendencias = tendencias;
    }
}