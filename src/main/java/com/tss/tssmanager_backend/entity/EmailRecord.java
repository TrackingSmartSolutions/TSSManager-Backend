package com.tss.tssmanager_backend.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "status")
    private String status;

    @Column(name = "tipo_correo_consolidado")
    private String tipoCorreoConsolidado;

    @OneToMany(mappedBy = "emailRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<EmailDestinarioEstado> estadosDestinatarios = new ArrayList<>();
}