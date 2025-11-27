package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.NotificacionPopupMostrada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificacionPopupMostradaRepository extends JpaRepository<NotificacionPopupMostrada, Integer> {
    boolean existsByActividadIdAndUsuarioId(Integer actividadId, Integer usuarioId);
}