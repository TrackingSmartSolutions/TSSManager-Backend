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

    public List<CuentaPorPagar> obtenerTodas() {
        return cuentasPorPagarRepository.findAll();
    }

    @Transactional
    public void marcarComoPagada(Integer id, LocalDate fechaPago, BigDecimal monto, Integer usuarioId) {
        CuentaPorPagar cuenta = cuentasPorPagarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta por pagar no encontrada con ID: " + id));
        cuenta.setEstatus("Pagado");
        cuenta.setFechaPago(fechaPago);
        cuenta.setMonto(monto);

        // Formatear LocalDateTime sin zona horaria
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String fechaFormateada = LocalDateTime.now().format(formatter);
        cuenta.setNota(cuenta.getNota() != null ? cuenta.getNota() + " - Marcada como pagada el " + fechaFormateada : "Marcada como pagada el " + fechaFormateada);

        cuentasPorPagarRepository.save(cuenta);

        // Crear transacción asociada
        Transaccion transaccion = new Transaccion();
        transaccion.setFecha(fechaPago);
        transaccion.setTipo(com.tss.tssmanager_backend.enums.TipoTransaccionEnum.GASTO);
        transaccion.setCategoria(cuenta.getTransaccion().getCategoria());
        transaccion.setCuenta(cuenta.getCuenta());
        transaccion.setMonto(monto);
        transaccion.setEsquema(cuenta.getTransaccion().getEsquema());
        transaccion.setFechaPago(fechaPago);
        transaccion.setFormaPago(cuenta.getFormaPago());
        transaccion.setNotas("Transacción generada desde Cuentas por Pagar");
        transaccion.setFechaCreacion(LocalDateTime.now());
        transaccion.setFechaModificacion(LocalDateTime.now());
        transaccionRepository.save(transaccion);

        // Verificar si es la última cuenta
        if (!cuentasPorPagarRepository.existsByTransaccionIdAndEstatusNot(cuenta.getTransaccion().getId(), "Pagado")) {
            System.out.println("Última cuenta pagada para transacción " + cuenta.getTransaccion().getId());
        }
    }

    @Transactional
    public void eliminarCuentaPorPagar(Integer id, Integer usuarioId) {
        if (!cuentasPorPagarRepository.existsById(id)) {
            throw new IllegalArgumentException("La cuenta por pagar con ID " + id + " no existe.");
        }
        cuentasPorPagarRepository.deleteById(id);
    }
}