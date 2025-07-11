package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.CuentasTransacciones;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CuentasTransaccionesRepository extends JpaRepository<CuentasTransacciones, Integer> {
    Optional<CuentasTransacciones> findByNombre(String nombre);
}