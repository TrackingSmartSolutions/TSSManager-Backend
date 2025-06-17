package com.tss.tssmanager_backend.dto;

import java.util.List;

public class ActividadesAbiertasDTO {
    private List<ActividadDTO> tareas;
    private List<ActividadDTO> llamadas;
    private List<ActividadDTO> reuniones;

    public ActividadesAbiertasDTO(List<ActividadDTO> tareas, List<ActividadDTO> llamadas, List<ActividadDTO> reuniones) {
        this.tareas = tareas;
        this.llamadas = llamadas;
        this.reuniones = reuniones;
    }

    public List<ActividadDTO> getTareas() { return tareas; }
    public void setTareas(List<ActividadDTO> tareas) { this.tareas = tareas; }
    public List<ActividadDTO> getLlamadas() { return llamadas; }
    public void setLlamadas(List<ActividadDTO> llamadas) { this.llamadas = llamadas; }
    public List<ActividadDTO> getReuniones() { return reuniones; }
    public void setReuniones(List<ActividadDTO> reuniones) { this.reuniones = reuniones; }
}
