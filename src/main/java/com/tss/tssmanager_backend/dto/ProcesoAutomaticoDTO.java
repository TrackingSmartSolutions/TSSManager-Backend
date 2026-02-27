package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.entity.ProcesoAutomatico;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

@Data
public class ProcesoAutomaticoDTO {
    private Integer id;
    private String nombre;
    private List<PasoDTO> pasos;

    @Data
    public static class PasoDTO {
        private Integer id;
        private Integer plantillaId;
        private String plantillaNombre;
        private Integer dias;
        private Integer orden;
    }

    public static ProcesoAutomaticoDTO fromEntity(ProcesoAutomatico p) {
        ProcesoAutomaticoDTO dto = new ProcesoAutomaticoDTO();
        dto.setId(p.getId());
        dto.setNombre(p.getNombre());

        dto.setPasos(p.getPasos().stream().map(paso -> {
            PasoDTO pasoDTO = new PasoDTO();
            pasoDTO.setId(paso.getId());
            pasoDTO.setPlantillaId(paso.getPlantilla().getId());
            pasoDTO.setPlantillaNombre(paso.getPlantilla().getNombre());
            pasoDTO.setDias(paso.getDias());
            pasoDTO.setOrden(paso.getOrden());
            return pasoDTO;
        }).collect(Collectors.toList()));

        return dto;
    }
}