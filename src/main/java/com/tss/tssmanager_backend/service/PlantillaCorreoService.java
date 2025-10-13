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
        return repositorio.findByIdWithAdjuntos(id);
    }

    private String limpiarContenidoHTML(String contenidoHTML) {
        if (contenidoHTML == null) return null;

        return contenidoHTML
                .replaceAll("\\s+", " ") // Normalizar espacios
                .trim();
    }

    private boolean validarContenidoNoVacio(String contenidoHTML) {
        if (contenidoHTML == null || contenidoHTML.trim().isEmpty()) {
            return false;
        }

        // Extraer solo el texto sin HTML para validar que no esté vacío
        String textoSinHTML = contenidoHTML.replaceAll("<[^>]*>", "").trim();
        return !textoSinHTML.isEmpty();
    }

    @Transactional
    public PlantillaCorreo guardarPlantilla(PlantillaCorreo plantilla, MultipartFile[] adjuntos) throws IOException {

        if (!validarContenidoNoVacio(plantilla.getMensaje())) {
            throw new IllegalArgumentException("El contenido de la plantilla no puede estar vacío");
        }
        plantilla.setMensaje(limpiarContenidoHTML(plantilla.getMensaje()));
        plantilla.setFechaCreacion(LocalDateTime.now());

        if (adjuntos != null && adjuntos.length > 0) {
            // Validar tamaño total primero
            long totalSize = 0;
            for (MultipartFile adjunto : adjuntos) {
                totalSize += adjunto.getSize();
            }

            if (totalSize > 10 * 1024 * 1024) {
                throw new IllegalArgumentException(
                        String.format("El tamaño total de los archivos excede el límite de 10MB. Tamaño actual: %.2f MB",
                                totalSize / (1024.0 * 1024.0)));
            }

            for (MultipartFile adjunto : adjuntos) {
                if (adjunto.getSize() > 5 * 1024 * 1024) {
                    throw new IllegalArgumentException(
                            String.format("El archivo '%s' excede el límite de 5MB por archivo. Tamaño: %.2f MB",
                                    adjunto.getOriginalFilename(),
                                    adjunto.getSize() / (1024.0 * 1024.0)));
                }
                if (!adjunto.getContentType().matches("application/(pdf)|image/(jpg|jpeg|png)|application/(msword|vnd.openxmlformats-officedocument.wordprocessingml.document)")) {
                    throw new IllegalArgumentException("Solo se permiten archivos PDF, JPG, PNG, DOC o DOCX");
                }

                // Extraer el nombre sin extensión para usarlo como public_id
                String nombreOriginal = adjunto.getOriginalFilename();
                String nombreSinExtension = nombreOriginal.substring(0, nombreOriginal.lastIndexOf('.'));
                String publicId = "plantillas_correos/" + nombreSinExtension + "_" + System.currentTimeMillis();

                Map uploadResult = cloudinary.uploader().upload(adjunto.getBytes(), ObjectUtils.asMap(
                        "resource_type", "raw",
                        "public_id", publicId,
                        "use_filename", true,
                        "unique_filename", false
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

        if (!validarContenidoNoVacio(detallesPlantilla.getMensaje())) {
            throw new IllegalArgumentException("El contenido de la plantilla no puede estar vacío");
        }

        plantilla.setNombre(detallesPlantilla.getNombre());
        plantilla.setAsunto(detallesPlantilla.getAsunto());
        plantilla.setMensaje(detallesPlantilla.getMensaje());

        // Manejar eliminación de adjuntos
        if (adjuntosToRemove != null) {
            List<String> urlsToRemove = new ObjectMapper().readValue(adjuntosToRemove, new TypeReference<List<String>>(){});
            plantilla.getAdjuntos().removeIf(adjunto -> urlsToRemove.contains(adjunto.getAdjuntoUrl()));
        }

        // Manejar nuevos adjuntos
        // Manejar nuevos adjuntos
        if (adjuntos != null && adjuntos.length > 0) {
            // Validar tamaño total de nuevos archivos
            long totalSizeNuevos = 0;
            for (MultipartFile adjunto : adjuntos) {
                totalSizeNuevos += adjunto.getSize();
            }

            if (totalSizeNuevos > 10 * 1024 * 1024) {
                throw new IllegalArgumentException(
                        String.format("El tamaño total de los nuevos archivos excede el límite de 10MB. Tamaño: %.2f MB",
                                totalSizeNuevos / (1024.0 * 1024.0)));
            }

            for (MultipartFile adjunto : adjuntos) {
                if (adjunto.getSize() > 5 * 1024 * 1024) {
                    throw new IllegalArgumentException(
                            String.format("El archivo '%s' excede el límite de 5MB por archivo. Tamaño: %.2f MB",
                                    adjunto.getOriginalFilename(),
                                    adjunto.getSize() / (1024.0 * 1024.0)));
                }
                if (!adjunto.getContentType().matches("application/(pdf)|image/(jpg|jpeg|png)|application/(msword|vnd.openxmlformats-officedocument.wordprocessingml.document)")) {
                    throw new IllegalArgumentException("Solo se permiten archivos PDF, JPG, PNG, DOC o DOCX");
                }

                // Extraer el nombre sin extensión para usarlo como public_id
                String nombreOriginal = adjunto.getOriginalFilename();
                String nombreSinExtension = nombreOriginal.substring(0, nombreOriginal.lastIndexOf('.'));
                String publicId = "plantillas_correos/" + nombreSinExtension + "_" + System.currentTimeMillis();

                Map uploadResult = cloudinary.uploader().upload(adjunto.getBytes(), ObjectUtils.asMap(
                        "resource_type", "raw",
                        "public_id", publicId,
                        "use_filename", true,
                        "unique_filename", false
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