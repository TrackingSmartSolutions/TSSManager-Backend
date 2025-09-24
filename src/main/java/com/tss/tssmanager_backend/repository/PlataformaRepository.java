package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Plataforma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlataformaRepository extends JpaRepository<Plataforma, Integer> {

    Optional<Plataforma> findByNombrePlataformaIgnoreCase(String nombrePlataforma);

    @Query("SELECT COUNT(e) > 0 FROM Equipo e WHERE e.plataforma.id = :plataformaId")
    boolean existsEquiposByPlataformaId(@Param("plataformaId") Integer plataformaId);
}