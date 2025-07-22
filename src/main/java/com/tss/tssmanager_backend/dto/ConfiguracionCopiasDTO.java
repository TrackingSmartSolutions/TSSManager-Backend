package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.TipoCopiaSeguridadEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionCopiasDTO {
    private Integer id;
    private Integer usuarioId;
    private List<TipoCopiaSeguridadEnum> datosRespaldar;
    private String frecuencia;
    private LocalTime horaRespaldo;
    private String googleDriveEmail;
    private Boolean googleDriveVinculada;
}