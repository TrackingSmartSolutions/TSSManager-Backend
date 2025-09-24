package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Sector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SectorRepository extends JpaRepository<Sector, Integer> {

    boolean existsByNombreSectorIgnoreCase(String nombreSector);

    Optional<Sector> findByNombreSectorIgnoreCase(String nombreSector);

    @Query("SELECT COUNT(e) > 0 FROM Empresa e WHERE e.sector.id = :sectorId")
    boolean existsAssociatedEmpresas(@Param("sectorId") Integer sectorId);

    @Query("SELECT COUNT(e) FROM Empresa e WHERE e.sector.id = :sectorId")
    long countAssociatedEmpresas(@Param("sectorId") Integer sectorId);

}