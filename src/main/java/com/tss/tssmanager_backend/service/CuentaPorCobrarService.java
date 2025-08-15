package com.tss.tssmanager_backend.service;

import com.cloudinary.Cloudinary;
import com.tss.tssmanager_backend.config.CloudinaryConfig;
import com.tss.tssmanager_backend.dto.CuentaPorCobrarDTO;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.EstatusPagoEnum;
import com.tss.tssmanager_backend.enums.EsquemaCobroEnum;
import com.tss.tssmanager_backend.enums.TipoTransaccionEnum;
import com.tss.tssmanager_backend.enums.EsquemaTransaccionEnum;
import com.tss.tssmanager_backend.exception.ResourceNotFoundException;
import com.tss.tssmanager_backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CuentaPorCobrarService {

    private static final Logger logger = LoggerFactory.getLogger(CuentaPorCobrarService.class);

    @Autowired
    private CuentaPorCobrarRepository cuentaPorCobrarRepository;

    @Autowired
    private CotizacionRepository cotizacionRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private TransaccionService transaccionService;

    @Autowired
    private CategoriaTransaccionesRepository categoriaTransaccionesRepository;

    @Autowired
    private CuentasTransaccionesRepository cuentasTransaccionesRepository;

    @Autowired
    private CloudinaryConfig cloudinaryConfig;

    @Transactional
    public List<CuentaPorCobrarDTO> crearCuentasPorCobrarFromCotizacion(Integer cotizacionId, EsquemaCobroEnum esquema, List<String> conceptosSeleccionados, Integer numeroPagos, LocalDate fechaInicial) {
        logger.info("Creando cuentas por cobrar desde cotización ID: {} with esquema: {} and numeroPagos: {}", cotizacionId, esquema, numeroPagos);

        Cotizacion cotizacion = cotizacionRepository.findById(cotizacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));

        Empresa cliente = cotizacion.getCliente();
        List<CuentaPorCobrar> cuentas = new ArrayList<>();

        // DEBUG: Imprimir conceptos disponibles y seleccionados
        logger.info("Conceptos seleccionados: {}", conceptosSeleccionados);
        logger.info("Conceptos disponibles en cotización:");
        cotizacion.getUnidades().forEach(u ->
                logger.info("  - Concepto: '{}', Importe: {}", u.getConcepto(), u.getImporteTotal())
        );

        // Obtener el total de las unidades seleccionadas con mejor matching
        BigDecimal totalUnidadesSeleccionadas = cotizacion.getUnidades().stream()
                .filter(u -> conceptosSeleccionados.stream()
                        .anyMatch(conceptoSeleccionado ->
                                u.getConcepto().trim().equalsIgnoreCase(conceptoSeleccionado.trim())
                        ))
                .map(UnidadCotizacion::getImporteTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        logger.info("Total de unidades seleccionadas calculado: {}", totalUnidadesSeleccionadas);

        // Validación para evitar importes en 0
        if (totalUnidadesSeleccionadas.compareTo(BigDecimal.ZERO) == 0) {
            logger.warn("¡ADVERTENCIA! El total de unidades seleccionadas es 0. Esto causará cuentas con cantidadCobrar = 0");
            logger.warn("Verificar que los conceptos seleccionados coincidan con los de la cotización");
        }

        LocalDate fechaBase = fechaInicial;

        if (esquema == EsquemaCobroEnum.ANUAL) {
            // Para ANUAL: numeroPagos + 1 cuentas
            int totalCuentas = numeroPagos + 1;

            for (int i = 0; i < totalCuentas; i++) {
                CuentaPorCobrar cuenta = new CuentaPorCobrar();
                cuenta.setCliente(cliente);
                cuenta.setCotizacion(cotizacion);
                cuenta.setEstatus(EstatusPagoEnum.PENDIENTE);
                cuenta.setEsquema(esquema);
                cuenta.setNoEquipos(cotizacion.getUnidades().size());
                cuenta.setConceptos(String.join(", ", conceptosSeleccionados));

                if (i == 0) {
                    // Primera cuenta: fecha de hoy, monto = subtotal
                    cuenta.setFechaPago(fechaBase);
                    cuenta.setCantidadCobrar(cotizacion.getSubtotal());
                    logger.info("Cuenta {} - Fecha: {}, Monto: {} (subtotal)", i+1, fechaBase, cotizacion.getSubtotal());
                } else {
                    // Siguientes cuentas: 365 días más, monto = total unidades seleccionadas
                    cuenta.setFechaPago(fechaBase.plusYears(i));
                    cuenta.setCantidadCobrar(totalUnidadesSeleccionadas);
                    logger.info("Cuenta {} - Fecha: {}, Monto: {} (unidades seleccionadas)", i+1, fechaBase.plusDays(i * 365), totalUnidadesSeleccionadas);
                }

                cuenta.setFolio(generateFolio(cliente.getNombre(), i + 1));
                cuentas.add(cuenta);
            }

        } else if (esquema == EsquemaCobroEnum.MENSUAL) {
            logger.info("Monto por cuenta mensual: {} (cada cuenta tendrá el monto total)", totalUnidadesSeleccionadas);

            for (int i = 0; i < numeroPagos; i++) {
                CuentaPorCobrar cuenta = new CuentaPorCobrar();
                cuenta.setCliente(cliente);
                cuenta.setCotizacion(cotizacion);
                cuenta.setEstatus(EstatusPagoEnum.PENDIENTE);
                cuenta.setEsquema(esquema);
                cuenta.setNoEquipos(cotizacion.getUnidades().size());
                cuenta.setConceptos(String.join(", ", conceptosSeleccionados));

                if (i == 0) {
                    cuenta.setFechaPago(fechaBase);
                } else {
                    cuenta.setFechaPago(fechaBase.plusMonths(i));
                }

                cuenta.setCantidadCobrar(totalUnidadesSeleccionadas);
                cuenta.setFolio(generateFolio(cliente.getNombre(), i + 1));
                cuentas.add(cuenta);
            }

        } else if (esquema == EsquemaCobroEnum.DISTRIBUIDOR || esquema == EsquemaCobroEnum.VITALICIA) {
            // Para DISTRIBUIDOR y VITALICIA: solo una cuenta
            CuentaPorCobrar cuenta = new CuentaPorCobrar();
            cuenta.setCliente(cliente);
            cuenta.setCotizacion(cotizacion);
            cuenta.setEstatus(EstatusPagoEnum.PENDIENTE);
            cuenta.setEsquema(esquema);
            cuenta.setNoEquipos(cotizacion.getUnidades().size());
            cuenta.setConceptos(String.join(", ", conceptosSeleccionados));
            cuenta.setFechaPago(fechaBase);
            cuenta.setCantidadCobrar(totalUnidadesSeleccionadas);
            cuenta.setFolio(generateFolio(cliente.getNombre(), 1));
            cuentas.add(cuenta);
        }

        List<CuentaPorCobrar> savedCuentas = cuentaPorCobrarRepository.saveAll(cuentas);
        return savedCuentas.stream().map(this::convertToDTOWithFolio).collect(Collectors.toList());
    }

    private CuentaPorCobrarDTO convertToDTOWithFolio(CuentaPorCobrar cuenta) {
        CuentaPorCobrarDTO dto = new CuentaPorCobrarDTO();
        dto.setId(cuenta.getId());
        dto.setFolio(cuenta.getFolio());
        dto.setFechaPago(cuenta.getFechaPago());
        dto.setClienteNombre(cuenta.getCliente().getNombre());
        dto.setClienteId(cuenta.getCliente().getId());
        dto.setEstatus(cuenta.getEstatus());
        dto.setEsquema(cuenta.getEsquema());
        dto.setNoEquipos(cuenta.getNoEquipos());
        dto.setCantidadCobrar(cuenta.getCantidadCobrar());
        dto.setConceptos(cuenta.getConceptos() != null ? List.of(cuenta.getConceptos().split(", ")) : List.of());
        dto.setComprobantePagoUrl(cuenta.getComprobantePagoUrl());
        dto.setFechaRealPago(cuenta.getFechaRealPago());
        return dto;
    }

    private String generateFolio(String clienteNombre, int paymentNumber) {
        return String.format("%s-%02d", clienteNombre.toUpperCase(), paymentNumber);
    }

    private CuentaPorCobrar convertToEntity(CuentaPorCobrarDTO dto, int numeroPago) {
        CuentaPorCobrar cuenta = new CuentaPorCobrar();
        String folioTemporal = String.format("TEMP-%s-%d-%d",
                dto.getClienteNombre().toUpperCase(),
                numeroPago,
                System.currentTimeMillis() % 1000);
        cuenta.setFolio(folioTemporal);
        cuenta.setFechaPago(dto.getFechaPago());
        cuenta.setCliente(empresaRepository.findByNombreContainingIgnoreCase(dto.getClienteNombre())
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado")));
        cuenta.setEstatus(dto.getEstatus());
        cuenta.setEsquema(dto.getEsquema());
        cuenta.setNoEquipos(dto.getNoEquipos());
        cuenta.setConceptos(dto.getConceptos() != null ? String.join(", ", dto.getConceptos()) : "");
        return cuenta;
    }

    @Transactional
    public CuentaPorCobrarDTO actualizarCuentaPorCobrar(Integer id, CuentaPorCobrarDTO dto) {
        logger.info("Actualizando cuenta por cobrar con ID: {}", id);

        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta por cobrar no encontrada con id: " + id));

        // Verificar que no esté pagada
        if (cuenta.getEstatus() == EstatusPagoEnum.PAGADO) {
            throw new IllegalStateException("No se puede editar una cuenta que ya está pagada");
        }

        // Actualizar campos editables
        if (dto.getFechaPago() != null) {
            cuenta.setFechaPago(dto.getFechaPago());
        }

        if (dto.getCantidadCobrar() != null) {
            cuenta.setCantidadCobrar(dto.getCantidadCobrar());
        }

        if (dto.getConceptos() != null && !dto.getConceptos().isEmpty()) {
            cuenta.setConceptos(String.join(", ", dto.getConceptos()));
        }

        CuentaPorCobrar savedCuenta = cuentaPorCobrarRepository.save(cuenta);
        return convertToDTO(savedCuenta);
    }

    @Transactional
    public void eliminarCuentaPorCobrar(Integer id) {
        logger.info("Eliminando cuenta por cobrar con ID: {}", id);
        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta por cobrar no encontrada con id: " + id));
        if (cuenta.getComprobantePagoUrl() != null || !cuenta.getSolicitudesFacturasNotas().isEmpty()) {
            throw new ResourceNotFoundException("No se puede eliminar la cuenta por cobrar porque está vinculada a una solicitud de factura/nota o ya tiene un comprobante de pago.");
        }
        cuentaPorCobrarRepository.delete(cuenta);
    }

    @Transactional
    public CuentaPorCobrarDTO marcarComoPagada(Integer id, LocalDate fechaPago, BigDecimal montoPago, MultipartFile comprobante) throws Exception {
        logger.info("Marcando como pagada cuenta por cobrar con ID: {} con monto: {}", id, montoPago);

        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta por cobrar no encontrada con id: " + id));

        if (cuenta.getEstatus() == EstatusPagoEnum.PAGADO) {
            throw new ResourceNotFoundException("La cuenta ya está marcada como pagada.");
        }

        if (cuenta.getSolicitudesFacturasNotas().isEmpty()) {
            throw new ResourceNotFoundException("La cuenta no está vinculada a una solicitud de factura.");
        }

        // Validar que el monto de pago sea válido
        BigDecimal saldoActual = cuenta.getSaldoPendiente() != null ? cuenta.getSaldoPendiente() : cuenta.getCantidadCobrar();
        if (montoPago.compareTo(BigDecimal.ZERO) <= 0 || montoPago.compareTo(saldoActual) > 0) {
            throw new IllegalArgumentException("El monto de pago debe ser mayor a 0 y menor o igual al saldo pendiente");
        }

        if (comprobante.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("El archivo no debe exceder 10MB");
        }

        // Actualizar montos
        BigDecimal montoAcumulado = cuenta.getMontoPagado().add(montoPago);
        BigDecimal nuevoSaldo = cuenta.getCantidadCobrar().subtract(montoAcumulado);

        cuenta.setMontoPagado(montoAcumulado);
        cuenta.setSaldoPendiente(nuevoSaldo);

        // Determinar estatus basado en el saldo
        if (nuevoSaldo.compareTo(BigDecimal.ZERO) == 0) {
            cuenta.setEstatus(EstatusPagoEnum.PAGADO);
            cuenta.setFechaRealPago(fechaPago);
        } else {
            cuenta.setEstatus(EstatusPagoEnum.EN_PROCESO);
        }

        cuenta.setComprobantePagoUrl("UPLOADING");
        CuentaPorCobrar savedCuenta = cuentaPorCobrarRepository.save(cuenta);

        try {
            Cloudinary cloudinary = cloudinaryConfig.cloudinary();
            Map<String, Object> uploadOptions = Map.of(
                    "resource_type", "raw",
                    "timeout", 30,
                    "chunk_size", 6000000,
                    "use_filename", true,
                    "unique_filename", true
            );

            Map uploadResult = cloudinary.uploader().upload(comprobante.getBytes(), uploadOptions);
            String comprobanteUrl = uploadResult.get("url").toString();

            cuenta.setComprobantePagoUrl(comprobanteUrl);
            savedCuenta = cuentaPorCobrarRepository.save(cuenta);

        } catch (Exception e) {
            logger.error("Error al subir comprobante para cuenta {}: {}", id, e.getMessage());
            cuenta.setComprobantePagoUrl("ERROR_UPLOAD");
            savedCuenta = cuentaPorCobrarRepository.save(cuenta);
        }

        CategoriaTransacciones categoriaVentas = categoriaTransaccionesRepository.findByDescripcion("Ventas")
                .orElseThrow(() -> new ResourceNotFoundException("Categoría 'Ventas' no encontrada"));

        // Buscar la cuenta específicamente en la categoría de ventas
        CuentasTransacciones cuentaTransaccion = cuentasTransaccionesRepository
                .findByNombreAndCategoria(cuenta.getCliente().getNombre(), categoriaVentas);

        // Si no existe, crear una nueva cuenta en la categoría de ventas
        if (cuentaTransaccion == null) {
            logger.info("Creando nueva cuenta de transacciones para cliente: {} en categoría Ventas", cuenta.getCliente().getNombre());
            cuentaTransaccion = new CuentasTransacciones();
            cuentaTransaccion.setNombre(cuenta.getCliente().getNombre());
            cuentaTransaccion.setCategoria(categoriaVentas);
            cuentaTransaccion = cuentasTransaccionesRepository.save(cuentaTransaccion);
        }

        // Crear la transacción
        Transaccion transaccion = new Transaccion();
        transaccion.setFecha(LocalDate.now());
        transaccion.setTipo(TipoTransaccionEnum.INGRESO);
        transaccion.setCategoria(categoriaVentas);
        transaccion.setCuenta(cuentaTransaccion);
        transaccion.setMonto(cuenta.getCantidadCobrar());
        transaccion.setEsquema(EsquemaTransaccionEnum.UNICA);
        transaccion.setFechaPago(fechaPago);
        transaccion.setFormaPago(cuenta.getSolicitudesFacturasNotas().get(0).getFormaPago());
        transaccion.setNotas("Transacción generada automáticamente desde Cuentas por Cobrar");

        transaccionService.agregarTransaccion(transaccion);

        return convertToDTO(savedCuenta);
    }

    @Transactional(readOnly = true)
    public List<CuentaPorCobrarDTO> listarCuentasPorCobrar() {
        logger.info("Listando todas las cuentas por cobrar");
        return cuentaPorCobrarRepository.findAll().stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private CuentaPorCobrarDTO convertToDTO(CuentaPorCobrar cuenta) {
        CuentaPorCobrarDTO dto = new CuentaPorCobrarDTO();
        dto.setId(cuenta.getId());
        dto.setFolio(cuenta.getFolio());
        dto.setFechaPago(cuenta.getFechaPago());
        dto.setClienteNombre(cuenta.getCliente().getNombre());
        dto.setClienteId(cuenta.getCliente().getId());
        dto.setEstatus(cuenta.getEstatus());
        dto.setEsquema(cuenta.getEsquema());
        dto.setNoEquipos(cuenta.getNoEquipos());
        dto.setCantidadCobrar(cuenta.getCantidadCobrar());
        dto.setConceptos(cuenta.getConceptos() != null ? List.of(cuenta.getConceptos().split(", ")) : List.of());
        dto.setComprobantePagoUrl(cuenta.getComprobantePagoUrl());
        dto.setFechaRealPago(cuenta.getFechaRealPago());
        dto.setMontoPagado(cuenta.getMontoPagado());
        dto.setSaldoPendiente(cuenta.getSaldoPendiente());
        dto.setCotizacionId(cuenta.getCotizacion() != null ? cuenta.getCotizacion().getId() : null);
        return dto;
    }

    private CuentaPorCobrar convertToEntity(CuentaPorCobrarDTO dto) {
        CuentaPorCobrar cuenta = new CuentaPorCobrar();
        cuenta.setFechaPago(dto.getFechaPago());
        cuenta.setCliente(empresaRepository.findByNombreContainingIgnoreCase(dto.getClienteNombre())
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado")));
        cuenta.setEstatus(dto.getEstatus());
        cuenta.setEsquema(dto.getEsquema());
        cuenta.setNoEquipos(dto.getNoEquipos());
        cuenta.setCantidadCobrar(dto.getCantidadCobrar());
        cuenta.setConceptos(dto.getConceptos() != null ? String.join(", ", dto.getConceptos()) : "");
        return cuenta;
    }

    @Transactional(readOnly = true)
    public boolean isVinculada(Integer id) {
        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta por cobrar no encontrada con id: " + id));
        return cuenta.getComprobantePagoUrl() != null || !cuenta.getSolicitudesFacturasNotas().isEmpty();
    }

    @Transactional(readOnly = true)
    public boolean existsByFolio(String folio) {
        return cuentaPorCobrarRepository.existsByFolio(folio);
    }

    @Transactional(readOnly = true)
    public byte[] obtenerComprobantePago(Integer id) throws Exception {
        logger.info("Obteniendo comprobante de pago para cuenta por cobrar con ID: {}", id);
        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta por cobrar no encontrada con id: " + id));

        if (cuenta.getComprobantePagoUrl() == null || cuenta.getComprobantePagoUrl().isEmpty()) {
            throw new ResourceNotFoundException("No se encontró el comprobante de pago asociado a esta cuenta.");
        }

        // Hacer una solicitud a Cloudinary para obtener el archivo
        java.net.URL url = new java.net.URL(cuenta.getComprobantePagoUrl());
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();

        if (connection.getResponseCode() != 200) {
            throw new Exception("Error al acceder al archivo en Cloudinary: " + connection.getResponseMessage());
        }

        byte[] fileContent = connection.getInputStream().readAllBytes();
        connection.disconnect();

        return fileContent;
    }
}