package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Sim;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(value = """
    SELECT s.*, e.imei as equipo_imei, e.nombre as equipo_nombre, e.tipo as equipo_tipo 
    FROM "SIMs" s 
    LEFT JOIN "Equipos" e ON s.equipo_imei = e.imei 
    ORDER BY s.id DESC
    """, nativeQuery = true)
    List<Object[]> findAllWithEquipoNative();

    @Query("SELECT s FROM Sim s LEFT JOIN FETCH s.equipo ORDER BY s.id DESC")
    Page<Sim> findAllWithEquipoPaged(Pageable pageable);

    @Query(value = """
    SELECT DISTINCT s.grupo 
    FROM "SIMs" s 
    WHERE s.grupo IS NOT NULL 
    AND s.grupo != 99 
    AND s.grupo != 0
    ORDER BY s.grupo
    """, nativeQuery = true)
    List<Integer> findAllGroupsOptimized();

    @Query(value = "SELECT COUNT(*) FROM \"SIMs\" WHERE grupo = ?1", nativeQuery = true)
    Long countSimsByGrupoNative(Integer grupo);

    @Query(value = "SELECT COUNT(*) FROM \"SIMs\" WHERE grupo = ?1 AND principal = 'SI'", nativeQuery = true)
    Long countPrincipalesByGrupoNative(Integer grupo);

    @Query(value = "SELECT COUNT(*) FROM \"SIMs\" WHERE grupo = ?1 AND principal = 'NO'", nativeQuery = true)
    Long countNonPrincipalesByGrupoNative(Integer grupo);

    @Query(value = "SELECT * FROM \"SIMs\" WHERE LOWER(numero) = LOWER(?1)", nativeQuery = true)
    Optional<Sim> findByNumeroOptimized(String numero);
}