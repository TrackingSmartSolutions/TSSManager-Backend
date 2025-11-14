package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.entity.CuentaPorCobrar;
import com.tss.tssmanager_backend.entity.CuentaPorPagar;
import com.tss.tssmanager_backend.enums.EstatusPagoEnum;
import com.tss.tssmanager_backend.repository.CuentaPorCobrarRepository;
import com.tss.tssmanager_backend.repository.CuentaPorPagarRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class CuentasVencidasSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(CuentasVencidasSchedulerService.class);

    @Autowired
    private CuentaPorCobrarRepository cuentaPorCobrarRepository;

    @Autowired
    private CuentaPorPagarRepository cuentaPorPagarRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void ejecutarAlIniciar() {
        logger.info("Ejecutando verificación de cuentas vencidas al iniciar la aplicación...");
        actualizarCuentasVencidas();
    }

    @Scheduled(cron = "0 1 0 * * *")
    @Transactional
    public void actualizarCuentasVencidas() {
        logger.info("Iniciando proceso de actualización de cuentas vencidas...");

        LocalDate hoy = LocalDate.now();

        // Actualizar Cuentas por Cobrar
        int cobrarActualizadas = actualizarCuentasPorCobrarVencidas(hoy);

        // Actualizar Cuentas por Pagar
        int pagarActualizadas = actualizarCuentasPorPagarVencidas(hoy);

        logger.info("Proceso completado. Cuentas por Cobrar actualizadas: {}, Cuentas por Pagar actualizadas: {}",
                cobrarActualizadas, pagarActualizadas);
    }

    private int actualizarCuentasPorCobrarVencidas(LocalDate hoy) {
        List<CuentaPorCobrar> cuentasPendientes = cuentaPorCobrarRepository
                .findByFechaPagoBeforeAndEstatus(hoy, EstatusPagoEnum.PENDIENTE);

        int contador = 0;
        for (CuentaPorCobrar cuenta : cuentasPendientes) {
            cuenta.setEstatus(EstatusPagoEnum.VENCIDA);
            cuentaPorCobrarRepository.save(cuenta);
            contador++;

            logger.debug("Cuenta por Cobrar {} marcada como VENCIDA", cuenta.getFolio());
        }

        return contador;
    }

    private int actualizarCuentasPorPagarVencidas(LocalDate hoy) {
        List<CuentaPorPagar> cuentasPendientes = cuentaPorPagarRepository
                .findByFechaPagoBeforeAndEstatus(hoy, "Pendiente");

        int contador = 0;
        for (CuentaPorPagar cuenta : cuentasPendientes) {
            cuenta.setEstatus("Vencida");
            cuentaPorPagarRepository.save(cuenta);
            contador++;

            logger.debug("Cuenta por Pagar {} marcada como Vencida", cuenta.getFolio());
        }

        return contador;
    }
}