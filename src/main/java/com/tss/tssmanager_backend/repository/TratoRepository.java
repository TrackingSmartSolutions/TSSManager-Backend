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

    @Query("SELECT t FROM Trato t LEFT JOIN FETCH t.contacto c LEFT JOIN FETCH c.telefonos WHERE t.id = :id")
    Optional<Trato> findTratoWithContactoAndTelefonos(@Param("id") Integer id);
    @Query("SELECT t FROM Trato t JOIN FETCH t.contacto WHERE t.id = :id")
    Optional<Trato> findTratoWithContacto(Integer id);
}