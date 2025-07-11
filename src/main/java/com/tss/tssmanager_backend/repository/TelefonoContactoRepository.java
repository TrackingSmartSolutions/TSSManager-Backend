package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.TelefonoContacto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TelefonoContactoRepository extends JpaRepository<TelefonoContacto, Integer> {
    @Modifying
    @Query("DELETE FROM TelefonoContacto t WHERE t.contacto.id = :contactoId")
    void deleteByContactoId(@Param("contactoId") Integer contactoId);
}