package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.entity.CuentaPorPagar;
import com.tss.tssmanager_backend.entity.Plataforma;
import com.tss.tssmanager_backend.entity.Sim;
import com.tss.tssmanager_backend.entity.Transaccion;
import com.tss.tssmanager_backend.enums.ConceptoCreditoEnum;
import com.tss.tssmanager_backend.enums.PlataformaEquipoEnum;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class CuentaPorPagarService {

    @Autowired
    private CuentaPorPagarRepository cuentasPorPagarRepository;

    @Autowired
    private CreditoPlataformaService creditoPlataformaService;

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private TransaccionService transaccionService;

    @Autowired
    private SimRepository simRepository;

    @Autowired
    private PlataformaService plataformaService;

    public List<CuentaPorPagar> obtenerTodas(String estatus) {

        if ("Todas".equals(estatus) || estatus == null) {
            return cuentasPorPagarRepository.findAllWithRelationsFiltered(null);
        }

        return cuentasPorPagarRepository.findAllWithRelationsFiltered(estatus);
    }

    @Transactional
    public void marcarComoPagada(Integer id, LocalDate fechaPago, BigDecimal montoPago, String formaPago, Integer usuarioId, boolean regenerarAutomaticamente, Integer cantidadCreditos) {
        CuentaPorPagar cuenta = cuentasPorPagarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta por pagar no encontrada con ID: " + id));

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
        boolean cuentaCompletamentePagada = nuevoSaldo.compareTo(BigDecimal.ZERO) == 0;

        if (cuentaCompletamentePagada) {
            cuenta.setEstatus("Pagado");
            actualizarVigenciaSim(cuenta, cuenta.getFechaPago());
        } else {
            cuenta.setEstatus("En proceso");
        }

        cuenta.setFormaPago(formaPago);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        ZonedDateTime fechaLocal = ZonedDateTime.now(ZoneId.of("America/Mexico_City"));
        String fechaFormateada = fechaLocal.format(formatter);
        String nota = String.format("Pago de $%s el %s", montoPago, fechaFormateada);
        cuenta.setNota(cuenta.getNota() != null ? cuenta.getNota() + " - " + nota : nota);

        cuentasPorPagarRepository.save(cuenta);

        Transaccion transaccionPago = new Transaccion();
        transaccionPago.setFecha(cuenta.getFechaPago());
        transaccionPago.setTipo(com.tss.tssmanager_backend.enums.TipoTransaccionEnum.GASTO);
        transaccionPago.setCategoria(transaccionOriginal.getCategoria());
        transaccionPago.setCuenta(cuenta.getCuenta());
        transaccionPago.setMonto(montoPago);
        transaccionPago.setEsquema(com.tss.tssmanager_backend.enums.EsquemaTransaccionEnum.UNICA);
        transaccionPago.setFechaPago(cuenta.getFechaPago());
        transaccionPago.setFormaPago(formaPago);
        transaccionPago.setNotas("Transacción generada desde Cuentas por Pagar - " + (transaccionOriginal.getNotas() != null ? transaccionOriginal.getNotas() : ""));
        transaccionPago.setFechaCreacion(LocalDateTime.now());
        transaccionPago.setFechaModificacion(LocalDateTime.now());
        transaccionRepository.save(transaccionPago);

        registrarAbonoCreditos(transaccionOriginal, montoPago, cuenta, cantidadCreditos);

        if (cuentaCompletamentePagada) {
            verificarYRegenerarSiEsNecesario(cuenta, transaccionOriginal, regenerarAutomaticamente, cuenta.getFechaPago());
        }
    }

    private void registrarAbonoCreditos(Transaccion transaccion, BigDecimal monto, CuentaPorPagar cuenta, Integer cantidadCreditos) {
        if (transaccion.getCategoria() != null &&
                (transaccion.getCategoria().getDescripcion().toLowerCase().contains("créditos plataforma") ||
                        transaccion.getCategoria().getDescripcion().toLowerCase().contains("licencias"))) {

            Plataforma plataforma = determinarPlataformaPorCuenta(transaccion.getCuenta());

            if (plataforma != null && cantidadCreditos != null && cantidadCreditos > 0) {
                LocalDateTime fechaPagoDateTime = cuenta.getFechaPago().atStartOfDay();

                // Diferenciar entre licencias (Fulltrack/F/Basic) y créditos (Track Solid/WhatsGPS)
                if (plataforma.getId().equals(5) || plataforma.getId().equals(6)) {
                    // Es una plataforma de LICENCIAS
                    String nota = String.format("Compra de %d licencias para %s",
                            cantidadCreditos, plataforma.getNombrePlataforma());

                    creditoPlataformaService.registrarCompraLicencias(
                            plataforma,
                            cantidadCreditos,
                            nota,
                            transaccion.getId(),
                            cuenta.getId(),
                            fechaPagoDateTime
                    );
                } else {
                    // Es una plataforma de CRÉDITOS
                    String subtipo = determinarSubtipoPorCuenta(transaccion.getCuenta());
                    ConceptoCreditoEnum concepto = ConceptoCreditoEnum.COMPRA;
                    String nota = String.format("Compra de %d créditos", cantidadCreditos);

                    creditoPlataformaService.registrarAbonoConSubtipo(
                            plataforma,
                            concepto,
                            new BigDecimal(cantidadCreditos),
                            nota,
                            transaccion.getId(),
                            cuenta.getId(),
                            subtipo,
                            fechaPagoDateTime
                    );
                }
            }
        }
    }


    private String determinarSubtipoPorCuenta(com.tss.tssmanager_backend.entity.CuentasTransacciones cuenta) {
        if (cuenta == null || cuenta.getNombre() == null) return null;

        String nombreCuenta = cuenta.getNombre().toLowerCase();

        if (nombreCuenta.contains("whatsgps anual")) {
            return "ANUAL";
        } else if (nombreCuenta.contains("whatsgps vitalicia")) {
            return "VITALICIA";
        }

        return null;
    }

    private Plataforma determinarPlataformaPorCuenta(com.tss.tssmanager_backend.entity.CuentasTransacciones cuenta) {
        if (cuenta == null || cuenta.getNombre() == null) return null;

        String nombreCuenta = cuenta.getNombre().toLowerCase();

        if (nombreCuenta.contains("whatsgps")) {
            return plataformaService.obtenerTodasLasPlataformas().stream()
                    .filter(p -> "WhatsGPS".equals(p.getNombrePlataforma()))
                    .findFirst()
                    .orElse(null);
        } else if (nombreCuenta.contains("tracksolid") || nombreCuenta.contains("track solid")) {
            return plataformaService.obtenerTodasLasPlataformas().stream()
                    .filter(p -> "Track Solid".equals(p.getNombrePlataforma()))
                    .findFirst()
                    .orElse(null);
        } else if (nombreCuenta.contains("fulltrack")) {
            return plataformaService.obtenerTodasLasPlataformas().stream()
                    .filter(p -> "Fulltrack".equals(p.getNombrePlataforma()))
                    .findFirst()
                    .orElse(null);
        } else if (nombreCuenta.contains("f/basic") || nombreCuenta.contains("fbasic")) {
            return plataformaService.obtenerTodasLasPlataformas().stream()
                    .filter(p -> "F/Basic".equals(p.getNombrePlataforma()))
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    @Transactional
    public void marcarComoPagada(Integer id, BigDecimal monto, String formaPago, Integer usuarioId) {
        marcarComoPagada(id, LocalDate.now(), monto, formaPago, usuarioId, false, null);
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

            // Recalcular el estatus basado en la nueva fecha si no está pagada
            if (!"Pagado".equals(cuenta.getEstatus()) && !"En proceso".equals(cuenta.getEstatus())) {
                LocalDate hoy = LocalDate.now();
                if (fechaPago.isBefore(hoy)) {
                    cuenta.setEstatus("Vencida");
                } else {
                    cuenta.setEstatus("Pendiente");
                }
            }
        }

        if (monto != null) {
            cuenta.setMonto(monto);

            // Recalcular saldo pendiente y estatus cuando cambie el monto
            BigDecimal montoPagado = cuenta.getMontoPagado() != null ? cuenta.getMontoPagado() : BigDecimal.ZERO;
            BigDecimal nuevoSaldo = monto.subtract(montoPagado);
            cuenta.setSaldoPendiente(nuevoSaldo);

            // Actualizar estatus basado en el nuevo saldo
            if (nuevoSaldo.compareTo(BigDecimal.ZERO) <= 0) {
                cuenta.setEstatus("Pagado");
                // Si la cuenta queda pagada, actualizar vigencia de SIM si existe
                if (cuenta.getSim() != null && fechaPago == null) {
                    // Usar la fecha actual si no se especifica una nueva fecha de pago
                    actualizarVigenciaSim(cuenta, LocalDate.now());
                } else if (cuenta.getSim() != null) {
                    actualizarVigenciaSim(cuenta, fechaPago);
                }
            } else if (montoPagado.compareTo(BigDecimal.ZERO) > 0) {
                cuenta.setEstatus("En proceso");
            } else {
                cuenta.setEstatus("Pendiente");
            }
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

                // Calcular la próxima fecha sumando exactamente 30 días
                LocalDate nuevaVigencia = fechaPago.plusDays(30);

                sim.setVigencia(Date.valueOf(nuevaVigencia));
                simRepository.save(sim);

                System.out.println("Vigencia de SIM " + sim.getNumero() + " actualizada a: " + nuevaVigencia + " (desde fecha de pago: " + fechaPago + ")");
            } catch (Exception e) {
                System.err.println("Error al actualizar vigencia de SIM: " + e.getMessage());
            }
        }
    }

    private void verificarYRegenerarSiEsNecesario(CuentaPorPagar cuentaPagada, Transaccion transaccionOriginal, boolean regenerarAutomaticamente, LocalDate fechaPagoReal) {
        if (!"Pagado".equals(cuentaPagada.getEstatus())) {
            return;
        }

        boolean esUltimaCuentaDeLaSerie = cuentaPagada.getNumeroPago().equals(cuentaPagada.getTotalPagos());
        boolean noEsUnica = !transaccionOriginal.getEsquema().name().equals("UNICA");

        if (esUltimaCuentaDeLaSerie && noEsUnica) {
            System.out.println("Última cuenta de la serie pagada para transacción " + transaccionOriginal.getId());

            if (regenerarAutomaticamente) {
                regenerarCuentasPorPagar(transaccionOriginal, fechaPagoReal, null);
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
        regenerarCuentasPorPagar(transaccionOriginal, fechaUltimoPago, null);
    }

    @Transactional
    private void regenerarCuentasPorPagar(Transaccion transaccionOriginal, LocalDate fechaUltimoPago, BigDecimal nuevoMonto) {
        try {
            // Obtener la SIM asociada ANTES de crear la nueva transacción
            Sim simAsociada = null;
            List<CuentaPorPagar> cuentasOriginales = cuentasPorPagarRepository.findByTransaccionId(transaccionOriginal.getId());
            if (!cuentasOriginales.isEmpty() && cuentasOriginales.get(0).getSim() != null) {
                simAsociada = cuentasOriginales.get(0).getSim();
            }

            Transaccion nuevaTransaccion = new Transaccion();
            nuevaTransaccion.setFecha(fechaUltimoPago);
            nuevaTransaccion.setTipo(transaccionOriginal.getTipo());
            nuevaTransaccion.setCategoria(transaccionOriginal.getCategoria());
            nuevaTransaccion.setCuenta(transaccionOriginal.getCuenta());

            nuevaTransaccion.setMonto(nuevoMonto != null ? nuevoMonto : transaccionOriginal.getMonto());
            nuevaTransaccion.setEsquema(transaccionOriginal.getEsquema());

            LocalDate proximaFechaPago = calcularProximaFechaPago(fechaUltimoPago, transaccionOriginal.getEsquema());
            nuevaTransaccion.setFechaPago(proximaFechaPago);

            nuevaTransaccion.setFormaPago(transaccionOriginal.getFormaPago());
            nuevaTransaccion.setNotas("Serie regenerada - " + (transaccionOriginal.getNotas() != null ? transaccionOriginal.getNotas() : ""));
            nuevaTransaccion.setNumeroPagos(transaccionOriginal.getNumeroPagos());
            nuevaTransaccion.setFechaCreacion(LocalDateTime.now());
            nuevaTransaccion.setFechaModificacion(LocalDateTime.now());

            transaccionService.agregarTransaccion(nuevaTransaccion);

            if (simAsociada != null) {
                List<CuentaPorPagar> nuevasCuentas = cuentasPorPagarRepository.findByTransaccionId(nuevaTransaccion.getId());
                for (CuentaPorPagar nuevaCuenta : nuevasCuentas) {
                    nuevaCuenta.setSim(simAsociada);
                    cuentasPorPagarRepository.save(nuevaCuenta);
                }

                System.out.println("SIM " + simAsociada.getNumero() + " asociada a " + nuevasCuentas.size() + " nuevas cuentas por pagar");
            }

            System.out.println("Cuentas por pagar regeneradas exitosamente:");
            System.out.println("- Transacción original: " + transaccionOriginal.getId());
            System.out.println("- Nueva transacción: " + nuevaTransaccion.getId());
            System.out.println("- Nueva serie: " + nuevaTransaccion.getNumeroPagos() + " pagos");
            System.out.println("- Primera fecha de pago: " + proximaFechaPago);
            System.out.println("- Monto: " + nuevaTransaccion.getMonto());

        } catch (Exception e) {
            System.err.println("Error al regenerar cuentas por pagar: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error crítico al regenerar cuentas por pagar: " + e.getMessage(), e);
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