package com.tss.tssmanager_backend.dto;

public class LoginResponse {
    private String token;
    private String message;
    private String rol;

    public LoginResponse(String token, String message, String rol) {
        this.token = token;
        this.message = message;
        this.rol = rol;
    }

    // Getters and Setters
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
}