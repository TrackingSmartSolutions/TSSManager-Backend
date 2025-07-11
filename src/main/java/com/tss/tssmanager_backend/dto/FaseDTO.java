package com.tss.tssmanager_backend.dto;

import lombok.Data;

@Data
public class FaseDTO {
    private String nombre;
    private boolean actual;
    private boolean completada;

    public FaseDTO(String nombre, boolean actual, boolean completada) {
        this.nombre = nombre;
        this.actual = actual;
        this.completada = completada;
    }
}

