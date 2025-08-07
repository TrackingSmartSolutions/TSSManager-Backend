package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Actividad;
import com.tss.tssmanager_backend.entity.Trato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TratoRepository extends JpaRepository<Trato, Integer> {
    List<Trato> findByPropietarioIdAndFechaCreacionBetween(Integer propietarioId, Instant startDate, Instant endDate);
    List<Trato> findByEmpresaId(Integer empresaId);
    List<Trato> findByFechaCreacionBetween(Instant startDate, Instant endDate);
    boolean existsByEmpresaId(Integer empresaId);
    List<Trato> findByEmpresaIdAndFechaCreacionBetween(Integer empresaId, Instant start, Instant end);
    List<Trato> findByEmpresaIdAndPropietarioIdAndFechaCreacionBetween(Integer empresaId, Integer propietarioId, Instant start, Instant end);
    List<Trato> findAllById(Iterable<Integer> ids);

    @Query("SELECT t FROM Trato t LEFT JOIN FETCH t.contacto c LEFT JOIN FETCH c.telefonos WHERE t.id = :id")
    Optional<Trato> findTratoWithContactoAndTelefonos(@Param("id") Integer id);
    @Query("SELECT t FROM Trato t JOIN FETCH t.contacto WHERE t.id = :id")
    Optional<Trato> findTratoWithContacto(Integer id);
    @Query("SELECT t.propietarioId, COUNT(t) FROM Trato t WHERE t.fechaCreacion BETWEEN ?1 AND ?2 GROUP BY t.propietarioId")
    List<Object[]> countTratosByPropietario(Instant startDate, Instant endDate);
    @Query("SELECT t.fase, COUNT(t) FROM Trato t WHERE t.propietarioId = :propietarioId AND t.fechaCreacion BETWEEN :startDate AND :endDate GROUP BY t.fase")
    List<Object[]> countTratosByFaseAndPropietario(@Param("propietarioId") Integer propietarioId, @Param("startDate") Instant startDate, @Param("endDate") Instant endDate);
    @Query("SELECT t.fase, COUNT(t) FROM Trato t WHERE t.fechaCreacion BETWEEN :startDate AND :endDate GROUP BY t.fase")
    List<Object[]> countTratosByFase(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("SELECT DISTINCT t FROM Trato t LEFT JOIN Actividad a ON t.id = a.tratoId " +
            "WHERE (t.propietarioId = :userId OR a.asignadoAId = :userId) " +
            "AND t.fechaCreacion BETWEEN :startDate AND :endDate")
    List<Trato> findByPropietarioIdOrAsignadoIdAndFechaCreacionBetween(
            @Param("userId") Integer userId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    // Método para encontrar tratos con correos de seguimiento activos
    List<Trato> findByCorreosSeguimientoActivoTrueAndFaseIn(List<String> fases);

    // Método para encontrar tratos que necesitan correos de seguimiento
    @Query("SELECT t FROM Trato t WHERE t.correosSeguimientoActivo = true " +
            "AND t.fase IN :fases " +
            "AND t.fechaActivacionSeguimiento IS NOT NULL")
    List<Trato> findTratosConSeguimientoActivo(@Param("fases") List<String> fases);

    List<Trato> findByPropietarioId(Integer propietarioId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Trato t WHERE t.propietarioId = :propietarioId")
    void deleteByPropietarioId(@Param("propietarioId") Integer propietarioId);

    @Query(value = """
        SELECT 
            t.id,
            t.nombre,
            t.empresa_id,
            t.numero_unidades,
            t.ingresos_esperados,
            t.descripcion,
            t.propietario_id,
            t.fecha_cierre,
            t.no_trato,
            t.probabilidad,
            t.fase,
            t.correos_automaticos_activos,
            t.fecha_creacion,
            t.fecha_modificacion,
            t.fecha_ultima_actividad,
            u.nombre as propietario_nombre,
            e.nombre as empresa_nombre,
            c.id as contacto_id,
            c.nombre as contacto_nombre,
            -- Contar actividades abiertas
            (SELECT COUNT(*) FROM "Actividades" a WHERE a.trato_id = t.id AND a.estatus = 'ABIERTA') as actividades_abiertas_count,
            -- Verificar si tiene actividades
            (SELECT COUNT(*) > 0 FROM "Actividades" a WHERE a.trato_id = t.id) as has_activities
        FROM "Tratos" t
        LEFT JOIN "Usuarios" u ON t.propietario_id = u.id
        LEFT JOIN "Empresas" e ON t.empresa_id = e.id
        LEFT JOIN "Contactos" c ON t.contacto_id = c.id
        WHERE (:empresaId IS NULL OR t.empresa_id = :empresaId)
        AND (:propietarioId IS NULL OR t.propietario_id = :propietarioId)
        AND t.fecha_creacion BETWEEN :startDate AND :endDate
        ORDER BY t.fecha_modificacion DESC
        """, nativeQuery = true)
    List<Object[]> findTratosOptimized(
            @Param("empresaId") Integer empresaId,
            @Param("propietarioId") Integer propietarioId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    // Query para empleados con actividades asignadas
    @Query(value = """
        SELECT DISTINCT
            t.id,
            t.nombre,
            t.empresa_id,
            t.numero_unidades,
            t.ingresos_esperados,
            t.descripcion,
            t.propietario_id,
            t.fecha_cierre,
            t.no_trato,
            t.probabilidad,
            t.fase,
            t.correos_automaticos_activos,
            t.fecha_creacion,
            t.fecha_modificacion,
            t.fecha_ultima_actividad,
            u.nombre as propietario_nombre,
            e.nombre as empresa_nombre,
            c.id as contacto_id,
            c.nombre as contacto_nombre,
            (SELECT COUNT(*) FROM "Actividades" a WHERE a.trato_id = t.id AND a.estatus = 'ABIERTA') as actividades_abiertas_count,
            (SELECT COUNT(*) > 0 FROM "Actividades" a WHERE a.trato_id = t.id) as has_activities
        FROM "Tratos" t
        LEFT JOIN "Usuarios" u ON t.propietario_id = u.id
        LEFT JOIN "Empresas" e ON t.empresa_id = e.id
        LEFT JOIN "Contactos" c ON t.contacto_id = c.id
        LEFT JOIN "Actividades" a ON t.id = a.trato_id
        WHERE (t.propietario_id = :usuarioId OR a.asignado_a_id = :usuarioId)
        AND t.fecha_creacion BETWEEN :startDate AND :endDate
        ORDER BY t.fecha_modificacion DESC
        """, nativeQuery = true)
    List<Object[]> findTratosForEmpleado(
            @Param("usuarioId") Integer usuarioId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    // Método para cargar actividades por lotes
    @Query("SELECT a FROM Actividad a WHERE a.tratoId IN :tratoIds ORDER BY a.tratoId, a.fechaLimite ASC")
    List<Actividad> findActivitiesByTratoIds(@Param("tratoIds") List<Integer> tratoIds);

    @Query(value = """
    SELECT 
        t.id,
        t.nombre,
        t.propietario_id,
        t.fecha_cierre,
        t.no_trato,
        t.ingresos_esperados,
        t.fase,
        t.fecha_ultima_actividad,
        t.fecha_creacion,
        t.fecha_modificacion,  -- AGREGAR ESTE CAMPO TAMBIÉN
        u.nombre as propietario_nombre,
        e.nombre as empresa_nombre,
        t.contacto_id,
        COALESCE((SELECT COUNT(*) FROM "Actividades" a WHERE a.trato_id = t.id), 0) as actividades_count,
        COALESCE((SELECT COUNT(*) FROM "Actividades" a WHERE a.trato_id = t.id AND a.estatus = 'ABIERTA'), 0) as actividades_abiertas_count,
        (SELECT a.tipo FROM "Actividades" a WHERE a.trato_id = t.id AND a.estatus = 'ABIERTA' ORDER BY a.fecha_limite ASC LIMIT 1) as proxima_actividad_tipo,
        (SELECT a.fecha_limite FROM "Actividades" a WHERE a.trato_id = t.id AND a.estatus = 'ABIERTA' ORDER BY a.fecha_limite ASC LIMIT 1) as proxima_actividad_fecha
    FROM "Tratos" t
    LEFT JOIN "Usuarios" u ON t.propietario_id = u.id
    LEFT JOIN "Empresas" e ON t.empresa_id = e.id
    WHERE (:empresaId IS NULL OR t.empresa_id = :empresaId)
    AND (:propietarioId IS NULL OR t.propietario_id = :propietarioId)
    AND t.fecha_creacion BETWEEN :startDate AND :endDate
    ORDER BY t.fecha_modificacion DESC
    """, nativeQuery = true)
    List<Object[]> findTratosBasico(
            @Param("empresaId") Integer empresaId,
            @Param("propietarioId") Integer propietarioId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query(value = """
    SELECT DISTINCT
        t.id,
        t.nombre,
        t.propietario_id,
        t.fecha_cierre,
        t.no_trato,
        t.ingresos_esperados,
        t.fase,
        t.fecha_ultima_actividad,
        t.fecha_creacion,
        t.fecha_modificacion,  -- AGREGAR ESTE CAMPO
        u.nombre as propietario_nombre,
        e.nombre as empresa_nombre,
        t.contacto_id,
        COALESCE((SELECT COUNT(*) FROM "Actividades" a WHERE a.trato_id = t.id), 0) as actividades_count,
        COALESCE((SELECT COUNT(*) FROM "Actividades" a WHERE a.trato_id = t.id AND a.estatus = 'ABIERTA'), 0) as actividades_abiertas_count,
        (SELECT a.tipo FROM "Actividades" a WHERE a.trato_id = t.id AND a.estatus = 'ABIERTA' ORDER BY a.fecha_limite ASC LIMIT 1) as proxima_actividad_tipo,
        (SELECT a.fecha_limite FROM "Actividades" a WHERE a.trato_id = t.id AND a.estatus = 'ABIERTA' ORDER BY a.fecha_limite ASC LIMIT 1) as proxima_actividad_fecha
    FROM "Tratos" t
    LEFT JOIN "Usuarios" u ON t.propietario_id = u.id
    LEFT JOIN "Empresas" e ON t.empresa_id = e.id
    LEFT JOIN "Actividades" act ON t.id = act.trato_id
    WHERE (t.propietario_id = :usuarioId OR act.asignado_a_id = :usuarioId)
    AND t.fecha_creacion BETWEEN :startDate AND :endDate
    ORDER BY t.fecha_modificacion DESC
    """, nativeQuery = true)
    List<Object[]> findTratosBasicoForEmpleado(
            @Param("usuarioId") Integer usuarioId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query(value = """
    SELECT 
        t.id, t.nombre, t.empresa_id, t.numero_unidades, t.ingresos_esperados,
        t.descripcion, t.propietario_id, t.fecha_cierre, t.no_trato, t.probabilidad,
        t.fase, t.correos_automaticos_activos, t.fecha_creacion, t.fecha_modificacion, 
        t.fecha_ultima_actividad,
        u.nombre as propietario_nombre,
        e.nombre as empresa_nombre,
        c.id as contacto_id, c.nombre as contacto_nombre, c.celular as contacto_celular,
        -- Actividades
        a.id as actividad_id, a.trato_id, a.tipo, a.subtipo_tarea, a.asignado_a_id,
        a.fecha_limite, a.hora_inicio, a.duracion, a.modalidad, a.lugar_reunion,
        a.medio, a.enlace_reunion, a.finalidad, a.estatus, a.fecha_completado,
        a.usuario_completado_id, a.respuesta, a.interes, a.informacion,
        a.siguiente_accion, a.notas as actividad_notas, a.fecha_creacion as actividad_fecha_creacion,
        a.fecha_modificacion as actividad_fecha_modificacion, a.contacto_id as actividad_contacto_id,
        ua.nombre as asignado_nombre,
        -- Notas
        n.id as nota_id, n.trato_id as nota_trato_id, n.usuario_id as nota_usuario_id,
        n.nota, n.fecha_creacion as nota_fecha_creacion, n.editado_por, n.fecha_edicion,
        un.nombre as nota_autor_nombre, ue.nombre as nota_editor_nombre
    FROM "Tratos" t
    LEFT JOIN "Usuarios" u ON t.propietario_id = u.id
    LEFT JOIN "Empresas" e ON t.empresa_id = e.id
    LEFT JOIN "Contactos" c ON t.contacto_id = c.id
    LEFT JOIN "Actividades" a ON t.id = a.trato_id
    LEFT JOIN "Usuarios" ua ON a.asignado_a_id = ua.id
    LEFT JOIN "Notas_Tratos" n ON t.id = n.trato_id
    LEFT JOIN "Usuarios" un ON n.usuario_id = un.id
    LEFT JOIN "Usuarios" ue ON n.editado_por = ue.id
    WHERE t.id = :tratoId
    ORDER BY a.fecha_limite ASC, n.fecha_creacion DESC
    """, nativeQuery = true)
    List<Object[]> findTratoCompleteById(@Param("tratoId") Integer tratoId);

    @Query(value = """
    SELECT 
        t.id, t.nombre, t.empresa_id, t.numero_unidades, t.ingresos_esperados,
        t.descripcion, t.propietario_id, t.fecha_cierre, t.no_trato, t.probabilidad,
        t.fase, t.correos_automaticos_activos, t.fecha_creacion, t.fecha_modificacion, 
        t.fecha_ultima_actividad,
        u.nombre as propietario_nombre,
        e.nombre as empresa_nombre, e.domicilio_fisico, e.sitio_web, e.sector,
        c.id as contacto_id, c.nombre as contacto_nombre, c.celular as contacto_celular,
        -- Primer teléfono del contacto
        (SELECT tel.telefono FROM "Telefonos_Contactos" tel WHERE tel.contacto_id = c.id LIMIT 1) as contacto_telefono,
        -- Primer correo del contacto
        (SELECT cor.correo FROM "Correos_Contactos" cor WHERE cor.contacto_id = c.id LIMIT 1) as contacto_correo,
        -- Actividades
        a.id as actividad_id, a.trato_id, a.tipo, a.subtipo_tarea, a.asignado_a_id,
        a.fecha_limite, a.hora_inicio, a.duracion, a.modalidad, a.lugar_reunion,
        a.medio, a.enlace_reunion, a.finalidad, a.estatus, a.fecha_completado,
        a.usuario_completado_id, a.respuesta, a.interes, a.informacion,
        a.siguiente_accion, a.notas as actividad_notas, a.fecha_creacion as actividad_fecha_creacion,
        a.fecha_modificacion as actividad_fecha_modificacion, a.contacto_id as actividad_contacto_id,
        ua.nombre as asignado_nombre,
        -- Notas
        n.id as nota_id, n.trato_id as nota_trato_id, n.usuario_id as nota_usuario_id,
        n.nota, n.fecha_creacion as nota_fecha_creacion, n.editado_por, n.fecha_edicion,
        un.nombre as nota_autor_nombre, ue.nombre as nota_editor_nombre
    FROM "Tratos" t
    LEFT JOIN "Usuarios" u ON t.propietario_id = u.id
    LEFT JOIN "Empresas" e ON t.empresa_id = e.id
    LEFT JOIN "Contactos" c ON t.contacto_id = c.id
    LEFT JOIN "Actividades" a ON t.id = a.trato_id
    LEFT JOIN "Usuarios" ua ON a.asignado_a_id = ua.id
    LEFT JOIN "Notas_Tratos" n ON t.id = n.trato_id
    LEFT JOIN "Usuarios" un ON n.usuario_id = un.id
    LEFT JOIN "Usuarios" ue ON n.editado_por = ue.id
    WHERE t.id = :tratoId
    ORDER BY a.fecha_limite ASC, n.fecha_creacion DESC
    """, nativeQuery = true)
    List<Object[]> findTratoCompleteByIdWithAllData(@Param("tratoId") Integer tratoId);

    @Query(value = """
    SELECT DISTINCT 
        t.id,
        t.nombre,
        t.empresa_id,
        t.numero_unidades,
        t.ingresos_esperados,
        t.descripcion,
        t.propietario_id,
        t.fecha_cierre,
        t.no_trato,
        t.probabilidad,
        t.fase,
        t.correos_automaticos_activos,
        t.fecha_creacion,
        t.fecha_modificacion,
        t.fecha_ultima_actividad,
        u.nombre as propietario_nombre,
        e.nombre as empresa_nombre,
        c.id as contacto_id,
        c.nombre as contacto_nombre,
        CASE WHEN act_count.total > 0 THEN act_count.total ELSE 0 END as actividades_count,
        CASE WHEN act_count.total > 0 THEN true ELSE false END as has_activities
    FROM "Tratos" t
    LEFT JOIN "Usuarios" u ON t.propietario_id = u.id
    LEFT JOIN "Empresas" e ON t.empresa_id = e.id
    LEFT JOIN "Contactos" c ON t.contacto_id = c.id
    LEFT JOIN (
        SELECT trato_id, COUNT(*) as total
        FROM "Actividades"
        GROUP BY trato_id
    ) act_count ON t.id = act_count.trato_id
    WHERE t.empresa_id = :empresaId
      AND (t.fecha_creacion BETWEEN :startDate AND :endDate)
      AND (
          t.propietario_id = :usuarioId 
          OR EXISTS (
              SELECT 1 FROM "Actividades" a 
              WHERE a.trato_id = t.id 
              AND a.asignado_a_id = :usuarioId
          )
      )
    ORDER BY t.fecha_cierre ASC
""", nativeQuery = true)
    List<Object[]> findTratosForEmpleadoByEmpresa(
            @Param("usuarioId") Integer usuarioId,
            @Param("empresaId") Integer empresaId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query(value = """
    SELECT DISTINCT
        t.id,
        t.nombre,
        t.propietario_id,
        t.fecha_cierre,
        t.no_trato,
        t.ingresos_esperados,
        t.fase,
        t.fecha_ultima_actividad,
        t.fecha_creacion,
        t.fecha_modificacion,
        u.nombre as propietario_nombre,
        e.nombre as empresa_nombre,
        c.id as contacto_id,
        COALESCE(act_count.total, 0) as actividades_count,
        COALESCE(act_abiertas.total, 0) as actividades_abiertas_count,
        prox_act.tipo as proxima_actividad_tipo,
        prox_act.fecha_limite as proxima_actividad_fecha
    FROM "Tratos" t
    LEFT JOIN "Usuarios" u ON t.propietario_id = u.id
    LEFT JOIN "Empresas" e ON t.empresa_id = e.id
    LEFT JOIN "Contactos" c ON t.contacto_id = c.id
    LEFT JOIN (
        SELECT trato_id, COUNT(*) as total
        FROM "Actividades"
        GROUP BY trato_id
    ) act_count ON t.id = act_count.trato_id
    LEFT JOIN (
        SELECT trato_id, COUNT(*) as total
        FROM "Actividades"
        WHERE estatus = 'ABIERTA'
        GROUP BY trato_id
    ) act_abiertas ON t.id = act_abiertas.trato_id
    LEFT JOIN (
        SELECT DISTINCT ON (trato_id) trato_id, tipo, fecha_limite
        FROM "Actividades"
        WHERE estatus = 'ABIERTA'
        ORDER BY trato_id, fecha_limite ASC
    ) prox_act ON t.id = prox_act.trato_id
    WHERE t.empresa_id = :empresaId
      AND (t.fecha_creacion BETWEEN :startDate AND :endDate)
      AND (
          t.propietario_id = :usuarioId 
          OR EXISTS (
              SELECT 1 FROM "Actividades" a 
              WHERE a.trato_id = t.id 
              AND a.asignado_a_id = :usuarioId
          )
      )
    ORDER BY t.fecha_cierre ASC
""", nativeQuery = true)
    List<Object[]> findTratosBasicoForEmpleadoByEmpresa(
            @Param("usuarioId") Integer usuarioId,
            @Param("empresaId") Integer empresaId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );
}