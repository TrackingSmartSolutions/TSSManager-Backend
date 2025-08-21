package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Cotizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CotizacionRepository extends JpaRepository<Cotizacion, Integer> {
    @Query("SELECT c FROM Cotizacion c ORDER BY c.fechaCreacion DESC")
    List<Cotizacion> findAllOrderByFechaCreacionDesc();
}