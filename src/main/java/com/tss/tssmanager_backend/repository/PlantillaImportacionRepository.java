package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.PlantillaImportacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlantillaImportacionRepository extends JpaRepository<PlantillaImportacion, Integer> {

    Optional<PlantillaImportacion> findByTipoDatosAndActivoTrue(String tipoDatos);

    boolean existsByTipoDatosAndActivoTrue(String tipoDatos);

    List<PlantillaImportacion> findByActivoTrue();

    // Método para ordenar por tipo de datos
    List<PlantillaImportacion> findByActivoTrueOrderByTipoDatos();
}

