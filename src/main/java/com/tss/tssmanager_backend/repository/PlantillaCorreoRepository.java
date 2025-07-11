package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.PlantillaCorreo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlantillaCorreoRepository extends JpaRepository<PlantillaCorreo, Integer> {
    @EntityGraph(attributePaths = {"adjuntos"})
    List<PlantillaCorreo> findAll();
}