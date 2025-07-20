package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.HistorialLimpieza;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HistorialLimpiezaRepository extends JpaRepository<HistorialLimpieza, Integer> {

    List<HistorialLimpieza> findByTablaNombreOrderByFechaLimpiezaDesc(String tablaNombre);

    @Query("SELECT hl FROM HistorialLimpieza hl WHERE hl.fechaLimpieza >= :fechaDesde ORDER BY hl.fechaLimpieza DESC")
    List<HistorialLimpieza> findRecentCleaningHistory(@Param("fechaDesde") LocalDateTime fechaDesde);

    @Query("SELECT hl FROM HistorialLimpieza hl WHERE hl.tipoLimpieza = :tipo ORDER BY hl.fechaLimpieza DESC")
    List<HistorialLimpieza> findByTipoLimpieza(@Param("tipo") String tipo);

    @Query("SELECT COUNT(hl) FROM HistorialLimpieza hl WHERE hl.fechaLimpieza >= :fechaDesde")
    Long countRecentCleanups(@Param("fechaDesde") LocalDateTime fechaDesde);

    @Query("SELECT COUNT(hl) FROM HistorialLimpieza hl WHERE hl.tipoLimpieza = :tipo AND hl.fechaLimpieza >= :fechaDesde")
    Long countByTipoLimpiezaAndFechaLimpiezaAfter(@Param("tipo") String tipo, @Param("fechaDesde") LocalDateTime fechaDesde);
}
