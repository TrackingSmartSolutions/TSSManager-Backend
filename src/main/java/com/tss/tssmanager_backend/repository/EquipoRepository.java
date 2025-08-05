package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Equipo;
import com.tss.tssmanager_backend.enums.EstatusEquipoEnum;
import com.tss.tssmanager_backend.enums.TipoEquipoEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EquipoRepository extends JpaRepository<Equipo, Integer> {

    @Query("SELECT e FROM Equipo e WHERE e.imei = :imei")
    Optional<Equipo> findByImei(@Param("imei") String imei);

    // Método para buscar equipos por cliente ID
    @Query("SELECT e FROM Equipo e WHERE e.clienteId = :clienteId")
    List<Equipo> findByClienteId(@Param("clienteId") Long clienteId);

    // Método para buscar equipos por tipo y estatus
    @Query("SELECT e FROM Equipo e WHERE e.tipo = :tipo AND e.estatus = :estatus")
    List<Equipo> findByTipoAndEstatus(@Param("tipo") String tipo, @Param("estatus") String estatus);

}