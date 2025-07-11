package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Actividad;
import com.tss.tssmanager_backend.enums.EstatusActividadEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}