package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.CoordenadasCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CoordenadasCacheRepository extends JpaRepository<CoordenadasCache, Long> {

    Optional<CoordenadasCache> findByDireccionHash(String direccionHash);

    @Query(value = """
        SELECT cc.* FROM coordenadas_cache cc 
        WHERE cc.direccion_hash IN :hashes
        """, nativeQuery = true)
    List<CoordenadasCache> findByDireccionHashIn(@Param("hashes") List<String> hashes);

    @Query(value = """
    SELECT DISTINCT e.domicilio_fisico 
    FROM "Empresas" e 
    LEFT JOIN coordenadas_cache cc ON cc.direccion_hash = encode(sha256(lower(trim(e.domicilio_fisico))::bytea), 'hex')
    WHERE e.domicilio_fisico IS NOT NULL 
    AND e.domicilio_fisico != '' 
    AND cc.id IS NULL
    """, nativeQuery = true)
    List<String> findDireccionesNoGeocodificadas();

}