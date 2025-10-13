package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.PlantillaCorreo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlantillaCorreoRepository extends JpaRepository<PlantillaCorreo, Integer> {
    @EntityGraph(attributePaths = {"adjuntos"})
    List<PlantillaCorreo> findAll();
    @Query("SELECT p FROM PlantillaCorreo p LEFT JOIN FETCH p.adjuntos WHERE p.id = :id")
    Optional<PlantillaCorreo> findByIdWithAdjuntos(@Param("id") Integer id);
}