package com.tss.tssmanager_backend.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class UsuarioDTO {
    private Integer id;
    private String nombreUsuario;
    private String nombre;
    private String apellidos;
    private String correoElectronico;
    private String rol;
    private String estatus;
    private Instant fechaCreacion;
    private Instant fechaModificacion;

}