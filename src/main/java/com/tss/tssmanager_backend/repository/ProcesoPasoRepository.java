package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.ProcesoPaso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcesoPasoRepository extends JpaRepository<ProcesoPaso, Integer> {
}
