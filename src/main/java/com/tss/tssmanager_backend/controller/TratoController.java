package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.TratoDTO;
import com.tss.tssmanager_backend.dto.ActividadDTO;
import com.tss.tssmanager_backend.dto.NotaTratoDTO;
import com.tss.tssmanager_backend.service.TratoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/tratos")
public class TratoController {

    @Autowired
    private TratoService tratoService;

    @GetMapping("/filtrar")
    public List<TratoDTO> filtrarTratos(
            @RequestParam(required = false) Integer propietarioId,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate) {
        return tratoService.filtrarTratos(propietarioId, startDate, endDate);
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
}