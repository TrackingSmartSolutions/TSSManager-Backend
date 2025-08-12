package com.tss.tssmanager_backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class DashboardMetricasDTO {
    private ResumenEjecutivoDTO resumenEjecutivo;
    private List<EmpresasCreadasDTO> empresasCreadas;
    private List<TasaRespuestaDTO> tasaRespuesta;
    private List<TasaConversionDTO> tasaConversion;

    // Constructores
    public DashboardMetricasDTO() {}

    public DashboardMetricasDTO(ResumenEjecutivoDTO resumenEjecutivo,
                                List<EmpresasCreadasDTO> empresasCreadas,
                                List<TasaRespuestaDTO> tasaRespuesta,
                                List<TasaConversionDTO> tasaConversion) {
        this.resumenEjecutivo = resumenEjecutivo;
        this.empresasCreadas = empresasCreadas;
        this.tasaRespuesta = tasaRespuesta;
        this.tasaConversion = tasaConversion;
    }
}