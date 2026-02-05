package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.ComisionDTO;
import com.tss.tssmanager_backend.dto.CrearComisionDTO;
import com.tss.tssmanager_backend.entity.CuentasTransacciones;
import com.tss.tssmanager_backend.service.ComisionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/comisiones")
public class ComisionController {

    @Autowired
    private ComisionService comisionService;

    @GetMapping
    public ResponseEntity<List<ComisionDTO>> obtenerTodas() {
        try {
            List<ComisionDTO> comisiones = comisionService.obtenerTodas();
            return ResponseEntity.ok(comisiones);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ComisionDTO> obtenerPorId(@PathVariable Integer id) {
        try {
            ComisionDTO comision = comisionService.obtenerPorId(id);
            return ResponseEntity.ok(comision);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping
    public ResponseEntity<ComisionDTO> crearComision(@RequestBody CrearComisionDTO dto) {
        try {
            ComisionDTO comision = comisionService.crearComisionManual(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(comision);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ComisionDTO> actualizarComision(
            @PathVariable Integer id,
            @RequestBody CrearComisionDTO dto) {
        try {
            ComisionDTO comision = comisionService.actualizarComision(id, dto);
            return ResponseEntity.ok(comision);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarComision(@PathVariable Integer id) {
        try {
            comisionService.eliminarComision(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/saldo-total")
    public ResponseEntity<BigDecimal> obtenerSaldoTotal() {
        try {
            BigDecimal saldo = comisionService.obtenerSaldoTotalPendiente();
            return ResponseEntity.ok(saldo);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/cuentas-comisiones")
    public ResponseEntity<List<CuentasTransacciones>> obtenerCuentasComisiones() {
        try {
            List<CuentasTransacciones> cuentas = comisionService.obtenerCuentasComisiones();
            return ResponseEntity.ok(cuentas);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}