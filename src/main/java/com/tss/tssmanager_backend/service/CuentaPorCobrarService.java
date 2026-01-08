package com.tss.tssmanager_backend.service;

import com.cloudinary.Cloudinary;
import com.tss.tssmanager_backend.config.CloudinaryConfig;
import com.tss.tssmanager_backend.dto.CuentaPorCobrarDTO;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.*;
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
import java.util.*;
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

    @Autowired
    private CotizacionService cotizacionService;

    @Transactional
    public List<CuentaPorCobrarDTO> crearCuentasPorCobrarFromCotizacion(Integer cotizacionId, EsquemaCobroEnum esquema, List<String> conceptosSeleccionados, Integer numeroPagos, LocalDate fechaInicial) {
        logger.info("Creando cuentas por cobrar desde cotización ID: {} with esquema: {} and numeroPagos: {}", cotizacionId, esquema, numeroPagos);

        Cotizacion cotizacion = cotizacionRepository.findById(cotizacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));

        Empresa cliente = cotizacion.getCliente();
        List<CuentaPorCobrar> cuentas = new ArrayList<>();

        logger.info("Conceptos seleccionados: {}", conceptosSeleccionados);
        logger.info("Conceptos disponibles en cotización:");
        cotizacion.getUnidades().forEach(u ->
                logger.info("  - Concepto: '{}', Importe: {}", u.getConcepto(), u.getImporteTotal())
        );

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
                cuenta.setNoEquipos(cotizacion.getUnidades().stream()
                        .filter(u -> "Equipos".equals(u.getUnidad()))
                        .mapToInt(UnidadCotizacion::getCantidad)
                        .sum());

                if (i == 0) {
                    // Primera cuenta: fecha de hoy, monto = subtotal, TODOS los conceptos
                    cuenta.setFechaPago(fechaBase);
                    cuenta.setCantidadCobrar(cotizacion.getSubtotal());
                    // Para la primera cuenta, incluir TODOS los conceptos de la cotización
                    String todosLosConceptos = cotizacion.getUnidades().stream()
                            .map(UnidadCotizacion::getConcepto)
                            .distinct()
                            .collect(Collectors.joining(", "));
                    cuenta.setConceptos(todosLosConceptos);
                    logger.info("Cuenta {} - Fecha: {}, Monto: {} (subtotal completo con todos los conceptos)", i+1, fechaBase, cotizacion.getSubtotal());
                } else {
                    // Siguientes cuentas: 365 días más, monto = total unidades seleccionadas, solo conceptos seleccionados
                    cuenta.setFechaPago(fechaBase.plusYears(i));
                    cuenta.setCantidadCobrar(totalUnidadesSeleccionadas);
                    cuenta.setConceptos(String.join(", ", conceptosSeleccionados));
                    logger.info("Cuenta {} - Fecha: {}, Monto: {} (unidades seleccionadas)", i+1, fechaBase.plusYears(i), totalUnidadesSeleccionadas);
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
        cotizacion.setEstatus(EstatusCotizacionEnum.ACEPTADA);
        cotizacionRepository.save(cotizacion);
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

        if (dto.getFechaPago() != null) {
            cuenta.setFechaPago(dto.getFechaPago());

            // Recalcular el estatus basado en la nueva fecha
            LocalDate hoy = LocalDate.now();
            if (cuenta.getEstatus() != EstatusPagoEnum.PAGADO && cuenta.getEstatus() != EstatusPagoEnum.EN_PROCESO) {
                if (dto.getFechaPago().isBefore(hoy)) {
                    cuenta.setEstatus(EstatusPagoEnum.VENCIDA);
                } else {
                    cuenta.setEstatus(EstatusPagoEnum.PENDIENTE);
                }
            }
        }

        boolean montoModificado = false;
        if (dto.getCantidadCobrar() != null && !dto.getCantidadCobrar().equals(cuenta.getCantidadCobrar())) {
            cuenta.setCantidadCobrar(dto.getCantidadCobrar());
            montoModificado = true;
        }

        if (dto.getConceptos() != null && !dto.getConceptos().isEmpty()) {
            cuenta.setConceptos(String.join(", ", dto.getConceptos()));
        }

        CuentaPorCobrar savedCuenta = cuentaPorCobrarRepository.save(cuenta);

        if (montoModificado) {
            actualizarSolicitudesVinculadas(savedCuenta);
        }

        return convertToDTO(savedCuenta);
    }

    private void actualizarSolicitudesVinculadas(CuentaPorCobrar cuenta) {
        if (cuenta.getSolicitudesFacturasNotas() != null && !cuenta.getSolicitudesFacturasNotas().isEmpty()) {
            for (SolicitudFacturaNota solicitud : cuenta.getSolicitudesFacturasNotas()) {
                // Actualizar el subtotal con el nuevo monto de la cuenta
                solicitud.setSubtotal(cuenta.getCantidadCobrar());

                // Recalcular IVA (16%)
                BigDecimal iva = cuenta.getCantidadCobrar().multiply(new BigDecimal("0.16"));
                solicitud.setIva(iva);

                // Calcular retenciones si aplica
                BigDecimal isrEstatal = BigDecimal.ZERO;
                BigDecimal isrFederal = BigDecimal.ZERO;

                if (solicitud.getTipo() == TipoDocumentoSolicitudEnum.SOLICITUD_DE_FACTURA &&
                        solicitud.getCliente().getRegimenFiscal().equals("601")) {

                    String domicilioFiscal = solicitud.getCliente().getDomicilioFiscal().toLowerCase();
                    boolean hasGuanajuato = domicilioFiscal.contains("gto") || domicilioFiscal.contains("guanajuato");
                    boolean cpMatch = domicilioFiscal.matches(".*\\b(36|37|38)\\d{4}\\b.*");

                    if (cpMatch || hasGuanajuato) {
                        isrEstatal = cuenta.getCantidadCobrar().multiply(new BigDecimal("0.02"));
                        isrFederal = cuenta.getCantidadCobrar().multiply(new BigDecimal("0.0125"));
                    } else if (!cpMatch && !hasGuanajuato) {
                        isrFederal = cuenta.getCantidadCobrar().multiply(new BigDecimal("0.0125"));
                    }
                }

                BigDecimal total = cuenta.getCantidadCobrar().add(iva).subtract(isrEstatal).subtract(isrFederal);
                solicitud.setTotal(total);

                solicitud.setImporteLetra(cotizacionService.convertToLetter(total));

                solicitud.setFechaModificacion(java.time.LocalDateTime.now());

                logger.info("Actualizando solicitud ID {} - Nuevo subtotal: {}, Total: {}",
                        solicitud.getId(), cuenta.getCantidadCobrar(), total);
            }
        }
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
    public CuentaPorCobrarDTO marcarComoPagada(Integer id, LocalDate fechaPago, BigDecimal montoPago,
                                               MultipartFile comprobante, Integer categoriaId) throws Exception {
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

        if (categoriaId == null) {
            throw new IllegalArgumentException("La categoría es obligatoria");
        }

        CategoriaTransacciones categoria = categoriaTransaccionesRepository.findById(categoriaId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + categoriaId));

        List<CuentasTransacciones> cuentasExistentes = cuentasTransaccionesRepository
                .findByNombreAndCategoria(cuenta.getCliente().getNombre(), categoria);

        CuentasTransacciones cuentaTransaccion;

        if (cuentasExistentes == null || cuentasExistentes.isEmpty()) {
            logger.info("Creando nueva cuenta de transacciones para cliente: {} en categoría: {}",
                    cuenta.getCliente().getNombre(), categoria.getDescripcion());
            cuentaTransaccion = new CuentasTransacciones();
            cuentaTransaccion.setNombre(cuenta.getCliente().getNombre());
            cuentaTransaccion.setCategoria(categoria);
            cuentaTransaccion = cuentasTransaccionesRepository.save(cuentaTransaccion);
        } else {
            // Usar la primera si hay múltiples (por si quedaron duplicados)
            cuentaTransaccion = cuentasExistentes.get(0);
            logger.info("Usando cuenta existente ID: {} para cliente: {} en categoría: {}",
                    cuentaTransaccion.getId(), cuenta.getCliente().getNombre(), categoria.getDescripcion());
        }

        List<String> categoriasExcluidas = Arrays.asList("Revisiones", "Renta Mensual", "Renta Anual");
        if (categoriasExcluidas.contains(categoria.getDescripcion())) {
            logger.info("Cuenta por cobrar {} marcada como pagada con categoría {}, no se contará en equipos vendidos",
                    cuenta.getId(), categoria.getDescripcion());
        }

        // Crear la transacción
        Transaccion transaccion = new Transaccion();
        transaccion.setFecha(LocalDate.now());
        transaccion.setTipo(TipoTransaccionEnum.INGRESO);
        transaccion.setCategoria(categoria);
        transaccion.setCuenta(cuentaTransaccion);
        transaccion.setMonto(cuenta.getCantidadCobrar());
        transaccion.setEsquema(EsquemaTransaccionEnum.UNICA);
        transaccion.setFechaPago(fechaPago);
        transaccion.setFormaPago(cuenta.getSolicitudesFacturasNotas().get(0).getFormaPago());
        transaccion.setNotas("Transacción generada automáticamente desde Cuentas por Cobrar");

        transaccionService.agregarTransaccion(transaccion);


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

        return convertToDTO(savedCuenta);
    }

    @Transactional(readOnly = true)
    // Aceptamos el estatus como parámetro (puede ser null para traer todas)
    public List<CuentaPorCobrarDTO> listarCuentasPorCobrar(EstatusPagoEnum estatus) {
        logger.info("Listando cuentas por cobrar con estatus: {}", estatus);

        List<CuentaPorCobrar> cuentas = cuentaPorCobrarRepository.findByEstatusWithRelations(estatus);

        List<Integer> idsVinculadas = cuentaPorCobrarRepository.findAllVinculatedIds();
        Set<Integer> vinculadasSet = new HashSet<>(idsVinculadas);

        return cuentas.stream()
                .map(cuenta -> convertToDTOOptimized(cuenta, vinculadasSet.contains(cuenta.getId())))
                .collect(Collectors.toList());
    }

    private CuentaPorCobrarDTO convertToDTOOptimized(CuentaPorCobrar cuenta, boolean isVinculada) {
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
        dto.setCotizacionId(cuenta.getCotizacion().getId());

        int equiposEnEstaCuenta = calcularEquiposEnCuentaOptimized(cuenta);
        dto.setNumeroEquipos(equiposEnEstaCuenta);

        return dto;
    }

    private int calcularEquiposEnCuentaOptimized(CuentaPorCobrar cuenta) {
        if (cuenta.getCotizacion() == null || cuenta.getConceptos() == null) {
            return 0;
        }

        List<String> conceptosCuenta = List.of(cuenta.getConceptos().split(", "));

        if (cuenta.getCotizacion().getUnidades() != null && !cuenta.getCotizacion().getUnidades().isEmpty()) {
            return cuenta.getCotizacion().getUnidades().stream()
                    .filter(unidad ->
                            "Equipos".equals(unidad.getUnidad()) &&
                                    conceptosCuenta.stream().anyMatch(concepto ->
                                            concepto.trim().equalsIgnoreCase(unidad.getConcepto().trim())
                                    )
                    )
                    .mapToInt(UnidadCotizacion::getCantidad)
                    .sum();
        }

        return 0;
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
        int equiposEnEstaCuenta = calcularEquiposEnCuenta(cuenta);
        dto.setNumeroEquipos(equiposEnEstaCuenta);
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

        // Validar si la URL indica un error de subida
        if ("ERROR_UPLOAD".equals(cuenta.getComprobantePagoUrl()) ||
                "UPLOADING".equals(cuenta.getComprobantePagoUrl())) {
            throw new ResourceNotFoundException("El comprobante de pago no se pudo subir correctamente o está en proceso de subida.");
        }

        // Validar que la URL sea válida
        if (!cuenta.getComprobantePagoUrl().startsWith("http://") &&
                !cuenta.getComprobantePagoUrl().startsWith("https://")) {
            throw new ResourceNotFoundException("La URL del comprobante de pago no es válida.");
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

    private int calcularEquiposEnCuenta(CuentaPorCobrar cuenta) {
        if (cuenta.getCotizacion() == null || cuenta.getConceptos() == null) {
            return 0;
        }
        List<String> conceptosCuenta = List.of(cuenta.getConceptos().split(", "));

        return cuenta.getCotizacion().getUnidades().stream()
                .filter(unidad ->
                        "Equipos".equals(unidad.getUnidad()) &&
                                conceptosCuenta.stream().anyMatch(concepto ->
                                        concepto.trim().equalsIgnoreCase(unidad.getConcepto().trim())
                                )
                )
                .mapToInt(UnidadCotizacion::getCantidad)
                .sum();
    }
}