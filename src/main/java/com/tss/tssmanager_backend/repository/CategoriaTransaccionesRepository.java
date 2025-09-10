package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.CategoriaTransacciones;
import com.tss.tssmanager_backend.enums.TipoTransaccionEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriaTransaccionesRepository extends JpaRepository<CategoriaTransacciones, Integer> {
    Optional<CategoriaTransacciones> findByDescripcion(String descripcion);
    Optional<CategoriaTransacciones> findByDescripcionIgnoreCase(String descripcion);
    List<CategoriaTransacciones> findByTipo(TipoTransaccionEnum tipo);
}