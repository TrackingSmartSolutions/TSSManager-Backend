package com.tss.tssmanager_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tss.tssmanager_backend.entity.Usuario;

import java.time.Instant;
import java.util.List;

@Repository
public interface DashboardMetricasRepository extends JpaRepository<Usuario, Integer> {

    // Consulta para obtener métricas de empresas creadas por usuario
    @Query(value = """
    SELECT 
        u.id as usuario_id,
        u.nombre || ' ' || u.apellidos as nombre_completo,
        COUNT(DISTINCT e.id) as empresas_nuevas,
        COUNT(DISTINCT CASE 
            WHEN EXISTS (
                SELECT 1 FROM "Actividades" a 
                WHERE a.trato_id IN (SELECT t.id FROM "Tratos" t WHERE t.empresa_id = e.id)
                AND a.estatus = 'CERRADA'
                AND a.fecha_completado BETWEEN :startDate AND :endDate
            ) THEN e.id END
        ) as empresas_contactadas,
        COUNT(DISTINCT CASE 
            WHEN EXISTS (
                SELECT 1 FROM "Tratos" t 
                WHERE t.empresa_id = e.id 
                AND t.fase IN ('ENVIO_DE_INFORMACION', 'REUNION', 'COTIZACION_PROPUESTA_PRACTICA', 
                              'NEGOCIACION_REVISION', 'CERRADO_GANADO')
            ) THEN e.id END
        ) as empresas_info_enviada
    FROM "Usuarios" u
    LEFT JOIN "Empresas" e ON e.creado_por = u.nombre_usuario 
        AND e.fecha_creacion BETWEEN :startDate AND :endDate
    WHERE u.estatus = 'ACTIVO'
        AND (:usuarioId IS NULL OR u.id = :usuarioId)
    GROUP BY u.id, u.nombre, u.apellidos
    HAVING COUNT(DISTINCT e.id) > 0 OR :usuarioId IS NOT NULL
    ORDER BY u.nombre
    """, nativeQuery = true)
    List<Object[]> findEmpresasCreadasPorUsuario(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("usuarioId") Integer usuarioId
    );

    // Consulta para obtener métricas de tasa de respuesta por usuario
    @Query(value = """
        SELECT 
            u.id as usuario_id,
            u.nombre || ' ' || u.apellidos as nombre_completo,
            COUNT(CASE WHEN a.tipo = 'LLAMADA' AND a.estatus = 'CERRADA' THEN 1 END) as total_llamadas,
            COUNT(CASE 
                WHEN a.tipo = 'LLAMADA' 
                AND a.estatus = 'CERRADA' 
                AND a.interes IN ('MEDIO', 'ALTO') 
                THEN 1 END
            ) as llamadas_exitosas
        FROM "Usuarios" u
        LEFT JOIN "Actividades" a ON a.asignado_a_id = u.id 
            AND a.fecha_completado BETWEEN :startDate AND :endDate
        WHERE u.estatus = 'ACTIVO'
            AND (:usuarioId IS NULL OR u.id = :usuarioId)
        GROUP BY u.id, u.nombre, u.apellidos
        HAVING COUNT(CASE WHEN a.tipo = 'LLAMADA' AND a.estatus = 'CERRADA' THEN 1 END) > 0 
            OR :usuarioId IS NOT NULL
        ORDER BY u.nombre
        """, nativeQuery = true)
    List<Object[]> findTasaRespuestaPorUsuario(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("usuarioId") Integer usuarioId
    );

