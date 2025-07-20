package com.tss.tssmanager_backend.listener;

import com.tss.tssmanager_backend.repository.HistorialLimpiezaRepository;
import com.tss.tssmanager_backend.service.AlmacenamientoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

@Component
@Slf4j
public class CleanupStartupRunner implements ApplicationRunner {

    @Autowired
    private AlmacenamientoService almacenamientoService;

    @Autowired
    private HistorialLimpiezaRepository historialRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Verificando limpieza pendiente al arranque del sistema...");

        try {
            // Verificar si se perdió la limpieza diaria
            if (debeProcesarLimpiezaDiaria()) {
                log.info("Ejecutando limpieza diaria pendiente...");
                almacenamientoService.ejecutarLimpiezaAutomatica();
            }

            // Verificar si se perdió el reporte semanal
            if (debeProcesarReporteSemanal()) {
                log.info("Generando reporte semanal pendiente...");
                almacenamientoService.generarReporteEstadisticasSemanales();
            }

        } catch (Exception e) {
            log.error("Error durante la verificación de limpieza al arranque", e);
        }
    }

    private boolean debeProcesarLimpiezaDiaria() {
        try {
            LocalDateTime hace24Horas = LocalDateTime.now().minusHours(24);
            long limpiezasRecientes = historialRepository.countByTipoLimpiezaAndFechaLimpiezaAfter(
                    "AUTOMATICA", hace24Horas
            );
            return limpiezasRecientes == 0;
        } catch (Exception e) {
            log.error("Error al verificar limpieza diaria", e);
            return true; // Ejecutar limpieza si hay error para garantizar consistencia
        }
    }

    private boolean debeProcesarReporteSemanal() {
        try {
            // Verificar si es lunes y no se ha generado reporte desde el último domingo
            LocalDateTime now = LocalDateTime.now();
            DayOfWeek dayOfWeek = now.getDayOfWeek();

            // Si es lunes, martes o miércoles, verificar si se perdió el reporte del domingo
            if (dayOfWeek == DayOfWeek.MONDAY || dayOfWeek == DayOfWeek.TUESDAY || dayOfWeek == DayOfWeek.WEDNESDAY) {
                LocalDateTime ultimoDomingo = now.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));

                // Verificar si hay reportes desde el último domingo
                long reportesRecientes = historialRepository.countByTipoLimpiezaAndFechaLimpiezaAfter(
                        "REPORTE_SEMANAL", ultimoDomingo
                );

                return reportesRecientes == 0;
            }

            return false;
        } catch (Exception e) {
            log.error("Error al verificar reporte semanal", e);
            return false;
        }
    }
}