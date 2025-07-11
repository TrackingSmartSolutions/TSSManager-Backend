package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.CuentaPorCobrar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuentaPorCobrarRepository extends JpaRepository<CuentaPorCobrar, Integer> {
    boolean existsByFolio(String folio);
    boolean existsByCotizacionId(Integer cotizacionId);
}