package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.CuentaPorPagarDTO;
import com.tss.tssmanager_backend.dto.RegenerarRequestDTO;
import com.tss.tssmanager_backend.dto.ReporteCuentasPorPagarDTO;
import com.tss.tssmanager_backend.entity.CuentaPorPagar;
import com.tss.tssmanager_backend.service.CuentaPorPagarService;
import com.tss.tssmanager_backend.service.ReporteCuentasPorPagarService;
import com.tss.tssmanager_backend.service.TransaccionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/cuentas-por-pagar")
public class CuentaPorPagarController {

    @Autowired
    private CuentaPorPagarService cuentasPorPagarService;
    @Autowired
    private ReporteCuentasPorPagarService reporteService;

    @GetMapping
    public ResponseEntity<List<CuentaPorPagar>> obtenerTodasLasCuentasPorPagar(
            @RequestParam(required = false) String estatus) {
        try {
            List<CuentaPorPagar> cuentas = cuentasPorPagarService.obtenerTodas(estatus);
            return new ResponseEntity<>(cuentas, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/marcar-como-pagada")
    public ResponseEntity<Void> marcarComoPagada(@RequestBody CuentaPorPagarDTO request) {
        try {
            cuentasPorPagarService.marcarComoPagada(
                    request.getId(),
                    request.getFechaPago(),
                    request.getMontoPago(),
                    request.getFormaPago(),
                    request.getUsuarioId(),
                    false,
                    request.getCantidadCreditos()
            );
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
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
            if (dto.getId() == null || dto.getMontoPago() == null || dto.getFormaPago() == null) {
                return ResponseEntity.badRequest().build();
            }

            cuentasPorPagarService.marcarComoPagada(
                    dto.getId(),
                    LocalDate.now(),
                    dto.getMontoPago(),
                    dto.getFormaPago(),
                    dto.getUsuarioId() != null ? dto.getUsuarioId() : 1,
                    true,
                    dto.getCantidadCreditos()
            );
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            System.err.println("Error de validación: " + e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            System.err.println("Error interno: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/reporte/datos")
    public ResponseEntity<ReporteCuentasPorPagarDTO> obtenerDatosReporte(
            @RequestParam String fechaInicio,
            @RequestParam String fechaFin,
            @RequestParam(defaultValue = "Todas") String filtroEstatus) {
        try {
            LocalDate inicio = LocalDate.parse(fechaInicio);
            LocalDate fin = LocalDate.parse(fechaFin);

            ReporteCuentasPorPagarDTO datos = reporteService.generarDatosReporte(inicio, fin, filtroEstatus);
            return ResponseEntity.ok(datos);
        } catch (Exception e) {
            System.err.println("Error al generar datos del reporte: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/reporte/pdf")
    public ResponseEntity<byte[]> generarReportePDF(
            @RequestParam String fechaInicio,
            @RequestParam String fechaFin,
            @RequestParam(defaultValue = "Todas") String filtroEstatus) {
        try {
            LocalDate inicio = LocalDate.parse(fechaInicio);
            LocalDate fin = LocalDate.parse(fechaFin);

            ReporteCuentasPorPagarDTO datos = reporteService.generarDatosReporte(inicio, fin, filtroEstatus);
            byte[] pdfBytes = reporteService.generarReportePDF(datos);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String fileName = String.format("reporte_cuentas_por_pagar_%s_%s.pdf",
                    inicio.format(formatter), fin.format(formatter));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            System.err.println("Error al generar reporte PDF: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<CuentaPorPagar> obtenerCuentaPorId(@PathVariable Integer id) {
        try {
            CuentaPorPagar cuenta = cuentasPorPagarService.obtenerPorId(id);
            return ResponseEntity.ok(cuenta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}