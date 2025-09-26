package com.tss.tssmanager_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class SimpleHealthController {

    private static final Logger logger = LoggerFactory.getLogger(SimpleHealthController.class);

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        logger.info("Health check endpoint /ping called");
        return ResponseEntity.ok("pong");
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        logger.info("Health check endpoint /health called");
        return ResponseEntity.ok("UP");
    }

    @GetMapping("/")
    public ResponseEntity<String> root() {
        logger.info("Root endpoint / called");
        return ResponseEntity.ok("TSS Manager Backend is running!");
    }

    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        logger.info("Ready check endpoint /ready called");
        return ResponseEntity.ok("READY");
    }
}