package com.tss.tssmanager_backend.dto;

import lombok.Data;

@Data
public class TasaConversionDTO {
    private Integer usuarioId;
    private String usuario;
    private Integer contactadas;
    private Integer respuestaPositiva;
    private Integer interesMedio;
    private Integer reuniones;
    private Double tasaRespuesta;
    private Double tasaInteres;
    private Double tasaReuniones;

    // Constructores
    public TasaConversionDTO() {
    }

    public TasaConversionDTO(Integer usuarioId, String usuario, Integer contactadas,
                             Integer respuestaPositiva, Integer interesMedio, Integer reuniones,
                             Double tasaRespuesta, Double tasaInteres, Double tasaReuniones) {
        this.usuarioId = usuarioId;
        this.usuario = usuario;
        this.contactadas = contactadas;
        this.respuestaPositiva = respuestaPositiva;
        this.interesMedio = interesMedio;
        this.reuniones = reuniones;
        this.tasaRespuesta = tasaRespuesta;
        this.tasaInteres = tasaInteres;
        this.tasaReuniones = tasaReuniones;
    }
}