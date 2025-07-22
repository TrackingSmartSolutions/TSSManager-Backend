package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.TipoCopiaSeguridadEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "\"Configuracion_Copias\"")
public class ConfiguracionCopias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "datos_respaldar", columnDefinition = "tipo_copia_seguridad_enum[]")
    private TipoCopiaSeguridadEnum[] datosRespaldar;

    @Column(name = "frecuencia", nullable = false)
    private String frecuencia = "SEMANAL";

    @Column(name = "hora_respaldo", nullable = false)
    private LocalTime horaRespaldo = LocalTime.of(2, 0);

    @Column(name = "google_drive_email")
    private String googleDriveEmail;

    @Column(name = "google_drive_vinculada")
    private Boolean googleDriveVinculada = false;

    @Column(name = "google_drive_token", columnDefinition = "TEXT")
    private String googleDriveToken;

    @Column(name = "google_drive_refresh_token", columnDefinition = "TEXT")
    private String googleDriveRefreshToken;

    @Column(name = "google_drive_folder_id", length = 255)
    private String googleDriveFolderId;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        fechaCreacion = now;
        fechaActualizacion = now;
    }

    @PreUpdate
    protected void onUpdate() {
        fechaActualizacion = LocalDateTime.now();
    }
}
