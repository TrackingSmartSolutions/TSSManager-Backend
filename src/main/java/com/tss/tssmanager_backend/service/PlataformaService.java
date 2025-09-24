package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.entity.Plataforma;
import com.tss.tssmanager_backend.repository.PlataformaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class PlataformaService {

    @Autowired
    private PlataformaRepository repository;

    public List<Plataforma> obtenerTodasLasPlataformas() {
        return repository.findAll();
    }

    public Plataforma obtenerPlataforma(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Plataforma no encontrada con ID: " + id));
    }

    @Transactional
    public Plataforma guardarPlataforma(Plataforma plataforma) {
        return repository.save(plataforma);
    }

    @Transactional
    public Plataforma actualizarPlataforma(Integer id, Plataforma plataformaDetails) {
        Plataforma plataforma = obtenerPlataforma(id);
        plataforma.setNombrePlataforma(plataformaDetails.getNombrePlataforma());
        return repository.save(plataforma);
    }

    @Transactional
    public void eliminarPlataforma(Integer id) {
        Plataforma plataforma = obtenerPlataforma(id);
        repository.delete(plataforma);
    }

    public Map<String, Object> verificarAsociaciones(Integer id) {
        boolean hasAssociations = repository.existsEquiposByPlataformaId(id);
        return Map.of("hasAssociations", hasAssociations);
    }
}