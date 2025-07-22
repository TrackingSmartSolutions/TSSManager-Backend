package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Contacto;
import com.tss.tssmanager_backend.enums.RolContactoEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ContactoRepository extends JpaRepository<Contacto, Integer> {
    long countByEmpresaId(Integer empresaId);
    List<Contacto> findByEmpresaId(Integer empresaId);
    List<Contacto> findByEmpresaIdAndRol(Integer empresaId, RolContactoEnum rol);



    List<Contacto> findByPropietario_Id(Integer propietarioId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Contacto c WHERE c.propietario.id = :propietarioId")
    void deleteByPropietario_Id(@Param("propietarioId") Integer propietarioId);

    Optional<Contacto> findByNombreAndPropietario_Id(String nombre, Integer propietarioId);
}