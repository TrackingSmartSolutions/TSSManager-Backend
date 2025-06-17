package com.tss.tssmanager_backend.dto;

public class FaseDTO {
    private String nombre;
    private boolean actual;
    private boolean completada;

    public FaseDTO(String nombre, boolean actual, boolean completada) {
        this.nombre = nombre;
        this.actual = actual;
        this.completada = completada;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public boolean isActual() { return actual; }
    public void setActual(boolean actual) { this.actual = actual; }
    public boolean isCompletada() { return completada; }
    public void setCompletada(boolean completada) { this.completada = completada; }
}

