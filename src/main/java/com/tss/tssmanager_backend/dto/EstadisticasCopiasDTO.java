package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.TipoCopiaSeguridadEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadisticasCopiasDTO {
    private Long copiasActivas;
    private Long copiasEstesMes;
    private Map<TipoCopiaSeguridadEnum, Long> copiasPorTipo;
    private String espacioUtilizado;
    private LocalDateTime ultimaCopia;
    private LocalDateTime proximaEjecucion;
    private Boolean googleDriveVinculado;
    private Boolean configuracionCompleta;
}
