package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Actividad;
import com.tss.tssmanager_backend.enums.EstatusActividadEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ActividadRepository extends JpaRepository<Actividad, Integer> {
    List<Actividad> findByTratoIdAndEstatus(Integer tratoId, String estatus);
    List<Actividad> findByAsignadoAId(Integer asignadoAId);
    List<Actividad> findByTratoId(Integer tratoId);

    List<Actividad> findByUsuarioCompletadoIdAndFechaCompletadoBetween(Integer userId, Instant start, Instant end);
    @Query("SELECT e.nombre FROM Empresa e WHERE e.id = (SELECT t.empresaId FROM Trato t WHERE t.id = :tratoId)")
    String findEmpresaNameByTratoId(Integer tratoId);

    List<Actividad> findByAsignadoAIdAndFechaLimiteAndEstatus(
            Integer asignadoAId,
            LocalDate fechaLimite,
            EstatusActividadEnum estatus
    );
    @Query("SELECT a FROM Actividad a WHERE a.asignadoAId = :asignadoAId AND a.fechaLimite BETWEEN :start AND :end")
    List<Actividad> findByAsignadoAIdAndFechaLimiteBetween(
            @Param("asignadoAId") Integer asignadoAId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
    List<Actividad> findByFechaLimiteBetween(LocalDate start, LocalDate end);

    List<Actividad> findByFechaLimite(LocalDate fecha);

    List<Actividad> findByFechaLimiteBetweenAndEstatus(LocalDate start, LocalDate end, EstatusActividadEnum estatus);

    @Query("SELECT a FROM Actividad a WHERE a.asignadoAId = :asignadoAId AND a.fechaLimite BETWEEN :start AND :end AND a.estatus != :estatus")
    List<Actividad> findByAsignadoAIdAndFechaLimiteBetweenAndEstatusNot(
            @Param("asignadoAId") Integer asignadoAId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("estatus") EstatusActividadEnum estatus);

    @Query("SELECT a FROM Actividad a WHERE a.fechaLimite BETWEEN :start AND :end AND a.estatus != :estatus")
    List<Actividad> findByFechaLimiteBetweenAndEstatusNot(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("estatus") EstatusActividadEnum estatus);

    @Modifying
    @Transactional
    @Query("UPDATE Actividad a SET a.asignadoAId = :nuevoAsignadoId WHERE a.asignadoAId = :antiguoAsignadoId AND a.estatus = 'ABIERTA'")
    void updateAsignadoAId(@Param("antiguoAsignadoId") Integer antiguoAsignadoId,
                           @Param("nuevoAsignadoId") Integer nuevoAsignadoId);

    Long countByAsignadoAIdAndEstatus(Integer asignadoAId, EstatusActividadEnum estatus);

    @Query(value = """
    SELECT 
        a.id,
        a.trato_id,
        a.tipo,
        a.subtipo_tarea,
        a.asignado_a_id,
        a.fecha_limite,
        a.hora_inicio,
        a.duracion,
        a.modalidad,
        a.lugar_reunion,
        a.medio,
        a.enlace_reunion,
        a.estatus,
        a.contacto_id,
        c.nombre as contacto_nombre,
        e.nombre as empresa_nombre,
        e.id as empresa_id
    FROM "Actividades" a
    LEFT JOIN "Contactos" c ON a.contacto_id = c.id
    LEFT JOIN "Tratos" t ON a.trato_id = t.id
    LEFT JOIN "Empresas" e ON t.empresa_id = e.id
    WHERE a.asignado_a_id = :asignadoAId 
    AND a.fecha_limite = :fecha
    AND a.estatus = 'ABIERTA'
    ORDER BY 
        CASE WHEN a.hora_inicio IS NULL THEN 1 ELSE 0 END,
        a.hora_inicio ASC
    """, nativeQuery = true)
    List<Object[]> findActividadesPendientesConEmpresa(
            @Param("asignadoAId") Integer asignadoAId,
            @Param("fecha") LocalDate fecha
    );
}