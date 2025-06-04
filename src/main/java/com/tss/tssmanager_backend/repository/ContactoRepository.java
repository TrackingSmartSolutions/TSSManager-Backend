package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Contacto;
import com.tss.tssmanager_backend.enums.RolContactoEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactoRepository extends JpaRepository<Contacto, Integer> {
    long countByEmpresaId(Integer empresaId);
    List<Contacto> findByEmpresaId(Integer empresaId);
    List<Contacto> findByEmpresaIdAndRol(Integer empresaId, RolContactoEnum rol);
}