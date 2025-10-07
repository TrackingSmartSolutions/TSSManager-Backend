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

    @Query(value = """
    SELECT e.id, e.imei, e.nombre, e.tipo, e.estatus, e.cliente_id, e.cliente_default,
           CASE WHEN s.equipo_imei IS NOT NULL THEN true ELSE false END as sim_referenciada
    FROM "Equipos" e 
    LEFT JOIN "SIMs" s ON e.imei = s.equipo_imei 
    WHERE (e.tipo = 'DEMO' OR e.tipo = 'VENDIDO') 
    AND e.estatus != 'ALMACEN'
    ORDER BY e.nombre
    """, nativeQuery = true)
    List<Object[]> findEquiposForSimSelection();

    @Query(value = """
    WITH empresa_nombres AS (
        SELECT id, nombre 
        FROM "Empresas"
    )
    SELECT 
        COALESCE(en.nombre, e.cliente_default, 'Sin Cliente') as cliente,
        COALESCE(
            CASE 
                WHEN es.estatus = 'REPORTANDO' THEN 'ONLINE'
                WHEN es.estatus = 'NO_REPORTANDO' THEN 'OFFLINE'
                ELSE 'OFFLINE'
            END, 
            'OFFLINE'
        ) as estatus_reporte,
        COALESCE(p.nombre_plataforma, 'Sin Plataforma') as plataforma,
        e.nombre as equipo_nombre,
        e.imei,
        COALESCE(es.motivo, 'Sin reporte de estatus') as motivo,
        es.fecha_check
    FROM "Equipos" e
    LEFT JOIN empresa_nombres en ON e.cliente_id = en.id
    LEFT JOIN plataformas p ON e.plataforma_id = p.id
    LEFT JOIN LATERAL (
        SELECT CAST(estatus AS VARCHAR) as estatus, motivo, fecha_check 
        FROM "Equipos_Estatus" 
        WHERE equipo_id = e.id 
        ORDER BY fecha_check DESC 
        LIMIT 1
    ) es ON true
    WHERE e.tipo IN ('VENDIDO', 'DEMO')
    ORDER BY e.nombre
    """, nativeQuery = true)
    List<Object[]> findDashboardEstatusData();

    @Query("SELECT e FROM Equipo e LEFT JOIN FETCH e.simReferenciada LEFT JOIN FETCH e.plataforma WHERE e.tipo IN ('VENDIDO', 'DEMO') ORDER BY e.nombre")  // Agregado FETCH e.plataforma
    List<Equipo> findEquiposParaCheck();

    @Query("SELECT e FROM Equipo e LEFT JOIN FETCH e.simReferenciada WHERE e IN :equipos")
    List<Equipo> findAllWithSimReferenciada(@Param("equipos") List<Equipo> equipos);

    @Query("SELECT e FROM Equipo e LEFT JOIN FETCH e.simReferenciada LEFT JOIN FETCH e.plataforma ORDER BY e.id")
    List<Equipo> findAllWithSims();

    @Query("SELECT e FROM Equipo e LEFT JOIN FETCH e.simReferenciada LEFT JOIN FETCH e.plataforma ORDER BY CASE WHEN e.fechaExpiracion IS NULL THEN 0 ELSE 1 END, e.fechaExpiracion ASC")
    List<Equipo> findAllWithSimsOrderedByExpiration();

    long countByPlataformaIdAndEstatus(Integer plataformaId, EstatusEquipoEnum estatus);

    @Query("SELECT e FROM Equipo e " +
            "LEFT JOIN FETCH e.plataforma " +
            "WHERE e.fechaExpiracion BETWEEN :start AND :end " +
            "AND e.estatus = 'ACTIVO' " +
            "ORDER BY e.fechaExpiracion ASC")
    List<Equipo> findByFechaExpiracionBetween(
            @Param("start") java.sql.Date start,
            @Param("end") java.sql.Date end
    );

    @Query("""
    SELECT e FROM Equipo e 
    LEFT JOIN FETCH e.plataforma 
    WHERE e.tipo IN ('VENDIDO', 'DEMO') 
    AND e.estatus = 'ACTIVO'
    AND e.fechaExpiracion IS NOT NULL
    AND CAST(e.fechaExpiracion AS date) BETWEEN CURRENT_DATE AND CAST(CURRENT_DATE + 30 DAY AS date)
    ORDER BY e.fechaExpiracion ASC
""")
    List<Equipo> findEquiposProximosAExpirar();
}