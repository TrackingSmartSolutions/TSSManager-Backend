package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.entity.CategoriaTransacciones;
import com.tss.tssmanager_backend.entity.CuentaPorPagar;
import com.tss.tssmanager_backend.entity.CuentasTransacciones;
import com.tss.tssmanager_backend.entity.Transaccion;
import com.tss.tssmanager_backend.enums.EsquemaTransaccionEnum;
import com.tss.tssmanager_backend.repository.CategoriaTransaccionesRepository;
import com.tss.tssmanager_backend.repository.CuentaPorPagarRepository;
import com.tss.tssmanager_backend.repository.CuentasTransaccionesRepository;
import com.tss.tssmanager_backend.repository.TransaccionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TransaccionService {

    @Autowired
    private TransaccionRepository transaccionRepository;
    @Autowired
    private CategoriaTransaccionesRepository categoriaRepository;
    @Autowired
    private CuentasTransaccionesRepository cuentaRepository;
    @Autowired
    private CuentaPorPagarRepository cuentaPorPagarRepository;

    public List<Transaccion> obtenerTodas() {
        return transaccionRepository.findAll();
    }

    @Transactional
    public void eliminarTransaccion(Integer id) {
        if (!transaccionRepository.existsById(id)) {
            throw new IllegalArgumentException("La transacción con ID " + id + " no existe.");
        }
        transaccionRepository.deleteById(id);
    }

    @Transactional
    public Transaccion agregarTransaccion(Transaccion transaccion) {
        if (transaccion.getFecha() == null || transaccion.getTipo() == null || transaccion.getCategoria() == null ||
                transaccion.getCuenta() == null || transaccion.getMonto() == null || transaccion.getMonto().compareTo(BigDecimal.ZERO) <= 0 ||
                transaccion.getFechaPago() == null || transaccion.getFormaPago() == null) {
            throw new IllegalArgumentException("Todos los campos obligatorios deben estar completos y el monto debe ser mayor a 0.");
        }

        transaccion.setFechaCreacion(LocalDateTime.now());
        transaccion.setFechaModificacion(LocalDateTime.now());
        Transaccion savedTransaccion = transaccionRepository.save(transaccion);

        if ("GASTO".equals(transaccion.getTipo().name())) {
            generarCuentasPorPagar(savedTransaccion);
        }

        return savedTransaccion;
    }

    private void generarCuentasPorPagar(Transaccion transaccion) {
        List<CuentaPorPagar> cuentasPorPagar = new ArrayList<>();
        LocalDate fechaPagoInicial = transaccion.getFechaPago();
        EsquemaTransaccionEnum esquema = transaccion.getEsquema();
        BigDecimal monto = transaccion.getMonto();
        String formaPago = transaccion.getFormaPago();
        String nota = transaccion.getNotas();

        int totalPagos = 1;
        if (EsquemaTransaccionEnum.MENSUAL.equals(esquema)) {
            totalPagos = 12;
        } else if (EsquemaTransaccionEnum.ANUAL.equals(esquema)) {
            totalPagos = 3;
        }

        for (int i = 0; i < totalPagos; i++) {
            CuentaPorPagar cuentaPorPagar = new CuentaPorPagar();
            cuentaPorPagar.setTransaccion(transaccion);
            cuentaPorPagar.setCuenta(transaccion.getCuenta());

            // Calcular fecha de pago según el esquema
            LocalDate fechaPago = fechaPagoInicial;
            if (EsquemaTransaccionEnum.MENSUAL.equals(esquema)) {
                fechaPago = fechaPagoInicial.plusMonths(i);
            } else if (EsquemaTransaccionEnum.ANUAL.equals(esquema)) {
                fechaPago = fechaPagoInicial.plusYears(i);
            }

            cuentaPorPagar.setFechaPago(fechaPago);
            cuentaPorPagar.setMonto(monto);
            cuentaPorPagar.setFormaPago(formaPago);
            cuentaPorPagar.setEstatus("Pendiente");
            cuentaPorPagar.setNota(nota);
            cuentaPorPagar.setFolio(transaccion.getCuenta().getNombre() + "-" + String.format("%02d", i + 1));
            cuentaPorPagar.setNumeroPago(i + 1);
            cuentaPorPagar.setTotalPagos(totalPagos);
            cuentaPorPagar.setFechaCreacion(LocalDateTime.now());
            cuentasPorPagar.add(cuentaPorPagar);
        }

        cuentaPorPagarRepository.saveAll(cuentasPorPagar);
    }

    @Transactional
    public CategoriaTransacciones agregarCategoria(CategoriaTransacciones categoria) {
        if (categoria.getTipo() == null || categoria.getDescripcion() == null || categoria.getDescripcion().trim().isEmpty()) {
            throw new IllegalArgumentException("El tipo y la descripción son obligatorios.");
        }
        return categoriaRepository.save(categoria);
    }

    @Transactional
    public void eliminarCategoria(Integer id) {
        if (!categoriaRepository.existsById(id)) {
            throw new IllegalArgumentException("La categoría con ID " + id + " no existe.");
        }
        categoriaRepository.deleteById(id);
    }

    public List<CategoriaTransacciones> obtenerTodasLasCategorias() {
        return categoriaRepository.findAll();
    }

    @Transactional
    public CuentasTransacciones agregarCuenta(CuentasTransacciones cuenta) {
        if (cuenta.getNombre() == null || cuenta.getNombre().trim().isEmpty() || cuenta.getCategoria() == null) {
            throw new IllegalArgumentException("El nombre y la categoría son obligatorios.");
        }
        return cuentaRepository.save(cuenta);
    }

    @Transactional
    public void eliminarCuenta(Integer id) {
        if (!cuentaRepository.existsById(id)) {
            throw new IllegalArgumentException("La cuenta con ID " + id + " no existe.");
        }
        cuentaRepository.deleteById(id);
    }

    public List<CuentasTransacciones> obtenerTodasLasCuentas() {
        return cuentaRepository.findAll();
    }
}