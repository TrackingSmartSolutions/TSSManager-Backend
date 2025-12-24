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

    @Query(value = """
SELECT 
    e.id,
    e.nombre,
    e.domicilio_fisico,
    s.nombre_sector as sector_nombre, 
    e.estatus,
    e.sitio_web,
    cc.lat,
    cc.lng
FROM "Empresas" e
LEFT JOIN "Sectores" s ON e.sector_id = s.id 
LEFT JOIN coordenadas_cache cc ON cc.direccion_hash = encode(sha256(lower(trim(e.domicilio_fisico))::bytea), 'hex')
WHERE e.domicilio_fisico IS NOT NULL 
AND e.domicilio_fisico != ''
ORDER BY e.nombre
""", nativeQuery = true)
    List<Object[]> findEmpresasConCoordenadas();

    @Query(value = "SELECT e.id FROM \"Empresas\" e WHERE NOT EXISTS (SELECT 1 FROM \"Tratos\" t WHERE t.empresa_id = e.id)", nativeQuery = true)
    List<Integer> findEmpresasSinTratos();

    @Query("SELECT u.nombre, COUNT(e) FROM Empresa e JOIN e.propietario u GROUP BY u.id, u.nombre")
    List<Object[]> contarEmpresasPorPropietario();

    @Query(value = """
    SELECT DISTINCT e.* 
    FROM "Empresas" e
    INNER JOIN "Equipos" eq ON e.id = eq.cliente_id
    WHERE e.estatus IN ('CLIENTE', 'EN_PROCESO')
    ORDER BY e.nombre
    """, nativeQuery = true)
    List<Empresa> findEmpresasConEquipos();
}