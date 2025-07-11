package com.tss.tssmanager_backend.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class CloudinaryConfig {

    @Value("${cloudinary.url}")
    private String cloudinaryUrl;

    @Bean
    public Cloudinary cloudinary() {
        if (cloudinaryUrl == null || cloudinaryUrl.isEmpty()) {
            throw new IllegalStateException("La variable de entorno CLOUDINARY_URL no está configurada");
        }

        try {
            String[] mainParts = cloudinaryUrl.replace("cloudinary://", "").split("@");
            if (mainParts.length != 2) {
                throw new IllegalArgumentException("Formato de CLOUDINARY_URL inválido. Debe ser cloudinary://api_key:api_secret@cloud_name");
            }

            String credentials = mainParts[0];
            String cloudName = mainParts[1];

            // Dividir las credenciales por ":"
            String[] credentialParts = credentials.split(":");
            if (credentialParts.length != 2) {
                throw new IllegalArgumentException("Formato de credenciales inválido. Debe ser api_key:api_secret");
            }

            String apiKey = credentialParts[0];
            String apiSecret = credentialParts[1];

            Map<String, String> config = new HashMap<>();
            config.put("cloud_name", cloudName);
            config.put("api_key", apiKey);
            config.put("api_secret", apiSecret);

            return new Cloudinary(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al parsear CLOUDINARY_URL: " + e.getMessage(), e);
        }
    }
}