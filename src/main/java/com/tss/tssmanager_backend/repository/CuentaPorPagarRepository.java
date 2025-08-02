package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.CuentaPorPagar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CuentaPorPagarRepository extends JpaRepository<CuentaPorPagar, Integer> {
    boolean existsByTransaccionIdAndEstatusNot(Integer transaccionId, String estatus);
    @Query("SELECT c FROM CuentaPorPagar c WHERE c.fechaPago BETWEEN :start AND :end")
    List<CuentaPorPagar> findByFechaPagoBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
    List<CuentaPorPagar> findByTransaccionId(Integer transaccionId);
}