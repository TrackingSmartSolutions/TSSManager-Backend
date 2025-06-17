package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.NotaTrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotaTratoRepository extends JpaRepository<NotaTrato, Long> {
    List<NotaTrato> findByTratoIdOrderByFechaCreacionDesc(Long tratoId);
}