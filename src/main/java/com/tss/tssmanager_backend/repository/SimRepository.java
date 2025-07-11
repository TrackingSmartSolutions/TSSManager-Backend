package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Sim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SimRepository extends JpaRepository<Sim, Integer> {
    @Query("SELECT s FROM Sim s WHERE s.equipo IS NULL AND (s.responsable = 'TSS' OR s.responsable = 'CLIENTE')")
    List<Sim> findAvailableSims();

    @Query("SELECT COUNT(s) FROM Sim s WHERE s.grupo = :grupo")
    Long countSimsByGrupo(Integer grupo);

    @Query("SELECT DISTINCT s.grupo FROM Sim s WHERE s.grupo IS NOT NULL")
    List<Integer> findAllGroups();

    @Query("SELECT MAX(s.grupo) FROM Sim s WHERE s.grupo IS NOT NULL")
    Integer findMaxGrupo();

    @Query("SELECT COUNT(s) FROM Sim s WHERE s.grupo = :grupo AND s.principal = 'SI'")
    Long countPrincipalesByGrupo(Integer grupo);

    @Query("SELECT COUNT(s) FROM Sim s WHERE s.grupo = :grupo AND s.principal = 'NO'")
    Long countNonPrincipalesByGrupo(Integer grupo);
}