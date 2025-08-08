package com.tss.tssmanager_backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.tss.tssmanager_backend.entity.ModeloEquipo;
import com.tss.tssmanager_backend.repository.ModeloEquipoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ModeloEquipoService {

    @Autowired
    private ModeloEquipoRepository repository;

    @Autowired
    private Cloudinary cloudinary;

    @CacheEvict(value = {"modelos", "equipos"}, allEntries = true)
    public ModeloEquipo guardarModelo(ModeloEquipo modelo, MultipartFile imagen) throws IOException {
        if (imagen != null && !imagen.isEmpty()) {
            if (imagen.getSize() > 2 * 1024 * 1024) {
                throw new IllegalArgumentException("El archivo no debe superar los 2MB");
            }
            if (!imagen.getContentType().matches("image/(png|jpg|jpeg)")) {
                throw new IllegalArgumentException("Solo se permiten archivos PNG, JPG o JPEG");
            }

            Map uploadResult = cloudinary.uploader().upload(imagen.getBytes(), ObjectUtils.asMap(
                    "resource_type", "image",
                    "folder", "modelos_equipos"
            ));
            modelo.setImagenUrl(uploadResult.get("url").toString());
        }
        return repository.save(modelo);
    }

    @CachePut(value = "modelos", key = "#id")
    @CacheEvict(value = "equipos", allEntries = true)
    public ModeloEquipo actualizarModelo(Integer id, ModeloEquipo modelo, MultipartFile imagen) throws IOException {
        ModeloEquipo existingModelo = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modelo no encontrado"));

        existingModelo.setNombre(modelo.getNombre());
        existingModelo.setUso(modelo.getUso());

        if (imagen != null && !imagen.isEmpty()) {
            if (imagen.getSize() > 2 * 1024 * 1024) {
                throw new IllegalArgumentException("El archivo no debe superar los 2MB");
            }
            if (!imagen.getContentType().matches("image/(png|jpg|jpeg)")) {
                throw new IllegalArgumentException("Solo se permiten archivos PNG, JPG o JPEG");
            }

            Map uploadResult = cloudinary.uploader().upload(imagen.getBytes(), ObjectUtils.asMap(
                    "resource_type", "image",
                    "folder", "modelos_equipos"
            ));
            existingModelo.setImagenUrl(uploadResult.get("url").toString());
        }

        return repository.save(existingModelo);
    }

    @CacheEvict(value = {"modelos", "equipos"}, allEntries = true)
    public void eliminarModelo(Integer id) {
        ModeloEquipo modelo = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modelo no encontrado"));
        repository.delete(modelo);
    }

    @Cacheable(value = "modelos", key = "#id")
    public ModeloEquipo obtenerModelo(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modelo no encontrado"));
    }

    @Cacheable(value = "modelos", key = "'all'")
    public Iterable<ModeloEquipo> obtenerTodosLosModelos() {
        return repository.findAll();
    }

    @Cacheable(value = "modelos", key = "'summary'")
    public Map<String, Object> obtenerModelosConConteo() {
        List<ModeloEquipo> modelos = repository.findAll();
        List<Object[]> conteos = repository.countEquiposByModelo();

        Map<Integer, Long> conteoMap = conteos.stream()
                .collect(Collectors.toMap(
                        row -> (Integer) row[0],
                        row -> ((Number) row[1]).longValue()
                ));

        List<Map<String, Object>> modelosConConteo = modelos.stream()
                .map(modelo -> {
                    Map<String, Object> modeloMap = new HashMap<>();
                    modeloMap.put("id", modelo.getId());
                    modeloMap.put("nombre", modelo.getNombre());
                    modeloMap.put("uso", modelo.getUso());
                    modeloMap.put("imagenUrl", modelo.getImagenUrl());
                    modeloMap.put("cantidad", conteoMap.getOrDefault(modelo.getId(), 0L));
                    return modeloMap;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("modelos", modelosConConteo);
        response.put("total", modelos.size());

        return response;
    }

    @Cacheable(value = "modelos", key = "'search:' + #nombre")
    public List<ModeloEquipo> buscarModelosPorNombre(String nombre) {
        return repository.findByNombreContainingIgnoreCase(nombre);
    }

    public Page<ModeloEquipo> obtenerModelosPaginados(Pageable pageable, String search) {
        if (search != null && !search.trim().isEmpty()) {
            return repository.findByNombreContainingIgnoreCasePaginated(search, pageable);
        }
        return repository.findAll(pageable);
    }
}