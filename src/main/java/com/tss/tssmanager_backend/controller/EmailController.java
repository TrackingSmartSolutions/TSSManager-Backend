package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.EmailRecordDTO;
import com.tss.tssmanager_backend.entity.Adjunto;
import com.tss.tssmanager_backend.entity.EmailRecord;
import com.tss.tssmanager_backend.entity.PlantillaCorreo;
import com.tss.tssmanager_backend.service.EmailService;
import com.tss.tssmanager_backend.service.PlantillaCorreoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/correos")
public class EmailController {

    private final EmailService emailService;
    private final PlantillaCorreoService plantillaService;

    @GetMapping
    public ResponseEntity<List<EmailRecordDTO>> obtenerTodosLosCorreos() {
        List<EmailRecordDTO> correos = emailService.obtenerTodosLosCorreos();
        if (correos.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(correos);
    }

    public EmailController(EmailService emailService, PlantillaCorreoService plantillaService) {
        this.emailService = emailService;
        this.plantillaService = plantillaService;
    }

    @PostMapping
    public ResponseEntity<EmailRecord> enviarCorreo(
            @RequestParam String destinatario,
            @RequestParam String asunto,
            @RequestParam String cuerpo,
            @RequestParam Integer tratoId,
            @RequestParam(required = false) MultipartFile[] archivosAdjuntos) {

        EmailRecord emailRecord = emailService.enviarCorreo(
                destinatario,
                asunto,
                cuerpo,
                null,
                archivosAdjuntos,
                tratoId
        );

        if (emailRecord != null && emailRecord.isExito()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(emailRecord);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(emailRecord);
        }
    }

    @PostMapping("/plantilla")
    public ResponseEntity<EmailRecord> enviarCorreoConPlantilla(
            @RequestParam String destinatario,
            @RequestParam Integer plantillaId,
            @RequestParam Integer tratoId,
            @RequestParam(required = false) String cuerpoPersonalizado,
            @RequestParam(required = false) MultipartFile[] archivosAdjuntosAdicionales) {

        try {
            // Obtener la plantilla con adjuntos (URLs de Cloudinary)
            Optional<PlantillaCorreo> plantillaOpt = plantillaService.obtenerPlantillaPorId(plantillaId);
            if (!plantillaOpt.isPresent()) {
                return ResponseEntity.badRequest().build();
            }

            PlantillaCorreo plantilla = plantillaOpt.get();
            String cuerpo = cuerpoPersonalizado != null ? cuerpoPersonalizado : plantilla.getMensaje();

            List<String> rutasArchivosAdjuntos = new ArrayList<>();
            if (plantilla.getAdjuntos() != null && !plantilla.getAdjuntos().isEmpty()) {
                for (Adjunto adjunto : plantilla.getAdjuntos()) {
                    rutasArchivosAdjuntos.add(adjunto.getAdjuntoUrl());
                }
            }

            EmailRecord emailRecord = emailService.enviarCorreo(
                    destinatario,
                    plantilla.getAsunto(),
                    cuerpo,
                    rutasArchivosAdjuntos,
                    archivosAdjuntosAdicionales,
                    tratoId
            );

            if (emailRecord != null && emailRecord.isExito()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(emailRecord);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(emailRecord);
            }

        } catch (Exception e) {
            System.err.println("Error al enviar correo con plantilla: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/trato/{tratoId}")
    public ResponseEntity<List<EmailRecord>> obtenerCorreosPorTratoId(@PathVariable Integer tratoId) {
        List<EmailRecord> correos = emailService.obtenerCorreosPorTratoId(tratoId);
        if (correos.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(correos);
    }
}