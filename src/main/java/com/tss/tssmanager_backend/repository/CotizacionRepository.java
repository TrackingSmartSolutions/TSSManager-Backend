package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Cotizacion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CotizacionRepository extends JpaRepository<Cotizacion, Integer> {
}