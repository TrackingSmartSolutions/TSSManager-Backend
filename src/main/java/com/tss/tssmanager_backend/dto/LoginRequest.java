package com.tss.tssmanager_backend.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String nombreUsuario;
    private String contrasena;

}