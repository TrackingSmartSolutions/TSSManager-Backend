package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.NotificacionPopupMostrada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificacionPopupMostradaRepository extends JpaRepository<NotificacionPopupMostrada, Integer> {
    boolean existsByActividadIdAndUsuarioId(Integer actividadId, Integer usuarioId);

    @Modifying
    @Query("DELETE FROM NotificacionPopupMostrada n WHERE n.actividadId = :actividadId")
    void deleteByActividadId(@Param("actividadId") Integer actividadId);
}