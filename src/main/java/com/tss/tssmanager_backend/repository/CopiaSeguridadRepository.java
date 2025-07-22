package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.CopiasSeguridad;
import com.tss.tssmanager_backend.enums.TipoCopiaSeguridadEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CopiaSeguridadRepository extends JpaRepository<CopiasSeguridad, Integer> {

    List<CopiasSeguridad> findByUsuarioIdOrderByFechaCreacionDesc(Integer usuarioId);

    List<CopiasSeguridad> findByUsuarioIdAndTipoDatos(Integer usuarioId, TipoCopiaSeguridadEnum tipoDatos);

    @Query("SELECT c FROM CopiasSeguridad c WHERE c.fechaEliminacion <= :fecha")
    List<CopiasSeguridad> findCopiasParaEliminar(@Param("fecha") LocalDateTime fecha);

    @Query("SELECT c FROM CopiasSeguridad c WHERE c.usuarioId = :usuarioId AND c.fechaEliminacion > :fecha ORDER BY c.fechaCreacion DESC")
    List<CopiasSeguridad> findCopiasActivasByUsuario(@Param("usuarioId") Integer usuarioId, @Param("fecha") LocalDateTime fecha);

    void deleteByFechaEliminacionBefore(LocalDateTime fecha);
}

