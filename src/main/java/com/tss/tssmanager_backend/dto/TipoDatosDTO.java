package com.tss.tssmanager_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TipoDatosDTO {
    private String value;
    private String label;
    private String descripcion;
}