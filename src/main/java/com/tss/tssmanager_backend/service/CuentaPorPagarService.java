package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.entity.CuentaPorPagar;
import com.tss.tssmanager_backend.entity.Transaccion;
import com.tss.tssmanager_backend.repository.CuentaPorPagarRepository;
import com.tss.tssmanager_backend.repository.TransaccionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    public List<CuentaPorPagar> obtenerTodas() {
        return cuentasPorPagarRepository.findAll();
    }

    @Transactional
    public void marcarComoPagada(Integer id, LocalDate fechaPago, BigDecimal monto, String formaPago, Integer usuarioId) {
        CuentaPorPagar cuenta = cuentasPorPagarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta por pagar no encontrada con ID: " + id));

        Transaccion transaccionOriginal = transaccionRepository.findById(cuenta.getTransaccion().getId())
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        cuenta.setEstatus("Pagado");
        cuenta.setFechaPago(fechaPago);
        cuenta.setMonto(monto);
        cuenta.setFormaPago(formaPago);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String fechaFormateada = LocalDateTime.now().format(formatter);
        cuenta.setNota(cuenta.getNota() != null ?
                cuenta.getNota() + " - Marcada como pagada el " + fechaFormateada :
                "Marcada como pagada el " + fechaFormateada);

        cuentasPorPagarRepository.save(cuenta);

        // Crear transacción asociada (registro del pago)
        Transaccion transaccionPago = new Transaccion();
        transaccionPago.setFecha(fechaPago);
        transaccionPago.setTipo(com.tss.tssmanager_backend.enums.TipoTransaccionEnum.GASTO);
        transaccionPago.setCategoria(transaccionOriginal.getCategoria());
        transaccionPago.setCuenta(cuenta.getCuenta());
        transaccionPago.setMonto(monto);
        transaccionPago.setEsquema(com.tss.tssmanager_backend.enums.EsquemaTransaccionEnum.UNICA);
        transaccionPago.setFechaPago(fechaPago);
        transaccionPago.setFormaPago(cuenta.getFormaPago());
        transaccionPago.setNotas("Transacción generada desde Cuentas por Pagar");
        transaccionPago.setFechaCreacion(LocalDateTime.now());
        transaccionPago.setFechaModificacion(LocalDateTime.now());
        transaccionRepository.save(transaccionPago);

        verificarYRegenerarSiEsNecesario(cuenta, transaccionOriginal);
    }

    @Transactional
    public void eliminarCuentaPorPagar(Integer id, Integer usuarioId) {
        if (!cuentasPorPagarRepository.existsById(id)) {
            throw new IllegalArgumentException("La cuenta por pagar con ID " + id + " no existe.");
        }
        cuentasPorPagarRepository.deleteById(id);
    }

    @Transactional
    private void verificarYRegenerarSiEsNecesario(CuentaPorPagar cuentaPagada, Transaccion transaccionOriginal) {
        // Verificar si quedan cuentas pendientes DESPUÉS de marcar esta como pagada
        boolean hayPendientes = cuentasPorPagarRepository
                .existsByTransaccionIdAndEstatusNot(transaccionOriginal.getId(), "Pagado");

        boolean esUltimaCuentaDeLaSerie = cuentaPagada.getNumeroPago().equals(cuentaPagada.getTotalPagos());

        if (!hayPendientes && esUltimaCuentaDeLaSerie && !transaccionOriginal.getEsquema().name().equals("UNICA")) {
            System.out.println("Última cuenta de la serie pagada para transacción " + transaccionOriginal.getId());

        }
    }

    @Transactional
    public void regenerarCuentasPorPagarManual(Integer transaccionId, LocalDate fechaUltimoPago) {
        Transaccion transaccionOriginal = transaccionRepository.findById(transaccionId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        regenerarCuentasPorPagar(transaccionOriginal, fechaUltimoPago);
    }

    @Transactional
    private void regenerarCuentasPorPagar(Transaccion transaccionOriginal, LocalDate fechaUltimoPago) {
        try {
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

            System.out.println("Cuentas por pagar regeneradas manualmente:");
            System.out.println("- Transacción original: " + transaccionOriginal.getId());
            System.out.println("- Nueva serie: " + nuevaTransaccion.getNumeroPagos() + " pagos");
            System.out.println("- Primera fecha de pago: " + proximaFechaPago);

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
                return fechaUltimoPago.plusMonths(1);
            case BIMESTRAL:
                return fechaUltimoPago.plusMonths(2);
            case TRIMESTRAL:
                return fechaUltimoPago.plusMonths(3);
            case SEMESTRAL:
                return fechaUltimoPago.plusMonths(6);
            case ANUAL:
                return fechaUltimoPago.plusYears(1);
            default:
                return fechaUltimoPago.plusMonths(1);
        }
    }
}