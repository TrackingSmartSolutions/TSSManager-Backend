package com.tss.tssmanager_backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "coordenadas_cache", indexes = {
        @Index(name = "idx_coordenadas_hash", columnList = "direccionHash"),
        @Index(name = "idx_coordenadas_original", columnList = "direccionOriginal")
})
public class CoordenadasCache {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "direccion_hash", unique = true, nullable = false, length = 64)
    private String direccionHash;

    @Column(name = "direccion_original", nullable = false, columnDefinition = "TEXT")
    private String direccionOriginal;

    @Column(name = "lat", nullable = false, precision = 10, scale = 8)
    private BigDecimal lat;

    @Column(name = "lng", nullable = false, precision = 11, scale = 8)
    private BigDecimal lng;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public CoordenadasCache() {}

    public CoordenadasCache(String direccionHash, String direccionOriginal, BigDecimal lat, BigDecimal lng) {
        this.direccionHash = direccionHash;
        this.direccionOriginal = direccionOriginal;
        this.lat = lat;
        this.lng = lng;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}