    // Consulta para obtener métricas de conversión por usuario
    @Query(value = """
        WITH empresas_contactadas AS (
            SELECT DISTINCT
                u.id as usuario_id,
                u.nombre || ' ' || u.apellidos as nombre_completo,
                t.empresa_id,
                t.id as trato_id
            FROM "Usuarios" u
            INNER JOIN "Actividades" a ON a.asignado_a_id = u.id 
                AND a.estatus = 'CERRADA'
                AND a.fecha_completado BETWEEN :startDate AND :endDate
            INNER JOIN "Tratos" t ON t.id = a.trato_id
            WHERE u.estatus = 'ACTIVO'
                AND (:usuarioId IS NULL OR u.id = :usuarioId)
        ),
        respuesta_positiva AS (
            SELECT DISTINCT
                ec.usuario_id,
                ec.nombre_completo,
                ec.empresa_id,
                ec.trato_id
            FROM empresas_contactadas ec
            INNER JOIN "Actividades" a ON a.trato_id = ec.trato_id
                AND a.asignado_a_id = ec.usuario_id
                AND a.estatus = 'CERRADA'
                AND a.respuesta = 'SI'
                AND a.fecha_completado BETWEEN :startDate AND :endDate
        ),
        interes_medio_alto AS (
            SELECT DISTINCT
                rp.usuario_id,
                rp.nombre_completo,
                rp.empresa_id,
                rp.trato_id
            FROM respuesta_positiva rp
            INNER JOIN "Actividades" a ON a.trato_id = rp.trato_id
                AND a.asignado_a_id = rp.usuario_id
                AND a.estatus = 'CERRADA'
                AND a.interes IN ('MEDIO', 'ALTO')
                AND a.fecha_completado BETWEEN :startDate AND :endDate
        ),
            reuniones_concretadas AS (
                 SELECT DISTINCT
                     ima.usuario_id,
                     ima.nombre_completo,
                     ima.empresa_id,
                     ima.trato_id
                 FROM interes_medio_alto ima
                 WHERE EXISTS (
                     SELECT 1 FROM "Actividades" a
                     WHERE a.trato_id = ima.trato_id
                         AND a.asignado_a_id = ima.usuario_id
                         AND a.estatus = 'CERRADA'
                         AND a.siguiente_accion = 'REUNION'
                         AND a.fecha_completado BETWEEN :startDate AND :endDate
                 )
             )
        SELECT 
            COALESCE(ec.usuario_id, u.id) as usuario_id,
            COALESCE(ec.nombre_completo, u.nombre || ' ' || u.apellidos) as nombre_completo,
            COUNT(DISTINCT ec.empresa_id) as empresas_contactadas,
            COUNT(DISTINCT rp.empresa_id) as respuesta_positiva,
            COUNT(DISTINCT ima.empresa_id) as interes_medio_alto,
            COUNT(DISTINCT rc.empresa_id) as reuniones_concretadas
        FROM "Usuarios" u
        LEFT JOIN empresas_contactadas ec ON ec.usuario_id = u.id
        LEFT JOIN respuesta_positiva rp ON rp.usuario_id = u.id AND rp.empresa_id = ec.empresa_id
        LEFT JOIN interes_medio_alto ima ON ima.usuario_id = u.id AND ima.empresa_id = rp.empresa_id  
        LEFT JOIN reuniones_concretadas rc ON rc.usuario_id = u.id AND rc.empresa_id = ima.empresa_id
        WHERE u.estatus = 'ACTIVO'
            AND (:usuarioId IS NULL OR u.id = :usuarioId)
            AND (ec.usuario_id IS NOT NULL OR :usuarioId IS NOT NULL)
        GROUP BY COALESCE(ec.usuario_id, u.id), COALESCE(ec.nombre_completo, u.nombre || ' ' || u.apellidos)
        ORDER BY nombre_completo
        """, nativeQuery = true)
    List<Object[]> findTasaConversionPorUsuario(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("usuarioId") Integer usuarioId
    );

