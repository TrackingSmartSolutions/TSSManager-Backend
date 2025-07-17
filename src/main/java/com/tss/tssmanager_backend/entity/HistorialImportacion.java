package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "\"Historial_Importaciones\"")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistorialImportacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(name = "tipo_datos", nullable = false)
    private String tipoDatos;

    @Column(name = "nombre_archivo", nullable = false)
    private String nombreArchivo;

    @Column(name = "registros_procesados")
    private Integer registrosProcesados = 0;

    @Column(name = "registros_exitosos")
    private Integer registrosExitosos = 0;

    @Column(name = "registros_fallidos")
    private Integer registrosFallidos = 0;

    @Column(columnDefinition = "TEXT")
    private String errores;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @PrePersist
    public void prePersist() {
        this.fechaCreacion = LocalDateTime.now();
    }
}
