package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.CategoriaTransacciones;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoriaTransaccionesRepository extends JpaRepository<CategoriaTransacciones, Integer> {
    Optional<CategoriaTransacciones> findByDescripcion(String descripcion);
}