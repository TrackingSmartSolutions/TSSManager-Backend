package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Trato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}