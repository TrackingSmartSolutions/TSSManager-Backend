package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Comision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ComisionRepository extends JpaRepository<Comision, Integer> {

    @Query("""
        SELECT c FROM Comision c 
        JOIN FETCH c.cuentaPorCobrar 
        JOIN FETCH c.empresa 
        JOIN FETCH c.trato
        JOIN FETCH c.vendedorCuenta
        JOIN FETCH c.proyectoCuenta
        ORDER BY c.fechaPago DESC
        """)
    List<Comision> findAllWithRelations();

    @Query("""
        SELECT c FROM Comision c 
        WHERE c.vendedorCuenta.id = :cuentaId 
        AND c.estatusVenta = 'PENDIENTE'
        ORDER BY c.fechaCreacion ASC
        """)
    List<Comision> findComisionesVentaPendientesByCuenta(@Param("cuentaId") Integer cuentaId);

    @Query("""
        SELECT c FROM Comision c 
        WHERE c.proyectoCuenta.id = :cuentaId 
        AND c.estatusProyecto = 'PENDIENTE'
        ORDER BY c.fechaCreacion ASC
        """)
    List<Comision> findComisionesProyectoPendientesByCuenta(@Param("cuentaId") Integer cuentaId);

    @Query("""
        SELECT c FROM Comision c 
        WHERE c.vendedorCuenta.id = :cuentaId 
        AND c.proyectoCuenta.id = :cuentaId
        AND (c.estatusVenta = 'PENDIENTE' OR c.estatusProyecto = 'PENDIENTE')
        ORDER BY c.fechaCreacion ASC, 
                 CASE WHEN c.estatusVenta = 'PENDIENTE' THEN 0 ELSE 1 END
        """)
    List<Comision> findComisionesPendientesMismaCuenta(@Param("cuentaId") Integer cuentaId);

    List<Comision> findByCuentaPorCobrarId(Integer cuentaPorCobrarId);

    boolean existsByCuentaPorCobrarId(Integer cuentaPorCobrarId);

    @Query("SELECT SUM(c.saldoPendienteVenta) FROM Comision c WHERE c.estatusVenta = 'PENDIENTE'")
    BigDecimal sumSaldoPendienteVenta();

    @Query("SELECT SUM(c.saldoPendienteProyecto) FROM Comision c WHERE c.estatusProyecto = 'PENDIENTE'")
    BigDecimal sumSaldoPendienteProyecto();

    @Query("""
        SELECT c FROM Comision c 
        WHERE c.empresa.id = :empresaId
        ORDER BY c.fechaPago DESC
        """)
    List<Comision> findByEmpresaId(@Param("empresaId") Integer empresaId);

    @Query("""
        SELECT c FROM Comision c 
        WHERE c.fechaPago BETWEEN :inicio AND :fin
        ORDER BY c.fechaPago DESC
        """)
    List<Comision> findByFechaPagoBetween(
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin
    );

    @Query("SELECT c.cuentaPorCobrar.id FROM Comision c")
    List<Integer> findAllCuentaPorCobrarIds();
}