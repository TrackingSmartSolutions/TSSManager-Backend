package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.RolContactoEnum;
import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
public class ContactoDTO {
    private Integer id;
    private String nombre;
    private RolContactoEnum rol;
    private String celular;
    private Instant fechaCreacion;
    private Instant fechaModificacion;
    private Instant fechaUltimaActividad;
    private List<CorreoDTO> correos;
    private List<TelefonoDTO> telefonos;
    private String creadoPor;
    private String modificadoPor;
    private PropietarioDTO propietario;
    private Integer propietarioId;
    private String telefono;
    private String whatsapp;
    private String email;

    public ContactoDTO() {
    }

    public ContactoDTO(String nombre, String telefono, String whatsapp, String email) {
        this.nombre = nombre;
        this.telefono = telefono;
        this.whatsapp = whatsapp;
        this.email = email;
    }
}