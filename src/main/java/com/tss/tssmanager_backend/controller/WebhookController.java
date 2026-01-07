package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    @Autowired
    private EmailService emailService;

    @PostMapping("/resend")
    public ResponseEntity<String> handleResendWebhook(@RequestBody Map<String, Object> payload) {
        try {
            String type = (String) payload.get("type");
            Map<String, Object> data = (Map<String, Object>) payload.get("data");

            if (data != null && type != null) {
                String emailId = (String) data.get("email_id");
                emailService.actualizarEstadoDesdeWebhook(emailId, type);
            }

            return ResponseEntity.ok("Webhook recibido");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error procesando webhook");
        }
    }
}