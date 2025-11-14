package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Notificacion;
import com.tss.tssmanager_backend.enums.EstatusNotificacionEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {
    List<Notificacion> findByUsuarioIdAndEstatus(Integer usuarioId, EstatusNotificacionEnum estatus);
    List<Notificacion> findByUsuarioId(Integer usuarioId);
    // Ordenar notificaciones por fecha de creación descendente (más recientes primero)
    List<Notificacion> findByUsuarioIdOrderByFechaCreacionDesc(Integer usuarioId);
    // Contar notificaciones no leídas
    Integer countByUsuarioIdAndEstatus(Integer usuarioId, EstatusNotificacionEnum estatus);
    // Verificar si existe una notificación reciente similar (para evitar duplicados)
    boolean existsByUsuarioIdAndTipoNotificacionAndMensajeAndFechaCreacionAfter(
            Integer usuarioId,
            String tipoNotificacion,
            String mensaje,
            Instant fechaCreacion
    );
    // Buscar notificaciones recientes para un usuario y tipo específico
    List<Notificacion> findByUsuarioIdAndTipoNotificacionAndFechaCreacionAfter(
            Integer usuarioId,
            String tipoNotificacion,
            Instant fechaCreacion
    );
    // Buscar todas las notificaciones no leídas de un usuario
    List<Notificacion> findByUsuarioIdAndEstatusOrderByFechaCreacionDesc(
            Integer usuarioId,
            EstatusNotificacionEnum estatus
    );

    List<Notificacion> findByEstatusAndFechaLeidaBefore(EstatusNotificacionEnum estatus, Instant fecha);
    @Modifying
    @Query("DELETE FROM Notificacion n WHERE n.estatus = :estatus AND n.fechaLeida < :fecha")
    int deleteByEstatusAndFechaLeidaBefore(@Param("estatus") EstatusNotificacionEnum estatus, @Param("fecha") Instant fecha);

}