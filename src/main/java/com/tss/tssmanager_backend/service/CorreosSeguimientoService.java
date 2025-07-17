package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.entity.Contacto;
import com.tss.tssmanager_backend.entity.PlantillaCorreo;
import com.tss.tssmanager_backend.entity.Trato;
import com.tss.tssmanager_backend.entity.Adjunto;
import com.tss.tssmanager_backend.repository.TratoRepository;
import com.tss.tssmanager_backend.repository.PlantillaCorreoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

@Service
public class CorreosSeguimientoService {

    @Autowired
    private TratoRepository tratoRepository;

    @Autowired
    private PlantillaCorreoRepository plantillaCorreoRepository;

    @Autowired
    private EmailService emailService;

    private final List<String> FASES_SEGUIMIENTO = Arrays.asList("ENVIO_DE_INFORMACION", "RESPUESTA_POR_CORREO");

    @Transactional
    public void activarCorreosSeguimiento(Integer tratoId) {
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
        List<Trato> tratosActivos = tratoRepository.findByCorreosSeguimientoActivoTrueAndFaseIn(FASES_SEGUIMIENTO);

        for (Trato trato : tratosActivos) {
            if (debeEnviarCorreo(trato)) {
                enviarCorreoSeguimiento(trato);
            }
        }
    }

    private boolean debeEnviarCorreo(Trato trato) {
        if (trato.getFechaActivacionSeguimiento() == null) {
            return false;
        }

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime fechaActivacion = trato.getFechaActivacionSeguimiento();
        long diasTranscurridos = java.time.Duration.between(fechaActivacion, ahora).toDays();

        int correosSeguimientoEnviados = trato.getCorreosSeguimientoEnviados() != null ?
                trato.getCorreosSeguimientoEnviados() : 0;

        // Verificar si debe enviar el correo según los días transcurridos
        if (correosSeguimientoEnviados == 0 && diasTranscurridos >= 3) {
            return true; // Primer correo (día 3)
        } else if (correosSeguimientoEnviados == 1 && diasTranscurridos >= 6) {
            return true; // Segundo correo (día 6)
        } else if (correosSeguimientoEnviados == 2 && diasTranscurridos >= 9) {
            return true; // Tercer correo (día 9)
        }

        return false;
    }

    private void enviarCorreoSeguimiento(Trato trato) {
        try {
            if (trato.getContacto() == null || trato.getContacto().getCorreos() == null || trato.getContacto().getCorreos().isEmpty()) {
                System.err.println("No se puede enviar correo de seguimiento: contacto sin correos para trato " + trato.getId());
                return;
            }

            // Obtener el primer correo del contacto
            String correoDestinatario = trato.getContacto().getCorreos().get(0).getCorreo();
            if (correoDestinatario == null || correoDestinatario.trim().isEmpty()) {
                System.err.println("No se puede enviar correo de seguimiento: correo vacío para trato " + trato.getId());
                return;
            }

            int numeroCorreo = (trato.getCorreosSeguimientoEnviados() != null ?
                    trato.getCorreosSeguimientoEnviados() : 0) + 1;

            PlantillaCorreo plantilla = obtenerPlantillaSeguimiento(numeroCorreo);
            if (plantilla == null) {
                System.err.println("No se encontró plantilla de seguimiento para el correo " + numeroCorreo);
                return;
            }

            // Preparar adjuntos de la plantilla
            List<String> rutasAdjuntos = new ArrayList<>();
            if (plantilla.getAdjuntos() != null && !plantilla.getAdjuntos().isEmpty()) {
                for (Adjunto adjunto : plantilla.getAdjuntos()) {
                    rutasAdjuntos.add(adjunto.getAdjuntoUrl());
                }
            }

            // Enviar el correo
            emailService.enviarCorreo(
                    correoDestinatario,
                    plantilla.getAsunto(),
                    plantilla.getMensaje(),
                    rutasAdjuntos,
                    trato.getId()
            );

            // Actualizar el contador de correos enviados
            trato.setCorreosSeguimientoEnviados(numeroCorreo);
            if (numeroCorreo >= 3) {
                trato.setCorreosSeguimientoActivo(false);
            }

            tratoRepository.save(trato);

        } catch (Exception e) {
            System.err.println("Error al enviar correo de seguimiento para trato " + trato.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private PlantillaCorreo obtenerPlantillaSeguimiento(int numeroCorreo) {
        List<PlantillaCorreo> plantillas = plantillaCorreoRepository.findAll();

        for (PlantillaCorreo plantilla : plantillas) {
            if (plantilla.getNombre().toLowerCase().contains("seguimiento " + numeroCorreo)) {
                return plantilla;
            }
        }

        return null;
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
        List<Trato> tratosActivos = tratoRepository.findByCorreosSeguimientoActivoTrueAndFaseIn(FASES_SEGUIMIENTO);

        for (Trato trato : tratosActivos) {
            if (hayCorreosPendientes(trato)) {
                enviarCorreosPendientes(trato);
            }
        }
    }

    private boolean hayCorreosPendientes(Trato trato) {
        if (trato.getFechaActivacionSeguimiento() == null) {
            return false;
        }

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime fechaActivacion = trato.getFechaActivacionSeguimiento();
        long diasTranscurridos = java.time.Duration.between(fechaActivacion, ahora).toDays();

        int correosSeguimientoEnviados = trato.getCorreosSeguimientoEnviados() != null ?
                trato.getCorreosSeguimientoEnviados() : 0;

        // Verificar si hay correos que debieron enviarse
        return (correosSeguimientoEnviados == 0 && diasTranscurridos >= 3) ||
                (correosSeguimientoEnviados == 1 && diasTranscurridos >= 6) ||
                (correosSeguimientoEnviados == 2 && diasTranscurridos >= 9);
    }

    private void enviarCorreosPendientes(Trato trato) {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime fechaActivacion = trato.getFechaActivacionSeguimiento();
        long diasTranscurridos = java.time.Duration.between(fechaActivacion, ahora).toDays();

        int correosSeguimientoEnviados = trato.getCorreosSeguimientoEnviados() != null ?
                trato.getCorreosSeguimientoEnviados() : 0;

        // Enviar todos los correos pendientes de una vez
        if (correosSeguimientoEnviados == 0 && diasTranscurridos >= 3) {
            enviarCorreoSeguimiento(trato);
        }
        if (correosSeguimientoEnviados <= 1 && diasTranscurridos >= 6) {
            enviarCorreoSeguimiento(trato);
        }
        if (correosSeguimientoEnviados <= 2 && diasTranscurridos >= 9) {
            enviarCorreoSeguimiento(trato);
        }
    }
}