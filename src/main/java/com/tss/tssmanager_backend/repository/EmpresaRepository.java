package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Empresa;
import com.tss.tssmanager_backend.enums.EstatusEmpresaEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface EmpresaRepository extends JpaRepository<Empresa, Integer> {
    List<Empresa> findByNombreContainingIgnoreCase(String nombre);
    List<Empresa> findByEstatus(EstatusEmpresaEnum estatus);
    List<Empresa> findByNombreContainingIgnoreCaseAndEstatus(String nombre, EstatusEmpresaEnum estatus);

    List<Empresa> findByPropietario_Id(Integer propietarioId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Empresa e WHERE e.propietario.id = :propietarioId")
    void deleteByPropietario_Id(@Param("propietarioId") Integer propietarioId);

    Optional<Empresa> findByNombreAndPropietario_Id(String nombre, Integer propietarioId);
}