package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.CuentaPorPagarDTO;
import com.tss.tssmanager_backend.dto.RegenerarRequestDTO;
import com.tss.tssmanager_backend.entity.CuentaPorPagar;
import com.tss.tssmanager_backend.service.CuentaPorPagarService;
import com.tss.tssmanager_backend.service.TransaccionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/cuentas-por-pagar")
public class CuentaPorPagarController {

    @Autowired
    private CuentaPorPagarService cuentasPorPagarService;

    @GetMapping
    public ResponseEntity<List<CuentaPorPagar>> obtenerTodasLasCuentasPorPagar() {
        try {
            List<CuentaPorPagar> cuentas = cuentasPorPagarService.obtenerTodas();
            return new ResponseEntity<>(cuentas, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/marcar-como-pagada")
    public ResponseEntity<Void> marcarComoPagada(@RequestBody CuentaPorPagarDTO request) {
        try {
            cuentasPorPagarService.marcarComoPagada(request.getId(), request.getMonto(), request.getFormaPago(), request.getUsuarioId());
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<CuentaPorPagar> actualizarCuentaPorPagar(
            @PathVariable Integer id,
            @RequestBody CuentaPorPagarDTO request) {
        try {
            CuentaPorPagar cuentaActualizada = cuentasPorPagarService.actualizarCuentaPorPagar(
                    id,
                    request.getFechaPago(),
                    request.getMonto(),
                    request.getFormaPago(),
                    request.getNota()
            );
            return ResponseEntity.ok(cuentaActualizada);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCuentaPorPagar(@PathVariable Integer id, @RequestParam Integer usuarioId) {
        try {
            cuentasPorPagarService.eliminarCuentaPorPagar(id, usuarioId);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/regenerar")
    public ResponseEntity<Void> regenerarCuentasPorPagar(@RequestBody RegenerarRequestDTO request) {
        try {
            if (request.getNuevoMonto() != null) {
                cuentasPorPagarService.regenerarCuentasPorPagarManual(
                        request.getTransaccionId(),
                        request.getFechaUltimoPago(),
                        request.getNuevoMonto()
                );
            } else {
                cuentasPorPagarService.regenerarCuentasPorPagarManual(
                        request.getTransaccionId(),
                        request.getFechaUltimoPago()
                );
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.err.println("Error al regenerar cuentas por pagar: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/marcar-como-pagada-calendario")
    public ResponseEntity<Void> marcarComoPagadaDesdeCalendario(@RequestBody CuentaPorPagarDTO dto) {
        try {
            cuentasPorPagarService.marcarComoPagada(
                    dto.getId(),
                    LocalDate.now(),
                    dto.getMonto(),
                    dto.getFormaPago(),
                    dto.getUsuarioId(),
                    true
            );
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}