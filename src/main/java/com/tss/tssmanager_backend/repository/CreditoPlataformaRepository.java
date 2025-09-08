package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.CreditoPlataforma;
import com.tss.tssmanager_backend.enums.PlataformaEquipoEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CreditoPlataformaRepository extends JpaRepository<CreditoPlataforma, Integer> {

    @Query("SELECT c FROM CreditoPlataforma c WHERE c.fecha BETWEEN :fechaInicio AND :fechaFin ORDER BY c.fecha DESC")
    List<CreditoPlataforma> findByFechaBetween(@Param("fechaInicio") LocalDateTime fechaInicio,
                                               @Param("fechaFin") LocalDateTime fechaFin);

    @Query("SELECT c FROM CreditoPlataforma c WHERE c.plataforma = :plataforma AND c.fecha BETWEEN :fechaInicio AND :fechaFin ORDER BY c.fecha DESC")
    List<CreditoPlataforma> findByPlataformaAndFechaBetween(@Param("plataforma") PlataformaEquipoEnum plataforma,
                                                            @Param("fechaInicio") LocalDateTime fechaInicio,
                                                            @Param("fechaFin") LocalDateTime fechaFin);

    @Query("SELECT c FROM CreditoPlataforma c ORDER BY c.fecha DESC")
    List<CreditoPlataforma> findAllOrderByFechaDesc();

    @Query("SELECT c FROM CreditoPlataforma c WHERE c.plataforma = :plataforma ORDER BY c.fecha DESC")
    List<CreditoPlataforma> findByPlataformaOrderByFechaDesc(@Param("plataforma") PlataformaEquipoEnum plataforma);

    @Query("SELECT c.plataforma, " +
            "SUM(CASE WHEN c.tipo = 'ABONO' THEN c.monto ELSE 0 END) - " +
            "SUM(CASE WHEN c.tipo = 'CARGO' THEN c.monto ELSE 0 END) as saldo " +
            "FROM CreditoPlataforma c " +
            "WHERE c.plataforma IN ('TRACK_SOLID', 'WHATSGPS') " +
            "GROUP BY c.plataforma")
    List<Object[]> getSaldosPorPlataforma();

    @Query("SELECT DATE(c.fecha) as fecha, c.plataforma, c.subtipo, " +
            "SUM(CASE WHEN c.tipo = 'ABONO' THEN c.monto ELSE 0 END) - " +
            "SUM(CASE WHEN c.tipo = 'CARGO' THEN c.monto ELSE 0 END) as saldoDiario " +
            "FROM CreditoPlataforma c " +
            "WHERE c.plataforma IN ('TRACK_SOLID', 'WHATSGPS') " +
            "AND c.fecha BETWEEN :fechaInicio AND :fechaFin " +
            "GROUP BY DATE(c.fecha), c.plataforma, c.subtipo " +
            "ORDER BY DATE(c.fecha) ASC")
    List<Object[]> getHistorialSaldos(@Param("fechaInicio") LocalDateTime fechaInicio,
                                      @Param("fechaFin") LocalDateTime fechaFin);

    // En CreditoPlataformaRepository.java agregar:
    @Query("SELECT c.plataforma, c.subtipo, " +
            "SUM(CASE WHEN c.tipo = 'ABONO' THEN c.monto ELSE -c.monto END) " +
            "FROM CreditoPlataforma c " +
            "WHERE c.plataforma = 'WHATSGPS' AND c.subtipo IS NOT NULL " +
            "GROUP BY c.plataforma, c.subtipo")
    List<Object[]> getSaldosPorPlataformaYSubtipo();
}