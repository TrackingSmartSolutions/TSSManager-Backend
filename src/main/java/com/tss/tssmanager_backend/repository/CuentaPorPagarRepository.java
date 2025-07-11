package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.CuentaPorPagar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuentaPorPagarRepository extends JpaRepository<CuentaPorPagar, Integer> {
    boolean existsByTransaccionIdAndEstatusNot(Integer transaccionId, String estatus);
}