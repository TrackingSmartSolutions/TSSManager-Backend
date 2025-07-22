package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.TipoCopiaSeguridadEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "\"Copias_Seguridad\"")
public class CopiasSeguridad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_datos", nullable = false, columnDefinition = "tipo_copia_seguridad_enum")
    private TipoCopiaSeguridadEnum tipoDatos;

    @Column(name = "fecha_creacion", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime fechaCreacion;

    @Column(name = "archivo_pdf_url", nullable = false, length = 255)
    private String archivoPdfUrl;

    @Column(name = "archivo_csv_url", nullable = false, length = 255)
    private String archivoCsvUrl;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "frecuencia", length = 20)
    private String frecuencia = "MANUAL";

    @Column(name = "hora_programada")
    private LocalTime horaProgramada;

    @Column(name = "fecha_eliminacion", columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime fechaEliminacion;

    @Column(name = "estado", length = 20)
    private String estado = "COMPLETADA";

    @Column(name = "tamano_archivo", length = 20)
    private String tamanoArchivo;

    @Column(name = "google_drive_folder_id", length = 255)
    private String googleDriveFolderId;

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
        if (fechaEliminacion == null) {
            fechaEliminacion = LocalDateTime.now().plusMonths(3);
        }
    }
}
