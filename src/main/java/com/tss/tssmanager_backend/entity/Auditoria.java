package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Entity
@Table(name = "\"Auditoria\"")
@Data
public class Auditoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tabla", nullable = false)
    private String tabla;

    @Column(name = "accion", nullable = false)
    private String accion;

    @Column(name = "registro_id")
    private Integer registroId;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "nombre_usuario")
    private String nombreUsuario;

    @Column(name = "detalle_accion", columnDefinition = "TEXT")
    private String detalleAccion;

    @Column(name = "datos_anteriores", columnDefinition = "jsonb")
    private String datosAnteriores;

    @Column(name = "datos_nuevos", columnDefinition = "jsonb")
    private String datosNuevos;

    @Column(name = "fecha", nullable = false)
    private Instant fecha;
}