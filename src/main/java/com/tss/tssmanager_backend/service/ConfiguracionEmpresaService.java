package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.entity.ConfiguracionEmpresa;
import com.tss.tssmanager_backend.repository.ConfiguracionEmpresaRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class ConfiguracionEmpresaService {

    @Autowired
    private ConfiguracionEmpresaRepository configuracionEmpresaRepository;

    @Autowired
    private Cloudinary cloudinary;

    public ConfiguracionEmpresa obtenerConfiguracion() {
        return configuracionEmpresaRepository.findById(1)
                .orElseGet(() -> {
                    ConfiguracionEmpresa nuevaConfig = new ConfiguracionEmpresa();
                    nuevaConfig.setId(1);
                    return configuracionEmpresaRepository.save(nuevaConfig);
                });
    }

    public ConfiguracionEmpresa guardarConfiguracion(ConfiguracionEmpresa configuracion, MultipartFile logo) throws IOException {
        ConfiguracionEmpresa existingConfig = configuracionEmpresaRepository.findById(1)
                .orElseThrow(() -> new RuntimeException("Configuración no encontrada"));

        existingConfig.setNombre(configuracion.getNombre());
        existingConfig.setEslogan(configuracion.getEslogan());
        existingConfig.setCorreoContacto(configuracion.getCorreoContacto());
        existingConfig.setTelefonoMovil(configuracion.getTelefonoMovil());
        existingConfig.setTelefonoFijo(configuracion.getTelefonoFijo());
        existingConfig.setDireccionPrincipal(configuracion.getDireccionPrincipal());

        if (logo != null && !logo.isEmpty()) {
            if (logo.getSize() > 2 * 1024 * 1024) {
                throw new IllegalArgumentException("El archivo no debe superar los 2MB");
            }
            if (!logo.getContentType().matches("image/(png|jpg|jpeg|svg\\+xml)")) {
                throw new IllegalArgumentException("Solo se permiten archivos PNG, JPG, JPEG o SVG.");
            }

            Map uploadResult = cloudinary.uploader().upload(logo.getBytes(), ObjectUtils.asMap(
                    "resource_type", "image",
                    "folder", "empresa/logos"
            ));
            existingConfig.setLogoUrl(uploadResult.get("url").toString());
        }

        return configuracionEmpresaRepository.save(existingConfig);
    }
}