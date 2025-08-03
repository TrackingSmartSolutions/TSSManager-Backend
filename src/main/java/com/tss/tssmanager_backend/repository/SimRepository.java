package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Sim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SimRepository extends JpaRepository<Sim, Integer> {
    @Query("SELECT s FROM Sim s WHERE s.equipo IS NULL AND (s.responsable = 'TSS' OR s.responsable = 'CLIENTE')")
    List<Sim> findAvailableSims();

    @Query("SELECT COUNT(s) FROM Sim s WHERE s.grupo = :grupo")
    Long countSimsByGrupo(Integer grupo);

    @Query("SELECT DISTINCT s.grupo FROM Sim s WHERE s.grupo IS NOT NULL")
    List<Integer> findAllGroups();

    @Query("SELECT COUNT(s) FROM Sim s WHERE s.grupo = :grupo AND s.principal = 'SI'")
    Long countPrincipalesByGrupo(Integer grupo);

    @Query("SELECT COUNT(s) FROM Sim s WHERE s.grupo = :grupo AND s.principal = 'NO'")
    Long countNonPrincipalesByGrupo(Integer grupo);

    @Query("SELECT COUNT(s) FROM Sim s WHERE s.grupo = :grupo AND s.principal = 'NO' AND s.id != :excludeId")
    Long countNonPrincipalesByGrupoExcluding(@Param("grupo") Integer grupo, @Param("excludeId") Integer excludeId);

    @Query("SELECT s FROM Sim s WHERE s.equipo.imei = :imei")
    Optional<Sim> findByEquipoImei(@Param("imei") String imei);

    @Query("SELECT COUNT(s) FROM Sim s WHERE s.equipo.imei = :imei")
    Long countByEquipoImei(@Param("imei") String imei);

    @Query("SELECT s FROM Sim s LEFT JOIN FETCH s.equipo")
    List<Sim> findAllWithEquipo();

    Optional<Sim> findByNumero(String numero);

    @Query("SELECT MAX(s.grupo) FROM Sim s WHERE s.grupo != 99 AND s.grupo != 0")
    Integer findMaxGrupoExcludingClientes();


}