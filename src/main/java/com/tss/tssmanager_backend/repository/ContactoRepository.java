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

    @Query("SELECT COUNT(c) FROM Contacto c " +
            "LEFT JOIN c.correos cor " +
            "LEFT JOIN c.telefonos tel " +
            "WHERE c.empresa.id = :empresaId " +
            "AND (:excludeId IS NULL OR c.id != :excludeId) " +
            "AND (" +
            "   (LOWER(c.nombre) = LOWER(:nombre)) " +
            "   OR (cor.correo IN :correos) " +
            "   OR (tel.telefono IN :telefonos) " +
            ")")
    Long countDuplicados(@Param("empresaId") Integer empresaId,
                         @Param("nombre") String nombre,
                         @Param("correos") List<String> correos,
                         @Param("telefonos") List<String> telefonos,
                         @Param("excludeId") Integer excludeId);

}