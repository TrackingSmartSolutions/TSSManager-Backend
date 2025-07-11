package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.entity.CategoriaTransacciones;
import com.tss.tssmanager_backend.entity.CuentasTransacciones;
import com.tss.tssmanager_backend.entity.Transaccion;
import com.tss.tssmanager_backend.repository.CategoriaTransaccionesRepository;
import com.tss.tssmanager_backend.repository.CuentasTransaccionesRepository;
import com.tss.tssmanager_backend.service.TransaccionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TransaccionController {

    @Autowired
    private TransaccionService transaccionService;

    @Autowired
    private CategoriaTransaccionesRepository categoriaTransaccionesRepository;

    @Autowired
    private CuentasTransaccionesRepository cuentasTransaccionesRepository;


    @PostMapping("/transacciones/crear")
    public ResponseEntity<Transaccion> agregarTransaccion(@RequestBody Transaccion transaccion) {
        try {

            if (transaccion.getCategoria() == null && transaccion.getCategoriaId() != null) {
                CategoriaTransacciones categoria = categoriaTransaccionesRepository.findById(transaccion.getCategoriaId())
                        .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada con ID: " + transaccion.getCategoriaId()));
                transaccion.setCategoria(categoria);
            }


            if (transaccion.getCuenta() == null && transaccion.getCuentaId() != null) {
                CuentasTransacciones cuenta = cuentasTransaccionesRepository.findById(transaccion.getCuentaId())
                        .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada con ID: " + transaccion.getCuentaId()));
                transaccion.setCuenta(cuenta);
            }

            Transaccion savedTransaccion = transaccionService.agregarTransaccion(transaccion);
            return new ResponseEntity<>(savedTransaccion, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/transacciones")
    public ResponseEntity<List<Transaccion>> obtenerTodasLasTransacciones() {
        try {
            List<Transaccion> transacciones = transaccionService.obtenerTodas();
            return new ResponseEntity<>(transacciones, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/transacciones/{id}")
    public ResponseEntity<Void> eliminarTransaccion(@PathVariable Integer id) {
        try {
            transaccionService.eliminarTransaccion(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @PostMapping("/categorias/crear")
    public ResponseEntity<CategoriaTransacciones> agregarCategoria(@RequestBody CategoriaTransacciones categoria) {
        try {
            CategoriaTransacciones savedCategoria = transaccionService.agregarCategoria(categoria);
            return new ResponseEntity<>(savedCategoria, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/categorias/{id}")
    public ResponseEntity<Void> eliminarCategoria(@PathVariable Integer id) {
        try {
            transaccionService.eliminarCategoria(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/categorias")
    public ResponseEntity<List<CategoriaTransacciones>> obtenerTodasLasCategorias() {
        try {
            List<CategoriaTransacciones> categorias = transaccionService.obtenerTodasLasCategorias();
            return new ResponseEntity<>(categorias, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/cuentas/crear")
    public ResponseEntity<CuentasTransacciones> agregarCuenta(@RequestBody CuentasTransacciones cuenta) {
        try {
            if (cuenta.getCategoria() == null && cuenta.getCategoriaId() != null) {
                CategoriaTransacciones categoria = categoriaTransaccionesRepository.findById(cuenta.getCategoriaId())
                        .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada con ID: " + cuenta.getCategoriaId()));
                cuenta.setCategoria(categoria);
            } else if (cuenta.getCategoria() == null) {
                throw new IllegalArgumentException("La categoría es obligatoria");
            }

            CuentasTransacciones savedCuenta = transaccionService.agregarCuenta(cuenta);
            return new ResponseEntity<>(savedCuenta, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/cuentas/{id}")
    public ResponseEntity<Void> eliminarCuenta(@PathVariable Integer id) {
        try {
            transaccionService.eliminarCuenta(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/cuentas")
    public ResponseEntity<List<CuentasTransacciones>> obtenerTodasLasCuentas() {
        try {
            List<CuentasTransacciones> cuentas = transaccionService.obtenerTodasLasCuentas();
            return new ResponseEntity<>(cuentas, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}