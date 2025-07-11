package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Equipo;
import com.tss.tssmanager_backend.enums.EstatusEquipoEnum;
import com.tss.tssmanager_backend.enums.TipoEquipoEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface EquipoRepository extends JpaRepository<Equipo, Integer> {
}