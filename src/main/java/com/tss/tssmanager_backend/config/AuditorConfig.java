package com.tss.tssmanager_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
public class AuditorConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            // Obtener el nombre del usuario autenticado desde el SecurityContextHolder
            String nombreUsuario = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                    .map(auth -> auth.getName())
                    .orElse("system"); // Valor por defecto si no hay autenticación

            // Verificar si el nombre es "anonymousUser" y devolver "system" en ese caso
            if ("anonymousUser".equals(nombreUsuario)) {
                return Optional.of("system");
            }

            // Devolver el nombreUsuario envuelto en un Optional
            return Optional.of(nombreUsuario);
        };
    }
}