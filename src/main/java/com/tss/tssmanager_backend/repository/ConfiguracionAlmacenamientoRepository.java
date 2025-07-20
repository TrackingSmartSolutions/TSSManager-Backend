package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.ConfiguracionAlmacenamiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConfiguracionAlmacenamientoRepository extends JpaRepository<ConfiguracionAlmacenamiento, Integer> {

    Optional<ConfiguracionAlmacenamiento> findByTablaNombre(String tablaNombre);

    List<ConfiguracionAlmacenamiento> findByHabilitadoLimpiezaTrue();

    @Query("SELECT ca FROM ConfiguracionAlmacenamiento ca WHERE ca.habilitadoLimpieza = true ORDER BY ca.tablaNombre")
    List<ConfiguracionAlmacenamiento> findAllEnabledOrderByTableName();
}