package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.repository.ProcesoAutomaticoRepository;
import com.tss.tssmanager_backend.repository.TratoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class CorreosSeguimientoService {

    @Autowired
    private TratoRepository tratoRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ProcesoAutomaticoRepository procesoRepository;

    private final List<String> FASES_SEGUIMIENTO = Arrays.asList("ENVIO_DE_INFORMACION", "RESPUESTA_POR_CORREO");

    @Transactional
    public void activarCorreosSeguimiento(Integer tratoId, Integer procesoId) {
        Trato trato = tratoRepository.findById(tratoId)
                .orElseThrow(() -> new RuntimeException("Trato no encontrado"));

        // Validar que el trato esté en una fase de seguimiento
        if (!FASES_SEGUIMIENTO.contains(trato.getFase())) {
            throw new RuntimeException("El trato no está en una fase de seguimiento válida");
        }

        // Validar que el contacto tenga correos
        if (trato.getContacto() == null || trato.getContacto().getCorreos() == null ||
                trato.getContacto().getCorreos().isEmpty()) {
            throw new RuntimeException("No se puede activar correos de seguimiento: el contacto no tiene correos registrados");
        }

        // Validar que al menos un correo no esté vacío
        String correoValido = obtenerCorreoPrincipalContacto(trato.getContacto());
        if (correoValido == null || correoValido.trim().isEmpty()) {
            throw new RuntimeException("No se puede activar correos de seguimiento: el contacto no tiene correos válidos");
        }

        trato.setCorreosSeguimientoActivo(true);
        trato.setFechaActivacionSeguimiento(LocalDateTime.now());
        trato.setCorreosSeguimientoEnviados(0);
        trato.setProcesoAutomaticoId(procesoId);
        trato.setProcesoPasoActual(0);
        trato.setProcesoFechaInicio(LocalDateTime.now());
        tratoRepository.save(trato);
    }

    @Transactional
    public void desactivarCorreosSeguimiento(Integer tratoId) {
        Trato trato = tratoRepository.findById(tratoId)
                .orElseThrow(() -> new RuntimeException("Trato no encontrado"));

        trato.setCorreosSeguimientoActivo(false);
        trato.setFechaActivacionSeguimiento(null);
        tratoRepository.save(trato);
    }

    @Transactional
    public void procesarCorreosSeguimiento() {
        List<Trato> tratosActivos = tratoRepository
                .findByCorreosSeguimientoActivoTrueAndFaseInWithContacto(FASES_SEGUIMIENTO);

        for (Trato trato : tratosActivos) {
            if (debeEnviarCorreo(trato)) {
                enviarCorreoSeguimiento(trato);
            }
        }
    }

    private boolean debeEnviarCorreo(Trato trato) {
        if (trato.getFechaActivacionSeguimiento() == null || trato.getProcesoAutomaticoId() == null) return false;

        ProcesoAutomatico proceso = procesoRepository.findByIdWithPasos(trato.getProcesoAutomaticoId()).orElse(null);
        if (proceso == null || proceso.getPasos().isEmpty()) return false;

        List<ProcesoPaso> pasos = proceso.getPasos().stream()
                .sorted(Comparator.comparingInt(ProcesoPaso::getOrden))
                .collect(Collectors.toList());

        int pasoActual = trato.getProcesoPasoActual() != null ? trato.getProcesoPasoActual() : 0;
        if (pasoActual >= pasos.size()) return false;

        LocalDate fechaActivacion = trato.getFechaActivacionSeguimiento().toLocalDate();
        LocalDate hoy = LocalDate.now();

        int diasAcumulados = 0;
        for (int i = 0; i <= pasoActual; i++) {
            diasAcumulados += pasos.get(i).getDias();
        }

        LocalDate fechaEnvio = fechaActivacion.plusDays(diasAcumulados);

        return !hoy.isBefore(fechaEnvio);
    }

    private void enviarCorreoSeguimiento(Trato trato) {
        ProcesoAutomatico proceso = procesoRepository.findByIdWithPasos(trato.getProcesoAutomaticoId()).orElse(null);
        if (proceso == null) return;

        List<ProcesoPaso> pasos = proceso.getPasos().stream()
                .sorted(Comparator.comparingInt(ProcesoPaso::getOrden))
                .collect(Collectors.toList());

        int pasoActual = trato.getProcesoPasoActual() != null ? trato.getProcesoPasoActual() : 0;
        if (pasoActual >= pasos.size()) return;

        ProcesoPaso paso = pasos.get(pasoActual);
        PlantillaCorreo plantilla = paso.getPlantilla();

        String correoDestinatario = obtenerCorreoPrincipalContacto(trato.getContacto());
        if (correoDestinatario == null) return;

        List<String> rutasAdjuntos = plantilla.getAdjuntos().stream()
                .map(Adjunto::getAdjuntoUrl).collect(Collectors.toList());

        emailService.enviarCorreo(correoDestinatario, plantilla.getAsunto(), plantilla.getMensaje(),
                rutasAdjuntos, null, trato.getId());

        trato.setProcesoPasoActual(pasoActual + 1);
        if (pasoActual + 1 >= pasos.size()) {
            trato.setCorreosSeguimientoActivo(false);
        }
        tratoRepository.save(trato);
    }

    private String obtenerCorreoPrincipalContacto(Contacto contacto) {
        if (contacto == null || contacto.getCorreos() == null || contacto.getCorreos().isEmpty()) {
            return null;
        }

        // Retornar el primer correo disponible
        return contacto.getCorreos().get(0).getCorreo();
    }

    public boolean estanActivosCorreosSeguimiento(Integer tratoId) {
        return tratoRepository.findById(tratoId)
                .map(trato -> trato.getCorreosSeguimientoActivo() != null && trato.getCorreosSeguimientoActivo())
                .orElse(false);
    }

    @Transactional
    public void verificarCorreosPendientes() {
        List<Trato> tratosActivos = tratoRepository
                .findByCorreosSeguimientoActivoTrueAndFaseInWithContacto(FASES_SEGUIMIENTO);
        for (Trato trato : tratosActivos) {
            if (debeEnviarCorreo(trato)) {
                enviarCorreoSeguimiento(trato);
            }
        }
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Mexico_City")
    @Transactional
    public void procesarCorreosSeguimientoAutomatico() {
        System.out.println("Procesando correos de seguimiento automáticos...");
        procesarCorreosSeguimiento();
    }
}