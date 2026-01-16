package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.*;
import com.tss.tssmanager_backend.entity.Actividad;
import com.tss.tssmanager_backend.enums.EstatusActividadEnum;
import com.tss.tssmanager_backend.repository.ActividadRepository;
import com.tss.tssmanager_backend.service.TratoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.Time;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tratos")
public class TratoController {

    @Autowired
    private TratoService tratoService;

    @Autowired
    private ActividadRepository actividadRepository;

    @GetMapping("/filtrar")
    public ResponseEntity<?> filtrarTratos(
            @RequestParam(required = false) Integer empresaId,
            @RequestParam(required = false) Integer propietarioId,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "false") boolean enablePagination) {

        try {
            if (enablePagination) {
                // Versión paginada para casos con muchos datos
                Page<TratoDTO> result = tratoService.filtrarTratosPaginados(
                        empresaId, propietarioId, startDate, endDate,
                        PageRequest.of(page, size, Sort.by("fechaModificacion").descending())
                );
                return ResponseEntity.ok(result);
            } else {
                List<TratoDTO> result = tratoService.filtrarTratos(empresaId, propietarioId, startDate, endDate);
                return ResponseEntity.ok(result);
            }
        } catch (Exception e) {
            // Log del error para debugging
            System.err.println("Error en filtrarTratos: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Error interno del servidor",
                            "message", "No se pudieron cargar los tratos. Intente nuevamente.",
                            "timestamp", Instant.now()
                    ));
        }
    }

    @GetMapping("/filtrar/basico")
    public ResponseEntity<List<TratoBasicoDTO>> filtrarTratosBasico(
            @RequestParam(required = false) Integer empresaId,
            @RequestParam(required = false) Integer propietarioId,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate) {

        try {
            List<TratoBasicoDTO> result = tratoService.filtrarTratosBasico(
                    empresaId, propietarioId, startDate, endDate);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.err.println("Error en filtrarTratosBasico: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}/detalles")
    public ResponseEntity<TratoDTO> getTratoDetalles(@PathVariable Integer id) {
        try {
            TratoDTO trato = tratoService.getTratoConDetalles(id);
            return ResponseEntity.ok(trato);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Transactional
    @GetMapping
    public List<TratoDTO> listarTratos() {
        return tratoService.listarTratos();
    }

    @PostMapping
    public TratoDTO crearTrato(@RequestBody TratoDTO tratoDTO) {
        return tratoService.crearTrato(tratoDTO);
    }

    @PutMapping("/{id}/mover-fase")
    public TratoDTO moverFase(@PathVariable Integer id, @RequestParam String nuevaFase) {
        return tratoService.moverFase(id, nuevaFase);
    }

    @PostMapping("/actividades")
    public ActividadDTO programarActividad(@RequestBody ActividadDTO actividadDTO) {
        return tratoService.programarActividad(actividadDTO);
    }

    @PutMapping("/{tratoId}/actividades/{actividadId}")
    public ActividadDTO reprogramarActividad(@PathVariable Integer tratoId, @PathVariable Integer actividadId, @RequestBody ActividadDTO actividadDTO) {
        ActividadDTO updatedActividad = tratoService.reprogramarActividad(actividadId, actividadDTO);
        return updatedActividad;
    }

    @PostMapping("/{tratoId}/notas")
    public NotaTratoDTO agregarNota(@PathVariable Integer tratoId, @RequestBody String nota) {
        return tratoService.agregarNota(tratoId, nota);
    }

    @PutMapping("/{tratoId}/notas/{notaId}")
    public NotaTratoDTO editarNota(@PathVariable Integer tratoId, @PathVariable Long notaId, @RequestBody String nota) {
        return tratoService.editarNota(tratoId, notaId, nota);
    }

    @DeleteMapping("/{tratoId}/notas/{notaId}")
    public void eliminarNota(@PathVariable Integer tratoId, @PathVariable Long notaId) {
        tratoService.eliminarNota(tratoId, notaId);
    }

    @PutMapping("/{id}")
    public TratoDTO editarTrato(@PathVariable Integer id, @RequestBody TratoDTO tratoDTO) {
        return tratoService.editarTrato(id, tratoDTO);
    }

    @PutMapping("/actividades/{id}/completar")
    public ActividadDTO completarActividad(@PathVariable Integer id, @RequestBody ActividadDTO actividadDTO) {
        return tratoService.completarActividad(id, actividadDTO);
    }

    @GetMapping("/{id}")
    public TratoDTO getTratoById(@PathVariable Integer id) {
        return tratoService.getTratoById(id);
    }

    @GetMapping("/contar-por-propietario")
    public List<TratoDTO> contarTratosPorPropietario(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate) {
        return tratoService.contarTratosPorPropietario(startDate, endDate);
    }

    @GetMapping("/contar-por-fase")
    public List<TratoCountDTO> contarTratosPorFase(
            @RequestParam(required = false) Integer propietarioId,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate) {
        return tratoService.contarTratosPorFaseYPropietario(propietarioId, startDate, endDate);
    }

    @GetMapping("/actividades/pendientes")
    public ResponseEntity<List<ActividadConEmpresaDTO>> getPendientesByAsignadoA(
            @RequestParam Integer asignadoAId,
            @RequestParam(required = false) Instant fecha) {

        LocalDate targetDate = (fecha != null)
                ? fecha.atZone(ZoneId.of("America/Mexico_City")).toLocalDate()
                : LocalDate.now(ZoneId.of("America/Mexico_City"));

        List<ActividadConEmpresaDTO> actividades = tratoService.getPendientesConEmpresa(asignadoAId, targetDate);
        return ResponseEntity.ok(actividades);
    }

    @PostMapping("/{tratoId}/actividades/{actividadId}/enviar-notificacion-email")
    public ResponseEntity<String> enviarNotificacionEmail(@PathVariable Integer tratoId, @PathVariable Integer actividadId) {
        try {
            tratoService.enviarCorreoReunion(actividadId, tratoId);
            return ResponseEntity.ok("Correo enviado exitosamente");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al enviar correo: " + e.getMessage());
        }
    }

    @PostMapping("/{tratoId}/actividades/{actividadId}/enviar-notificacion-email-reprogramada")
    public ResponseEntity<String> enviarNotificacionEmailReprogramada(@PathVariable Integer tratoId, @PathVariable Integer actividadId) {
        try {
            tratoService.enviarCorreoReunionReprogramada(actividadId, tratoId);
            return ResponseEntity.ok("Correo de reprogramación enviado exitosamente");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al enviar correo: " + e.getMessage());
        }
    }

    @GetMapping("/{tratoId}/contacto/verificar-datos")
    public ResponseEntity<Map<String, Object>> verificarDatosContacto(@PathVariable Integer tratoId) {
        try {
            Map<String, Object> datos = tratoService.verificarDatosContacto(tratoId);
            return ResponseEntity.ok(datos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{tratoId}/generar-mensaje-whatsapp")
    public ResponseEntity<Map<String, String>> generarMensajeWhatsApp(@PathVariable Integer tratoId, @RequestBody Map<String, Integer> request) {
        try {
            Integer actividadId = request.get("actividadId");
            boolean esReprogramacion = request.getOrDefault("esReprogramacion", 0) == 1;
            Map<String, String> resultado = tratoService.generarMensajeWhatsApp(tratoId, actividadId, esReprogramacion);
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/verificar-conflicto-horario")
    public ResponseEntity<Map<String, Boolean>> verificarConflictoHorario(
            @RequestParam Integer asignadoAId,
            @RequestParam String fecha,
            @RequestParam String hora,
            @RequestParam(required = false) String duracion,
            @RequestParam(required = false) Integer actividadIdExcluir) {

        try {
            LocalDate fechaActividad = LocalDate.parse(fecha);
            Time horaActividad = Time.valueOf(hora);

            boolean hayConflicto = tratoService.existeConflictoHorario(
                    asignadoAId, fechaActividad, horaActividad, duracion, actividadIdExcluir);

            Map<String, Boolean> response = new HashMap<>();
            response.put("hayConflicto", hayConflicto);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Boolean> response = new HashMap<>();
            response.put("hayConflicto", false);
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/{tratoId}/interacciones")
    public ResponseEntity<Map<String, Object>> crearInteraccionGenerica(
            @PathVariable Integer tratoId,
            @RequestBody InteraccionGenericaDTO interaccionDTO) {
        try {
            Map<String, Object> response = tratoService.crearInteraccionGenerica(tratoId, interaccionDTO);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

    }

    @PutMapping("/actividades/{id}/editar")
    public ActividadDTO editarInteraccion(@PathVariable Integer id, @RequestBody ActividadDTO actividadDTO) {
        return tratoService.editarInteraccion(id, actividadDTO);
    }

    @DeleteMapping("/actividades/{id}")
    public ResponseEntity<Map<String, Object>> eliminarActividad(@PathVariable Integer id) {
        try {
            tratoService.eliminarActividad(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Actividad eliminada exitosamente");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminarTrato(@PathVariable Integer id) {
        try {
            tratoService.eliminarTrato(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Trato eliminado exitosamente");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}