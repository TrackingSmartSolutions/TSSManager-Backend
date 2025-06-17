package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Actividad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActividadRepository extends JpaRepository<Actividad, Integer> {
    List<Actividad> findByTratoIdAndEstatus(Integer tratoId, String estatus);
    List<Actividad> findByAsignadoAId(Integer asignadoAId);
    List<Actividad> findByTratoId(Integer tratoId);
}