package com.tss.tssmanager_backend.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // Configuración general para todos los caches
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .expireAfterAccess(15, TimeUnit.MINUTES)
                .recordStats()
        );

        cacheManager.setCacheNames(Arrays.asList(
                "usuarios",
                "empresas",
                "tratos",
                "contactos",
                "equipos",
                "modelos",
                "sims",
                "notificaciones",
                "calendario-eventos",
                "dashboard-stats",
                "reports-data",
                "gruposDisponibles",
                "simsDisponibles",
                "equiposDisponibles"
        ));

        return cacheManager;
    }

    @Bean
    public CacheManager usuariosCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("usuarios");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(2, TimeUnit.HOURS)
                .recordStats()
        );
        return cacheManager;
    }

    @Bean
    public CacheManager empresasCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("empresas");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats()
        );
        return cacheManager;
    }

    @Bean
    public CacheManager tratosCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("tratos");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(45, TimeUnit.MINUTES)
                .recordStats()
        );
        return cacheManager;
    }

    @Bean
    public CacheManager dashboardCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("dashboard-stats");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()
        );
        return cacheManager;
    }
}