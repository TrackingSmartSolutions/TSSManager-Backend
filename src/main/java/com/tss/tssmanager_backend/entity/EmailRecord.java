package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Entity
@Data
@Table(name = "\"Email_records\"")
public class EmailRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String destinatario;

    @Column(nullable = false)
    private String asunto;

    @Column(columnDefinition = "TEXT")
    private String cuerpo;

    @Column(name = "archivos_adjuntos")
    private String archivosAdjuntos;

    @Column(name = "fecha_envio", nullable = false)
    private ZonedDateTime fechaEnvio;

    @Column(name = "trato_id")
    private Integer tratoId;

    @Column(nullable = false)
    private boolean exito;

    @Column(name = "resend_email_id")
    private String resendEmailId;

}