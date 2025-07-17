package com.tss.tssmanager_backend.listener;

import com.tss.tssmanager_backend.service.CorreosSeguimientoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartupListener {

    @Autowired
    private CorreosSeguimientoService correosSeguimientoService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            System.out.println("Aplicación iniciada - Verificando correos de seguimiento pendientes...");
            correosSeguimientoService.verificarCorreosPendientes();
            System.out.println("Verificación de correos pendientes completada.");
        } catch (Exception e) {
            System.err.println("Error al verificar correos pendientes en el inicio: " + e.getMessage());
            e.printStackTrace();
        }
    }
}