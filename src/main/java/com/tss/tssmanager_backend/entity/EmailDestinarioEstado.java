package com.tss.tssmanager_backend.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import java.time.ZonedDateTime;

@Entity
@Data
@Table(name = "\"Email_destinatario_estados\"")
public class EmailDestinarioEstado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "email_record_id", nullable = false)
    @JsonBackReference
    private EmailRecord emailRecord;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String status;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}