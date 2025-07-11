package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.EmailRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailRecordRepository extends JpaRepository<EmailRecord, Integer> {
    List<EmailRecord> findByTratoId(Integer tratoId);
}