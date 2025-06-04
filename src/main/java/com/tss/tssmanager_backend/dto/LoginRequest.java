// En LoginRequest
package com.tss.tssmanager_backend.dto;

public class LoginRequest {
    private String nombreUsuario;
    private String contrasena;

    // Getters and Setters
    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }
    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }
}