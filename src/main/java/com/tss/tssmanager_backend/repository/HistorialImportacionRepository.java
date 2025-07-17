package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.HistorialImportacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistorialImportacionRepository extends JpaRepository<HistorialImportacion, Integer> {

    List<HistorialImportacion> findByUsuarioIdOrderByFechaCreacionDesc(Integer usuarioId);
}
