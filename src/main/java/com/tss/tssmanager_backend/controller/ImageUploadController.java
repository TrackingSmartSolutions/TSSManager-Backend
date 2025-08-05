package com.tss.tssmanager_backend.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
public class ImageUploadController {

    private final Cloudinary cloudinary;

    @Autowired
    public ImageUploadController(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @PostMapping("/image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            if (!file.getContentType().startsWith("image/")) {
                return ResponseEntity.badRequest().body("Solo se permiten archivos de imagen");
            }

            // Configurar opciones de subida para optimizar la imagen
            Map<String, Object> uploadOptions = ObjectUtils.asMap(
                    "folder", "email_images",
                    "quality", "auto:good",
                    "width", 600,
                    "height", 400,
                    "crop", "limit"
            );

            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    uploadOptions
            );

            return ResponseEntity.ok(Map.of("url", uploadResult.get("url").toString()));

        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error al subir la imagen: " + e.getMessage());
        }
    }
}