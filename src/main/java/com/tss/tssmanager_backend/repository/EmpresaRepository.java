package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Empresa;
import com.tss.tssmanager_backend.enums.EstatusEmpresaEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmpresaRepository extends JpaRepository<Empresa, Integer> {
    List<Empresa> findByNombreContainingIgnoreCase(String nombre);
    List<Empresa> findByEstatus(EstatusEmpresaEnum estatus);
    List<Empresa> findByNombreContainingIgnoreCaseAndEstatus(String nombre, EstatusEmpresaEnum estatus);

}