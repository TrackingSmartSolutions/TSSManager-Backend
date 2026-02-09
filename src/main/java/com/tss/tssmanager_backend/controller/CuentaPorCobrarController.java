package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.*;
import com.tss.tssmanager_backend.entity.CategoriaTransacciones;
import com.tss.tssmanager_backend.entity.CuentaPorCobrar;
import com.tss.tssmanager_backend.enums.EsquemaCobroEnum;
import com.tss.tssmanager_backend.enums.EstatusPagoEnum;
import com.tss.tssmanager_backend.enums.TipoTransaccionEnum;
import com.tss.tssmanager_backend.exception.ResourceNotFoundException;
import com.tss.tssmanager_backend.repository.CategoriaTransaccionesRepository;
import com.tss.tssmanager_backend.repository.ComisionRepository;
import com.tss.tssmanager_backend.repository.CuentaPorCobrarRepository;
import com.tss.tssmanager_backend.service.ComisionService;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cuentas-por-cobrar")
public class CuentaPorCobrarController {

    private static final Logger logger = LoggerFactory.getLogger(CuentaPorCobrarController.class);

    @Autowired
    private CuentaPorCobrarService cuentaPorCobrarService;

    @Autowired
    private ComisionService comisionService;

    @Autowired
    private ComisionRepository comisionRepository;

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

    @PostMapping(value = "/{id}/marcar-pagada", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> marcarComoPagada(
            @PathVariable Integer id,
            @RequestParam("fechaPago") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaPago,
            @RequestParam("montoPago") BigDecimal montoPago,
            @RequestParam("categoriaId") Integer categoriaId,
            @RequestPart("comprobante") MultipartFile comprobante
    ) {
        try {
            logger.info("Solicitud para marcar pagada ID: {}, Monto: {}, Cat: {}", id, montoPago, categoriaId);

            CuentaPorCobrarDTO cuentaActualizada = cuentaPorCobrarService.marcarComoPagada(
                    id,
                    fechaPago,
                    montoPago,
                    comprobante,
                    categoriaId
            );

            boolean yaExisteComision = comisionService.existeComisionParaCuenta(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("cuenta", cuentaActualizada);
            response.put("mostrarModalComision", !yaExisteComision);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error al marcar como pagada", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/crear-comision")
    public ResponseEntity<ComisionDTO> crearComisionDesdeCuenta(
            @PathVariable Integer id,
            @RequestBody ComisionDesdeCuentaPorCobrarDTO dto) {
        try {
            CuentaPorCobrar cuenta = cuentaPorCobrarService.obtenerPorId(id);
            BigDecimal montoPagado = cuenta.getMontoPagado() != null ? cuenta.getMontoPagado() : BigDecimal.ZERO;

            ComisionDTO comision = comisionService.crearComisionDesdeCuentaPorCobrar(id, montoPagado, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(comision);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/trato/{tratoId}/pagadas-proceso")
    public ResponseEntity<List<CuentaPorCobrarSimpleDTO>> obtenerCuentasPorTratoConEstatus(
            @PathVariable Integer tratoId,
            @RequestParam(required = false) Integer cuentaActualId) {
        try {
            System.out.println("=== INICIO GET CUENTAS POR TRATO ===");
            System.out.println("tratoId recibido: " + tratoId);
            System.out.println("cuentaActualId: " + cuentaActualId);

            System.out.println("Obteniendo IDs con comisión...");
            List<Integer> idsConComision = comisionRepository.findAllCuentaPorCobrarIds();
            System.out.println("IDs con comisión: " + idsConComision);

            System.out.println("Buscando cuentas por trato...");
            List<CuentaPorCobrarSimpleProjection> cuentasProj = cuentaPorCobrarRepository
                    .findByTratoIdSimple(tratoId);
            System.out.println("Cuentas encontradas: " + cuentasProj.size());

            List<CuentaPorCobrarSimpleDTO> dtos = cuentasProj.stream()
                    .filter(c -> {
                        boolean esLaCuentaActual = cuentaActualId != null && c.getId().equals(cuentaActualId);
                        boolean tieneComision = idsConComision.contains(c.getId());

                        System.out.println("Cuenta " + c.getId() + " - esActual: " + esLaCuentaActual + ", tieneComision: " + tieneComision);

                        return esLaCuentaActual || !tieneComision;
                    })
                    .map(c -> new CuentaPorCobrarSimpleDTO(
                            c.getId(),
                            c.getFolio(),
                            c.getMontoPagado(),
                            c.getEstatus()
                    ))
                    .collect(Collectors.toList());

            System.out.println("DTOs finales: " + dtos.size());
            System.out.println("=== FIN GET CUENTAS POR TRATO ===");

            return ResponseEntity.ok(dtos);

        } catch (Exception e) {
            System.err.println("=== ERROR EN GET CUENTAS POR TRATO ===");
            System.err.println("tratoId: " + tratoId);
            System.err.println("cuentaActualId: " + cuentaActualId);
            System.err.println("Mensaje de error: " + e.getMessage());
            e.printStackTrace();
            System.err.println("=== FIN ERROR ===");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
    public ResponseEntity<List<CuentaPorCobrarDTO>> listarCuentasPorCobrar(
            @RequestParam(required = false) EstatusPagoEnum estatus) {
        logger.info("Solicitud para listar cuentas por cobrar filtradas por: {}", estatus);
        return ResponseEntity.ok(cuentaPorCobrarService.listarCuentasPorCobrar(estatus));
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