package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.ProcesoAutomaticoDTO;
import com.tss.tssmanager_backend.entity.PlantillaCorreo;
import com.tss.tssmanager_backend.entity.ProcesoAutomatico;
import com.tss.tssmanager_backend.entity.ProcesoPaso;
import com.tss.tssmanager_backend.repository.PlantillaCorreoRepository;
import com.tss.tssmanager_backend.repository.ProcesoAutomaticoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProcesoAutomaticoService {

    @Autowired
    private ProcesoAutomaticoRepository repository;
    @Autowired
    private PlantillaCorreoRepository plantillaRepository;

    @Transactional(readOnly = true)
    public List<ProcesoAutomaticoDTO> obtenerTodos() {
        return repository.findAllWithPasos().stream()
                .map(ProcesoAutomaticoDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public ProcesoAutomaticoDTO obtenerPorId(Integer id) {
        return repository.findByIdWithPasos(id)
                .map(ProcesoAutomaticoDTO::fromEntity)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado"));
    }

    @Transactional
    public ProcesoAutomaticoDTO crear(ProcesoAutomaticoDTO dto) {
        if (dto.getPasos() == null || dto.getPasos().isEmpty())
            throw new IllegalArgumentException("El proceso debe tener al menos una plantilla");

        ProcesoAutomatico proceso = new ProcesoAutomatico();
        proceso.setNombre(dto.getNombre());
        proceso.setFechaCreacion(LocalDateTime.now());
        proceso.setFechaModificacion(LocalDateTime.now());

        for (int i = 0; i < dto.getPasos().size(); i++) {
            ProcesoAutomaticoDTO.PasoDTO pasoDTO = dto.getPasos().get(i);
            PlantillaCorreo plantilla = plantillaRepository.findById(pasoDTO.getPlantillaId())
                    .orElseThrow(() -> new RuntimeException("Plantilla no encontrada"));
            ProcesoPaso paso = new ProcesoPaso();
            paso.setProceso(proceso);
            paso.setPlantilla(plantilla);
            paso.setDias(pasoDTO.getDias());
            paso.setOrden(i + 1);
            proceso.getPasos().add(paso);
        }

        ProcesoAutomatico saved = repository.save(proceso);
        return repository.findByIdWithPasos(saved.getId())
                .map(ProcesoAutomaticoDTO::fromEntity)
                .orElseThrow();
    }

    @Transactional
    public ProcesoAutomaticoDTO actualizar(Integer id, ProcesoAutomaticoDTO dto) {
        ProcesoAutomatico proceso = repository.findByIdWithPasos(id)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado"));

        proceso.setNombre(dto.getNombre());
        proceso.setFechaModificacion(LocalDateTime.now());
        proceso.getPasos().clear();

        for (int i = 0; i < dto.getPasos().size(); i++) {
            ProcesoAutomaticoDTO.PasoDTO pasoDTO = dto.getPasos().get(i);
            PlantillaCorreo plantilla = plantillaRepository.findById(pasoDTO.getPlantillaId())
                    .orElseThrow(() -> new RuntimeException("Plantilla no encontrada"));
            ProcesoPaso paso = new ProcesoPaso();
            paso.setProceso(proceso);
            paso.setPlantilla(plantilla);
            paso.setDias(pasoDTO.getDias());
            paso.setOrden(i + 1);
            proceso.getPasos().add(paso);
        }

        repository.save(proceso);
        return repository.findByIdWithPasos(proceso.getId())
                .map(ProcesoAutomaticoDTO::fromEntity)
                .orElseThrow();
    }

    @Transactional
    public void eliminar(Integer id) {
        repository.deleteById(id);
    }

    public boolean plantillaEstaEnUso(Integer plantillaId) {
        return repository.existePasoConPlantilla(plantillaId);
    }
}