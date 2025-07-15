package com.tss.tssmanager_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class EventoCalendarioDTO {
    private String titulo;
    private Instant inicio;
    private Instant fin;
    private String color;
    private String tipo;
    private String asignadoA;
    private String trato;
    private String modalidad;
    private String medio;
    private String numeroSim;
    private String imei;
    private String numeroCuenta;
    private String cliente;
    private String estado;
    private String esquema;
    private Boolean allDay;
}