package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.EmailDestinarioEstado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDestinarioEstadoRepository extends JpaRepository<EmailDestinarioEstado, Integer> {
    EmailDestinarioEstado findByEmailRecordResendEmailIdAndEmail(String resendEmailId, String email);
}