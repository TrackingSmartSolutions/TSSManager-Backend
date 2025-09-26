package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.CuentaPorCobrarDTO;
import com.tss.tssmanager_backend.entity.CategoriaTransacciones;
import com.tss.tssmanager_backend.entity.CuentaPorCobrar;
import com.tss.tssmanager_backend.enums.EsquemaCobroEnum;
import com.tss.tssmanager_backend.enums.TipoTransaccionEnum;
import com.tss.tssmanager_backend.exception.ResourceNotFoundException;
import com.tss.tssmanager_backend.repository.CategoriaTransaccionesRepository;
import com.tss.tssmanager_backend.repository.CuentaPorCobrarRepository;
import com.tss.tssmanager_backend.service.CuentaPorCobrarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cuentas-por-cobrar")
public class CuentaPorCobrarController {

    private static final Logger logger = LoggerFactory.getLogger(CuentaPorCobrarController.class);

    @Autowired
    private CuentaPorCobrarService cuentaPorCobrarService;

    @Autowired
    private CuentaPorCobrarRepository cuentaPorCobrarRepository;

    @Autowired
    private CategoriaTransaccionesRepository categoriaTransaccionesRepository;

    @PostMapping("/from-cotizacion/{cotizacionId}")
    public ResponseEntity<List<CuentaPorCobrarDTO>> crearCuentasPorCobrarFromCotizacion(
            @PathVariable Integer cotizacionId,
            @RequestParam EsquemaCobroEnum esquema,
            @RequestParam List<String> conceptosSeleccionados,
            @RequestParam(required = false) Integer numeroPagos,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicial) {
        logger.info("Solicitud para crear cuentas por cobrar desde cotización ID: {} with numeroPagos: {} y fechaInicial: {}", cotizacionId, numeroPagos, fechaInicial);
        return ResponseEntity.ok(cuentaPorCobrarService.crearCuentasPorCobrarFromCotizacion(cotizacionId, esquema, conceptosSeleccionados, numeroPagos, fechaInicial));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CuentaPorCobrarDTO> actualizarCuentaPorCobrar(
            @PathVariable Integer id,
            @RequestBody CuentaPorCobrarDTO dto) {
        logger.info("Solicitud para actualizar cuenta por cobrar con ID: {}", id);
        return ResponseEntity.ok(cuentaPorCobrarService.actualizarCuentaPorCobrar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCuentaPorCobrar(@PathVariable Integer id) {
        logger.info("Solicitud para eliminar cuenta por cobrar con ID: {}", id);
        cuentaPorCobrarService.eliminarCuentaPorCobrar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/marcar-pagada")
    public ResponseEntity<CuentaPorCobrarDTO> marcarComoPagada(@PathVariable Integer id,
                                                               @RequestPart("fechaPago") String fechaPago,
                                                               @RequestPart("montoPago") String montoPago,
                                                               @RequestPart("categoriaId") String categoriaId,
                                                               @RequestPart("comprobante") MultipartFile comprobante) throws Exception {
        logger.info("Solicitud para marcar como pagada cuenta por cobrar con ID: {} con monto: {} y categoría: {}", id, montoPago, categoriaId);
        LocalDate fechaPagoDate = LocalDate.parse(fechaPago);
        BigDecimal montoDecimal = new BigDecimal(montoPago);
        Integer categoriaIdInt = Integer.parseInt(categoriaId);
        return ResponseEntity.ok(cuentaPorCobrarService.marcarComoPagada(id, fechaPagoDate, montoDecimal, comprobante, categoriaIdInt));
    }

    @GetMapping("/categorias-ingreso")
    public ResponseEntity<List<CategoriaTransacciones>> obtenerCategoriasIngreso() {
        try {
            List<CategoriaTransacciones> categoriasIngreso = categoriaTransaccionesRepository.findByTipo(TipoTransaccionEnum.INGRESO);
            return ResponseEntity.ok(categoriasIngreso);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping
    public ResponseEntity<List<CuentaPorCobrarDTO>> listarCuentasPorCobrar() {
        logger.info("Solicitud para listar todas las cuentas por cobrar");
        return ResponseEntity.ok(cuentaPorCobrarService.listarCuentasPorCobrar());
    }

    @GetMapping("/{id}/check-vinculada")
    public ResponseEntity<Map<String, Boolean>> checkVinculada(@PathVariable Integer id) {
        boolean vinculada = cuentaPorCobrarService.isVinculada(id);
        return ResponseEntity.ok(Map.of("vinculada", vinculada));
    }

    @GetMapping("/{id}/download-comprobante")
    public ResponseEntity<byte[]> descargarComprobante(@PathVariable Integer id) throws Exception {
        logger.info("Solicitud para descargar comprobante de pago con ID: {}", id);

        // Obtener el contenido del archivo desde el servicio
        byte[] fileContent = cuentaPorCobrarService.obtenerComprobantePago(id);

        // Configurar encabezados para la descarga
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF); // Asumimos que el comprobante es PDF
        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta por cobrar no encontrada con id: " + id));
        String[] urlParts = cuenta.getComprobantePagoUrl().split("/");
        String fileName = urlParts.length > 0 ? urlParts[urlParts.length - 1] : "comprobante_pago.pdf";
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setContentLength(fileContent.length);

        return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
    }

    @GetMapping("/vinculaciones")
    public ResponseEntity<Map<String, List<Integer>>> obtenerVinculaciones() {
        logger.info("Obteniendo todas las vinculaciones de cuentas por cobrar");
        try {
            List<Integer> idsVinculadas = cuentaPorCobrarRepository.findAllVinculatedIds();
            return ResponseEntity.ok(Map.of("idsVinculadas", idsVinculadas));
        } catch (Exception e) {
            logger.error("Error al obtener vinculaciones: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

}