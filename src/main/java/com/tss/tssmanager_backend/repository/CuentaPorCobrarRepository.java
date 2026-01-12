package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.CuentaPorCobrar;
import com.tss.tssmanager_backend.enums.EstatusPagoEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
    @Query("SELECT c FROM CuentaPorCobrar c WHERE c.fechaPago < :hoy AND c.estatus = :estatus")
    List<CuentaPorCobrar> findByFechaPagoBeforeAndEstatus(
            @Param("hoy") LocalDate hoy,
            @Param("estatus") EstatusPagoEnum estatus
    );
    List<CuentaPorCobrar> findByFechaPagoAndEstatusNot(LocalDate fechaPago, EstatusPagoEnum estatus);
    List<CuentaPorCobrar> findByEstatus(EstatusPagoEnum estatus);
    List<CuentaPorCobrar> findByFechaPagoAndEstatusIn(LocalDate fechaPago, List<EstatusPagoEnum> estatus);
    List<CuentaPorCobrar> findByEstatusIn(List<EstatusPagoEnum> estatus);

    @Query("SELECT new com.tss.tssmanager_backend.dto.BalanceResumenDTO$EquipoVendidoDTO(" +
            "c.cliente.nombre, c.fechaRealPago, " +
            "COALESCE(SUM(CASE WHEN u.unidad = 'Equipos' THEN u.cantidad ELSE 0 END), 0)) " +
            "FROM CuentaPorCobrar c " +
            "JOIN c.cotizacion cot " +
            "JOIN cot.unidades u " +
            "WHERE c.estatus = 'PAGADO' " +
            "AND u.unidad = 'Equipos' " +
            "AND c.id = (SELECT MIN(sub.id) FROM CuentaPorCobrar sub WHERE sub.cotizacion.id = cot.id) " +
            "AND EXISTS (" +
            "   SELECT 1 FROM Transaccion t " +
            "   WHERE t.fechaPago = c.fechaRealPago " +
            "   AND t.cuenta.nombre = c.cliente.nombre " +
            "   AND t.categoria.id = 1 " +
            "   AND t.tipo = 'INGRESO' " +
            "   AND t.monto = c.montoPagado " +
            ") " +
            "AND (:anio IS NULL OR YEAR(c.fechaRealPago) = :anio) " +
            "AND (:mes IS NULL OR MONTH(c.fechaRealPago) = :mes) " +
            "GROUP BY c.cliente.nombre, c.fechaRealPago " +
            "HAVING SUM(CASE WHEN u.unidad = 'Equipos' THEN u.cantidad ELSE 0 END) > 0 " +
            "ORDER BY c.fechaRealPago ASC")
    List<com.tss.tssmanager_backend.dto.BalanceResumenDTO.EquipoVendidoDTO> findEquiposVendidosReporte(
            @org.springframework.data.repository.query.Param("anio") Integer anio,
            @org.springframework.data.repository.query.Param("mes") Integer mes
    );

    @Query("""
        SELECT c FROM CuentaPorCobrar c 
        JOIN FETCH c.cliente 
        JOIN FETCH c.cotizacion 
        LEFT JOIN FETCH c.solicitudesFacturasNotas
        WHERE (:estatus IS NULL OR c.estatus = :estatus)
        ORDER BY c.fechaPago ASC
        """)
    List<CuentaPorCobrar> findByEstatusWithRelations(@Param("estatus") EstatusPagoEnum estatus);

    @Query("SELECT c.estatus FROM CuentaPorCobrar c " +
            "JOIN c.solicitudesFacturasNotas s " +
            "WHERE s.id = :solicitudId")
    Optional<EstatusPagoEnum> findEstatusBySolicitudId(@Param("solicitudId") Integer solicitudId);
}
