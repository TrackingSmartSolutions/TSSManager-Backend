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

    @Query("SELECT s.numero FROM Sim s WHERE s.grupo = :grupo AND s.principal = 'SI'")
    Optional<String> findNumeroPrincipalByGrupo(@Param("grupo") Integer grupo);

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

    @Query(value = """
    SELECT s.id, s.numero, s.tarifa, s.vigencia, s.recarga, s.responsable, 
           s.principal, s.grupo, s.equipo_imei, s.contrasena, 
           e.imei as equipo_imei, e.nombre as equipo_nombre
    FROM "SIMs" s 
    LEFT JOIN "Equipos" e ON s.equipo_imei = e.imei 
    ORDER BY s.id DESC
    LIMIT ?1 OFFSET ?2
    """, nativeQuery = true)
    List<Object[]> findSimsPaginatedOptimized(int limit, int offset);

    @Query(value = "SELECT COUNT(*) FROM \"SIMs\"", nativeQuery = true)
    Long countAllSims();


    @Query(value = """
    SELECT DISTINCT s.grupo 
    FROM "SIMs" s 
    WHERE s.grupo IS NOT NULL 
    ORDER BY s.grupo
    """, nativeQuery = true)
    List<Integer> findAllGroupsForFilter();

    @Query(value = """
SELECT s.id, s.numero, s.tarifa, s.vigencia, s.recarga, s.responsable, 
       s.principal, s.grupo, s.equipo_imei, s.contrasena, 
       e.imei as equipo_imei, e.nombre as equipo_nombre
FROM "SIMs" s 
LEFT JOIN "Equipos" e ON s.equipo_imei = e.imei 
WHERE (:grupo IS NULL OR s.grupo = :grupo)
AND (:numero IS NULL OR LOWER(s.numero) LIKE LOWER(CONCAT('%', :numero, '%')))
ORDER BY 
    CASE WHEN s.vigencia IS NULL THEN 1 ELSE 0 END,
    s.vigencia ASC,
    s.numero ASC
""", nativeQuery = true)
    List<Object[]> findSimsPaginatedWithFilters(@Param("grupo") Integer grupo,
                                                @Param("numero") String numero);

    @Query(value = """
    SELECT COUNT(*)
    FROM "SIMs" s 
    WHERE (:grupo IS NULL OR s.grupo = :grupo)
    AND (:numero IS NULL OR LOWER(s.numero) LIKE LOWER(CONCAT('%', :numero, '%')))
    """, nativeQuery = true)
    Long countSimsWithFilters(@Param("grupo") Integer grupo, @Param("numero") String numero);

    @Query(value = """
SELECT s.id, s.numero, s.tarifa, s.vigencia, s.recarga, s.responsable, 
       s.principal, s.grupo, s.equipo_imei, s.contrasena,
       e.imei as equipo_imei, e.nombre as equipo_nombre
FROM "SIMs" s 
LEFT JOIN "Equipos" e ON s.equipo_imei = e.imei 
WHERE (:grupo IS NULL OR s.grupo = :grupo)
AND (:numero IS NULL OR s.numero ILIKE CONCAT('%', :numero, '%'))
ORDER BY 
    CASE WHEN s.vigencia IS NULL THEN 1 ELSE 0 END,
    s.vigencia ASC,
    s.id DESC
LIMIT 100
""", nativeQuery = true)
    List<Object[]> findSimsOptimizedWithLimit(@Param("grupo") Integer grupo,
                                              @Param("numero") String numero);
}