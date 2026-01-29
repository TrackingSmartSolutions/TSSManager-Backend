package com.tss.tssmanager_backend.service;

import com.cloudinary.Cloudinary;
import com.tss.tssmanager_backend.config.CloudinaryConfig;
import com.tss.tssmanager_backend.dto.ConceptoCuentaDTO;
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
        logger.info("Creando cuentas por cobrar desde cotización ID: {} con esquema: {} y numeroPagos: {}", cotizacionId, esquema, numeroPagos);

        Cotizacion cotizacion = cotizacionRepository.findById(cotizacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));

        Empresa cliente = cotizacion.getCliente();
        List<CuentaPorCobrar> cuentas = new ArrayList<>();
        LocalDate fechaBase = fechaInicial;

        // Obtener el total de equipos reales de la cotización original
        int totalEquiposReal = cotizacion.getUnidades().stream()
                .filter(u -> "Equipos".equalsIgnoreCase(u.getUnidad()))
                .mapToInt(UnidadCotizacion::getCantidad)
                .sum();

        // Determinar cuántas cuentas crear
        int totalIteraciones = 1;
        if (esquema == EsquemaCobroEnum.ANUAL) totalIteraciones = numeroPagos + 1;
        else if (esquema == EsquemaCobroEnum.MENSUAL) totalIteraciones = numeroPagos;

        for (int i = 0; i < totalIteraciones; i++) {
            CuentaPorCobrar cuenta = new CuentaPorCobrar();
            cuenta.setCliente(cliente);
            cuenta.setCotizacion(cotizacion);
            cuenta.setEstatus(EstatusPagoEnum.PENDIENTE);
            cuenta.setEsquema(esquema);
            cuenta.setMontoPagado(BigDecimal.ZERO);

            // Manejo de fechas según esquema
            if (i == 0) {
                cuenta.setFechaPago(fechaBase);
                cuenta.setNoEquipos(totalEquiposReal);
            } else {
                cuenta.setFechaPago(esquema == EsquemaCobroEnum.ANUAL ? fechaBase.plusYears(i) : fechaBase.plusMonths(i));
                cuenta.setNoEquipos(0);
            }

            List<ConceptoCuenta> listaConceptos = new ArrayList<>();

            if ((esquema == EsquemaCobroEnum.ANUAL && i == 0)) {
                listaConceptos = cotizacion.getUnidades().stream().map(u -> {
                    ConceptoCuenta c = new ConceptoCuenta();
                    c.setCuentaPorCobrar(cuenta);
                    c.setCantidad(u.getCantidad());
                    c.setUnidad(u.getUnidad());
                    c.setConcepto(u.getConcepto());
                    c.setPrecioUnitario(u.getPrecioUnitario());
                    c.setDescuento(u.getDescuento());
                    c.setImporteTotal(u.getImporteTotal());
                    return c;
                }).collect(Collectors.toList());
                cuenta.setCantidadCobrar(cotizacion.getSubtotal());
            } else {
                // Caso parcialidades o esquemas filtrados: Solo conceptos seleccionados
                listaConceptos = cotizacion.getUnidades().stream()
                        .filter(u -> conceptosSeleccionados.stream().anyMatch(sel -> sel.trim().equalsIgnoreCase(u.getConcepto().trim())))
                        .map(u -> {
                            ConceptoCuenta c = new ConceptoCuenta();
                            c.setCuentaPorCobrar(cuenta);
                            c.setCantidad(u.getCantidad());
                            c.setUnidad(u.getUnidad());
                            c.setConcepto(u.getConcepto());
                            c.setPrecioUnitario(u.getPrecioUnitario());
                            c.setDescuento(u.getDescuento());
                            c.setImporteTotal(u.getImporteTotal());
                            return c;
                        }).collect(Collectors.toList());

                BigDecimal totalCuenta = listaConceptos.stream()
                        .map(ConceptoCuenta::getImporteTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                cuenta.setCantidadCobrar(totalCuenta);
            }

            cuenta.setConceptos(listaConceptos);
            cuenta.setSaldoPendiente(cuenta.getCantidadCobrar());
            cuenta.setFolio(generateFolio(cliente.getNombre(), i + 1));
            cuentas.add(cuenta);
        }

        List<CuentaPorCobrar> savedCuentas = cuentaPorCobrarRepository.saveAll(cuentas);
        cotizacion.setEstatus(EstatusCotizacionEnum.ACEPTADA);
        cotizacionRepository.save(cotizacion);

        return savedCuentas.stream().map(this::convertToDTO).collect(Collectors.toList());
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
        dto.setComprobantePagoUrl(cuenta.getComprobantePagoUrl());
        dto.setFechaRealPago(cuenta.getFechaRealPago());
        dto.setMontoPagado(cuenta.getMontoPagado());
        dto.setSaldoPendiente(cuenta.getSaldoPendiente());
        dto.setCotizacionId(cuenta.getCotizacion() != null ? cuenta.getCotizacion().getId() : null);

        // Mapeo de la lista de objetos detallados
        if (cuenta.getConceptos() != null) {
            dto.setConceptos(cuenta.getConceptos().stream().map(c -> {
                ConceptoCuentaDTO cDto = new ConceptoCuentaDTO();
                cDto.setId(c.getId());
                cDto.setCantidad(c.getCantidad());
                cDto.setUnidad(c.getUnidad());
                cDto.setConcepto(c.getConcepto());
                cDto.setPrecioUnitario(c.getPrecioUnitario());
                cDto.setDescuento(c.getDescuento());
                cDto.setImporteTotal(c.getImporteTotal());
                return cDto;
            }).collect(Collectors.toList()));
        }

        dto.setNumeroEquipos(calcularEquiposEnCuentaOptimized(cuenta));
        return dto;
    }

    private String generateFolio(String clienteNombre, int paymentNumber) {
        return String.format("%s-%02d", clienteNombre.toUpperCase(), paymentNumber);
    }

    private CuentaPorCobrar convertToEntity(CuentaPorCobrarDTO dto, int numeroPago) {
        CuentaPorCobrar cuenta = new CuentaPorCobrar();

        // Generar folio temporal único
        String folioTemporal = String.format("TEMP-%s-%d-%d",
                dto.getClienteNombre().toUpperCase(),
                numeroPago,
                System.currentTimeMillis() % 1000);

        cuenta.setFolio(folioTemporal);
        cuenta.setFechaPago(dto.getFechaPago());

        cuenta.setCliente(empresaRepository.findByNombreContainingIgnoreCase(dto.getClienteNombre())
                .stream()
                .filter(e -> e.getNombre().equalsIgnoreCase(dto.getClienteNombre()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado")));

        cuenta.setEstatus(dto.getEstatus());
        cuenta.setEsquema(dto.getEsquema());
        cuenta.setNoEquipos(dto.getNoEquipos());
        cuenta.setCantidadCobrar(dto.getCantidadCobrar());
        cuenta.setMontoPagado(BigDecimal.ZERO);
        cuenta.setSaldoPendiente(dto.getCantidadCobrar());

        // Convertir la lista de DTOs a Entidades relacionadas
        if (dto.getConceptos() != null) {
            List<ConceptoCuenta> conceptos = dto.getConceptos().stream().map(cDto -> {
                ConceptoCuenta c = new ConceptoCuenta();
                c.setCuentaPorCobrar(cuenta); // Vinculación bidireccional
                c.setCantidad(cDto.getCantidad());
                c.setUnidad(cDto.getUnidad());
                c.setConcepto(cDto.getConcepto());
                c.setPrecioUnitario(cDto.getPrecioUnitario());
                c.setImporteTotal(cDto.getImporteTotal());
                return c;
            }).collect(Collectors.toList());
            cuenta.setConceptos(conceptos);
        }

        return cuenta;
    }

    @Transactional
    public CuentaPorCobrarDTO actualizarCuentaPorCobrar(Integer id, CuentaPorCobrarDTO dto) {
        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No encontrada"));

        if (cuenta.getEstatus() == EstatusPagoEnum.PAGADO) {
            throw new IllegalStateException("No se puede editar una cuenta pagada");
        }

        cuenta.setFechaPago(dto.getFechaPago());

        if (cuenta.getConceptos() != null) {
            cuenta.getConceptos().clear();
        } else {
            cuenta.setConceptos(new ArrayList<>());
        }

        List<ConceptoCuenta> nuevosConceptos = dto.getConceptos().stream().map(c -> {
            ConceptoCuenta concepto = new ConceptoCuenta();
            concepto.setCuentaPorCobrar(cuenta);
            concepto.setCantidad(c.getCantidad());
            concepto.setUnidad(c.getUnidad());
            concepto.setConcepto(c.getConcepto());
            concepto.setPrecioUnitario(c.getPrecioUnitario());
            concepto.setDescuento(c.getDescuento() != null ? c.getDescuento() : BigDecimal.ZERO);
            concepto.setImporteTotal(c.getImporteTotal());
            return concepto;
        }).collect(Collectors.toList());

        cuenta.getConceptos().addAll(nuevosConceptos);

        BigDecimal nuevoTotal = nuevosConceptos.stream()
                .map(ConceptoCuenta::getImporteTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        cuenta.setCantidadCobrar(nuevoTotal);
        CuentaPorCobrar saved = cuentaPorCobrarRepository.save(cuenta);
        actualizarSolicitudesVinculadas(saved);

        return convertToDTO(saved);
    }

    private void actualizarSolicitudesVinculadas(CuentaPorCobrar cuenta) {
        if (cuenta.getSolicitudesFacturasNotas() != null && !cuenta.getSolicitudesFacturasNotas().isEmpty()) {

            String nuevosConceptosTexto = cuenta.getConceptos().stream()
                    .map(ConceptoCuenta::getConcepto)
                    .collect(Collectors.joining(", "));
            for (SolicitudFacturaNota solicitud : cuenta.getSolicitudesFacturasNotas()) {
                // Actualizar el subtotal con el nuevo monto de la cuenta
                solicitud.setSubtotal(cuenta.getCantidadCobrar());
                solicitud.setConceptosSeleccionados(nuevosConceptosTexto);
                // Recalcular IVA (16%)
                BigDecimal iva = cuenta.getCantidadCobrar().multiply(new BigDecimal("0.16"));
                solicitud.setIva(iva);

                // Calcular retenciones si aplica
                BigDecimal isrEstatal = BigDecimal.ZERO;
                BigDecimal isrFederal = BigDecimal.ZERO;

                if (solicitud.getTipo() == TipoDocumentoSolicitudEnum.SOLICITUD_DE_FACTURA &&
                        (solicitud.getCliente().getRegimenFiscal().equals("601") ||
                                solicitud.getCliente().getRegimenFiscal().equals("627"))) {

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
        transaccion.setMonto(montoPago);
        transaccion.setEsquema(EsquemaTransaccionEnum.UNICA);
        transaccion.setFechaPago(fechaPago);
        transaccion.setFormaPago(cuenta.getSolicitudesFacturasNotas().get(0).getFormaPago());
        transaccion.setNotas("Transacción generada automáticamente desde Cuentas por Cobrar");
        transaccion.setCuentaPorCobrarId(cuenta.getId());

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
        dto.setComprobantePagoUrl(cuenta.getComprobantePagoUrl());
        dto.setFechaRealPago(cuenta.getFechaRealPago());
        dto.setMontoPagado(cuenta.getMontoPagado());
        dto.setSaldoPendiente(cuenta.getSaldoPendiente());
        dto.setCotizacionId(cuenta.getCotizacion() != null ? cuenta.getCotizacion().getId() : null);

        // Mapeo de conceptos detallados
        if (cuenta.getConceptos() != null) {
            dto.setConceptos(cuenta.getConceptos().stream().map(c -> {
                ConceptoCuentaDTO cDto = new ConceptoCuentaDTO();
                cDto.setId(c.getId());
                cDto.setCantidad(c.getCantidad());
                cDto.setUnidad(c.getUnidad());
                cDto.setConcepto(c.getConcepto());
                cDto.setPrecioUnitario(c.getPrecioUnitario());
                cDto.setDescuento(c.getDescuento());
                cDto.setImporteTotal(c.getImporteTotal());
                return cDto;
            }).collect(Collectors.toList()));
        }

        dto.setNumeroEquipos(calcularEquiposEnCuentaOptimized(cuenta));
        return dto;
    }

    private int calcularEquiposEnCuentaOptimized(CuentaPorCobrar cuenta) {
        if (cuenta.getConceptos() == null || cuenta.getConceptos().isEmpty()) {
            return 0;
        }
        return cuenta.getConceptos().stream()
                .filter(c -> "Equipos".equalsIgnoreCase(c.getUnidad()))
                .mapToInt(ConceptoCuenta::getCantidad)
                .sum();
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
        dto.setComprobantePagoUrl(cuenta.getComprobantePagoUrl());
        dto.setFechaRealPago(cuenta.getFechaRealPago());
        dto.setMontoPagado(cuenta.getMontoPagado());
        dto.setSaldoPendiente(cuenta.getSaldoPendiente());
        dto.setCotizacionId(cuenta.getCotizacion() != null ? cuenta.getCotizacion().getId() : null);
        int equiposEnEstaCuenta = calcularEquiposEnCuenta(cuenta);
        int equipos = 0;
        if (cuenta.getConceptos() != null) {
            equipos = cuenta.getConceptos().stream()
                    .filter(c -> "Equipos".equalsIgnoreCase(c.getUnidad()))
                    .mapToInt(ConceptoCuenta::getCantidad)
                    .sum();
        }
        dto.setNumeroEquipos(equipos);
        if (cuenta.getConceptos() != null) {
            dto.setConceptos(cuenta.getConceptos().stream().map(c -> {
                ConceptoCuentaDTO cDto = new ConceptoCuentaDTO();
                cDto.setId(c.getId());
                cDto.setCantidad(c.getCantidad());
                cDto.setUnidad(c.getUnidad());
                cDto.setConcepto(c.getConcepto());
                cDto.setPrecioUnitario(c.getPrecioUnitario());
                cDto.setDescuento(c.getDescuento());
                cDto.setImporteTotal(c.getImporteTotal());
                return cDto;
            }).collect(Collectors.toList()));
        }
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

        if (dto.getConceptos() != null) {
            List<ConceptoCuenta> conceptos = dto.getConceptos().stream().map(cDto -> {
                ConceptoCuenta c = new ConceptoCuenta();
                c.setCuentaPorCobrar(cuenta);
                c.setCantidad(cDto.getCantidad());
                c.setUnidad(cDto.getUnidad());
                c.setConcepto(cDto.getConcepto());
                c.setPrecioUnitario(cDto.getPrecioUnitario());
                c.setImporteTotal(cDto.getImporteTotal());
                return c;
            }).collect(Collectors.toList());
            cuenta.setConceptos(conceptos);
        }

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
        if (cuenta.getConceptos() == null) return 0;
        return cuenta.getConceptos().stream()
                .filter(c -> "Equipos".equalsIgnoreCase(c.getUnidad()))
                .mapToInt(ConceptoCuenta::getCantidad)
                .sum();
    }

    @Transactional
    public void revertirPagoDesdeTransaccion(Transaccion transaccion) {
        if (transaccion.getCuentaPorCobrarId() == null) return;

        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findById(transaccion.getCuentaPorCobrarId())
                .orElse(null);

        if (cuenta != null) {
            BigDecimal montoEliminado = transaccion.getMonto();

            BigDecimal pagadoActual = cuenta.getMontoPagado() != null ? cuenta.getMontoPagado() : BigDecimal.ZERO;

            BigDecimal nuevoPagado = pagadoActual.subtract(montoEliminado);

            if (nuevoPagado.compareTo(BigDecimal.ZERO) < 0) {
                nuevoPagado = BigDecimal.ZERO;
            }
            cuenta.setMontoPagado(nuevoPagado);

            BigDecimal nuevoSaldo = cuenta.getCantidadCobrar().subtract(nuevoPagado);
            cuenta.setSaldoPendiente(nuevoSaldo);

            if (cuenta.getSaldoPendiente().compareTo(BigDecimal.ZERO) > 0) {
                LocalDate hoy = LocalDate.now();

                if (nuevoPagado.compareTo(BigDecimal.ZERO) > 0) {
                    cuenta.setEstatus(EstatusPagoEnum.EN_PROCESO);
                } else {
                    if (cuenta.getFechaPago().isBefore(hoy)) {
                        cuenta.setEstatus(EstatusPagoEnum.VENCIDA);
                    } else {
                        cuenta.setEstatus(EstatusPagoEnum.PENDIENTE);
                    }

                    cuenta.setFechaRealPago(null);
                    cuenta.setComprobantePagoUrl(null);
                }
            }

            cuentaPorCobrarRepository.save(cuenta);
        }
    }
}