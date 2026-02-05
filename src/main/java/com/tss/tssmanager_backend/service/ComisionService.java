package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.ComisionDTO;
import com.tss.tssmanager_backend.dto.ComisionDesdeCuentaPorCobrarDTO;
import com.tss.tssmanager_backend.dto.CrearComisionDTO;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.EstatusComisionEnum;
import com.tss.tssmanager_backend.enums.TipoTransaccionEnum;
import com.tss.tssmanager_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ComisionService {

    @Autowired
    private ComisionRepository comisionRepository;

    @Autowired
    private CuentaPorCobrarRepository cuentaPorCobrarRepository;

    @Autowired
    private CuentasTransaccionesRepository cuentasTransaccionesRepository;

    @Autowired
    private CategoriaTransaccionesRepository categoriaTransaccionesRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private TratoRepository tratoRepository;

    private static final Integer CATEGORIA_COMISIONES_ID = 17;
    private static final String CUENTA_PROYECTO_DEFAULT = "Dagoberto Emmanuel Nieto González";

    public List<ComisionDTO> obtenerTodas() {
        return comisionRepository.findAllWithRelations()
                .stream()
                .map(this::convertirADTO)
                .collect(Collectors.toList());
    }

    public ComisionDTO obtenerPorId(Integer id) {
        Comision comision = comisionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Comisión no encontrada con ID: " + id));
        return convertirADTO(comision);
    }

    @Transactional
    public ComisionDTO crearComisionDesdeCuentaPorCobrar(
            Integer cuentaPorCobrarId,
            BigDecimal montoPagado,
            ComisionDesdeCuentaPorCobrarDTO dto) {
        // Validar que no exista ya una comisión para esta cuenta
        if (comisionRepository.existsByCuentaPorCobrarId(cuentaPorCobrarId)) {
            throw new IllegalStateException("Ya existe una comisión para esta cuenta por cobrar");
        }

        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findById(cuentaPorCobrarId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta por cobrar no encontrada"));

        Cotizacion cotizacion = cuenta.getCotizacion();
        if (cotizacion == null || cotizacion.getTratoId() == null) {
            throw new IllegalArgumentException("La cuenta por cobrar no tiene cotización o trato asociado");
        }

        Trato trato = tratoRepository.findById(cotizacion.getTratoId())
                .orElseThrow(() -> new IllegalArgumentException("Trato no encontrado"));

        Empresa empresa = empresaRepository.findById(trato.getEmpresaId())
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));

        // Obtener o crear cuenta de vendedor
        CuentasTransacciones vendedorCuenta = obtenerOCrearCuentaTransaccion(
                dto.getVendedorCuentaId(),
                dto.getVendedorNuevoNombre()
        );

        // Obtener o crear cuenta de proyecto (Dagoberto por defecto)
        CuentasTransacciones proyectoCuenta = obtenerOCrearCuentaTransaccion(
                null,
                CUENTA_PROYECTO_DEFAULT
        );

        Comision comision = new Comision();
        comision.setCuentaPorCobrar(cuenta);
        comision.setEmpresa(empresa);
        comision.setTrato(trato);
        comision.setFechaPago(cuenta.getFechaRealPago() != null ? cuenta.getFechaRealPago() : LocalDate.now());
        comision.setMontoBase(montoPagado);

        // Comisión de venta
        comision.setVendedorCuenta(vendedorCuenta);
        comision.setPorcentajeVenta(dto.getPorcentajeVenta());
        BigDecimal montoVenta = calcularMonto(montoPagado, dto.getPorcentajeVenta());
        comision.setMontoComisionVenta(montoVenta);
        comision.setSaldoPendienteVenta(montoVenta);
        comision.setEstatusVenta(dto.getPorcentajeVenta().compareTo(BigDecimal.ZERO) == 0
                ? EstatusComisionEnum.PAGADO : EstatusComisionEnum.PENDIENTE);

        // Comisión de proyecto
        comision.setProyectoCuenta(proyectoCuenta);
        comision.setPorcentajeProyecto(dto.getPorcentajeProyecto());
        BigDecimal montoProyecto = calcularMonto(montoPagado, dto.getPorcentajeProyecto());
        comision.setMontoComisionProyecto(montoProyecto);
        comision.setSaldoPendienteProyecto(montoProyecto);
        comision.setEstatusProyecto(dto.getPorcentajeProyecto().compareTo(BigDecimal.ZERO) == 0
                ? EstatusComisionEnum.PAGADO : EstatusComisionEnum.PENDIENTE);

        comision.setNotas(dto.getNotas());

        Comision saved = comisionRepository.save(comision);
        return convertirADTO(saved);
    }

    @Transactional
    public ComisionDTO crearComisionManual(CrearComisionDTO dto) {
        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findById(dto.getCuentaPorCobrarId())
                .orElseThrow(() -> new IllegalArgumentException("Cuenta por cobrar no encontrada"));

        // Validar que no exista ya una comisión
        if (comisionRepository.existsByCuentaPorCobrarId(dto.getCuentaPorCobrarId())) {
            throw new IllegalStateException("Ya existe una comisión para esta cuenta por cobrar");
        }

        Empresa empresa = empresaRepository.findById(dto.getEmpresaId())
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));

        Trato trato = tratoRepository.findById(dto.getTratoId())
                .orElseThrow(() -> new IllegalArgumentException("Trato no encontrado"));

        CuentasTransacciones vendedorCuenta = obtenerOCrearCuentaTransaccion(
                dto.getVendedorCuentaId(),
                dto.getVendedorNuevoNombre()
        );

        CuentasTransacciones proyectoCuenta = obtenerOCrearCuentaTransaccion(
                dto.getProyectoCuentaId(),
                dto.getProyectoNuevoNombre() != null ? dto.getProyectoNuevoNombre() : CUENTA_PROYECTO_DEFAULT
        );

        BigDecimal montoBase = cuenta.getMontoPagado() != null ? cuenta.getMontoPagado() : BigDecimal.ZERO;

        Comision comision = new Comision();
        comision.setCuentaPorCobrar(cuenta);
        comision.setEmpresa(empresa);
        comision.setTrato(trato);
        comision.setFechaPago(cuenta.getFechaRealPago() != null ? cuenta.getFechaRealPago() : cuenta.getFechaPago());
        comision.setMontoBase(montoBase);

        // Venta
        comision.setVendedorCuenta(vendedorCuenta);
        comision.setPorcentajeVenta(dto.getPorcentajeVenta());
        BigDecimal montoVenta = calcularMonto(montoBase, dto.getPorcentajeVenta());
        comision.setMontoComisionVenta(montoVenta);
        comision.setSaldoPendienteVenta(montoVenta);
        comision.setEstatusVenta(dto.getPorcentajeVenta().compareTo(BigDecimal.ZERO) == 0
                ? EstatusComisionEnum.PAGADO : EstatusComisionEnum.PENDIENTE);

        // Proyecto
        comision.setProyectoCuenta(proyectoCuenta);
        comision.setPorcentajeProyecto(dto.getPorcentajeProyecto());
        BigDecimal montoProyecto = calcularMonto(montoBase, dto.getPorcentajeProyecto());
        comision.setMontoComisionProyecto(montoProyecto);
        comision.setSaldoPendienteProyecto(montoProyecto);
        comision.setEstatusProyecto(dto.getPorcentajeProyecto().compareTo(BigDecimal.ZERO) == 0
                ? EstatusComisionEnum.PAGADO : EstatusComisionEnum.PENDIENTE);

        comision.setNotas(dto.getNotas());

        Comision saved = comisionRepository.save(comision);
        return convertirADTO(saved);
    }

    @Transactional
    public void actualizarComisionesPorPagoParcial(Integer cuentaPorCobrarId, BigDecimal nuevoPagoMonto) {
        List<Comision> comisiones = comisionRepository.findByCuentaPorCobrarId(cuentaPorCobrarId);

        if (comisiones.isEmpty()) {
            return;
        }

        Comision comision = comisiones.get(0);
        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findById(cuentaPorCobrarId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta por cobrar no encontrada"));

        BigDecimal nuevoMontoBase = cuenta.getMontoPagado();
        BigDecimal nuevoMontoVenta = calcularMonto(nuevoMontoBase, comision.getPorcentajeVenta());
        BigDecimal nuevoMontoProyecto = calcularMonto(nuevoMontoBase, comision.getPorcentajeProyecto());

        BigDecimal diferenciaVenta = nuevoMontoVenta.subtract(comision.getMontoComisionVenta());
        BigDecimal diferenciaProyecto = nuevoMontoProyecto.subtract(comision.getMontoComisionProyecto());

        comision.setMontoBase(nuevoMontoBase);
        comision.setMontoComisionVenta(nuevoMontoVenta);
        comision.setMontoComisionProyecto(nuevoMontoProyecto);

        BigDecimal nuevoSaldoVenta = comision.getSaldoPendienteVenta().add(diferenciaVenta);
        comision.setSaldoPendienteVenta(nuevoSaldoVenta);

        if (nuevoSaldoVenta.compareTo(BigDecimal.ZERO) > 0) {
            comision.setEstatusVenta(EstatusComisionEnum.PENDIENTE);
        }

        BigDecimal nuevoSaldoProyecto = comision.getSaldoPendienteProyecto().add(diferenciaProyecto);
        comision.setSaldoPendienteProyecto(nuevoSaldoProyecto);

        if (nuevoSaldoProyecto.compareTo(BigDecimal.ZERO) > 0) {
            comision.setEstatusProyecto(EstatusComisionEnum.PENDIENTE);
        }

        comisionRepository.save(comision);
    }

    @Transactional
    public void aplicarPagoComisiones(Integer cuentaId, BigDecimal montoPago) {
        CuentasTransacciones cuenta = cuentasTransaccionesRepository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada"));

        // Verificar si es la misma cuenta para venta y proyecto
        List<Comision> comisionesMismaCuenta = comisionRepository
                .findComisionesPendientesMismaCuenta(cuentaId);

        if (!comisionesMismaCuenta.isEmpty()) {
            aplicarPagoMismaCuenta(comisionesMismaCuenta, montoPago);
            return;
        }

        // Si no es la misma cuenta, aplicar por separado
        List<Comision> comisionesVenta = comisionRepository
                .findComisionesVentaPendientesByCuenta(cuentaId);

        if (!comisionesVenta.isEmpty()) {
            aplicarPagoFIFO(comisionesVenta, montoPago, true);
            return;
        }

        List<Comision> comisionesProyecto = comisionRepository
                .findComisionesProyectoPendientesByCuenta(cuentaId);

        if (!comisionesProyecto.isEmpty()) {
            aplicarPagoFIFO(comisionesProyecto, montoPago, false);
        }
    }

    private void aplicarPagoMismaCuenta(List<Comision> comisiones, BigDecimal montoPago) {
        BigDecimal montoRestante = montoPago;

        for (Comision comision : comisiones) {
            if (montoRestante.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            // Primero saldar comisión de venta si está pendiente
            if (comision.getEstatusVenta() == EstatusComisionEnum.PENDIENTE) {
                BigDecimal saldoVenta = comision.getSaldoPendienteVenta();

                if (montoRestante.compareTo(saldoVenta) >= 0) {
                    // Pago completo de venta
                    comision.setSaldoPendienteVenta(BigDecimal.ZERO);
                    comision.setEstatusVenta(EstatusComisionEnum.PAGADO);
                    montoRestante = montoRestante.subtract(saldoVenta);
                } else {
                    // Pago parcial de venta
                    comision.setSaldoPendienteVenta(saldoVenta.subtract(montoRestante));
                    montoRestante = BigDecimal.ZERO;
                }
            }

            // Luego saldar comisión de proyecto si aún hay monto restante
            if (montoRestante.compareTo(BigDecimal.ZERO) > 0 &&
                    comision.getEstatusProyecto() == EstatusComisionEnum.PENDIENTE) {

                BigDecimal saldoProyecto = comision.getSaldoPendienteProyecto();

                if (montoRestante.compareTo(saldoProyecto) >= 0) {
                    // Pago completo de proyecto
                    comision.setSaldoPendienteProyecto(BigDecimal.ZERO);
                    comision.setEstatusProyecto(EstatusComisionEnum.PAGADO);
                    montoRestante = montoRestante.subtract(saldoProyecto);
                } else {
                    // Pago parcial de proyecto
                    comision.setSaldoPendienteProyecto(saldoProyecto.subtract(montoRestante));
                    montoRestante = BigDecimal.ZERO;
                }
            }

            comisionRepository.save(comision);
        }
    }

    private void aplicarPagoFIFO(List<Comision> comisiones, BigDecimal montoPago, boolean esVenta) {
        BigDecimal montoRestante = montoPago;

        for (Comision comision : comisiones) {
            if (montoRestante.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal saldoPendiente = esVenta
                    ? comision.getSaldoPendienteVenta()
                    : comision.getSaldoPendienteProyecto();

            if (montoRestante.compareTo(saldoPendiente) >= 0) {
                // Pago completo
                if (esVenta) {
                    comision.setSaldoPendienteVenta(BigDecimal.ZERO);
                    comision.setEstatusVenta(EstatusComisionEnum.PAGADO);
                } else {
                    comision.setSaldoPendienteProyecto(BigDecimal.ZERO);
                    comision.setEstatusProyecto(EstatusComisionEnum.PAGADO);
                }
                montoRestante = montoRestante.subtract(saldoPendiente);
            } else {
                // Pago parcial
                BigDecimal nuevoSaldo = saldoPendiente.subtract(montoRestante);
                if (esVenta) {
                    comision.setSaldoPendienteVenta(nuevoSaldo);
                } else {
                    comision.setSaldoPendienteProyecto(nuevoSaldo);
                }
                montoRestante = BigDecimal.ZERO;
            }

            comisionRepository.save(comision);
        }
    }

    @Transactional
    public void eliminarComision(Integer id) {
        Comision comision = comisionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Comisión no encontrada"));

        comisionRepository.delete(comision);
    }

    @Transactional
    public ComisionDTO actualizarComision(Integer id, CrearComisionDTO dto) {
        Comision comision = comisionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Comisión no encontrada"));

        // Actualizar vendedor
        if (dto.getVendedorCuentaId() != null || dto.getVendedorNuevoNombre() != null) {
            CuentasTransacciones vendedorCuenta = obtenerOCrearCuentaTransaccion(
                    dto.getVendedorCuentaId(),
                    dto.getVendedorNuevoNombre()
            );
            comision.setVendedorCuenta(vendedorCuenta);
        }

        // Actualizar proyecto
        if (dto.getProyectoCuentaId() != null || dto.getProyectoNuevoNombre() != null) {
            CuentasTransacciones proyectoCuenta = obtenerOCrearCuentaTransaccion(
                    dto.getProyectoCuentaId(),
                    dto.getProyectoNuevoNombre()
            );
            comision.setProyectoCuenta(proyectoCuenta);
        }

        // Recalcular comisiones si cambiaron porcentajes
        if (dto.getPorcentajeVenta() != null) {
            comision.setPorcentajeVenta(dto.getPorcentajeVenta());
            BigDecimal nuevoMontoVenta = calcularMonto(comision.getMontoBase(), dto.getPorcentajeVenta());
            BigDecimal diferencia = nuevoMontoVenta.subtract(comision.getMontoComisionVenta());

            comision.setMontoComisionVenta(nuevoMontoVenta);

            if (comision.getEstatusVenta() == EstatusComisionEnum.PENDIENTE) {
                BigDecimal nuevoSaldo = comision.getSaldoPendienteVenta().add(diferencia);
                comision.setSaldoPendienteVenta(nuevoSaldo.max(BigDecimal.ZERO));
            }

            comision.setEstatusVenta(dto.getPorcentajeVenta().compareTo(BigDecimal.ZERO) == 0
                    ? EstatusComisionEnum.PAGADO : EstatusComisionEnum.PENDIENTE);
        }

        if (dto.getPorcentajeProyecto() != null) {
            comision.setPorcentajeProyecto(dto.getPorcentajeProyecto());
            BigDecimal nuevoMontoProyecto = calcularMonto(comision.getMontoBase(), dto.getPorcentajeProyecto());
            BigDecimal diferencia = nuevoMontoProyecto.subtract(comision.getMontoComisionProyecto());

            comision.setMontoComisionProyecto(nuevoMontoProyecto);

            if (comision.getEstatusProyecto() == EstatusComisionEnum.PENDIENTE) {
                BigDecimal nuevoSaldo = comision.getSaldoPendienteProyecto().add(diferencia);
                comision.setSaldoPendienteProyecto(nuevoSaldo.max(BigDecimal.ZERO));
            }

            comision.setEstatusProyecto(dto.getPorcentajeProyecto().compareTo(BigDecimal.ZERO) == 0
                    ? EstatusComisionEnum.PAGADO : EstatusComisionEnum.PENDIENTE);
        }

        if (dto.getNotas() != null) {
            comision.setNotas(dto.getNotas());
        }

        Comision updated = comisionRepository.save(comision);
        return convertirADTO(updated);
    }

    public BigDecimal obtenerSaldoTotalPendiente() {
        BigDecimal saldoVenta = comisionRepository.sumSaldoPendienteVenta();
        BigDecimal saldoProyecto = comisionRepository.sumSaldoPendienteProyecto();

        return (saldoVenta != null ? saldoVenta : BigDecimal.ZERO)
                .add(saldoProyecto != null ? saldoProyecto : BigDecimal.ZERO);
    }

    private CuentasTransacciones obtenerOCrearCuentaTransaccion(Integer cuentaId, String nombreNuevo) {
        if (cuentaId != null) {
            return cuentasTransaccionesRepository.findById(cuentaId)
                    .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada con ID: " + cuentaId));
        }

        if (nombreNuevo != null && !nombreNuevo.trim().isEmpty()) {
            CategoriaTransacciones categoria = categoriaTransaccionesRepository.findById(CATEGORIA_COMISIONES_ID)
                    .orElseThrow(() -> new IllegalArgumentException("Categoría de comisiones no encontrada"));

            List<CuentasTransacciones> cuentasExistentes = cuentasTransaccionesRepository
                    .findByNombreAndCategoria(nombreNuevo.trim(), categoria);

            if (cuentasExistentes != null && !cuentasExistentes.isEmpty()) {
                return cuentasExistentes.get(0);
            }

            CuentasTransacciones nuevaCuenta = new CuentasTransacciones();
            nuevaCuenta.setNombre(nombreNuevo.trim());
            nuevaCuenta.setCategoria(categoria);

            return cuentasTransaccionesRepository.save(nuevaCuenta);
        }

        throw new IllegalArgumentException("Debe proporcionar un ID de cuenta o un nombre nuevo");
    }

    private BigDecimal calcularMonto(BigDecimal base, BigDecimal porcentaje) {
        if (base == null || porcentaje == null) {
            return BigDecimal.ZERO;
        }
        return base.multiply(porcentaje)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private ComisionDTO convertirADTO(Comision comision) {
        ComisionDTO dto = new ComisionDTO();
        dto.setId(comision.getId());
        dto.setCuentaPorCobrarId(comision.getCuentaPorCobrar().getId());
        dto.setFolioCuentaPorCobrar(comision.getCuentaPorCobrar().getFolio());
        dto.setEmpresaId(comision.getEmpresa().getId());
        dto.setEmpresaNombre(comision.getEmpresa().getNombre());
        dto.setTratoId(comision.getTrato().getId());
        dto.setTratoNombre(comision.getTrato().getNombre());
        dto.setFechaPago(comision.getFechaPago());
        dto.setMontoBase(comision.getMontoBase());

        dto.setVendedorCuentaId(comision.getVendedorCuenta().getId());
        dto.setVendedorNombre(comision.getVendedorCuenta().getNombre());
        dto.setPorcentajeVenta(comision.getPorcentajeVenta());
        dto.setMontoComisionVenta(comision.getMontoComisionVenta());
        dto.setSaldoPendienteVenta(comision.getSaldoPendienteVenta());
        dto.setEstatusVenta(comision.getEstatusVenta());

        dto.setProyectoCuentaId(comision.getProyectoCuenta().getId());
        dto.setProyectoNombre(comision.getProyectoCuenta().getNombre());
        dto.setPorcentajeProyecto(comision.getPorcentajeProyecto());
        dto.setMontoComisionProyecto(comision.getMontoComisionProyecto());
        dto.setSaldoPendienteProyecto(comision.getSaldoPendienteProyecto());
        dto.setEstatusProyecto(comision.getEstatusProyecto());

        dto.setNotas(comision.getNotas());

        return dto;
    }

    public List<CuentasTransacciones> obtenerCuentasComisiones() {
        CategoriaTransacciones categoria = categoriaTransaccionesRepository.findById(CATEGORIA_COMISIONES_ID)
                .orElseThrow(() -> new IllegalArgumentException("Categoría de comisiones no encontrada"));

        return cuentasTransaccionesRepository.findByCategoria(categoria);
    }

    public boolean existeComisionParaCuenta(Integer cuentaPorCobrarId) {
        return comisionRepository.existsByCuentaPorCobrarId(cuentaPorCobrarId);
    }
}