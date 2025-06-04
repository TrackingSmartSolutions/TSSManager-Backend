package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.EstatusUsuarioEnum;
import com.tss.tssmanager_backend.enums.RolUsuarioEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "\"Usuarios\"")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "apellidos", nullable = false, length = 100)
    private String apellidos;

    @Column(name = "nombre_usuario", unique = true, length = 100)
    private String nombreUsuario;

    @Column(name = "correo_electronico", nullable = false, unique = true, length = 100)
    private String correoElectronico;

    @Column(name = "contrasena", nullable = false, length = 255)
    private String contrasena;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false)
    private RolUsuarioEnum rol;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false)
    private EstatusUsuarioEnum estatus;

    @Column(name = "ultima_actividad")
    private LocalDateTime ultimaActividad;

    @CreatedDate
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    @Column(name = "fecha_modificacion", nullable = false)
    private LocalDateTime fechaModificacion;
}