package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Factura;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacturaRepository extends JpaRepository<Factura, Integer> {
}