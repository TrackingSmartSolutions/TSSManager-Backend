package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Cotizacion;
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
}