package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.ModeloEquipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ModeloEquipoRepository extends JpaRepository<ModeloEquipo, Integer> {

    Page<ModeloEquipo> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);
    @Query("SELECT m.id, m.nombre, m.uso, m.imagenUrl FROM ModeloEquipo m ORDER BY m.nombre")
    List<Object[]> findAllBasicInfo();

    @Query("SELECT m FROM ModeloEquipo m WHERE LOWER(m.nombre) LIKE LOWER(CONCAT('%', :nombre, '%')) ORDER BY m.nombre")
    List<ModeloEquipo> findByNombreContainingIgnoreCase(@Param("nombre") String nombre);

    @Query("SELECT m FROM ModeloEquipo m WHERE LOWER(m.nombre) LIKE LOWER(CONCAT('%', :nombre, '%')) ORDER BY m.nombre")
    Page<ModeloEquipo> findByNombreContainingIgnoreCasePaginated(@Param("nombre") String nombre, Pageable pageable);

    @Query(value = "SELECT m.id as modelo_id, COALESCE(COUNT(e.id), 0) as cantidad " +
            "FROM \"Modelos_Equipos\" m " +
            "LEFT JOIN \"Equipos\" e ON m.id = e.modelo_id " +
            "GROUP BY m.id", nativeQuery = true)
    List<Object[]> countEquiposByModelo();
}