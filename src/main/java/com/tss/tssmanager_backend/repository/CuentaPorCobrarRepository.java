package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.CuentaPorCobrar;
import com.tss.tssmanager_backend.enums.EstatusPagoEnum;
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
    @Query("SELECT c FROM CuentaPorCobrar c WHERE c.fechaPago BETWEEN :start AND :end AND c.estatus != :estatus")
    List<CuentaPorCobrar> findByFechaPagoBetweenAndEstatusNot(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("estatus") EstatusPagoEnum estatus);

    @Query("""
        SELECT c FROM CuentaPorCobrar c 
        JOIN FETCH c.cliente 
        JOIN FETCH c.cotizacion cot
        LEFT JOIN FETCH c.solicitudesFacturasNotas
        ORDER BY c.fechaPago DESC
        """)
    List<CuentaPorCobrar> findAllWithRelations();

    @Query("SELECT c.id FROM CuentaPorCobrar c WHERE c.comprobantePagoUrl IS NOT NULL OR SIZE(c.solicitudesFacturasNotas) > 0")
    List<Integer> findAllVinculatedIds();
}