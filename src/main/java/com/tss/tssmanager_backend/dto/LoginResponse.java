package com.tss.tssmanager_backend.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private String message;
    private String rol;

    public LoginResponse(String token, String message, String rol) {
        this.token = token;
        this.message = message;
        this.rol = rol;
    }
}