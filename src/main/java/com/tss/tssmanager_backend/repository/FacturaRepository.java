package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Factura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FacturaRepository extends JpaRepository<Factura, Integer> {
    Optional<Factura> findByNoSolicitud(String noSolicitud);
}