    // Consulta para obtener resumen ejecutivo
    @Query(value = """
        SELECT 
            -- Total empresas creadas
            (SELECT COUNT(*) FROM "Empresas" WHERE fecha_creacion BETWEEN :startDate AND :endDate) as total_empresas,
            
            -- Promedio de contacto
            CASE 
                WHEN (SELECT COUNT(*) FROM "Empresas" WHERE fecha_creacion BETWEEN :startDate AND :endDate) = 0 
                THEN 0
                ELSE ROUND(
                    (SELECT COUNT(DISTINCT e.id) * 100.0
                     FROM "Empresas" e
                     WHERE e.fecha_creacion BETWEEN :startDate AND :endDate
                     AND EXISTS (
                         SELECT 1 FROM "Actividades" a 
                         INNER JOIN "Tratos" t ON t.id = a.trato_id
                         WHERE t.empresa_id = e.id
                         AND a.estatus = 'CERRADA'
                         AND a.fecha_completado BETWEEN :startDate AND :endDate
                     )) / 
                    (SELECT COUNT(*) FROM "Empresas" WHERE fecha_creacion BETWEEN :startDate AND :endDate), 2)
            END as promedio_contacto,
            
            -- Tasa respuesta global
            CASE 
                WHEN (SELECT COUNT(*) FROM "Actividades" WHERE tipo = 'LLAMADA' AND estatus = 'CERRADA' 
                      AND fecha_completado BETWEEN :startDate AND :endDate) = 0 
                THEN 0
                ELSE ROUND(
                    (SELECT COUNT(*) * 100.0 FROM "Actividades" 
                     WHERE tipo = 'LLAMADA' AND estatus = 'CERRADA' 
                     AND interes IN ('MEDIO', 'ALTO')
                     AND fecha_completado BETWEEN :startDate AND :endDate) /
                    (SELECT COUNT(*) FROM "Actividades" WHERE tipo = 'LLAMADA' AND estatus = 'CERRADA' 
                     AND fecha_completado BETWEEN :startDate AND :endDate), 2)
            END as tasa_respuesta_global,
            
            -- Tasa conversión global  
            CASE 
                WHEN (SELECT COUNT(DISTINCT t.empresa_id) FROM "Tratos" t
                      INNER JOIN "Actividades" a ON a.trato_id = t.id
                      WHERE a.estatus = 'CERRADA' AND a.respuesta = 'SI' AND a.interes IN ('MEDIO', 'ALTO')
                      AND a.fecha_completado BETWEEN :startDate AND :endDate) = 0
                THEN 0
                ELSE ROUND(
                    (SELECT COUNT(DISTINCT t.empresa_id) * 100.0 FROM "Tratos" t
                     INNER JOIN "Actividades" a1 ON a1.trato_id = t.id
                     WHERE a1.estatus = 'CERRADA' AND a1.respuesta = 'SI' AND a1.interes IN ('MEDIO', 'ALTO')
                     AND a1.fecha_completado BETWEEN :startDate AND :endDate
                     AND EXISTS (
                         SELECT 1 FROM "Actividades" a2 
                         WHERE a2.trato_id = t.id 
                         AND a2.tipo = 'REUNION'
                         AND a2.id > a1.id
                         AND EXTRACT(WEEK FROM a1.fecha_completado) = EXTRACT(WEEK FROM a2.fecha_creacion)
                     )) /
                    (SELECT COUNT(DISTINCT t.empresa_id) FROM "Tratos" t
                     INNER JOIN "Actividades" a ON a.trato_id = t.id
                     WHERE a.estatus = 'CERRADA' AND a.respuesta = 'SI' AND a.interes IN ('MEDIO', 'ALTO')
                     AND a.fecha_completado BETWEEN :startDate AND :endDate), 2)
            END as tasa_conversion_global
        """, nativeQuery = true)
    List<Object[]> findResumenEjecutivo(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    // Consulta para obtener resumen ejecutivo del período anterior (para tendencias)
    @Query(value = """
        SELECT 
            -- Total empresas creadas período anterior
            (SELECT COUNT(*) FROM "Empresas" WHERE fecha_creacion BETWEEN :startDatePrev AND :endDatePrev) as total_empresas_prev,
            
            -- Promedio de contacto período anterior
            CASE 
                WHEN (SELECT COUNT(*) FROM "Empresas" WHERE fecha_creacion BETWEEN :startDatePrev AND :endDatePrev) = 0 
                THEN 0
                ELSE ROUND(
                    (SELECT COUNT(DISTINCT e.id) * 100.0
                     FROM "Empresas" e
                     WHERE e.fecha_creacion BETWEEN :startDatePrev AND :endDatePrev
                     AND EXISTS (
                         SELECT 1 FROM "Actividades" a 
                         INNER JOIN "Tratos" t ON t.id = a.trato_id
                         WHERE t.empresa_id = e.id
                         AND a.estatus = 'CERRADA'
                         AND a.fecha_completado BETWEEN :startDatePrev AND :endDatePrev
                     )) / 
                    (SELECT COUNT(*) FROM "Empresas" WHERE fecha_creacion BETWEEN :startDatePrev AND :endDatePrev), 2)
            END as promedio_contacto_prev,
            
            -- Tasa respuesta global período anterior
            CASE 
                WHEN (SELECT COUNT(*) FROM "Actividades" WHERE tipo = 'LLAMADA' AND estatus = 'CERRADA' 
                      AND fecha_completado BETWEEN :startDatePrev AND :endDatePrev) = 0 
                THEN 0
                ELSE ROUND(
                    (SELECT COUNT(*) * 100.0 FROM "Actividades" 
                     WHERE tipo = 'LLAMADA' AND estatus = 'CERRADA' 
                     AND interes IN ('MEDIO', 'ALTO')
                     AND fecha_completado BETWEEN :startDatePrev AND :endDatePrev) /
                    (SELECT COUNT(*) FROM "Actividades" WHERE tipo = 'LLAMADA' AND estatus = 'CERRADA' 
                     AND fecha_completado BETWEEN :startDatePrev AND :endDatePrev), 2)
            END as tasa_respuesta_global_prev,
            
            -- Tasa conversión global período anterior
            CASE 
                WHEN (SELECT COUNT(DISTINCT t.empresa_id) FROM "Tratos" t
                      INNER JOIN "Actividades" a ON a.trato_id = t.id
                      WHERE a.estatus = 'CERRADA' AND a.respuesta = 'SI' AND a.interes IN ('MEDIO', 'ALTO')
                      AND a.fecha_completado BETWEEN :startDatePrev AND :endDatePrev) = 0
                THEN 0
                ELSE ROUND(
                    (SELECT COUNT(DISTINCT t.empresa_id) * 100.0 FROM "Tratos" t
                     INNER JOIN "Actividades" a1 ON a1.trato_id = t.id
                     WHERE a1.estatus = 'CERRADA' AND a1.respuesta = 'SI' AND a1.interes IN ('MEDIO', 'ALTO')
                     AND a1.fecha_completado BETWEEN :startDatePrev AND :endDatePrev
                     AND EXISTS (
                         SELECT 1 FROM "Actividades" a2 
                         WHERE a2.trato_id = t.id 
                         AND a2.tipo = 'REUNION'
                         AND a2.id > a1.id
                         AND EXTRACT(WEEK FROM a1.fecha_completado) = EXTRACT(WEEK FROM a2.fecha_creacion)
                     )) /
                    (SELECT COUNT(DISTINCT t.empresa_id) FROM "Tratos" t
                     INNER JOIN "Actividades" a ON a.trato_id = t.id
                     WHERE a.estatus = 'CERRADA' AND a.respuesta = 'SI' AND a.interes IN ('MEDIO', 'ALTO')
                     AND a.fecha_completado BETWEEN :startDatePrev AND :endDatePrev), 2)
            END as tasa_conversion_global_prev
        """, nativeQuery = true)
    List<Object[]> findResumenEjecutivoPeriodoAnterior(
            @Param("startDatePrev") Instant startDatePrev,
            @Param("endDatePrev") Instant endDatePrev
    );
}