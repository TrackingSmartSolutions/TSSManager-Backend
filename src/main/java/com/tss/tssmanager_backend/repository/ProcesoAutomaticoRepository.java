package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.ProcesoAutomatico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcesoAutomaticoRepository extends JpaRepository<ProcesoAutomatico, Integer> {
    @Query("SELECT p FROM ProcesoAutomatico p LEFT JOIN FETCH p.pasos ps LEFT JOIN FETCH ps.plantilla WHERE p.id = :id")
    Optional<ProcesoAutomatico> findByIdWithPasos(@Param("id") Integer id);

    @Query("SELECT COUNT(ps) > 0 FROM ProcesoPaso ps WHERE ps.plantilla.id = :plantillaId")
    boolean existePasoConPlantilla(@Param("plantillaId") Integer plantillaId);

    @Query("SELECT DISTINCT p FROM ProcesoAutomatico p LEFT JOIN FETCH p.pasos ps LEFT JOIN FETCH ps.plantilla")
    List<ProcesoAutomatico> findAllWithPasos();
}
