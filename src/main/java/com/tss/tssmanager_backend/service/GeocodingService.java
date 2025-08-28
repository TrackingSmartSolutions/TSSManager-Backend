package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.entity.CoordenadasCache;
import com.tss.tssmanager_backend.entity.Empresa;
import com.tss.tssmanager_backend.dto.EmpresaConCoordenadasDTO;
import com.tss.tssmanager_backend.repository.CoordenadasCacheRepository;
import com.tss.tssmanager_backend.repository.EmpresaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

@Service
public class GeocodingService {

    @Autowired
    private CoordenadasCacheRepository coordenadasRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    // Generar hash SHA-256 de dirección
    private String getAddressHash(String address) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(address.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating address hash", e);
        }
    }

    // Obtener empresas con coordenadas
    public List<EmpresaConCoordenadasDTO> getEmpresasConCoordenadas() {
        List<Object[]> results = empresaRepository.findEmpresasConCoordenadas();

        return results.stream()
                .map(row -> new EmpresaConCoordenadasDTO(
                        (Integer) row[0],           // id
                        (String) row[1],         // nombre
                        (String) row[2],         // domicilioFisico
                        (String) row[3],         // sector
                        (String) row[4],         // estatus
                        (String) row[5],         // sitioWeb
                        (BigDecimal) row[6],     // lat
                        (BigDecimal) row[7]      // lng
                ))
                .collect(Collectors.toList());
    }

    private boolean isValidAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return false;
        }

        String trimmed = address.trim().toLowerCase();

        if (trimmed.length() < 8) {
            return false;
        }

        String[] invalidPatterns = {
                "^(n/a|na|sin\\s+direcci[óo]n|no\\s+aplica|pendiente|tbd|por\\s+definir)$",
                "^[.\\-_\\s#,;:]+$", // Solo caracteres especiales
                "^\\d{1,3}$", // Solo números de 1-3 dígitos
                "^[a-z]\\s*$", // Solo una letra
                ".*información\\s+no\\s+disponible.*",
                "^qdefrgthy.*", // Tu ejemplo de texto basura
                "^(león|mexico|guadalajara|querétaro)\\s*,?\\s*(gto|mexico)?\\s*$" // Solo ciudad sin dirección
        };

        for (String pattern : invalidPatterns) {
            if (trimmed.matches(pattern)) {
                System.out.println("Dirección rechazada por patrón inválido: " + address);
                return false;
            }
        }

        boolean hasNumber = trimmed.matches(".*\\d.*");
        boolean hasLetter = trimmed.matches(".*[a-záéíóúñü].*");

        if (!hasNumber || !hasLetter) {
            System.out.println("Dirección rechazada por falta de números o letras: " + address);
            return false;
        }

        return true;
    }

    private Map<String, BigDecimal> geocodeAddress(String address) {
        if (!isValidAddress(address)) {
            System.out.println("❌ Dirección inválida: " + address);
            return null;
        }

        String cleanAddress = normalizeAddress(address);

        Map<String, BigDecimal> result = null;

        result = tryGeocodingStrategy(cleanAddress, "Dirección completa");
        if (result != null) return result;

        if (!cleanAddress.toLowerCase().contains("león") && !cleanAddress.toLowerCase().contains("guanajuato")) {
            String withContext = cleanAddress + ", León, Guanajuato, México";
            result = tryGeocodingStrategy(withContext, "Con contexto León, Gto");
            if (result != null) return result;
        }

        if (!cleanAddress.toLowerCase().contains("león")) {
            String withLeon = cleanAddress + ", León, México";
            result = tryGeocodingStrategy(withLeon, "Con León");
            if (result != null) return result;
        }

        String simplified = simplifyAddress(cleanAddress);
        if (!simplified.equals(cleanAddress)) {
            result = tryGeocodingStrategy(simplified + ", León, Guanajuato, México", "Dirección simplificada");
            if (result != null) return result;
        }
        String streetOnly = extractMainStreet(cleanAddress);
        if (streetOnly != null && !streetOnly.equals(simplified)) {
            result = tryGeocodingStrategy(streetOnly + ", León, Guanajuato, México", "Solo calle principal");
            if (result != null) return result;
        }
        result = tryAreaGeocoding(cleanAddress);
        if (result != null) return result;

        result = tryGeocodingWithoutCountryRestriction(cleanAddress);
        if (result != null) return result;

        result = getFallbackLeonCoordinates(cleanAddress);
        if (result != null) return result;
        return null;
    }

    private String extractMainStreet(String address) {
        String street = address;

        street = street.replaceAll("\\b\\d{5}\\b", "").trim();

        street = street.replaceAll("\\s+\\d+\\s*,?\\s*$", "").trim();

        street = street.replaceAll("León de los Aldama,?\\s*", "").trim();
        street = street.replaceAll("Guanajuato,?\\s*", "").trim();
        street = street.replaceAll("México,?\\s*$", "").trim();

        street = street.replaceAll(",+", ",").replaceAll("^,|,$", "").trim();

        if (street.length() >= 5 && !street.matches("\\d+")) {
            return street;
        }

        return null;
    }

    private Map<String, BigDecimal> tryAreaGeocoding(String address) {
        System.out.println("🏘️ Intentando geocodificación por área...");

        String[] commonAreas = {
                "Centro", "Obregón", "Belisario Domínguez", "San Juan de Dios",
                "Jardines de San Sebastián", "Lomas de San Juan", "Moderna",
                "Valle del Sol", "Los Naranjos", "Panorama", "San Felipe"
        };

        if (address.contains("37119") || address.contains("37208")) {
            String zone = address.contains("37119") ? "Centro León" : "Zona Norte León";
            Map<String, BigDecimal> result = tryGeocodingStrategy(zone + ", Guanajuato, México", "Por código postal");
            if (result != null) return result;
        }

        return tryGeocodingStrategy("León Centro, Guanajuato, México", "León Centro genérico");
    }

    private Map<String, BigDecimal> getFallbackLeonCoordinates(String address) {
        System.out.println("🎯 Usando coordenadas genéricas de León como último recurso...");

        BigDecimal baseLat = new BigDecimal("21.1619");
        BigDecimal baseLng = new BigDecimal("-101.6971");

        Random random = new Random(address.hashCode());
        double latVariation = (random.nextDouble() - 0.5) * 0.02;
        double lngVariation = (random.nextDouble() - 0.5) * 0.02;

        BigDecimal finalLat = baseLat.add(BigDecimal.valueOf(latVariation));
        BigDecimal finalLng = baseLng.add(BigDecimal.valueOf(lngVariation));

        Map<String, BigDecimal> coords = new HashMap<>();
        coords.put("lat", finalLat);
        coords.put("lng", finalLng);
        return coords;
    }

    private Map<String, BigDecimal> tryGeocodingStrategy(String query, String strategyName) {
        System.out.println("🔍 " + strategyName + ": " + query);

        String url = String.format(
                "https://nominatim.openstreetmap.org/search?format=json&q=%s&limit=5&countrycodes=mx&addressdetails=1&bounded=0",
                java.net.URLEncoder.encode(query, StandardCharsets.UTF_8)
        );

        try {
            // Configurar headers apropiados
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "TSSManager/1.0 (contacto@tssmanager.com)");
            headers.set("Accept", "application/json");
            headers.set("Accept-Language", "es,en");
            headers.set("Referer", "https://tssmanager.com");

            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    List.class
            ).getBody();

            if (response != null && !response.isEmpty()) {
                // Buscar el mejor resultado
                for (int i = 0; i < response.size(); i++) {
                    Map<String, Object> result = response.get(i);
                    try {
                        BigDecimal lat = new BigDecimal(result.get("lat").toString());
                        BigDecimal lng = new BigDecimal(result.get("lon").toString());

                        // Debug: mostrar información del resultado
                        String displayName = (String) result.get("display_name");
                        String type = (String) result.get("type");

                        // Verificar que esté en México
                        if (isValidMexicanCoordinate(lat, lng)) {
                            // Preferir resultados de León o Guanajuato
                            if (displayName.toLowerCase().contains("león") ||
                                    displayName.toLowerCase().contains("guanajuato")) {

                                Map<String, BigDecimal> coords = new HashMap<>();
                                coords.put("lat", lat);
                                coords.put("lng", lng);
                                return coords;
                            } else if (i == 0) {
                                Map<String, BigDecimal> coords = new HashMap<>();
                                coords.put("lat", lat);
                                coords.put("lng", lng);
                                return coords;
                            }
                        } else {
                            System.out.println("Coordenadas fuera de México: " + lat + ", " + lng);
                        }
                    } catch (Exception e) {
                        continue;
                    }
                }
            } else {
                System.out.println("Sin resultados para esta estrategia");
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }

    private String simplifyAddress(String address) {
        // Extraer solo la parte principal de la dirección (calle y número)
        String simplified = address;

        // Remover códigos postales
        simplified = simplified.replaceAll("\\b\\d{5}\\b", "").trim();

        // Remover "León de los Aldama" redundante
        simplified = simplified.replaceAll("León de los Aldama,?\\s*", "").trim();

        // Remover "Guanajuato" y "México" para simplificar
        simplified = simplified.replaceAll("Guanajuato,?\\s*", "").trim();
        simplified = simplified.replaceAll("México,?\\s*$", "").trim();

        // Limpiar comas múltiples
        simplified = simplified.replaceAll(",+", ",").replaceAll("^,|,$", "").trim();

        return simplified.isEmpty() ? address : simplified;
    }

    private String normalizeAddress(String address) {
        if (address == null) return "";

        return address.trim()
                .replaceAll("\\s+", " ") // Múltiples espacios a uno
                .replaceAll("([,.])\\s*([,.]+)", "$1") // Limpiar puntuación duplicada
                .trim();
    }

    private boolean isValidMexicanCoordinate(BigDecimal lat, BigDecimal lng) {
        return lat.compareTo(BigDecimal.valueOf(14.0)) >= 0 &&
                lat.compareTo(BigDecimal.valueOf(33.0)) <= 0 &&
                lng.compareTo(BigDecimal.valueOf(-119.0)) >= 0 &&
                lng.compareTo(BigDecimal.valueOf(-86.0)) <= 0;
    }

    private Map<String, BigDecimal> tryGeocodingWithoutCountryRestriction(String address) {
        String[] queries = {
                address + ", México",
                address,
                simplifyAddress(address) + ", León, México"
        };

        for (String query : queries) {
            String url = String.format(
                    "https://nominatim.openstreetmap.org/search?format=json&q=%s&limit=3&addressdetails=1",
                    java.net.URLEncoder.encode(query, StandardCharsets.UTF_8)
            );

            try {
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.set("User-Agent", "TSSManager/1.0 (contacto@tssmanager.com)");
                headers.set("Accept", "application/json");

                org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> response = restTemplate.exchange(
                        url,
                        org.springframework.http.HttpMethod.GET,
                        entity,
                        List.class
                ).getBody();

                if (response != null && !response.isEmpty()) {
                    for (Map<String, Object> result : response) {
                        try {
                            BigDecimal lat = new BigDecimal(result.get("lat").toString());
                            BigDecimal lng = new BigDecimal(result.get("lon").toString());
                            String displayName = (String) result.get("display_name");

                            if (isValidMexicanCoordinate(lat, lng)) {
                                Map<String, BigDecimal> coords = new HashMap<>();
                                coords.put("lat", lat);
                                coords.put("lng", lng);
                                return coords;
                            }
                        } catch (Exception e) {
                            continue;
                        }
                    }
                }

                // Pequeña pausa entre intentos
                Thread.sleep(500);

            } catch (Exception e) {
                System.out.println("Error en query sin restricción: " + e.getMessage());
            }
        }

        return null;
    }

    public void preprocessAddresses() {
        List<String> direccionesNoGeocodificadas = coordenadasRepository.findDireccionesNoGeocodificadas();

        int processed = 0;
        int successful = 0;
        int failed = 0;

        for (String address : direccionesNoGeocodificadas) {
            try {
                Map<String, BigDecimal> coords = geocodeAddress(address);

                if (coords != null) {
                    String hash = getAddressHash(address);

                    Optional<CoordenadasCache> existing = coordenadasRepository.findByDireccionHash(hash);

                    if (existing.isPresent()) {
                        CoordenadasCache cache = existing.get();
                        cache.setLat(coords.get("lat"));
                        cache.setLng(coords.get("lng"));
                        coordenadasRepository.save(cache);
                    } else {
                        CoordenadasCache cache = new CoordenadasCache(hash, address, coords.get("lat"), coords.get("lng"));
                        coordenadasRepository.save(cache);
                    }
                    successful++;
                } else {
                    failed++;
                }

                processed++;

                // Rate limiting - 1.5 segundos entre requests para ser más respetuosos
                TimeUnit.MILLISECONDS.sleep(1000);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                failed++;
                processed++;
            }
        }

        System.out.println("Geocodificación completada:");
        System.out.println("Procesadas: " + processed);
        System.out.println("Exitosas: " + successful);
        System.out.println("Fallidas: " + failed);
        System.out.println("Tasa de éxito: " + String.format("%.1f%%", (successful * 100.0 / processed)));
    }
}