package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.HistorialSaldosSim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HistorialSaldosSimRepository extends JpaRepository<HistorialSaldosSim, Integer> {
    @Modifying
    @Query("DELETE FROM HistorialSaldosSim h WHERE h.sim.id = :simId")
    void deleteBySimId(Integer simId);

    List<HistorialSaldosSim> findBySimId(Integer simId);
}