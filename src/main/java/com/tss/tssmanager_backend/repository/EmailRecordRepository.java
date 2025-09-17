package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.EmailRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;

public interface EmailRecordRepository extends JpaRepository<EmailRecord, Integer> {
    List<EmailRecord> findByTratoId(Integer tratoId);
    List<EmailRecord> findByDestinatarioContainingAndAsuntoContainingAndFechaEnvioAfterAndExitoTrue(
            String destinatario, String asunto, ZonedDateTime fechaEnvio);

    List<EmailRecord> findByDestinatarioContainingAndCuerpoContainingAndFechaEnvioAfterAndExitoTrue(
            String destinatario, String cuerpo, ZonedDateTime fechaEnvio);
}