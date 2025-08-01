package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.*;
import lombok.Data;

@Data
public class InteraccionGenericaDTO {
    private TipoActividadEnum tipo;
    private MedioReunionEnum medio;
    private RespuestaEnum respuesta;
    private InteresEnum interes;
    private InformacionEnum informacion;
    private SiguienteAccionEnum siguienteAccion;
    private String notas;
}
