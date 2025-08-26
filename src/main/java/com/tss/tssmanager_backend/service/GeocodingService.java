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

        // Debe tener al menos 10 caracteres para ser una dirección válida
        if (trimmed.length() < 10) {
            return false;
        }

        String[] invalidPatterns = {
                "^(n/a|na|sin\\s+direcci[óo]n|no\\s+aplica|pendiente|tbd|por\\s+definir)$",
                "^[.\\-_\\s#,;:]+$",
                "^\\d{1,4}$", // Solo números
                "^[a-záéíóúñü]\\s*$", // Solo una palabra
                ".*información\\s+no\\s+disponible.*",
                "^(león|mexico|guadalajara|querétaro)\\s*,?\\s*(gto|mexico)?\\s*$",
                "^calle\\s+\\d+$",
                "^av\\s+\\d+$",
                "^avenue\\s+\\d+$"
        };

        for (String pattern : invalidPatterns) {
            if (trimmed.matches(pattern)) {
                return false;
            }
        }

        // Debe contener elementos esenciales de una dirección
        boolean hasStreetIndicator = trimmed.matches(".*(calle|av|avenue|avenida|blvd|boulevard|privada|priv|fraccionamiento|frac).*");
        boolean hasNumber = trimmed.matches(".*\\d+.*");
        boolean hasLetters = trimmed.matches(".*[a-záéíóúñü]{2,}.*");

        // Debe tener al menos 2 de estos 3 elementos
        int validElements = 0;
        if (hasStreetIndicator) validElements++;
        if (hasNumber) validElements++;
        if (hasLetters) validElements++;

        return validElements >= 2;
    }

    private String extractMainComponents(String address) {
        String result = address;

        String houseNumber = "";
        java.util.regex.Pattern numberPattern = java.util.regex.Pattern.compile("\\b(\\d{1,5})\\b");
        java.util.regex.Matcher numberMatcher = numberPattern.matcher(result);
        if (numberMatcher.find()) {
            houseNumber = numberMatcher.group(1) + " ";
        }

        // Limpiar la dirección manteniendo la estructura principal
        String streetName = result
                .replaceAll("\\b\\d{5}\\b", "") // Solo remover códigos postales de 5 dígitos
                .replaceAll(",\\s*león de los aldama", "")
                .replaceAll(",\\s*guanajuato", "")
                .replaceAll(",\\s*méxico\\s*$", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (streetName.length() < 10) {
            return address;
        }

        String[] words = streetName.split("\\s+");
        StringBuilder mainStreet = new StringBuilder();
        int wordCount = 0;

        for (String word : words) {
            if (word.length() >= 2 && wordCount < 5) {
                mainStreet.append(word).append(" ");
                wordCount++;
            }
        }

        String result_final = mainStreet.toString().trim();
        return result_final.isEmpty() ? address : result_final;
    }

    private boolean isReasonableLocation(Map<String, BigDecimal> coords, String originalAddress) {
        BigDecimal lat = coords.get("lat");
        BigDecimal lng = coords.get("lng");

        // Verificar que esté en México
        if (!isValidMexicanCoordinate(lat, lng)) {
            System.out.println("✗ Ubicación fuera de México: " + lat + ", " + lng);
            return false;
        }

        String addressLower = originalAddress.toLowerCase();

        if (addressLower.contains("león") || addressLower.contains("leon")) {
            BigDecimal leonCenterLat = new BigDecimal("21.1619");
            BigDecimal leonCenterLng = new BigDecimal("-101.6971");

            double latDiff = lat.subtract(leonCenterLat).doubleValue();
            double lngDiff = lng.subtract(leonCenterLng).doubleValue();
            double distance = Math.sqrt(latDiff * latDiff + lngDiff * lngDiff);

            if (distance > 0.25) {
                System.out.println("⚠ Dirección de León algo lejos pero aceptable: " +
                        lat + ", " + lng + " (distancia: " + String.format("%.3f", distance) + ")");
            }
            return true; // Aceptar todas las ubicaciones en México si mencionan León
        }

        System.out.println("✓ Ubicación válida en México: " + lat + ", " + lng);
        return true;
    }

    private Map<String, BigDecimal> tryMultipleGeocodingServices(String query) {
        String[] urls = {
                // URL 1: Con restricción de país y viewbox de León
                String.format("https://nominatim.openstreetmap.org/search?format=json&q=%s&limit=5&countrycodes=mx&addressdetails=1&bounded=0&viewbox=-101.8,-101.6,21.0,21.3",
                        java.net.URLEncoder.encode(query, StandardCharsets.UTF_8)),

                // URL 2: Con restricción de país solamente
                String.format("https://nominatim.openstreetmap.org/search?format=json&q=%s&limit=5&countrycodes=mx&addressdetails=1",
                        java.net.URLEncoder.encode(query, StandardCharsets.UTF_8)),

                // URL 3: Sin restricciones geográficas
                String.format("https://nominatim.openstreetmap.org/search?format=json&q=%s&limit=3&addressdetails=1",
                        java.net.URLEncoder.encode(query, StandardCharsets.UTF_8))
        };

        for (int i = 0; i < urls.length; i++) {
            try {
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.set("User-Agent", "TSSManager/1.0 (contacto@tssmanager.com)");
                headers.set("Accept", "application/json");
                headers.set("Accept-Language", "es-MX,es,en");
                headers.set("Referer", "https://tssmanager.com");

                org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> response = restTemplate.exchange(
                        urls[i], org.springframework.http.HttpMethod.GET, entity, List.class
                ).getBody();

                if (response != null && !response.isEmpty()) {
                    System.out.println("Encontrados " + response.size() + " resultados en URL " + (i+1));

                    for (Map<String, Object> result : response) {
                        try {
                            BigDecimal lat = new BigDecimal(result.get("lat").toString());
                            BigDecimal lng = new BigDecimal(result.get("lon").toString());
                            String displayName = (String) result.get("display_name");

                            // Validar que las coordenadas estén en México
                            if (isValidMexicanCoordinate(lat, lng)) {
                                System.out.println("✓ Coordenadas válidas encontradas: " + lat + ", " + lng);
                                System.out.println("  Lugar: " + displayName);

                                Map<String, BigDecimal> coords = new HashMap<>();
                                coords.put("lat", lat);
                                coords.put("lng", lng);
                                return coords;
                            } else {
                                System.out.println("✗ Coordenadas fuera de México: " + lat + ", " + lng);
                            }
                        } catch (Exception e) {
                            System.out.println("Error procesando resultado: " + e.getMessage());
                            continue;
                        }
                    }
                } else {
                    System.out.println("Sin resultados en URL " + (i+1));
                }

            } catch (Exception e) {
                System.out.println("Error con URL " + (i+1) + ": " + e.getMessage());
                continue;
            }
        }

        return null;
    }

    private String simplifyForGeocoding(String address) {
        String simplified = address;

        // Remover elementos que confunden la geocodificación
        simplified = simplified.replaceAll("\\b(C\\.P\\.?|CP\\.?)\\s*\\d{5}\\b", ""); // Códigos postales
        simplified = simplified.replaceAll("\\b\\d{5}\\b", ""); // Códigos postales sin CP
        simplified = simplified.replaceAll("\\b(Col\\.|Colonia)\\s+", ""); // Colonia
        simplified = simplified.replaceAll("\\b(Frac\\.|Fraccionamiento)\\s+", ""); // Fraccionamiento
        simplified = simplified.replaceAll("\\b(entre|esquina|esq\\.).*", ""); // Referencias entre calles
        simplified = simplified.replaceAll("\\b(int\\.|interior|depto\\.|departamento)\\s*\\d+", ""); // Números interiores

        // Limpiar espacios y puntuación extra
        simplified = simplified.replaceAll("[,;]+", ",");
        simplified = simplified.replaceAll("\\s+", " ");
        simplified = simplified.replaceAll("^,+|,+$", "");
        simplified = simplified.trim();

        return simplified.isEmpty() ? address : simplified;
    }

    private Map<String, BigDecimal> geocodeAddress(String address) {
        if (!isValidAddress(address)) {
            System.out.println("Dirección inválida: " + address);
            return null;
        }

        // Estrategias progresivamente más simples
        String[] strategies = buildGeocodingStrategies(address);

        for (int i = 0; i < strategies.length; i++) {
            String strategy = strategies[i];
            if (strategy == null || strategy.trim().isEmpty()) continue;

            System.out.println("Estrategia " + (i+1) + ": " + strategy);

            Map<String, BigDecimal> result = callNominatim(strategy);
            if (result != null && isValidMexicanCoordinate(result.get("lat"), result.get("lng"))) {
                System.out.println("✓ Éxito con estrategia " + (i+1));
                return result;
            }

            try { Thread.sleep(1200); } catch (InterruptedException e) { break; }
        }

        return null;
    }

    private String[] buildGeocodingStrategies(String originalAddress) {
        String normalized = normalizeAddress(originalAddress);

        // Extraer componentes básicos
        String streetName = extractStreetNameOnly(normalized);
        String houseNumber = extractHouseNumber(normalized);

        return new String[] {
                // Estrategia 1: Solo la calle principal + León
                streetName + ", León, México",

                // Estrategia 2: Calle con número + León
                (houseNumber.isEmpty() ? streetName : houseNumber + " " + streetName) + ", León, Guanajuato",

                // Estrategia 3: Versión simplificada
                simplifyStreetName(streetName) + ", León",

                // Estrategia 4: Solo León (fallback para obtener coordenadas aproximadas)
                "León, Guanajuato, México",

                // Estrategia 5: Dirección original pero limpia
                cleanOriginalAddress(normalized)
        };
    }

    private String extractStreetNameOnly(String address) {
        // Remover código postal
        String clean = address.replaceAll("\\b\\d{5}\\b", "");

        // Remover referencias geográficas al final
        clean = clean.replaceAll(",\\s*león de los aldama.*$", "");
        clean = clean.replaceAll(",\\s*guanajuato.*$", "");
        clean = clean.replaceAll(",\\s*méxico.*$", "");

        // Extraer la parte principal de la calle
        String[] parts = clean.split(",");
        if (parts.length > 0) {
            String streetPart = parts[0].trim();

            // Si tiene "Boulevard", "Avenida", etc., mantenerlo
            if (streetPart.matches(".*(boulevard|blvd|avenida|av|calle|privada|andador).*")) {
                return streetPart;
            }

            // Si no, tomar las primeras 3-4 palabras
            String[] words = streetPart.split("\\s+");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < Math.min(4, words.length); i++) {
                if (words[i].length() >= 2) {
                    result.append(words[i]).append(" ");
                }
            }
            return result.toString().trim();
        }

        return clean.trim();
    }

    private String extractHouseNumber(String address) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b(\\d{1,5})\\b");
        java.util.regex.Matcher matcher = pattern.matcher(address);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String simplifyStreetName(String streetName) {
        return streetName
                .replaceAll("\\bdel?\\b", "")
                .replaceAll("\\blos?\\b", "")
                .replaceAll("\\blas?\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanOriginalAddress(String address) {
        return address
                .replaceAll("\\b\\d{5}\\b", "")
                .replaceAll(",\\s*león de los aldama", "")
                .replaceAll(",\\s*guanajuato", "")
                .replaceAll(",\\s*méxico", "")
                .replaceAll("\\s+", " ")
                .replaceAll(",+", ",")
                .replaceAll("^,|,$", "")
                .trim();
    }

    private Map<String, BigDecimal> callNominatim(String query) {
        String url = String.format(
                "https://nominatim.openstreetmap.org/search?format=json&q=%s&limit=10&addressdetails=1",
                java.net.URLEncoder.encode(query, StandardCharsets.UTF_8)
        );

        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "TSSManager/1.0 (contacto@tssmanager.com)");
            headers.set("Accept", "application/json");
            headers.set("Accept-Language", "es,en");

            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, entity, List.class
            ).getBody();

            if (response != null && !response.isEmpty()) {
                System.out.println("  → " + response.size() + " resultados encontrados");

                // Buscar el mejor resultado
                for (Map<String, Object> result : response) {
                    try {
                        BigDecimal lat = new BigDecimal(result.get("lat").toString());
                        BigDecimal lng = new BigDecimal(result.get("lon").toString());
                        String displayName = (String) result.get("display_name");

                        // Preferir resultados que contengan México y/o León/Guanajuato
                        boolean containsMexico = displayName.toLowerCase().contains("méxico") ||
                                displayName.toLowerCase().contains("mexico");
                        boolean containsLeon = displayName.toLowerCase().contains("león") ||
                                displayName.toLowerCase().contains("guanajuato");

                        if (containsMexico || containsLeon) {
                            System.out.println("  ✓ Mejor resultado: " + displayName);
                            Map<String, BigDecimal> coords = new HashMap<>();
                            coords.put("lat", lat);
                            coords.put("lng", lng);
                            return coords;
                        }

                    } catch (Exception e) {
                        continue;
                    }
                }

                // Si no encontramos uno perfecto, usar el primero que sea válido
                try {
                    Map<String, Object> firstResult = response.get(0);
                    BigDecimal lat = new BigDecimal(firstResult.get("lat").toString());
                    BigDecimal lng = new BigDecimal(firstResult.get("lon").toString());

                    Map<String, BigDecimal> coords = new HashMap<>();
                    coords.put("lat", lat);
                    coords.put("lng", lng);
                    return coords;

                } catch (Exception e) {
                    System.out.println("  ✗ Error procesando primer resultado");
                }

            } else {
                System.out.println("  ✗ Sin resultados");
            }

        } catch (Exception e) {
            System.out.println("  ✗ Error en llamada: " + e.getMessage());
        }

        return null;
    }

    private String extractStreetName(String address) {
        String street = address;

        street = street.replaceAll("\\b\\d{5}\\b", "").trim();

        street = street.replaceAll(",\\s*león de los aldama.*$", "");
        street = street.replaceAll(",\\s*guanajuato.*$", "");
        street = street.replaceAll(",\\s*méxico\\s*$", "");

        street = street.replaceAll(",+", ",").replaceAll("^,|,$", "").trim();

        return street.isEmpty() ? address : street;
    }

    private boolean detectsCity(String address) {
        String addressLower = address.toLowerCase();

        String[] cities = {
                "guadalajara", "mexico", "méxico", "querétaro", "queretaro",
                "monterrey", "puebla", "tijuana", "león", "leon",
                "juárez", "juarez", "chihuahua", "mérida", "merida",
                "aguascalientes", "morelia", "celaya", "irapuato",
                "salamanca", "dolores hidalgo", "san miguel"
        };

        for (String city : cities) {
            if (addressLower.contains(city)) {
                return true;
            }
        }

        return false;
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
        System.out.println("Intentando geocodificación por área...");

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

    private Map<String, BigDecimal> tryGeocodingStrategy(String query, String strategyName) {

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
                System.out.println("Procesando: " + address);
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
                    System.out.println("✓ Geocodificada exitosamente");
                } else {
                    failed++;
                    System.out.println("✗ No se pudo geocodificar");
                }

                processed++;

                TimeUnit.MILLISECONDS.sleep(1200);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                failed++;
                processed++;
                System.out.println("Error procesando dirección: " + e.getMessage());
            }
        }

        for (String address : direccionesNoGeocodificadas) {
            try {
                System.out.println("\n==== PROCESANDO DIRECCIÓN " + (processed + 1) + " ====");
                System.out.println("Dirección: " + address);
                System.out.println("Longitud: " + address.length() + " caracteres");

                Map<String, BigDecimal> coords = geocodeAddress(address);

                if (coords != null) {
                    // ... resto del código existente para guardar ...
                    System.out.println("✓ GEOCODIFICACIÓN EXITOSA: " + coords.get("lat") + ", " + coords.get("lng"));
                } else {
                    failed++;
                    System.out.println("✗ GEOCODIFICACIÓN FALLIDA");
                }

                processed++;
                TimeUnit.MILLISECONDS.sleep(1200);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                failed++;
                processed++;
                System.out.println("✗ ERROR PROCESANDO: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("=== RESUMEN DE GEOCODIFICACIÓN ===");
        System.out.println("Procesadas: " + processed);
        System.out.println("Exitosas: " + successful);
        System.out.println("Fallidas: " + failed);
        if (processed > 0) {
            System.out.println("Tasa de éxito: " + String.format("%.1f%%", (successful * 100.0 / processed)));
        }
    }

    public void testSingleAddress(String address) {
        System.out.println("=== PRUEBA MANUAL DE DIRECCIÓN ===");
        System.out.println("Dirección original: " + address);

        String[] testQueries = {
                "Boulevard José María Morelos, León, México",
                "José María Morelos 402, León",
                "Morelos 402, León, Guanajuato",
                "León, Guanajuato, México",
                "León de los Aldama",
                "Boulevard Morelos, León"
        };

        for (String query : testQueries) {
            System.out.println("\n--- Probando: " + query + " ---");
            testDirectNominatim(query);
            try { Thread.sleep(1500); } catch (InterruptedException e) { break; }
        }
    }

    private void testDirectNominatim(String query) {
        String url = String.format(
                "https://nominatim.openstreetmap.org/search?format=json&q=%s&limit=5",
                java.net.URLEncoder.encode(query, StandardCharsets.UTF_8)
        );

        System.out.println("URL completa: " + url);

        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "TSSManager/1.0 (contacto@tssmanager.com)");
            headers.set("Accept", "application/json");

            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<String> rawResponse = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, entity, String.class
            );

            System.out.println("Status Code: " + rawResponse.getStatusCode());
            System.out.println("Headers: " + rawResponse.getHeaders());
            System.out.println("Body: " + rawResponse.getBody());

            // También parsear como JSON si es posible
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> jsonResponse = restTemplate.exchange(
                        url, org.springframework.http.HttpMethod.GET, entity, List.class
                ).getBody();

                if (jsonResponse != null && !jsonResponse.isEmpty()) {
                    System.out.println("JSON parseado correctamente - " + jsonResponse.size() + " resultados");
                } else {
                    System.out.println("JSON vacío o null");
                }
            } catch (Exception jsonError) {
                System.out.println("Error parseando JSON: " + jsonError.getMessage());
            }

        } catch (Exception e) {
            System.out.println("Error completo: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void testNominatimConfigurations(String query) {
        System.out.println("=== PROBANDO DIFERENTES CONFIGURACIONES ===");
        System.out.println("Query: " + query);

        String[][] configurations = {
                {"TSSManager/1.0 (contacto@tssmanager.com)", "application/json"},
                {"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "application/json"},
                {"TSSApp/1.0", "application/json"},
                {"RestTemplate", "*/*"}
        };

        String baseUrl = "https://nominatim.openstreetmap.org/search";

        for (int i = 0; i < configurations.length; i++) {
            System.out.println("\n--- Configuración " + (i+1) + " ---");
            System.out.println("User-Agent: " + configurations[i][0]);

            try {
                String url = String.format("%s?format=json&q=%s&limit=3",
                        baseUrl, java.net.URLEncoder.encode(query, StandardCharsets.UTF_8));

                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.set("User-Agent", configurations[i][0]);
                headers.set("Accept", configurations[i][1]);
                headers.set("Accept-Language", "es,en");

                org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

                org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                        url, org.springframework.http.HttpMethod.GET, entity, String.class
                );

                System.out.println("Status: " + response.getStatusCode());
                String body = response.getBody();

                if (body != null && !body.trim().isEmpty()) {
                    if (body.startsWith("[") && body.length() > 10) {
                        System.out.println("✓ Respuesta JSON válida (" + body.length() + " chars)");
                        // Intentar contar resultados
                        long count = body.chars().filter(ch -> ch == '{').count();
                        System.out.println("Aproximadamente " + count + " resultados");
                    } else {
                        System.out.println("Respuesta: " + body.substring(0, Math.min(200, body.length())));
                    }
                } else {
                    System.out.println("✗ Respuesta vacía");
                }

                Thread.sleep(2000); // Pausa entre requests

            } catch (Exception e) {
                System.out.println("✗ Error: " + e.getMessage());
            }
        }
    }
}