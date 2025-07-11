package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.CorreoContacto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CorreoContactoRepository extends JpaRepository<CorreoContacto, Integer> {
    @Modifying
    @Query("DELETE FROM CorreoContacto c WHERE c.contacto.id = :contactoId")
    void deleteByContactoId(@Param("contactoId") Integer contactoId);

}