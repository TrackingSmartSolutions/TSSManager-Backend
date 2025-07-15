package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.*;
import com.tss.tssmanager_backend.service.CalendarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/calendario")
public class CalendarioController {

    @Autowired
    private CalendarioService calendarioService;

    @GetMapping("/eventos")
    public ResponseEntity<List<EventoCalendarioDTO>> obtenerEventos(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate,
            @RequestParam(required = false) String usuario) {
        return ResponseEntity.ok(calendarioService.obtenerEventos(startDate, endDate, usuario));
    }
}