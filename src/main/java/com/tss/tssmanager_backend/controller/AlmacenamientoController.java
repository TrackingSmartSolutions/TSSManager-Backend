package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.*;
import com.tss.tssmanager_backend.entity.ConfiguracionAlmacenamiento;
import com.tss.tssmanager_backend.entity.HistorialLimpieza;
import com.tss.tssmanager_backend.repository.ConfiguracionAlmacenamientoRepository;
import com.tss.tssmanager_backend.service.AlmacenamientoService;
import com.tss.tssmanager_backend.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/almacenamiento")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Almacenamiento", description = "Gestión de almacenamiento y limpieza de datos")
public class AlmacenamientoController {

    @Autowired
    private AlmacenamientoService almacenamientoService;

    @Autowired
    private ConfiguracionAlmacenamientoRepository configuracionRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/estadisticas")
    @Operation(summary = "Obtener estadísticas de almacenamiento",
            description = "Devuelve estadísticas detalladas de uso de almacenamiento por tabla")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estadísticas obtenidas exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public ResponseEntity<List<EstadisticasAlmacenamientoDTO>> obtenerEstadisticasAlmacenamiento() {
        try {
            List<EstadisticasAlmacenamientoDTO> estadisticas = almacenamientoService.obtenerEstadisticasAlmacenamiento();
            return ResponseEntity.ok(estadisticas);
        } catch (Exception e) {
            log.error("Error al obtener estadísticas de almacenamiento", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/resumen")
    @Operation(summary = "Obtener resumen de almacenamiento",
            description = "Devuelve un resumen general del uso de almacenamiento")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resumen obtenido exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public ResponseEntity<ResumenAlmacenamientoDTO> obtenerResumenAlmacenamiento() {
        try {
            ResumenAlmacenamientoDTO resumen = almacenamientoService.obtenerResumenAlmacenamiento();
            return ResponseEntity.ok(resumen);
        } catch (Exception e) {
            log.error("Error al obtener resumen de almacenamiento", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/configuracion")
    @Operation(summary = "Obtener configuración de tablas",
            description = "Devuelve la configuración de tablas habilitadas para limpieza")
    public ResponseEntity<List<ConfiguracionAlmacenamientoDTO>> obtenerConfiguracionTablasHabilitadas() {
        try {
            List<ConfiguracionAlmacenamientoDTO> configuracion = almacenamientoService.obtenerConfiguracionTablasHabilitadas();
            return ResponseEntity.ok(configuracion);
        } catch (Exception e) {
            log.error("Error al obtener configuración de tablas", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/estadisticas/{tablaNombre}")
    @Operation(summary = "Obtener estadísticas de tabla específica",
            description = "Devuelve estadísticas de una tabla específica con criterios de antigüedad")
    public ResponseEntity<EstadisticasAlmacenamientoDTO> obtenerEstadisticasTablaEspecifica(
            @Parameter(description = "Nombre de la tabla") @PathVariable String tablaNombre,
            @Parameter(description = "Días de antigüedad para considerar registros antiguos") @RequestParam(defaultValue = "180") Integer diasAntiguedad) {
        try {
            EstadisticasAlmacenamientoDTO estadisticas = almacenamientoService.obtenerEstadisticasTablaEspecifica(tablaNombre, diasAntiguedad);
            return ResponseEntity.ok(estadisticas);
        } catch (Exception e) {
            log.error("Error al obtener estadísticas de tabla específica: " + tablaNombre, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/limpieza/manual")
    @Operation(summary = "Ejecutar limpieza manual",
            description = "Ejecuta una limpieza manual de registros según los criterios especificados")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Limpieza ejecutada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public ResponseEntity<ResultadoLimpiezaDTO> ejecutarLimpiezaManual(
            @RequestBody SolicitudLimpiezaDTO solicitud,
            HttpServletRequest request) {
        try {
            // Obtener usuario ID del token
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            Long usuarioIdLong = jwtUtil.extractUserId(token);
            Integer usuarioId = usuarioIdLong != null ? usuarioIdLong.intValue() : null;

            // Validar solicitud
            if (solicitud.getTablaNombre() == null || solicitud.getDiasAntiguedad() == null) {
                return ResponseEntity.badRequest().body(
                        new ResultadoLimpiezaDTO(null, 0, BigDecimal.ZERO, "Tabla o días de antigüedad no especificados", false)
                );
            }

            // Requerir confirmación explícita
            if (solicitud.getConfirmarEliminacion() == null || !solicitud.getConfirmarEliminacion()) {
                return ResponseEntity.badRequest().body(
                        new ResultadoLimpiezaDTO(solicitud.getTablaNombre(), 0, BigDecimal.ZERO, "Confirmación de eliminación requerida", false)
                );
            }

            // Actualizar días de retención temporalmente para la limpieza
            Optional<ConfiguracionAlmacenamiento> config = configuracionRepository.findByTablaNombre(solicitud.getTablaNombre());
            if (!config.isPresent() || !config.get().getHabilitadoLimpieza()) {
                return ResponseEntity.badRequest().body(
                        new ResultadoLimpiezaDTO(solicitud.getTablaNombre(), 0, BigDecimal.ZERO, "Tabla no habilitada para limpieza", false)
                );
            }

            // Guardar días de retención originales
            Integer diasOriginales = config.get().getDiasRetencion();
            config.get().setDiasRetencion(solicitud.getDiasAntiguedad());
            configuracionRepository.save(config.get());

            ResultadoLimpiezaDTO resultado = almacenamientoService.ejecutarLimpiezaManual(solicitud, usuarioId);

            // Restaurar días de retención originales
            config.get().setDiasRetencion(diasOriginales);
            configuracionRepository.save(config.get());

            if (resultado.getExito()) {
                return ResponseEntity.ok(resultado);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultado);
            }

        } catch (Exception e) {
            log.error("Error al ejecutar limpieza manual", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResultadoLimpiezaDTO(null, 0, BigDecimal.ZERO, "Error interno del servidor: " + e.getMessage(), false));
        }
    }

    @PostMapping("/limpieza/automatica")
    @Operation(summary = "Ejecutar limpieza automática",
            description = "Ejecuta la limpieza automática de tratos CERRADO_PERDIDO sin actividad")
    public ResponseEntity<Map<String, Object>> ejecutarLimpiezaAutomatica() {
        try {
            Integer tratosEliminados = almacenamientoService.ejecutarLimpiezaAutomatica();

            Map<String, Object> response = Map.of(
                    "success", true,
                    "tratosEliminados", tratosEliminados,
                    "mensaje", "Limpieza automática ejecutada exitosamente"
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error al ejecutar limpieza automática", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "mensaje", "Error al ejecutar limpieza automática"));
        }
    }

    @GetMapping("/historial")
    @Operation(summary = "Obtener historial de limpieza",
            description = "Devuelve el historial de limpiezas realizadas")
    public ResponseEntity<List<HistorialLimpieza>> obtenerHistorialLimpieza(
            @Parameter(description = "Días hacia atrás para buscar historial") @RequestParam(defaultValue = "30") Integer dias) {
        try {
            List<HistorialLimpieza> historial = almacenamientoService.obtenerHistorialLimpieza(dias);
            return ResponseEntity.ok(historial);
        } catch (Exception e) {
            log.error("Error al obtener historial de limpieza", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/validar-limpieza")
    @Operation(summary = "Validar criterios de limpieza",
            description = "Valida y simula una limpieza sin ejecutarla")
    public ResponseEntity<EstadisticasAlmacenamientoDTO> validarLimpieza(
            @RequestParam String tablaNombre,
            @RequestParam Integer diasAntiguedad) {
        try {
            EstadisticasAlmacenamientoDTO estadisticas = almacenamientoService.obtenerEstadisticasTablaEspecifica(tablaNombre, diasAntiguedad);
            return ResponseEntity.ok(estadisticas);
        } catch (Exception e) {
            log.error("Error al validar criterios de limpieza", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}