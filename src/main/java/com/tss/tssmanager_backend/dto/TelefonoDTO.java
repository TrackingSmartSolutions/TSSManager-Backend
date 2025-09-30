package com.tss.tssmanager_backend.dto;

import lombok.Data;

@Data
public class TelefonoDTO {
    private String telefono;

    public TelefonoDTO() {
    }

    public TelefonoDTO(String telefono) {
        this.telefono = telefono;
    }
}