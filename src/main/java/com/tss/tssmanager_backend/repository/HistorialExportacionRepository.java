package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.HistorialExportacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistorialExportacionRepository extends JpaRepository<HistorialExportacion, Integer> {

    List<HistorialExportacion> findByUsuarioIdOrderByFechaCreacionDesc(Integer usuarioId);

    @Query("SELECT he FROM HistorialExportacion he WHERE he.usuarioId = :usuarioId ORDER BY he.fechaCreacion DESC")
    List<HistorialExportacion> findHistorialByUsuario(@Param("usuarioId") Integer usuarioId);
}
