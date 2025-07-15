package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.CuentaPorCobrar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CuentaPorCobrarRepository extends JpaRepository<CuentaPorCobrar, Integer> {
    boolean existsByFolio(String folio);
    boolean existsByCotizacionId(Integer cotizacionId);
    @Query("SELECT c FROM CuentaPorCobrar c WHERE c.fechaPago BETWEEN :start AND :end")
    List<CuentaPorCobrar> findByFechaPagoBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}