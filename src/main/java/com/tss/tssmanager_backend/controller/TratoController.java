package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.TratoCountDTO;
import com.tss.tssmanager_backend.dto.TratoDTO;
import com.tss.tssmanager_backend.dto.ActividadDTO;
import com.tss.tssmanager_backend.dto.NotaTratoDTO;
import com.tss.tssmanager_backend.entity.Actividad;
import com.tss.tssmanager_backend.enums.EstatusActividadEnum;
import com.tss.tssmanager_backend.repository.ActividadRepository;
import com.tss.tssmanager_backend.service.TratoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    public List<TratoDTO> filtrarTratos(
            @RequestParam(required = false) Integer empresaId,
            @RequestParam(required = false) Integer propietarioId,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate) {
        return tratoService.filtrarTratos(empresaId, propietarioId, startDate, endDate);
    }

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
    public ResponseEntity<List<ActividadDTO>> getPendientesByAsignadoA(
            @RequestParam Integer asignadoAId,
            @RequestParam(required = false) Instant fecha) {

        LocalDate targetDate = (fecha != null)
                ? fecha.atZone(ZoneOffset.UTC).toLocalDate()
                : LocalDate.now(ZoneOffset.UTC);

        List<Actividad> actividades = actividadRepository.findByAsignadoAIdAndFechaLimiteAndEstatus(
                asignadoAId, targetDate, EstatusActividadEnum.ABIERTA);

        return ResponseEntity.ok(actividades.stream()
                .map(tratoService::convertToDTO)
                .collect(Collectors.toList()));
    }
}