package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.dto.EquiposEstatusDTO;
import com.tss.tssmanager_backend.entity.EquiposEstatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

public interface EquiposEstatusRepository extends JpaRepository<EquiposEstatus, Integer> {

    @Modifying
    @Query("DELETE FROM EquiposEstatus e WHERE e.fechaCheck = :fechaCheck")
    int deleteByFechaCheck(@Param("fechaCheck") Timestamp fechaCheck);
    List<EquiposEstatus> findAllByOrderByFechaCheckDesc();

    @Query("""
        SELECT new com.tss.tssmanager_backend.dto.EquiposEstatusDTO(
            es.id, 
            es.equipo.id, 
            es.estatus, 
            es.motivo, 
            es.fechaCheck
        ) 
        FROM EquiposEstatus es 
        ORDER BY es.fechaCheck DESC
        """)
    List<EquiposEstatusDTO> findAllEstatusOptimized();

    @Query("""
        SELECT new com.tss.tssmanager_backend.dto.EquiposEstatusDTO(
            es.id, 
            es.equipo.id, 
            es.estatus, 
            es.motivo, 
            es.fechaCheck
        ) 
        FROM EquiposEstatus es 
        ORDER BY es.fechaCheck DESC
        """)
    Page<EquiposEstatusDTO> findAllEstatusOptimizedPaged(Pageable pageable);

    @Query("""
        SELECT new com.tss.tssmanager_backend.dto.EquiposEstatusDTO(
            es.id, 
            es.equipo.id, 
            es.estatus, 
            es.motivo, 
            es.fechaCheck
        ) 
        FROM EquiposEstatus es 
        WHERE es.fechaCheck >= :fechaDesde
        ORDER BY es.fechaCheck DESC
        """)
    List<EquiposEstatusDTO> findRecentEstatusOptimized(@Param("fechaDesde") Timestamp fechaDesde);

    @Query("SELECT MAX(es.fechaCheck) FROM EquiposEstatus es")
    Timestamp findMaxFechaCheck();
}