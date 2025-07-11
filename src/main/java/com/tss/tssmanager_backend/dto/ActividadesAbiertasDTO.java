package com.tss.tssmanager_backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class ActividadesAbiertasDTO {
    private List<ActividadDTO> tareas;
    private List<ActividadDTO> llamadas;
    private List<ActividadDTO> reuniones;

    public ActividadesAbiertasDTO(List<ActividadDTO> tareas, List<ActividadDTO> llamadas, List<ActividadDTO> reuniones) {
        this.tareas = tareas;
        this.llamadas = llamadas;
        this.reuniones = reuniones;
    }

}
