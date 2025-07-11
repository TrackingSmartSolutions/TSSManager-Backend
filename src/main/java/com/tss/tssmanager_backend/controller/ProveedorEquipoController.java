package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.entity.ProveedorEquipo;
import com.tss.tssmanager_backend.service.ProveedorEquipoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedorEquipoController {

    @Autowired
    private ProveedorEquipoService service;

    @GetMapping
    public ResponseEntity<Iterable<ProveedorEquipo>> obtenerTodosLosProveedoresEquipo() {
        return ResponseEntity.ok(service.obtenerTodosLosProveedoresEquipo());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProveedorEquipo> obtenerProveedorEquipo(@PathVariable Integer id) {
        return ResponseEntity.ok(service.obtenerProveedorEquipo(id));
    }

    @PostMapping
    public ResponseEntity<ProveedorEquipo> guardarProveedorEquipo(@RequestBody ProveedorEquipo proveedor) {
        return ResponseEntity.ok(service.guardarProveedorEquipo(proveedor));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProveedorEquipo> actualizarProveedorEquipo(@PathVariable Integer id, @RequestBody ProveedorEquipo proveedor) {
        return ResponseEntity.ok(service.actualizarProveedorEquipo(id, proveedor));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarProveedorEquipo(@PathVariable Integer id) {
        service.eliminarProveedorEquipo(id);
        return ResponseEntity.noContent().build();
    }
}