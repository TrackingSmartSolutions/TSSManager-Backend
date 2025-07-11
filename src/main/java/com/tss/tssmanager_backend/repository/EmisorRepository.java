package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Emisor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmisorRepository extends JpaRepository<Emisor, Integer> {
}