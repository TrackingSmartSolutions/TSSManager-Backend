package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.CuentaPorPagar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CuentaPorPagarRepository extends JpaRepository<CuentaPorPagar, Integer> {
    boolean existsByTransaccionIdAndEstatusNot(Integer transaccionId, String estatus);
    @Query("SELECT c FROM CuentaPorPagar c WHERE c.fechaPago BETWEEN :start AND :end")
    List<CuentaPorPagar> findByFechaPagoBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
    List<CuentaPorPagar> findByTransaccionId(Integer transaccionId);
    List<CuentaPorPagar> findAllByOrderByFechaPagoAsc();
    List<CuentaPorPagar> findBySimId(Integer simId);

    @Query("SELECT c FROM CuentaPorPagar c WHERE c.fechaPago BETWEEN :start AND :end AND c.estatus != :estatus")
    List<CuentaPorPagar> findByFechaPagoBetweenAndEstatusNot(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("estatus") String estatus);

    @Query("SELECT c FROM CuentaPorPagar c WHERE c.fechaPago < :hoy AND c.estatus = :estatus")
    List<CuentaPorPagar> findByFechaPagoBeforeAndEstatus(
            @Param("hoy") LocalDate hoy,
            @Param("estatus") String estatus
    );
    List<CuentaPorPagar> findByFechaPagoAndEstatusNot(LocalDate fechaPago, String estatus);
    List<CuentaPorPagar> findByEstatus(String estatus);
    List<CuentaPorPagar> findByFechaPagoAndEstatusIn(LocalDate fechaPago, List<String> estatus);
    List<CuentaPorPagar> findByEstatusIn(List<String> estatus);
    @Query("""
        SELECT c FROM CuentaPorPagar c 
        JOIN FETCH c.transaccion t
        JOIN FETCH t.cuenta cu
        LEFT JOIN FETCH t.categoria cat
        WHERE (:estatus IS NULL OR c.estatus = :estatus)
        ORDER BY c.fechaPago ASC
        """)
    List<CuentaPorPagar> findAllWithRelationsFiltered(@Param("estatus") String estatus);

    @Modifying
    @Query("DELETE FROM CuentaPorPagar c WHERE c.sim.id = :simId AND c.estatus = 'Pendiente'")
    void deleteBySimIdAndEstatusPendiente(@Param("simId") Integer simId);
}
