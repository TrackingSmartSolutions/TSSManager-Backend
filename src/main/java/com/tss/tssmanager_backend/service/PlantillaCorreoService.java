package com.tss.tssmanager_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.tssmanager_backend.dto.PlantillaCorreoDTO;
import com.tss.tssmanager_backend.entity.Adjunto;
import com.tss.tssmanager_backend.entity.PlantillaCorreo;
import com.tss.tssmanager_backend.repository.PlantillaCorreoRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PlantillaCorreoService {

    @Autowired
    private PlantillaCorreoRepository repositorio;

    @Autowired
    private Cloudinary cloudinary;

    public List<PlantillaCorreo> obtenerTodasLasPlantillas() {
        return repositorio.findAll();
    }

    public Optional<PlantillaCorreo> obtenerPlantillaPorId(Integer id) {
        return repositorio.findById(id);
    }

    @Transactional
    public PlantillaCorreo guardarPlantilla(PlantillaCorreo plantilla, MultipartFile[] adjuntos) throws IOException {
        plantilla.setFechaCreacion(LocalDateTime.now());

        if (adjuntos != null && adjuntos.length > 0) {
            for (MultipartFile adjunto : adjuntos) {
                if (adjunto.getSize() > 5 * 1024 * 1024) {
                    throw new IllegalArgumentException("El archivo no debe superar los 5MB");
                }
                if (!adjunto.getContentType().matches("application/(pdf)|image/(jpg|jpeg|png)|application/(msword|vnd.openxmlformats-officedocument.wordprocessingml.document)")) {
                    throw new IllegalArgumentException("Solo se permiten archivos PDF, JPG, PNG, DOC o DOCX");
                }

                Map uploadResult = cloudinary.uploader().upload(adjunto.getBytes(), ObjectUtils.asMap(
                        "resource_type", "raw",
                        "folder", "plantillas_correos"
                ));

                Adjunto nuevoAdjunto = new Adjunto();
                nuevoAdjunto.setPlantilla(plantilla);
                nuevoAdjunto.setAdjuntoUrl(uploadResult.get("url").toString());
                plantilla.getAdjuntos().add(nuevoAdjunto);
            }
        }

        return repositorio.save(plantilla);
    }

    @Transactional
    public PlantillaCorreoDTO actualizarPlantilla(Integer id, PlantillaCorreo detallesPlantilla, MultipartFile[] adjuntos, String adjuntosToRemove) throws IOException {
        PlantillaCorreo plantilla = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("Plantilla no encontrada"));

        plantilla.setNombre(detallesPlantilla.getNombre());
        plantilla.setAsunto(detallesPlantilla.getAsunto());
        plantilla.setMensaje(detallesPlantilla.getMensaje());

        // Manejar eliminación de adjuntos
        if (adjuntosToRemove != null) {
            List<String> urlsToRemove = new ObjectMapper().readValue(adjuntosToRemove, new TypeReference<List<String>>(){});
            plantilla.getAdjuntos().removeIf(adjunto -> urlsToRemove.contains(adjunto.getAdjuntoUrl()));
        }

        // Manejar nuevos adjuntos
        if (adjuntos != null && adjuntos.length > 0) {
            // No limpiamos todos los adjuntos aquí, solo añadimos los nuevos
            for (MultipartFile adjunto : adjuntos) {
                if (adjunto.getSize() > 5 * 1024 * 1024) {
                    throw new IllegalArgumentException("El archivo no debe superar los 5MB");
                }
                if (!adjunto.getContentType().matches("application/(pdf)|image/(jpg|jpeg|png)|application/(msword|vnd.openxmlformats-officedocument.wordprocessingml.document)")) {
                    throw new IllegalArgumentException("Solo se permiten archivos PDF, JPG, PNG, DOC o DOCX");
                }

                Map uploadResult = cloudinary.uploader().upload(adjunto.getBytes(), ObjectUtils.asMap(
                        "resource_type", "raw",
                        "folder", "plantillas_correos"
                ));

                Adjunto nuevoAdjunto = new Adjunto();
                nuevoAdjunto.setPlantilla(plantilla);
                nuevoAdjunto.setAdjuntoUrl(uploadResult.get("url").toString());
                plantilla.getAdjuntos().add(nuevoAdjunto);
            }
        }

        plantilla.setFechaModificacion(LocalDateTime.now());
        repositorio.save(plantilla); // Asegura que los cambios se persistan
        return PlantillaCorreoDTO.fromEntity(plantilla); // Devuelve el DTO dentro de la transacción
    }

    @Transactional
    public void eliminarPlantilla(Integer id) {
        PlantillaCorreo plantilla = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("Plantilla no encontrada"));
        repositorio.delete(plantilla);
    }
}