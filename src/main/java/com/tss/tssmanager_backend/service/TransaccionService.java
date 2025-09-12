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

    // CORREGIDO: Manejo de errores en obtenerTodas
    public List<Transaccion> obtenerTodas() {
        try {
            return transaccionRepository.findAll();
        } catch (Exception e) {
            // Log del error real
            System.err.println("Error al obtener transacciones: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al obtener las transacciones: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void eliminarTransaccion(Integer id) {
        if (!transaccionRepository.existsById(id)) {
            throw new IllegalArgumentException("La transacción con ID " + id + " no existe.");
        }

        try {
            // Primero eliminar las cuentas por pagar relacionadas
            List<CuentaPorPagar> cuentasPorPagar = cuentaPorPagarRepository.findByTransaccionId(id);
            if (!cuentasPorPagar.isEmpty()) {
                cuentaPorPagarRepository.deleteAll(cuentasPorPagar);
            }

            // Luego eliminar la transacción
            transaccionRepository.deleteById(id);
        } catch (Exception e) {
            System.err.println("Error al eliminar transacción: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al eliminar la transacción: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Transaccion agregarTransaccion(Transaccion transaccion) {
        // Validaciones básicas
        if (transaccion.getFecha() == null || transaccion.getTipo() == null ||
                transaccion.getMonto() == null || transaccion.getMonto().compareTo(BigDecimal.ZERO) <= 0 ||
                transaccion.getFechaPago() == null || transaccion.getFormaPago() == null) {
            throw new IllegalArgumentException("Todos los campos obligatorios deben estar completos y el monto debe ser mayor a 0.");
        }

        try {
            // CORREGIDO: Verificar categoría antes de usarla
            if (transaccion.getCategoria() == null) {
                throw new IllegalArgumentException("La categoría es obligatoria.");
            }

            // Si no tiene cuenta asignada pero tiene nombre de cuenta, crear/buscar la cuenta
            if (transaccion.getCuenta() == null && transaccion.getNombreCuenta() != null &&
                    !transaccion.getNombreCuenta().trim().isEmpty()) {
                CuentasTransacciones cuenta = crearCuentaSiNoExiste(
                        transaccion.getNombreCuenta().trim(),
                        transaccion.getCategoria().getId()
                );
                transaccion.setCuenta(cuenta);
            }

            if (transaccion.getCuenta() == null) {
                throw new IllegalArgumentException("La cuenta es obligatoria.");
            }

            transaccion.setFechaCreacion(LocalDateTime.now());
            transaccion.setFechaModificacion(LocalDateTime.now());

            Transaccion savedTransaccion = transaccionRepository.save(transaccion);

            // Solo generar cuentas por pagar si es un gasto
            if ("GASTO".equals(transaccion.getTipo().name())) {
                generarCuentasPorPagar(savedTransaccion);
            }

            return savedTransaccion;

        } catch (Exception e) {
            System.err.println("Error al agregar transacción: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al crear la transacción: " + e.getMessage(), e);
        }
    }

    @Transactional
    private CuentasTransacciones crearCuentaSiNoExiste(String nombreCuenta, Integer categoriaId) {
        try {
            // Buscar la categoría por ID
            CategoriaTransacciones categoria = categoriaRepository.findById(categoriaId)
                    .orElseThrow(() -> new IllegalArgumentException("La categoría con ID " + categoriaId + " no existe."));

            // Verificar si la cuenta ya existe con ese nombre Y esa categoría específica
            CuentasTransacciones cuentaExistente = cuentaRepository.findByNombreAndCategoria(nombreCuenta, categoria);
            if (cuentaExistente != null) {
                return cuentaExistente;
            }

            // Crear nueva cuenta
            CuentasTransacciones nuevaCuenta = new CuentasTransacciones();
            nuevaCuenta.setNombre(nombreCuenta);
            nuevaCuenta.setCategoria(categoria);
            return cuentaRepository.save(nuevaCuenta);

        } catch (Exception e) {
            System.err.println("Error al crear cuenta: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al crear la cuenta: " + e.getMessage(), e);
        }
    }

    private void generarCuentasPorPagar(Transaccion transaccion) {
        try {
            List<CuentaPorPagar> cuentasPorPagar = new ArrayList<>();
            LocalDate fechaPagoInicial = transaccion.getFechaPago();
            EsquemaTransaccionEnum esquema = transaccion.getEsquema();
            BigDecimal monto = transaccion.getMonto();
            String formaPago = transaccion.getFormaPago();
            String nota = transaccion.getNotas();

            // Usar el número de pagos personalizado o el por defecto
            int totalPagos = transaccion.getNumeroPagos() != null ? transaccion.getNumeroPagos() : getDefaultPagos(esquema);

            for (int i = 0; i < totalPagos; i++) {
                CuentaPorPagar cuentaPorPagar = new CuentaPorPagar();
                cuentaPorPagar.setTransaccion(transaccion);
                cuentaPorPagar.setCuenta(transaccion.getCuenta());

                // Calcular fecha de pago según el esquema
                LocalDate fechaPago = calcularFechaPago(fechaPagoInicial, esquema, i);

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

        } catch (Exception e) {
            System.err.println("Error al generar cuentas por pagar: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al generar cuentas por pagar: " + e.getMessage(), e);
        }
    }

    private int getDefaultPagos(EsquemaTransaccionEnum esquema) {
        if (esquema == null) return 1;

        switch (esquema) {
            case UNICA: return 1;
            case SEMANAL: return 4;
            case QUINCENAL: return 6;
            case MENSUAL: return 12;
            case BIMESTRAL: return 6;
            case TRIMESTRAL: return 4;
            case SEMESTRAL: return 4;
            case ANUAL: return 3;
            default: return 1;
        }
    }

    private LocalDate calcularFechaPago(LocalDate fechaInicial, EsquemaTransaccionEnum esquema, int incremento) {
        if (fechaInicial == null || esquema == null) return fechaInicial;

        switch (esquema) {
            case UNICA:
                return fechaInicial;
            case SEMANAL:
                return fechaInicial.plusWeeks(incremento);
            case QUINCENAL:
                return fechaInicial.plusDays(incremento * 14);
            case MENSUAL:
                return fechaInicial.plusMonths(incremento);
            case BIMESTRAL:
                return fechaInicial.plusMonths(incremento * 2);
            case TRIMESTRAL:
                return fechaInicial.plusMonths(incremento * 3);
            case SEMESTRAL:
                return fechaInicial.plusMonths(incremento * 6);
            case ANUAL:
                return fechaInicial.plusYears(incremento);
            default:
                return fechaInicial;
        }
    }

    @Transactional
    public CategoriaTransacciones agregarCategoria(CategoriaTransacciones categoria) {
        if (categoria.getTipo() == null || categoria.getDescripcion() == null || categoria.getDescripcion().trim().isEmpty()) {
            throw new IllegalArgumentException("El tipo y la descripción son obligatorios.");
        }

        try {
            return categoriaRepository.save(categoria);
        } catch (Exception e) {
            System.err.println("Error al agregar categoría: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al crear la categoría: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void eliminarCategoria(Integer id) {
        if (!categoriaRepository.existsById(id)) {
            throw new IllegalArgumentException("La categoría con ID " + id + " no existe.");
        }

        try {
            categoriaRepository.deleteById(id);
        } catch (Exception e) {
            System.err.println("Error al eliminar categoría: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al eliminar la categoría: " + e.getMessage(), e);
        }
    }

    public List<CategoriaTransacciones> obtenerTodasLasCategorias() {
        try {
            return categoriaRepository.findAll();
        } catch (Exception e) {
            System.err.println("Error al obtener categorías: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al obtener las categorías: " + e.getMessage(), e);
        }
    }

    @Transactional
    public CuentasTransacciones agregarCuenta(CuentasTransacciones cuenta) {
        if (cuenta.getNombre() == null || cuenta.getNombre().trim().isEmpty() || cuenta.getCategoria() == null) {
            throw new IllegalArgumentException("El nombre y la categoría son obligatorios.");
        }

        try {
            return cuentaRepository.save(cuenta);
        } catch (Exception e) {
            System.err.println("Error al agregar cuenta: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al crear la cuenta: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void eliminarCuenta(Integer id) {
        if (!cuentaRepository.existsById(id)) {
            throw new IllegalArgumentException("La cuenta con ID " + id + " no existe.");
        }

        try {
            cuentaRepository.deleteById(id);
        } catch (Exception e) {
            System.err.println("Error al eliminar cuenta: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al eliminar la cuenta: " + e.getMessage(), e);
        }
    }

    public List<CuentasTransacciones> obtenerTodasLasCuentas() {
        try {
            return cuentaRepository.findAll();
        } catch (Exception e) {
            System.err.println("Error al obtener cuentas: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al obtener las cuentas: " + e.getMessage(), e);
        }
    }
}