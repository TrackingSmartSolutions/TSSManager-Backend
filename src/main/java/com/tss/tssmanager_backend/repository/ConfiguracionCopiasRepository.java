package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.ConfiguracionCopias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfiguracionCopiasRepository extends JpaRepository<ConfiguracionCopias, Integer> {

    Optional<ConfiguracionCopias> findByUsuarioId(Integer usuarioId);

    List<ConfiguracionCopias> findByGoogleDriveVinculadaTrue();

    List<ConfiguracionCopias> findByFrecuencia(String frecuencia);
}