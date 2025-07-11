package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransaccionRepository extends JpaRepository<Transaccion, Integer> {
}
