package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.entity.CuentaPorPagar;
import com.tss.tssmanager_backend.entity.Sim;
import com.tss.tssmanager_backend.entity.Transaccion;
import com.tss.tssmanager_backend.repository.CuentaPorPagarRepository;
import com.tss.tssmanager_backend.repository.SimRepository;
import com.tss.tssmanager_backend.repository.TransaccionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class CuentaPorPagarService {

    @Autowired
    private CuentaPorPagarRepository cuentasPorPagarRepository;

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private TransaccionService transaccionService;

    @Autowired
    private SimRepository simRepository;

    public List<CuentaPorPagar> obtenerTodas() {
        return cuentasPorPagarRepository.findAllByOrderByFechaPagoAsc();
    }

    @Transactional
    public void marcarComoPagada(Integer id, LocalDate fechaPago, BigDecimal montoPago, String formaPago, Integer usuarioId, boolean regenerarAutomaticamente) {
        CuentaPorPagar cuenta = cuentasPorPagarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta por pagar no encontrada con ID: " + id));

        // Obtener la transacción original al inicio
        Transaccion transaccionOriginal = transaccionRepository.findById(cuenta.getTransaccion().getId())
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        // Validar que el monto de pago sea válido
        BigDecimal saldoActual = cuenta.getSaldoPendiente() != null ? cuenta.getSaldoPendiente() : cuenta.getMonto();
        if (montoPago.compareTo(BigDecimal.ZERO) <= 0 || montoPago.compareTo(saldoActual) > 0) {
            throw new IllegalArgumentException("El monto de pago debe ser mayor a 0 y menor o igual al saldo pendiente");
        }

        // Actualizar montos
        BigDecimal montoAcumulado = cuenta.getMontoPagado().add(montoPago);
        BigDecimal nuevoSaldo = cuenta.getMonto().subtract(montoAcumulado);

        cuenta.setMontoPagado(montoAcumulado);
        cuenta.setSaldoPendiente(nuevoSaldo);

        // Determinar estatus basado en el saldo
        if (nuevoSaldo.compareTo(BigDecimal.ZERO) == 0) {
            cuenta.setEstatus("Pagado");
            actualizarVigenciaSim(cuenta, LocalDate.now());
            // Solo verificar regeneración si está completamente pagada
            verificarYRegenerarSiEsNecesario(cuenta, transaccionOriginal, regenerarAutomaticamente);
        } else {
            cuenta.setEstatus("En proceso");
        }

        cuenta.setFormaPago(formaPago);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String fechaFormateada = LocalDateTime.now().format(formatter);
        String nota = String.format("Pago parcial de $%s el %s", montoPago, fechaFormateada);
        cuenta.setNota(cuenta.getNota() != null ? cuenta.getNota() + " - " + nota : nota);

        cuentasPorPagarRepository.save(cuenta);

        Transaccion transaccionPago = new Transaccion();
        transaccionPago.setFecha(LocalDate.now());
        transaccionPago.setTipo(com.tss.tssmanager_backend.enums.TipoTransaccionEnum.GASTO);
        transaccionPago.setCategoria(transaccionOriginal.getCategoria());
        transaccionPago.setCuenta(cuenta.getCuenta());
        transaccionPago.setMonto(montoPago);
        transaccionPago.setEsquema(com.tss.tssmanager_backend.enums.EsquemaTransaccionEnum.UNICA);
        transaccionPago.setFechaPago(LocalDate.now());
        transaccionPago.setFormaPago(cuenta.getFormaPago());
        transaccionPago.setNotas("Transacción generada desde Cuentas por Pagar - Pago parcial");
        transaccionPago.setFechaCreacion(LocalDateTime.now());
        transaccionPago.setFechaModificacion(LocalDateTime.now());
        transaccionRepository.save(transaccionPago);
    }

    @Transactional
    public void marcarComoPagada(Integer id, BigDecimal monto, String formaPago, Integer usuarioId) {
        marcarComoPagada(id, LocalDate.now(), monto, formaPago, usuarioId, false);
    }

    @Transactional
    public CuentaPorPagar actualizarCuentaPorPagar(Integer id, LocalDate fechaPago, BigDecimal monto, String formaPago, String nota) {
        CuentaPorPagar cuenta = cuentasPorPagarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta por pagar no encontrada con ID: " + id));

        // Verificar que no esté pagada
        if ("Pagado".equals(cuenta.getEstatus())) {
            throw new IllegalStateException("No se puede editar una cuenta que ya está pagada");
        }
        // Actualizar campos editables
        if (fechaPago != null) {
            cuenta.setFechaPago(fechaPago);
        }
        if (monto != null) {
            cuenta.setMonto(monto);
        }
        if (formaPago != null && !formaPago.isEmpty()) {
            cuenta.setFormaPago(formaPago);
        }
        if (nota != null) {
            cuenta.setNota(nota);
        }
        return cuentasPorPagarRepository.save(cuenta);
    }

    @Transactional
    public void eliminarCuentaPorPagar(Integer id, Integer usuarioId) {
        if (!cuentasPorPagarRepository.existsById(id)) {
            throw new IllegalArgumentException("La cuenta por pagar con ID " + id + " no existe.");
        }
        cuentasPorPagarRepository.deleteById(id);
    }

    private void actualizarVigenciaSim(CuentaPorPagar cuenta, LocalDate fechaPago) {
        if (cuenta.getSim() != null) {
            try {
                Sim sim = cuenta.getSim();
                Transaccion transaccion = cuenta.getTransaccion();

                // Calcular nueva vigencia basada en el esquema
                LocalDate nuevaVigencia = calcularProximaFechaPago(fechaPago, transaccion.getEsquema());

                // Actualizar vigencia de la SIM
                sim.setVigencia(Date.valueOf(nuevaVigencia));
                simRepository.save(sim);

                System.out.println("Vigencia de SIM " + sim.getNumero() + " actualizada a: " + nuevaVigencia);
            } catch (Exception e) {
                System.err.println("Error al actualizar vigencia de SIM: " + e.getMessage());
            }
        }
    }

    @Transactional
    private void verificarYRegenerarSiEsNecesario(CuentaPorPagar cuentaPagada, Transaccion transaccionOriginal, boolean regenerarAutomaticamente) {
        if (!"Pagado".equals(cuentaPagada.getEstatus())) {
            return;
        }
        // Verificar si quedan cuentas pendientes DESPUÉS de marcar esta como pagada
        boolean hayPendientes = cuentasPorPagarRepository
                .existsByTransaccionIdAndEstatusNot(transaccionOriginal.getId(), "Pagado");

        boolean esUltimaCuentaDeLaSerie = cuentaPagada.getNumeroPago().equals(cuentaPagada.getTotalPagos());

        if (!hayPendientes && esUltimaCuentaDeLaSerie && !transaccionOriginal.getEsquema().name().equals("UNICA")) {
            System.out.println("Última cuenta de la serie pagada para transacción " + transaccionOriginal.getId());

            if (regenerarAutomaticamente) {
                regenerarCuentasPorPagar(transaccionOriginal, cuentaPagada.getFechaPago());
            }

        }
    }

    @Transactional
    public void regenerarCuentasPorPagarManual(Integer transaccionId, LocalDate fechaUltimoPago, BigDecimal nuevoMonto) {
        Transaccion transaccionOriginal = transaccionRepository.findById(transaccionId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        regenerarCuentasPorPagar(transaccionOriginal, fechaUltimoPago, nuevoMonto);
    }

    @Transactional
    public void regenerarCuentasPorPagarManual(Integer transaccionId, LocalDate fechaUltimoPago) {
        regenerarCuentasPorPagarManual(transaccionId, fechaUltimoPago, null);
    }

    @Transactional
    private void regenerarCuentasPorPagar(Transaccion transaccionOriginal, LocalDate fechaUltimoPago) {
        try {
            Sim simAsociada = null;
            List<CuentaPorPagar> cuentasOriginales = cuentasPorPagarRepository.findByTransaccionId(transaccionOriginal.getId());
            if (!cuentasOriginales.isEmpty()) {
                simAsociada = cuentasOriginales.get(0).getSim();
            }

            Transaccion nuevaTransaccion = new Transaccion();
            nuevaTransaccion.setFecha(fechaUltimoPago);
            nuevaTransaccion.setTipo(transaccionOriginal.getTipo());
            nuevaTransaccion.setCategoria(transaccionOriginal.getCategoria());
            nuevaTransaccion.setCuenta(transaccionOriginal.getCuenta());
            nuevaTransaccion.setMonto(transaccionOriginal.getMonto());
            nuevaTransaccion.setEsquema(transaccionOriginal.getEsquema());

            LocalDate proximaFechaPago = calcularProximaFechaPago(fechaUltimoPago, transaccionOriginal.getEsquema());
            nuevaTransaccion.setFechaPago(proximaFechaPago);

            nuevaTransaccion.setFormaPago(transaccionOriginal.getFormaPago());
            nuevaTransaccion.setNotas(transaccionOriginal.getNotas());
            nuevaTransaccion.setNumeroPagos(transaccionOriginal.getNumeroPagos());
            nuevaTransaccion.setFechaCreacion(LocalDateTime.now());
            nuevaTransaccion.setFechaModificacion(LocalDateTime.now());

            transaccionService.agregarTransaccion(nuevaTransaccion);

            // Si había una SIM asociada, asociarla a las nuevas cuentas por pagar
            if (simAsociada != null) {
                List<CuentaPorPagar> nuevasCuentas = cuentasPorPagarRepository.findByTransaccionId(nuevaTransaccion.getId());
                for (CuentaPorPagar nuevaCuenta : nuevasCuentas) {
                    nuevaCuenta.setSim(simAsociada);
                    cuentasPorPagarRepository.save(nuevaCuenta);
                }

                System.out.println("SIM " + simAsociada.getNumero() + " asociada a " + nuevasCuentas.size() + " nuevas cuentas por pagar");
            }

            System.out.println("Cuentas por pagar regeneradas automáticamente:");
            System.out.println("- Transacción original: " + transaccionOriginal.getId());
            System.out.println("- Nueva serie: " + nuevaTransaccion.getNumeroPagos() + " pagos");
            System.out.println("- Primera fecha de pago: " + proximaFechaPago);
            if (simAsociada != null) {
                System.out.println("- SIM asociada: " + simAsociada.getNumero());
            }

        } catch (Exception e) {
            System.err.println("Error al regenerar cuentas por pagar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Transactional
    private void regenerarCuentasPorPagar(Transaccion transaccionOriginal, LocalDate fechaUltimoPago, BigDecimal nuevoMonto) {
        try {
            Sim simAsociada = null;
            List<CuentaPorPagar> cuentasOriginales = cuentasPorPagarRepository.findByTransaccionId(transaccionOriginal.getId());
            if (!cuentasOriginales.isEmpty()) {
                simAsociada = cuentasOriginales.get(0).getSim();
            }

            Transaccion nuevaTransaccion = new Transaccion();
            nuevaTransaccion.setFecha(fechaUltimoPago);
            nuevaTransaccion.setTipo(transaccionOriginal.getTipo());
            nuevaTransaccion.setCategoria(transaccionOriginal.getCategoria());
            nuevaTransaccion.setCuenta(transaccionOriginal.getCuenta());

            // Usar el nuevo monto si se proporciona, sino usar el monto original
            nuevaTransaccion.setMonto(nuevoMonto != null ? nuevoMonto : transaccionOriginal.getMonto());

            nuevaTransaccion.setEsquema(transaccionOriginal.getEsquema());

            LocalDate proximaFechaPago = calcularProximaFechaPago(fechaUltimoPago, transaccionOriginal.getEsquema());
            nuevaTransaccion.setFechaPago(proximaFechaPago);

            nuevaTransaccion.setFormaPago(transaccionOriginal.getFormaPago());
            nuevaTransaccion.setNotas(transaccionOriginal.getNotas());
            nuevaTransaccion.setNumeroPagos(transaccionOriginal.getNumeroPagos());
            nuevaTransaccion.setFechaCreacion(LocalDateTime.now());
            nuevaTransaccion.setFechaModificacion(LocalDateTime.now());

            transaccionService.agregarTransaccion(nuevaTransaccion);

            // Si había una SIM asociada, asociarla a las nuevas cuentas por pagar
            if (simAsociada != null) {
                List<CuentaPorPagar> nuevasCuentas = cuentasPorPagarRepository.findByTransaccionId(nuevaTransaccion.getId());
                for (CuentaPorPagar nuevaCuenta : nuevasCuentas) {
                    nuevaCuenta.setSim(simAsociada);
                    cuentasPorPagarRepository.save(nuevaCuenta);
                }

                System.out.println("SIM " + simAsociada.getNumero() + " asociada a " + nuevasCuentas.size() + " nuevas cuentas por pagar");
            }

            System.out.println("Cuentas por pagar regeneradas automáticamente:");
            System.out.println("- Transacción original: " + transaccionOriginal.getId());
            System.out.println("- Nueva serie: " + nuevaTransaccion.getNumeroPagos() + " pagos");
            System.out.println("- Primera fecha de pago: " + proximaFechaPago);
            System.out.println("- Monto: " + nuevaTransaccion.getMonto());
            if (simAsociada != null) {
                System.out.println("- SIM asociada: " + simAsociada.getNumero());
            }

        } catch (Exception e) {
            System.err.println("Error al regenerar cuentas por pagar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private LocalDate calcularProximaFechaPago(LocalDate fechaUltimoPago, com.tss.tssmanager_backend.enums.EsquemaTransaccionEnum esquema) {
        switch (esquema) {
            case SEMANAL:
                return fechaUltimoPago.plusWeeks(1);
            case QUINCENAL:
                return fechaUltimoPago.plusDays(15);
            case MENSUAL:
                return fechaUltimoPago.plusDays(30);
            case BIMESTRAL:
                return fechaUltimoPago.plusDays(60);
            case TRIMESTRAL:
                return fechaUltimoPago.plusDays(90);
            case SEMESTRAL:
                return fechaUltimoPago.plusDays(180);
            case ANUAL:
                return fechaUltimoPago.plusDays(365);
            default:
                return fechaUltimoPago.plusDays(30);
        }
    }

    public CuentaPorPagar obtenerPorId(Integer id) {
        return cuentasPorPagarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta por pagar no encontrada con ID: " + id));
    }
}