package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.EquiposEstatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Date;
import java.util.List;

public interface EquiposEstatusRepository extends JpaRepository<EquiposEstatus, Integer> {

    @Modifying
    @Query("DELETE FROM EquiposEstatus e WHERE e.fechaCheck = :fechaCheck")
    int deleteByFechaCheck(@Param("fechaCheck") Date fechaCheck);
    List<EquiposEstatus> findAllByOrderByFechaCheckDesc();
}