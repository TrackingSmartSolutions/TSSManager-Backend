package com.tss.tssmanager_backend.dto;

import lombok.Data;

@Data
public class CorreoDTO {
    private String correo;

    public CorreoDTO() {
    }

    public CorreoDTO(String correo) {
        this.correo = correo;
    }
}