package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.SectorDTO;
import com.tss.tssmanager_backend.entity.Sector;
import com.tss.tssmanager_backend.exception.ResourceNotFoundException;
import com.tss.tssmanager_backend.repository.SectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SectorService {

    private static final Logger logger = LoggerFactory.getLogger(SectorService.class);

    @Autowired
    private SectorRepository sectorRepository;

    @Transactional
    public SectorDTO crearSector(SectorDTO sectorDTO) {
        logger.info("Creando nuevo sector: {}", sectorDTO.getNombreSector());

        // Validar que el nombre no esté vacío
        if (sectorDTO.getNombreSector() == null || sectorDTO.getNombreSector().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del sector es obligatorio");
        }

        String nombreLimpio = sectorDTO.getNombreSector().trim();

        // Verificar que no exista un sector con el mismo nombre
        if (sectorRepository.existsByNombreSectorIgnoreCase(nombreLimpio)) {
            throw new IllegalArgumentException("El Nombre Sector ya está registrado. Por favor, ingrese un nombre diferente.");
        }

        Sector sector = new Sector();
        sector.setNombreSector(nombreLimpio);

        String usuarioLogueado = getUsuarioLogueadoName();
        sector.setCreadoPor(usuarioLogueado);
        sector.setModificadoPor(usuarioLogueado);
        sector.setFechaCreacion(Instant.now());
        sector.setFechaModificacion(Instant.now());

        Sector savedSector = sectorRepository.save(sector);
        logger.info("Sector creado exitosamente con ID: {}", savedSector.getId());

        return convertToDTO(savedSector);
    }

    @Transactional
    public SectorDTO actualizarSector(Integer id, SectorDTO sectorDTO) {
        logger.info("Actualizando sector con ID: {}", id);

        Sector sector = sectorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sector no encontrado con id: " + id));

        // Validar que el nombre no esté vacío
        if (sectorDTO.getNombreSector() == null || sectorDTO.getNombreSector().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del sector es obligatorio");
        }

        String nombreLimpio = sectorDTO.getNombreSector().trim();

        // Verificar que no exista otro sector con el mismo nombre (excluyendo el actual)
        if (!sector.getNombreSector().equalsIgnoreCase(nombreLimpio) &&
                sectorRepository.existsByNombreSectorIgnoreCase(nombreLimpio)) {
            throw new IllegalArgumentException("El Nombre Sector ya está registrado. Por favor, ingrese un nombre diferente.");
        }

        sector.setNombreSector(nombreLimpio);
        sector.setModificadoPor(getUsuarioLogueadoName());
        sector.setFechaModificacion(Instant.now());

        Sector savedSector = sectorRepository.save(sector);
        logger.info("Sector con ID: {} actualizado exitosamente", id);

        return convertToDTO(savedSector);
    }

    @Transactional
    public void eliminarSector(Integer id) {
        logger.info("Eliminando sector con ID: {}", id);

        Sector sector = sectorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sector no encontrado con id: " + id));

        // Verificar si el sector está asociado a empresas
        if (sectorRepository.existsAssociatedEmpresas(id)) {
            long countEmpresas = sectorRepository.countAssociatedEmpresas(id);
            logger.error("No se puede eliminar el sector con ID: {} porque está vinculado a {} empresas", id, countEmpresas);
            throw new IllegalStateException("No se puede eliminar el sector porque está vinculado a una o más empresas.");
        }

        sectorRepository.delete(sector);
        logger.info("Sector con ID: {} eliminado exitosamente", id);
    }

    @Transactional(readOnly = true)
    public List<SectorDTO> listarSectores() {
        logger.info("Listando todos los sectores");
        List<Sector> sectores = sectorRepository.findAll();
        return sectores.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SectorDTO obtenerSectorPorId(Integer id) {
        logger.info("Obteniendo sector con ID: {}", id);
        Sector sector = sectorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sector no encontrado con id: " + id));
        return convertToDTO(sector);
    }

    public boolean checkAssociations(Integer id) {
        return sectorRepository.existsAssociatedEmpresas(id);
    }

    private SectorDTO convertToDTO(Sector sector) {
        SectorDTO dto = new SectorDTO();
        dto.setId(sector.getId());
        dto.setNombreSector(sector.getNombreSector());
        dto.setCreadoPor(sector.getCreadoPor());
        dto.setModificadoPor(sector.getModificadoPor());
        dto.setFechaCreacion(sector.getFechaCreacion());
        dto.setFechaModificacion(sector.getFechaModificacion());
        return dto;
    }

    private String getUsuarioLogueadoName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            logger.warn("No se encontró usuario autenticado en el contexto de seguridad");
            return "sistema";
        }
        return auth.getName();
    }
}