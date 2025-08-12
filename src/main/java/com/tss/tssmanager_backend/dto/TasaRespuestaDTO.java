package com.tss.tssmanager_backend.dto;

import lombok.Data;

@Data
public class TasaRespuestaDTO {
    private Integer usuarioId;
    private String usuario;
    private Integer totalLlamadas;
    private Integer llamadasExitosas;
    private Double tasa;

    // Constructores
    public TasaRespuestaDTO() {
    }

    public TasaRespuestaDTO(Integer usuarioId, String usuario, Integer totalLlamadas,
                            Integer llamadasExitosas, Double tasa) {
        this.usuarioId = usuarioId;
        this.usuario = usuario;
        this.totalLlamadas = totalLlamadas;
        this.llamadasExitosas = llamadasExitosas;
        this.tasa = tasa;
    }
}