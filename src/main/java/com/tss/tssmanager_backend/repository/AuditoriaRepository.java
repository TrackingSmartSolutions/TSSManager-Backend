package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Auditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditoriaRepository extends JpaRepository<Auditoria, Integer> {
    List<Auditoria> findByFechaBetween(Instant fechaInicio, Instant fechaFin);
}