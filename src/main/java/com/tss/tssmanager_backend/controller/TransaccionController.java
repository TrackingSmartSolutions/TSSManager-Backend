package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.entity.CategoriaTransacciones;
import com.tss.tssmanager_backend.entity.CuentasTransacciones;
import com.tss.tssmanager_backend.entity.Transaccion;
import com.tss.tssmanager_backend.enums.EsquemaTransaccionEnum;
import com.tss.tssmanager_backend.enums.TipoTransaccionEnum;
import com.tss.tssmanager_backend.repository.CategoriaTransaccionesRepository;
import com.tss.tssmanager_backend.repository.CuentasTransaccionesRepository;
import com.tss.tssmanager_backend.service.TransaccionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
    public ResponseEntity<Transaccion> agregarTransaccion(@RequestBody Map<String, Object> request) {
        try {
            Transaccion transaccion = new Transaccion();

            // Mapear campos básicos
            if (request.containsKey("fecha")) {
                transaccion.setFecha(LocalDate.parse(request.get("fecha").toString()));
            }
            if (request.containsKey("tipo")) {
                transaccion.setTipo(TipoTransaccionEnum.valueOf(request.get("tipo").toString()));
            }
            if (request.containsKey("monto")) {
                transaccion.setMonto(new BigDecimal(request.get("monto").toString()));
            }
            if (request.containsKey("esquema")) {
                transaccion.setEsquema(EsquemaTransaccionEnum.valueOf(request.get("esquema").toString()));
            }
            if (request.containsKey("numeroPagos")) {
                transaccion.setNumeroPagos(Integer.parseInt(request.get("numeroPagos").toString()));
            }
            if (request.containsKey("fechaPago")) {
                transaccion.setFechaPago(LocalDate.parse(request.get("fechaPago").toString()));
            }
            if (request.containsKey("formaPago")) {
                transaccion.setFormaPago(request.get("formaPago").toString());
            }
            if (request.containsKey("notas")) {
                transaccion.setNotas(request.get("notas").toString());
            }

            // Manejar categoría
            if (request.containsKey("categoriaId")) {
                CategoriaTransacciones categoria = categoriaTransaccionesRepository.findById(Integer.parseInt(request.get("categoriaId").toString()))
                        .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada con ID: " + request.get("categoriaId")));
                transaccion.setCategoria(categoria);
            }

            // Manejar cuenta existente
            if (request.containsKey("cuentaId") && request.get("cuentaId") != null && !request.get("cuentaId").toString().isEmpty()) {
                CuentasTransacciones cuenta = cuentasTransaccionesRepository.findById(Integer.parseInt(request.get("cuentaId").toString()))
                        .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada con ID: " + request.get("cuentaId")));
                transaccion.setCuenta(cuenta);
            }

            // Manejar nombre de cuenta para crear dinámicamente
            if (request.containsKey("cuentaNombre") && request.get("cuentaNombre") != null && !request.get("cuentaNombre").toString().isEmpty()) {
                transaccion.setNombreCuenta(request.get("cuentaNombre").toString());
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