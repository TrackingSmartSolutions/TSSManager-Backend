package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.entity.Adjunto;
import com.tss.tssmanager_backend.entity.EmailRecord;
import com.tss.tssmanager_backend.entity.PlantillaCorreo;
import com.tss.tssmanager_backend.service.EmailService;
import com.tss.tssmanager_backend.service.PlantillaCorreoService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/correos")
public class EmailController {

    private final EmailService emailService;
    private final PlantillaCorreoService plantillaService;

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

        List<String> rutasArchivosAdjuntos = new ArrayList<>();

        // Procesar archivos adjuntos si existen
        if (archivosAdjuntos != null && archivosAdjuntos.length > 0) {
            for (MultipartFile archivo : archivosAdjuntos) {
                if (!archivo.isEmpty()) {
                    try {
                        // Crear directorio temporal si no existe
                        Path directorioTemporal = Paths.get("temp/adjuntos");
                        if (!Files.exists(directorioTemporal)) {
                            Files.createDirectories(directorioTemporal);
                        }

                        // Generar nombre único para el archivo
                        String nombreArchivo = UUID.randomUUID().toString() + "_" + archivo.getOriginalFilename();
                        Path rutaArchivo = directorioTemporal.resolve(nombreArchivo);

                        // Guardar archivo temporalmente
                        Files.copy(archivo.getInputStream(), rutaArchivo, StandardCopyOption.REPLACE_EXISTING);
                        String cloudinaryUrl = emailService.uploadTempFileToCloudinary(rutaArchivo);
                        rutasArchivosAdjuntos.add(cloudinaryUrl);
                        Files.deleteIfExists(rutaArchivo);

                    } catch (IOException e) {
                        System.err.println("Error al procesar archivo adjunto: " + e.getMessage());
                    }
                }
            }
        }

        EmailRecord emailRecord = emailService.enviarCorreo(destinatario, asunto, cuerpo, rutasArchivosAdjuntos, tratoId);

        if (emailRecord.isExito()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(emailRecord);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(emailRecord);
        }
    }

    @PostMapping("/plantilla")
    @Transactional
    public ResponseEntity<EmailRecord> enviarCorreoConPlantilla(
            @RequestParam String destinatario,
            @RequestParam Integer plantillaId,
            @RequestParam Integer tratoId,
            @RequestParam(required = false) String cuerpoPersonalizado,
            @RequestParam(required = false) MultipartFile[] archivosAdjuntosAdicionales) {

        try {
            // Obtener la plantilla con adjuntos
            Optional<PlantillaCorreo> plantillaOpt = plantillaService.obtenerPlantillaPorId(plantillaId);
            if (!plantillaOpt.isPresent()) {
                return ResponseEntity.badRequest().build();
            }

            PlantillaCorreo plantilla = plantillaOpt.get();

            // Usar el cuerpo personalizado si se proporciona, sino usar el de la plantilla
            String cuerpo = cuerpoPersonalizado != null ? cuerpoPersonalizado : plantilla.getMensaje();

            List<String> rutasArchivosAdjuntos = new ArrayList<>();


            if (plantilla.getAdjuntos() != null && !plantilla.getAdjuntos().isEmpty()) {
                for (Adjunto adjunto : plantilla.getAdjuntos()) {
                    rutasArchivosAdjuntos.add(adjunto.getAdjuntoUrl());
                }
            }

            // Procesar archivos adjuntos adicionales si existen
            if (archivosAdjuntosAdicionales != null && archivosAdjuntosAdicionales.length > 0) {
                for (MultipartFile archivo : archivosAdjuntosAdicionales) {
                    if (!archivo.isEmpty()) {
                        try {
                            // Crear directorio temporal si no existe
                            Path directorioTemporal = Paths.get("temp/adjuntos");
                            if (!Files.exists(directorioTemporal)) {
                                Files.createDirectories(directorioTemporal);
                            }

                            // Generar nombre único para el archivo
                            String nombreArchivo = UUID.randomUUID().toString() + "_" + archivo.getOriginalFilename();
                            Path rutaArchivo = directorioTemporal.resolve(nombreArchivo);

                            // Guardar archivo temporalmente
                            Files.copy(archivo.getInputStream(), rutaArchivo, StandardCopyOption.REPLACE_EXISTING);
                            String cloudinaryUrl = emailService.uploadTempFileToCloudinary(rutaArchivo);
                            rutasArchivosAdjuntos.add(cloudinaryUrl);

                            Files.deleteIfExists(rutaArchivo);

                        } catch (IOException e) {
                            System.err.println("Error al procesar archivo adjunto adicional: " + e.getMessage());
                        }
                    }
                }
            }

            EmailRecord emailRecord = emailService.enviarCorreo(
                    destinatario,
                    plantilla.getAsunto(),
                    cuerpo,
                    rutasArchivosAdjuntos,
                    tratoId
            );


            if (emailRecord.isExito()) {
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