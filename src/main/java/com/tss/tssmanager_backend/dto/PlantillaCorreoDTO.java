package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.entity.PlantillaCorreo;
import com.tss.tssmanager_backend.entity.Adjunto;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class PlantillaCorreoDTO {
    private Integer id;
    private String nombre;
    private String asunto;
    private String mensaje;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;
    private List<String> adjuntoUrls;

    public static PlantillaCorreoDTO fromEntity(PlantillaCorreo plantilla) {
        PlantillaCorreoDTO dto = new PlantillaCorreoDTO();
        dto.setId(plantilla.getId());
        dto.setNombre(plantilla.getNombre());
        dto.setAsunto(plantilla.getAsunto());
        dto.setMensaje(plantilla.getMensaje());
        dto.setFechaCreacion(plantilla.getFechaCreacion());
        dto.setFechaModificacion(plantilla.getFechaModificacion());
        if (plantilla.getAdjuntos() != null) {
            dto.setAdjuntoUrls(plantilla.getAdjuntos().stream()
                    .map(Adjunto::getAdjuntoUrl)
                    .collect(Collectors.toList()));
        }
        return dto;
    }
}