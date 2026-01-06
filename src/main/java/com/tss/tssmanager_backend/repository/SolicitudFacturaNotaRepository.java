package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.SolicitudFacturaNota;
import com.tss.tssmanager_backend.enums.EstatusPagoEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SolicitudFacturaNotaRepository extends JpaRepository<SolicitudFacturaNota, Integer> {
    List<SolicitudFacturaNota> findByCuentaPorCobrar_EstatusNot(EstatusPagoEnum estatus);
    Optional<SolicitudFacturaNota> findByIdentificador(String identificador);

    @Query("""
    SELECT DISTINCT s FROM SolicitudFacturaNota s 
    LEFT JOIN FETCH s.cuentaPorCobrar cpc
    LEFT JOIN FETCH cpc.cliente
    LEFT JOIN FETCH s.emisor 
    LEFT JOIN FETCH s.cliente 
    LEFT JOIN FETCH s.cotizacion
    ORDER BY s.fechaEmision DESC
    """)
    List<SolicitudFacturaNota> findAllWithRelations();
}