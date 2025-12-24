package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Cotizacion;
import com.tss.tssmanager_backend.entity.Trato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CotizacionRepository extends JpaRepository<Cotizacion, Integer> {
    @Query("SELECT c FROM Cotizacion c ORDER BY c.fechaCreacion DESC")
    List<Cotizacion> findAllOrderByFechaCreacionDesc();

    @Query("SELECT c FROM Cotizacion c LEFT JOIN FETCH c.unidades WHERE c.id = :id")
    Optional<Cotizacion> findByIdWithUnidades(@Param("id") Integer id);

    List<Cotizacion> findByTratoIdOrderByFechaCreacionDesc(Integer tratoId);

    @Query("SELECT c FROM Cotizacion c WHERE c.cliente.id = :clienteId AND c.tratoId IS NULL ORDER BY c.fechaCreacion DESC")
    List<Cotizacion> findByClienteIdAndTratoIdIsNull(@Param("clienteId") Integer clienteId);

    @Query("SELECT DISTINCT t FROM Trato t WHERE t.empresaId = :empresaId AND t.fase NOT IN ('CERRADO_PERDIDO') AND t.fase IN ('COTIZACION_PROPUESTA_PRACTICA', 'NEGOCIACION_REVISION', 'CERRADO_GANADO')")
    List<Trato> findTratosDisponiblesParaCotizacion(@Param("empresaId") Integer empresaId);
}