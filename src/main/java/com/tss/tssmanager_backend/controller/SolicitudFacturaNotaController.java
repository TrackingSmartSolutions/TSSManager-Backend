package com.tss.tssmanager_backend.controller;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.tss.tssmanager_backend.dto.SolicitudFacturaNotaDTO;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.TipoDocumentoSolicitudEnum;
import com.tss.tssmanager_backend.exception.ResourceNotFoundException;
import com.tss.tssmanager_backend.service.SolicitudFacturaNotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import com.tss.tssmanager_backend.dto.FacturaDTO;
import com.tss.tssmanager_backend.repository.FacturaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/solicitudes-factura-nota")
public class SolicitudFacturaNotaController {

    private static final Logger logger = LoggerFactory.getLogger(SolicitudFacturaNotaController.class);

    @Autowired
    private SolicitudFacturaNotaService solicitudService;

    @Autowired
    private FacturaRepository facturaRepository;

    // Endpoints para Emisor
    @PostMapping("/emisores")
    public ResponseEntity<Emisor> crearEmisor(@RequestPart Emisor emisor, @RequestPart(required = false) MultipartFile constanciaRegimen) throws Exception {
        logger.info("Solicitud para crear emisor: {}", emisor.getNombre());
        return ResponseEntity.ok(solicitudService.crearEmisor(emisor, constanciaRegimen));
    }

    @PutMapping("/emisores/{id}")
    public ResponseEntity<Emisor> actualizarEmisor(@PathVariable Integer id, @RequestPart Emisor emisor, @RequestPart(required = false) MultipartFile constanciaRegimen) throws Exception {
        logger.info("Solicitud para actualizar emisor con ID: {}", id);
        return ResponseEntity.ok(solicitudService.actualizarEmisor(id, emisor, constanciaRegimen));
    }

    @DeleteMapping("/emisores/{id}")
    public ResponseEntity<Void> eliminarEmisor(@PathVariable Integer id) {
        logger.info("Solicitud para eliminar emisor con ID: {}", id);
        solicitudService.eliminarEmisor(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/emisores")
    public ResponseEntity<List<Emisor>> listarEmisores() {
        logger.info("Solicitud para listar emisores");
        return ResponseEntity.ok(solicitudService.listarEmisores());
    }

    // Endpoints para SolicitudFacturaNota
    @PostMapping
    public ResponseEntity<SolicitudFacturaNotaDTO> crearSolicitud(@RequestBody SolicitudFacturaNota solicitud) throws Exception {
        logger.info("Solicitud para crear solicitud de tipo: {}", solicitud.getTipo());
        return ResponseEntity.ok(solicitudService.crearSolicitud(solicitud));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SolicitudFacturaNotaDTO> actualizarSolicitud(@PathVariable Integer id, @RequestBody SolicitudFacturaNota solicitud) throws Exception {
        logger.info("Solicitud para actualizar solicitud con ID: {}", id);
        return ResponseEntity.ok(solicitudService.actualizarSolicitud(id, solicitud));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarSolicitud(@PathVariable Integer id) {
        logger.info("Solicitud para eliminar solicitud con ID: {}", id);
        solicitudService.eliminarSolicitud(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<SolicitudFacturaNotaDTO>> listarSolicitudes() {
        logger.info("Solicitud para listar solicitudes");
        return ResponseEntity.ok(solicitudService.listarSolicitudes());
    }

    @GetMapping("/solicitudes/{id}/download-pdf")
    public ResponseEntity<ByteArrayResource> descargarSolicitudPDF(@PathVariable Integer id) throws Exception {
        logger.info("Solicitud para descargar PDF de solicitud con ID: {}", id);
        ByteArrayResource resource = solicitudService.generateSolicitudPDF(id);
        SolicitudFacturaNota solicitud = solicitudService.findById(id); // Para obtener el nombre del archivo

        HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        String fileName = solicitud.getIdentificador() + "_" +
                solicitud.getFechaEmision().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".pdf";
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setContentType(MediaType.APPLICATION_PDF);

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(resource.contentLength())
                .body(resource);
    }

    private PdfPCell createCell(String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(Font.HELVETICA, 12)));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        return cell;
    }

    // Endpoints para Factura
    @PostMapping("/facturas")
    public ResponseEntity<FacturaDTO> timbrarFactura(@RequestPart Factura factura, @RequestPart("archivo") MultipartFile archivo) throws Exception {
        logger.info("Solicitud para timbrar factura para solicitud: {}", factura.getNoSolicitud());
        return ResponseEntity.ok(solicitudService.timbrarFactura(factura, archivo));
    }

    @GetMapping("/facturas")
    public ResponseEntity<List<FacturaDTO>> listarFacturas() {
        logger.info("Solicitud para listar facturas");
        return ResponseEntity.ok(solicitudService.listarFacturas());
    }

    @GetMapping("/facturas/{id}/download")
    public ResponseEntity<byte[]> descargarFactura(@PathVariable Integer id) throws Exception {
        logger.info("Solicitud para descargar factura con ID: {}", id);
        Factura factura = facturaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Factura no encontrada con id: " + id));

        if (factura.getArchivoUrl() == null || factura.getArchivoUrl().isEmpty()) {
            throw new ResourceNotFoundException("No se encontró el archivo de la factura.");
        }

        // Hacer una solicitud a Cloudinary para obtener el archivo
        java.net.URL url = new java.net.URL(factura.getArchivoUrl());
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();

        if (connection.getResponseCode() != 200) {
            throw new Exception("Error al acceder al archivo en Cloudinary: " + connection.getResponseMessage());
        }

        byte[] fileContent = connection.getInputStream().readAllBytes();
        connection.disconnect();

        // Configurar encabezados para la descarga
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        String[] urlParts = factura.getArchivoUrl().split("/");
        String fileName = urlParts.length > 0 ? urlParts[urlParts.length - 1] : "factura.pdf";
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setContentLength(fileContent.length);

        return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
    }

}
