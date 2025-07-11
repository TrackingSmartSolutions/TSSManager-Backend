package com.tss.tssmanager_backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.tss.tssmanager_backend.entity.ModeloEquipo;
import com.tss.tssmanager_backend.repository.ModeloEquipoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class ModeloEquipoService {

    @Autowired
    private ModeloEquipoRepository repository;

    @Autowired
    private Cloudinary cloudinary;

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

    public void eliminarModelo(Integer id) {
        ModeloEquipo modelo = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modelo no encontrado"));
        repository.delete(modelo);
    }

    public ModeloEquipo obtenerModelo(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modelo no encontrado"));
    }

    public Iterable<ModeloEquipo> obtenerTodosLosModelos() {
        return repository.findAll();
    }
}