package com.tss.tssmanager_backend.dto;

import lombok.Data;

@Data
public class TendenciasDTO {
    private String totalEmpresas;
    private String promedioContacto;
    private String tasaRespuestaGlobal;
    private String tasaConversionGlobal;

    // Constructores
    public TendenciasDTO() {
    }

    public TendenciasDTO(String totalEmpresas, String promedioContacto,
                         String tasaRespuestaGlobal, String tasaConversionGlobal) {
        this.totalEmpresas = totalEmpresas;
        this.promedioContacto = promedioContacto;
        this.tasaRespuestaGlobal = tasaRespuestaGlobal;
        this.tasaConversionGlobal = tasaConversionGlobal;
    }
}