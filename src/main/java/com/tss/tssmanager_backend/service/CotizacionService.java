package com.tss.tssmanager_backend.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import com.lowagie.text.Image;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import com.tss.tssmanager_backend.dto.CotizacionDTO;
import com.tss.tssmanager_backend.dto.UnidadCotizacionDTO;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.EstatusCotizacionEnum;
import com.tss.tssmanager_backend.exception.ResourceNotFoundException;
import com.tss.tssmanager_backend.repository.*;
import com.tss.tssmanager_backend.security.CustomUserDetails;
import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CotizacionService {

    private static final Logger logger = LoggerFactory.getLogger(CotizacionService.class);

    @Autowired
    private CotizacionRepository cotizacionRepository;

    @Autowired
    private UnidadCotizacionRepository unidadCotizacionRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private CuentaPorCobrarRepository cuentaPorCobrarRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TratoRepository tratoRepository;

    private byte[] membreteCache = null;
    private final Object membreteLock = new Object();

    private static final String[] unidades = {
            "", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
            "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve"
    };

    private static final String[] decenas = {
            "", "", "veinte", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"
    };

    private static final String[] centenas = {
            "", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
            "seiscientos", "setecientos", "ochocientos", "novecientos"
    };

    @Transactional(readOnly = true)
    public CotizacionDTO obtenerCotizacionPorId(Integer id) {
        logger.info("Buscando cotización completa con ID: {}", id);

        Cotizacion cotizacion = cotizacionRepository.findByIdWithUnidades(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada con id: " + id));

        return convertToDTO(cotizacion);
    }

    @Transactional
    public CotizacionDTO crearCotizacion(CotizacionDTO cotizacionDTO) {
        logger.info("Creando nueva cotización para cliente: {}", cotizacionDTO.getClienteNombre());

        Empresa cliente = empresaRepository.findByNombreContainingIgnoreCase(cotizacionDTO.getClienteNombre())
                .stream()
                .filter(e -> e.getNombre().equalsIgnoreCase(cotizacionDTO.getClienteNombre()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado: " + cotizacionDTO.getClienteNombre()));

        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setCliente(cliente);
        cotizacion.setFechaCreacion(Instant.now());
        cotizacion.setEstatus(EstatusCotizacionEnum.PENDIENTE);

        if (cotizacionDTO.getTratoId() != null) {
            cotizacion.setTratoId(cotizacionDTO.getTratoId());
        }

        cotizacion.setUsuarioCreadorId(getCurrentUserId());

        BigDecimal subtotal = cotizacionDTO.getUnidades().stream()
                .map(u -> {
                    BigDecimal descuento = u.getDescuento() != null ? u.getDescuento() : BigDecimal.ZERO;
                    BigDecimal descuentoPorcentaje = descuento.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
                    BigDecimal descuentoMonto = u.getPrecioUnitario().multiply(descuentoPorcentaje).multiply(new BigDecimal(u.getCantidad()));
                    return u.getPrecioUnitario().multiply(new BigDecimal(u.getCantidad())).subtract(descuentoMonto);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal iva = subtotal.multiply(new BigDecimal("0.16"));
        BigDecimal isrEstatal = BigDecimal.ZERO;
        BigDecimal isrFederal = BigDecimal.ZERO;
        BigDecimal total = subtotal.add(iva);

        cotizacion.setSubtotal(subtotal);
        cotizacion.setIva(iva);
        cotizacion.setIsrEstatal(isrEstatal);
        cotizacion.setIsrFederal(isrFederal);
        cotizacion.setTotal(total);
        cotizacion.setImporteLetra(convertToLetter(total));

        Cotizacion savedCotizacion = cotizacionRepository.save(cotizacion);

        List<UnidadCotizacion> unidades = cotizacionDTO.getUnidades().stream().map(u -> {
            UnidadCotizacion unidad = new UnidadCotizacion();
            unidad.setCotizacion(savedCotizacion);
            unidad.setCantidad(u.getCantidad());
            unidad.setUnidad(u.getUnidad());
            unidad.setConcepto(u.getConcepto());
            unidad.setPrecioUnitario(u.getPrecioUnitario());
            BigDecimal descuento = u.getDescuento() != null ? u.getDescuento() : BigDecimal.ZERO;
            BigDecimal descuentoPorcentaje = descuento.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            BigDecimal descuentoMonto = u.getPrecioUnitario().multiply(descuentoPorcentaje).multiply(new BigDecimal(u.getCantidad()));
            unidad.setDescuento(descuento);
            unidad.setImporteTotal(u.getPrecioUnitario().multiply(new BigDecimal(u.getCantidad())).subtract(descuentoMonto));
            return unidad;
        }).collect(Collectors.toList());

        unidadCotizacionRepository.saveAll(unidades);
        cotizacion.setUnidades(unidades);

        CotizacionDTO result = convertToDTO(savedCotizacion);
        result.setIsrEstatal(isrEstatal);
        result.setIsrFederal(isrFederal);
        result.setEmpresaData(empresaService.convertToEmpresaDTO(cliente));

        return result;
    }

    private Integer getCurrentUserId() {
        return ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
    }

    @Transactional
    public CotizacionDTO actualizarCotizacion(Integer id, CotizacionDTO cotizacionDTO) {
        logger.info("Actualizando cotización con ID: {}", id);
        Cotizacion cotizacion = cotizacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada con id: " + id));

        Empresa cliente = empresaRepository.findByNombreContainingIgnoreCase(cotizacionDTO.getClienteNombre())
                .stream()
                .filter(e -> e.getNombre().equalsIgnoreCase(cotizacionDTO.getClienteNombre()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado: " + cotizacionDTO.getClienteNombre()));

        cotizacion.setCliente(cliente);
        if (cotizacionDTO.getTratoId() != null) {
            cotizacion.setTratoId(cotizacionDTO.getTratoId());
        }

        BigDecimal subtotal = cotizacionDTO.getUnidades().stream()
                .map(u -> {
                    BigDecimal descuento = u.getDescuento() != null ? u.getDescuento() : BigDecimal.ZERO;
                    BigDecimal descuentoPorcentaje = descuento.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
                    BigDecimal descuentoMonto = u.getPrecioUnitario().multiply(descuentoPorcentaje).multiply(new BigDecimal(u.getCantidad()));
                    return u.getPrecioUnitario().multiply(new BigDecimal(u.getCantidad())).subtract(descuentoMonto);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal iva = subtotal.multiply(new BigDecimal("0.16"));
        BigDecimal isrEstatal = BigDecimal.ZERO;
        BigDecimal isrFederal = BigDecimal.ZERO;

        BigDecimal total = subtotal.add(iva);

        cotizacion.setSubtotal(subtotal);
        cotizacion.setIva(iva);
        cotizacion.setIsrEstatal(isrEstatal);
        cotizacion.setIsrFederal(isrFederal);
        cotizacion.setTotal(total);
        cotizacion.setImporteLetra(convertToLetter(total));

        cotizacion.getUnidades().clear();

        List<UnidadCotizacion> nuevasUnidades = cotizacionDTO.getUnidades().stream().map(u -> {
            UnidadCotizacion unidad = new UnidadCotizacion();
            unidad.setCotizacion(cotizacion);
            unidad.setCantidad(u.getCantidad());
            unidad.setUnidad(u.getUnidad());
            unidad.setConcepto(u.getConcepto());
            unidad.setPrecioUnitario(u.getPrecioUnitario());
            BigDecimal descuento = u.getDescuento() != null ? u.getDescuento() : BigDecimal.ZERO;
            BigDecimal descuentoPorcentaje = descuento.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            BigDecimal descuentoMonto = u.getPrecioUnitario().multiply(descuentoPorcentaje).multiply(new BigDecimal(u.getCantidad()));
            unidad.setDescuento(descuento);
            unidad.setImporteTotal(u.getPrecioUnitario().multiply(new BigDecimal(u.getCantidad())).subtract(descuentoMonto));
            return unidad;
        }).collect(Collectors.toList());

        cotizacion.getUnidades().addAll(nuevasUnidades);

        Cotizacion updatedCotizacion = cotizacionRepository.save(cotizacion);

        CotizacionDTO result = convertToDTO(updatedCotizacion);
        result.setIsrEstatal(isrEstatal);
        result.setIsrFederal(isrFederal);
        result.setEmpresaData(empresaService.convertToEmpresaDTO(cliente));
        return result;
    }


    @Transactional
    public void eliminarCotizacion(Integer id) {
        logger.info("Eliminando cotización con ID: {}", id);

        Cotizacion cotizacion = cotizacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada con id: " + id));
        if (isCotizacionVinculadaACuentaPorCobrar(id)) {
            throw new IllegalStateException("No se puede eliminar la cotización porque está vinculada a una o más cuentas por cobrar");
        }
        cotizacionRepository.delete(cotizacion);
    }


    @Transactional(readOnly = true)
    public List<CotizacionDTO> listarCotizaciones() {
        logger.info("Listando todas las cotizaciones");
        return cotizacionRepository.findAllOrderByFechaCreacionDesc()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public CotizacionDTO convertToDTO(Cotizacion cotizacion) {
        CotizacionDTO dto = new CotizacionDTO();
        dto.setId(cotizacion.getId());
        dto.setClienteNombre(cotizacion.getCliente().getNombre());
        dto.setSubtotal(cotizacion.getSubtotal());
        dto.setIva(cotizacion.getIva());
        dto.setIsrEstatal(cotizacion.getIsrEstatal());
        dto.setIsrFederal(cotizacion.getIsrFederal());
        dto.setTotal(cotizacion.getTotal());
        dto.setImporteLetra(cotizacion.getImporteLetra());
        dto.setFechaCreacion(cotizacion.getFechaCreacion());
        dto.setFecha(cotizacion.getFechaCreacion()
                .atZone(java.time.ZoneId.of("America/Mexico_City"))
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        dto.setEstatus(cotizacion.getEstatus());
        dto.setTratoId(cotizacion.getTratoId());
        dto.setUsuarioCreadorId(cotizacion.getUsuarioCreadorId());

        // Cargar nombre de usuario creador
        if (cotizacion.getUsuarioCreadorId() != null) {
            Usuario usuario = usuarioRepository.findById(cotizacion.getUsuarioCreadorId()).orElse(null);
            dto.setUsuarioCreadorNombre(usuario != null ? usuario.getNombre() : "Usuario Desconocido");
        }

        // Cargar nombre del trato
        if (cotizacion.getTratoId() != null) {
            Trato trato = tratoRepository.findById(cotizacion.getTratoId()).orElse(null);
            dto.setTratoNombre(trato != null ? trato.getNombre() : "Trato Desconocido");
        }

        dto.setUnidades(cotizacion.getUnidades().stream().map(u -> {
            UnidadCotizacionDTO udto = new UnidadCotizacionDTO();
            udto.setId(u.getId());
            udto.setCantidad(u.getCantidad());
            udto.setUnidad(u.getUnidad());
            udto.setConcepto(u.getConcepto());
            udto.setPrecioUnitario(u.getPrecioUnitario());
            udto.setDescuento(u.getDescuento());
            udto.setImporteTotal(u.getImporteTotal());
            return udto;
        }).collect(Collectors.toList()));

        dto.setCantidadTotal(
                cotizacion.getUnidades().stream()
                        .filter(u -> "Equipos".equals(u.getUnidad()))
                        .mapToInt(UnidadCotizacion::getCantidad)
                        .sum()
        );
        dto.setConceptosCount((int) cotizacion.getUnidades().stream().map(UnidadCotizacion::getConcepto).distinct().count());
        dto.setEmpresaData(empresaService.convertToEmpresaDTO(cotizacion.getCliente()));
        dto.setNotasComercialesNombre(cotizacion.getNotasComercialesNombre());
        dto.setNotasComercialesTopo(cotizacion.getNotasComercialesTopo());
        dto.setFichaTecnicaNombre(cotizacion.getFichaTecnicaNombre());
        dto.setFichaTecnicaTipo(cotizacion.getFichaTecnicaTipo());

        return dto;
    }

    private String convertirGrupo(long numero) {
        if (numero == 0) return "";
        if (numero < 20) return unidades[(int) numero];
        if (numero < 100) {
            int dec = (int) (numero / 10);
            int uni = (int) (numero % 10);
            if (uni == 0) return decenas[dec];
            if (dec == 2) return "veinti" + unidades[uni];
            return decenas[dec] + (uni > 0 ? " y " + unidades[uni] : "");
        }
        int cen = (int) (numero / 100);
        long resto = numero % 100;
        String resultado = "";
        if (cen == 1 && resto == 0) {
            resultado = "cien";
        } else {
            resultado = centenas[cen];
        }
        if (resto > 0) {
            resultado += " " + convertirGrupo(resto);
        }
        return resultado;
    }

    public String convertToLetter(BigDecimal number) {
        if (number == null) return "Cero pesos 00/100 M.N.";

        BigDecimal roundedNumber = number.setScale(2, RoundingMode.HALF_UP);
        long entero = roundedNumber.longValue();
        BigDecimal decimalPart = roundedNumber.remainder(BigDecimal.ONE);
        int centavos = decimalPart.movePointRight(2).intValue();

        String resultado = "";
        long numeroActual = entero;

        if (numeroActual >= 1000000) {
            long millones = numeroActual / 1000000;
            if (millones == 1) {
                resultado += "un millón ";
            } else {
                resultado += convertirGrupo(millones) + " millones ";
            }
            numeroActual %= 1000000;
        }

        if (numeroActual >= 1000) {
            long miles = numeroActual / 1000;
            if (miles == 1) {
                resultado += "mil ";
            } else {
                resultado += convertirGrupo(miles) + " mil ";
            }
            numeroActual %= 1000;
        }

        if (numeroActual > 0) {
            resultado += convertirGrupo(numeroActual);
        }

        resultado = resultado.trim();
        if (entero == 1) {
            resultado += " peso";
        } else {
            resultado += " pesos";
        }

        resultado += " " + String.format("%02d", centavos) + "/100 M.N.";

        if (resultado.length() > 0) {
            return Character.toUpperCase(resultado.charAt(0)) + resultado.substring(1);
        }
        return resultado;
    }

    private Image cargarMembrete() throws Exception {
        synchronized (membreteLock) {
            if (membreteCache == null) {
                membreteCache = procesarMembrete();
            }
        }

        Image membrete = Image.getInstance(membreteCache);
        membrete.scaleAbsolute(PageSize.A4.getWidth(), PageSize.A4.getHeight());

        float xPos = (PageSize.A4.getWidth() - membrete.getScaledWidth()) / 2;
        float yPos = (PageSize.A4.getHeight() - membrete.getScaledHeight()) / 2;
        membrete.setAbsolutePosition(xPos, yPos);

        return membrete;
    }

    private byte[] procesarMembrete() throws Exception {
        InputStream inputStream = null;
        BufferedImage bufferedImage = null;

        try {
            inputStream = getClass().getResourceAsStream("/static/images/membrete.png");
            if (inputStream == null) {
                throw new Exception("No se pudo encontrar el archivo de membrete");
            }

            bufferedImage = ImageIO.read(inputStream);
            inputStream.close();

            int targetWidth = (int) PageSize.A4.getWidth() * 3;
            int targetHeight = (int) PageSize.A4.getHeight() * 3;

            if (bufferedImage.getWidth() > targetWidth || bufferedImage.getHeight() > targetHeight) {
                BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = resized.createGraphics();

                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                        java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(java.awt.RenderingHints.KEY_COLOR_RENDERING,
                        java.awt.RenderingHints.VALUE_COLOR_RENDER_QUALITY);
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

                g.drawImage(bufferedImage, 0, 0, targetWidth, targetHeight, null);
                g.dispose();

                bufferedImage = resized;
            }

            ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();

            if (param.canWriteCompressed()) {
                param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.95f);
            }

            writer.setOutput(ImageIO.createImageOutputStream(compressedOut));
            writer.write(null, new javax.imageio.IIOImage(bufferedImage, null, null), param);
            writer.dispose();

            byte[] result = compressedOut.toByteArray();
            compressedOut.close();

            bufferedImage.flush();
            bufferedImage = null;
            System.gc();

            logger.info("Membrete procesado y cacheado: {} bytes", result.length);
            return result;

        } catch (Exception e) {
            logger.error("Error procesando membrete: {}", e.getMessage());
            throw e;

        } finally {
            if (inputStream != null) {
                try { inputStream.close(); } catch (Exception ignored) {}
            }
            if (bufferedImage != null) {
                bufferedImage.flush();
            }
        }
    }

    @Transactional(readOnly = true)
    public ByteArrayResource generateCotizacionPDF(Integer id, boolean incluirArchivos) throws Exception {
        logger.info("Generando PDF para cotización con ID: {}", id);

        Cotizacion cotizacion = cotizacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada con id: " + id));

        if (!incluirArchivos || (cotizacion.getNotasComercialesContenido() == null && cotizacion.getFichaTecnicaContenido() == null)) {
            return generarPDFCotizacion(id);
        }

        return combinarPDFsOptimizado(id, cotizacion);
    }

    private ByteArrayResource combinarPDFsOptimizado(Integer id, Cotizacion cotizacion) throws Exception {
        ByteArrayOutputStream combined = new ByteArrayOutputStream();
        PdfCopy copy = null;
        com.lowagie.text.Document document = null;

        java.util.ArrayList<PdfReader> readers = new java.util.ArrayList<>();

        try {
            // 1. Generar PDF principal
            ByteArrayResource cotizacionPDF = generarPDFCotizacion(id);
            byte[] mainPdfBytes = cotizacionPDF.getByteArray();

            PdfReader mainReader = new PdfReader(mainPdfBytes);
            readers.add(mainReader);

            document = new com.lowagie.text.Document(mainReader.getPageSizeWithRotation(1));
            copy = new PdfCopy(document, combined);
            document.open();

            int mainPages = mainReader.getNumberOfPages();
            for (int i = 1; i <= mainPages; i++) {
                PdfImportedPage page = copy.getImportedPage(mainReader, i);
                copy.addPage(page);
                page = null;

                if (i % 2 == 0) {
                    copy.freeReader(mainReader);
                    System.gc();
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {}
                }
            }

            mainPdfBytes = null;
            cotizacionPDF = null;
            copy.freeReader(mainReader);
            System.gc();

            // 2. Procesar Notas Comerciales
            if (cotizacion.getNotasComercialesContenido() != null) {
                byte[] notasBytes = cotizacion.getNotasComercialesContenido();
                PdfReader notasReader = new PdfReader(notasBytes);
                readers.add(notasReader);

                int notasPages = notasReader.getNumberOfPages();
                for (int i = 1; i <= notasPages; i++) {
                    PdfImportedPage page = copy.getImportedPage(notasReader, i);
                    copy.addPage(page);
                    page = null;

                    if (i % 2 == 0) {
                        copy.freeReader(notasReader);
                        System.gc();
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {}
                    }
                }

                notasBytes = null;
                copy.freeReader(notasReader);
                System.gc();
            }

            // 3. Procesar Ficha Técnica
            if (cotizacion.getFichaTecnicaContenido() != null) {
                byte[] fichaBytes = cotizacion.getFichaTecnicaContenido();
                PdfReader fichaReader = new PdfReader(fichaBytes);
                readers.add(fichaReader);

                int fichaPages = fichaReader.getNumberOfPages();
                for (int i = 1; i <= fichaPages; i++) {
                    PdfImportedPage page = copy.getImportedPage(fichaReader, i);
                    copy.addPage(page);
                    page = null;

                    if (i % 2 == 0) {
                        copy.freeReader(fichaReader);
                        System.gc();
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {}
                    }
                }

                fichaBytes = null;
                copy.freeReader(fichaReader);
                System.gc();
            }

            document.close();
            copy.close();

            for (PdfReader reader : readers) {
                if (reader != null) {
                    reader.close();
                }
            }
            readers.clear();
            readers = null;

            byte[] result = combined.toByteArray();
            combined.close();

            document = null;
            copy = null;
            System.gc();

            return new ByteArrayResource(result);

        } catch (OutOfMemoryError oom) {
            logger.error("OutOfMemoryError en combinarPDFsOptimizado: {}", oom.getMessage());
            throw new Exception("Memoria insuficiente para generar el PDF combinado", oom);

        } catch (Exception e) {
            logger.error("Error combinando PDFs: {}", e.getMessage());
            throw new Exception("Error al combinar los archivos PDF", e);

        } finally {
            try {
                if (copy != null) {
                    copy.close();
                }
                if (document != null && document.isOpen()) {
                    document.close();
                }
                if (combined != null) {
                    combined.close();
                }
                for (PdfReader reader : readers) {
                    if (reader != null) {
                        reader.close();
                    }
                }
            } catch (Exception ignored) {}

            System.gc();
        }
    }

    @Transactional(readOnly = true)
    public ByteArrayResource generarPDFCotizacion(Integer id) throws Exception {
        logger.info("Generando PDF para cotización con ID: {}", id);

        Cotizacion cotizacion = cotizacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada con id: " + id));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = null;
        PdfWriter writer = null;

        try {
            document = new Document(PageSize.A4, 40, 40, 90, 50);
            writer = PdfWriter.getInstance(document, out);
            writer.setCompressionLevel(9);
            writer.setFullCompression();
            document.open();

            try {
                Image membrete = cargarMembrete();
                if (membrete != null) {
                    PdfContentByte canvas = writer.getDirectContentUnder();

                    canvas.saveState();
                    canvas.addImage(membrete);
                    canvas.restoreState();

                    membrete = null;

                    logger.info("Membrete agregado exitosamente");
                }
            } catch (Exception e) {
                logger.warn("Error al agregar membrete: {}", e.getMessage());
            }

        Color azulCorporativo = new Color(41, 84, 144);
        Color azulClaro = new Color(230, 240, 250);
        Color grisOscuro = new Color(64, 64, 64);
        Color grisMedio = new Color(128, 128, 128);
        Color blancoHueso = new Color(250, 250, 250);

        Font tituloFont = new Font(Font.HELVETICA, 20, Font.BOLD, azulCorporativo);
        Font subtituloFont = new Font(Font.HELVETICA, 14, Font.BOLD, grisOscuro);
        Font headerTableFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
        Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL, grisOscuro);
        Font boldFont = new Font(Font.HELVETICA, 10, Font.BOLD, grisOscuro);
        Font totalFont = new Font(Font.HELVETICA, 12, Font.BOLD, azulCorporativo);

        PdfPTable headerTable = new PdfPTable(1);
        headerTable.setWidthPercentage(100);
        headerTable.setSpacingAfter(15);

        String title = "COTIZACIÓN DE ";
        String significantConcept = cotizacion.getUnidades().stream()
                .findFirst()
                .map(u -> {
                    String concepto = u.getConcepto().toUpperCase();
                    if (concepto.contains("RASTREO") || concepto.contains("GPS")) return "SERVICIOS DE RASTREO GPS";
                    if (concepto.contains("DASHCAM")) return "SISTEMA DASHCAM";
                    if (concepto.contains("COMBUSTIBLE")) return "SENSOR DE COMBUSTIBLE";
                    return concepto.split(" ")[0];
                })
                .orElse("SERVICIOS TECNOLÓGICOS");
        title += significantConcept;

        PdfPCell titleCell = new PdfPCell(new Phrase(title, tituloFont));
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setPadding(15);
        headerTable.addCell(titleCell);

        document.add(headerTable);

        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{3f, 1f});
        infoTable.setSpacingAfter(20);

        PdfPCell clienteCell = new PdfPCell();
        clienteCell.setBorder(Rectangle.BOX);
        clienteCell.setBorderColor(azulCorporativo);
        clienteCell.setBorderWidth(1f);
        clienteCell.setPadding(12);
        clienteCell.setBackgroundColor(azulClaro);

        Paragraph clienteInfo = new Paragraph();
        clienteInfo.add(new Chunk("CLIENTE: ", boldFont));
        clienteInfo.add(new Chunk(cotizacion.getCliente().getNombre(), normalFont));
        clienteCell.addElement(clienteInfo);
        infoTable.addCell(clienteCell);

        PdfPCell fechaCell = new PdfPCell();
        fechaCell.setBorder(Rectangle.BOX);
        fechaCell.setBorderColor(azulCorporativo);
        fechaCell.setBorderWidth(1f);
        fechaCell.setPadding(12);
        fechaCell.setBackgroundColor(azulClaro);

        String fechaFormateada = cotizacion.getFechaCreacion()
                .atZone(java.time.ZoneId.of("America/Mexico_City"))
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        Paragraph fechaInfo = new Paragraph();
        fechaInfo.add(new Chunk("FECHA: ", boldFont));
        fechaInfo.add(new Chunk(fechaFormateada, normalFont));
        fechaInfo.setAlignment(Element.ALIGN_RIGHT);
        fechaCell.addElement(fechaInfo);
        infoTable.addCell(fechaCell);

        document.add(infoTable);

        PdfPTable conceptosTable = new PdfPTable(6);
        conceptosTable.setWidthPercentage(100);
        conceptosTable.setWidths(new float[]{1.2f, 1.5f, 4f, 1.5f, 1.5f, 1.5f});
        conceptosTable.setSpacingAfter(20);

        String[] headers = {"Cantidad", "Unidad", "Concepto", "Precio Unit.", "Descuento", "Importe"};
        for (String header : headers) {
            PdfPCell headerCell = new PdfPCell(new Phrase(header, headerTableFont));
            headerCell.setBackgroundColor(azulCorporativo);
            headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            headerCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            headerCell.setPadding(10);
            headerCell.setBorder(Rectangle.BOX);
            headerCell.setBorderColor(Color.WHITE);
            headerCell.setBorderWidth(1f);
            conceptosTable.addCell(headerCell);
        }

        boolean isEvenRow = false;
        for (UnidadCotizacion unidad : cotizacion.getUnidades()) {
            Color rowColor = isEvenRow ? blancoHueso : Color.WHITE;

            conceptosTable.addCell(createStyledTableCell(
                    String.valueOf(unidad.getCantidad()),
                    Element.ALIGN_CENTER,
                    normalFont,
                    rowColor
            ));

            conceptosTable.addCell(createStyledTableCell(
                    unidad.getUnidad(),
                    Element.ALIGN_CENTER,
                    normalFont,
                    rowColor
            ));

            PdfPCell conceptoCell = new PdfPCell(new Phrase(unidad.getConcepto(), normalFont));
            conceptoCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            conceptoCell.setVerticalAlignment(Element.ALIGN_TOP);
            conceptoCell.setPadding(8);
            conceptoCell.setBackgroundColor(rowColor);
            conceptoCell.setBorder(Rectangle.BOX);
            conceptoCell.setBorderColor(grisMedio);
            conceptoCell.setBorderWidth(0.5f);
            conceptosTable.addCell(conceptoCell);

            conceptosTable.addCell(createStyledTableCell(
                    formatCurrency(unidad.getPrecioUnitario()),
                    Element.ALIGN_RIGHT,
                    normalFont,
                    rowColor
            ));

            conceptosTable.addCell(createStyledTableCell(
                    formatPercentage(unidad.getDescuento()),
                    Element.ALIGN_RIGHT,
                    normalFont,
                    rowColor
            ));

            conceptosTable.addCell(createStyledTableCell(
                    formatCurrency(unidad.getImporteTotal()),
                    Element.ALIGN_RIGHT,
                    boldFont,
                    rowColor
            ));

            isEvenRow = !isEvenRow;
        }

        document.add(conceptosTable);

        PdfPTable totalsTable = new PdfPTable(2);
        totalsTable.setWidthPercentage(60);
        totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalsTable.setSpacingAfter(20);

        addTotalRow(totalsTable, "Subtotal:", formatCurrency(cotizacion.getSubtotal()),
                normalFont, boldFont, null);

        addTotalRow(totalsTable, "IVA (16%):", formatCurrency(cotizacion.getIva()),
                normalFont, boldFont, null);

        PdfPCell separatorCell = new PdfPCell();
        separatorCell.setColspan(2);
        separatorCell.setBorder(Rectangle.TOP);
        separatorCell.setBorderColor(azulCorporativo);
        separatorCell.setBorderWidth(2f);
        separatorCell.setFixedHeight(5);
        totalsTable.addCell(separatorCell);

        addTotalRow(totalsTable, "TOTAL:", formatCurrency(cotizacion.getTotal()),
                totalFont, totalFont, null);

        document.add(totalsTable);

        PdfPTable importeLetraTable = new PdfPTable(1);
        importeLetraTable.setWidthPercentage(100);
        importeLetraTable.setSpacingAfter(15);

        PdfPCell importeLetraHeaderCell = new PdfPCell(new Phrase("IMPORTE CON LETRA", headerTableFont));
        importeLetraHeaderCell.setBackgroundColor(azulCorporativo);
        importeLetraHeaderCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        importeLetraHeaderCell.setPadding(8);
        importeLetraHeaderCell.setBorder(Rectangle.BOX);
        importeLetraTable.addCell(importeLetraHeaderCell);

        PdfPCell importeLetraContentCell = new PdfPCell(new Phrase(cotizacion.getImporteLetra(), boldFont));
        importeLetraContentCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        importeLetraContentCell.setPadding(12);
        importeLetraContentCell.setBackgroundColor(blancoHueso);
        importeLetraContentCell.setBorder(Rectangle.BOX);
        importeLetraContentCell.setBorderColor(azulCorporativo);
        importeLetraTable.addCell(importeLetraContentCell);

            document.add(importeLetraTable);

            document.close();

            byte[] pdfBytes = out.toByteArray();
            out.close();

            writer = null;
            document = null;

            return new ByteArrayResource(pdfBytes);

        } catch (Exception e) {
            logger.error("Error generando PDF: {}", e.getMessage());

            if (document != null && document.isOpen()) {
                document.close();
            }
            if (out != null) {
                out.close();
            }

            throw e;
        }
    }

    private PdfPCell createStyledTableCell(String text, int alignment, Font font, Color backgroundColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(8);
        cell.setBackgroundColor(backgroundColor);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(new Color(200, 200, 200));
        cell.setBorderWidth(0.5f);
        return cell;
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont, Color backgroundColor) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setPadding(8);
        labelCell.setBackgroundColor(backgroundColor);
        labelCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(8);
        valueCell.setBackgroundColor(backgroundColor);
        valueCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(valueCell);
    }

    private String formatCurrency(BigDecimal amount) {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));
        return currencyFormat.format(amount);
    }

    private String formatPercentage(BigDecimal percentage) {
        if (percentage.compareTo(BigDecimal.ZERO) == 0) {
            return "0%";
        }
        NumberFormat percentFormat = NumberFormat.getPercentInstance(new Locale("es", "MX"));
        return percentFormat.format(percentage.divide(BigDecimal.valueOf(100)));
    }

    @Transactional(readOnly = true)
    public Cotizacion findById(Integer id) {
        logger.info("Buscando cotización con ID: {}", id);
        return cotizacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada con id: " + id));
    }

    @Transactional(readOnly = true)
    public boolean isCotizacionVinculadaACuentaPorCobrar(Integer cotizacionId) {
        logger.info("Verificando si cotización con ID {} está vinculada a cuenta por cobrar", cotizacionId);

        return cuentaPorCobrarRepository.existsByCotizacionId(cotizacionId);
    }

    @Transactional
    public void subirArchivosAdicionales(Integer cotizacionId, MultipartFile notasComerciales, MultipartFile fichaTecnica) throws Exception {
        Cotizacion cotizacion = cotizacionRepository.findById(cotizacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada con id: " + cotizacionId));

        // Procesar Notas Comerciales
        if (notasComerciales != null && !notasComerciales.isEmpty()) {
            String tipoNotas = notasComerciales.getContentType();
            byte[] contenidoNotas;

            if ("image/png".equals(tipoNotas)) {
                contenidoNotas = convertImageToPdf(notasComerciales.getBytes(), notasComerciales.getOriginalFilename());
                tipoNotas = "application/pdf";
                cotizacion.setNotasComercialesNombre(notasComerciales.getOriginalFilename().replace(".png", ".pdf"));
            } else if ("application/pdf".equals(tipoNotas)) {
                contenidoNotas = notasComerciales.getBytes();
                cotizacion.setNotasComercialesNombre(notasComerciales.getOriginalFilename());
            } else {
                throw new IllegalArgumentException("Formato no soportado para Notas Comerciales. Solo PDF y PNG");
            }

            cotizacion.setNotasComercialesContenido(contenidoNotas);
            cotizacion.setNotasComercialesTopo(tipoNotas);
        }

        // Procesar Ficha Técnica
        if (fichaTecnica != null && !fichaTecnica.isEmpty()) {
            String tipoFicha = fichaTecnica.getContentType();
            byte[] contenidoFicha;

            if ("image/png".equals(tipoFicha)) {
                // Convertir PNG a PDF
                contenidoFicha = convertImageToPdf(fichaTecnica.getBytes(), fichaTecnica.getOriginalFilename());
                tipoFicha = "application/pdf";
                cotizacion.setFichaTecnicaNombre(fichaTecnica.getOriginalFilename().replace(".png", ".pdf"));
            } else if ("application/pdf".equals(tipoFicha)) {
                contenidoFicha = fichaTecnica.getBytes();
                cotizacion.setFichaTecnicaNombre(fichaTecnica.getOriginalFilename());
            } else {
                throw new IllegalArgumentException("Formato no soportado para Ficha Técnica. Solo PDF y PNG");
            }

            cotizacion.setFichaTecnicaContenido(contenidoFicha);
            cotizacion.setFichaTecnicaTipo(tipoFicha);
        }

        cotizacionRepository.save(cotizacion);
        logger.info("Archivos adicionales guardados para cotización ID: {}", cotizacionId);
    }

    private byte[] convertImageToPdf(byte[] imageBytes, String originalFileName) throws Exception {
        ByteArrayOutputStream pdfOut = null;
        Document document = null;
        BufferedImage bufferedImage = null;

        try {
            bufferedImage = ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));

            int maxWidth = 2480;
            int maxHeight = 3508;

            if (bufferedImage.getWidth() > maxWidth || bufferedImage.getHeight() > maxHeight) {
                double scale = Math.min(
                        (double) maxWidth / bufferedImage.getWidth(),
                        (double) maxHeight / bufferedImage.getHeight()
                );

                int newWidth = (int) (bufferedImage.getWidth() * scale);
                int newHeight = (int) (bufferedImage.getHeight() * scale);

                BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = resized.createGraphics();

                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                        java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

                g.drawImage(bufferedImage, 0, 0, newWidth, newHeight, null);
                g.dispose();

                bufferedImage = resized;
            }

            ByteArrayOutputStream compressedImageOut = new ByteArrayOutputStream();
            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.90f);

            writer.setOutput(ImageIO.createImageOutputStream(compressedImageOut));
            writer.write(null, new javax.imageio.IIOImage(bufferedImage, null, null), param);
            writer.dispose();

            byte[] compressedImageBytes = compressedImageOut.toByteArray();
            compressedImageOut.close();

            pdfOut = new ByteArrayOutputStream();
            document = new Document(PageSize.A4, 0, 0, 0, 0);
            PdfWriter pdfWriter = PdfWriter.getInstance(document, pdfOut);
            pdfWriter.setCompressionLevel(9);
            document.open();

            Image pdfImage = Image.getInstance(compressedImageBytes);

            float scaleWidth = PageSize.A4.getWidth() / pdfImage.getWidth();
            float scaleHeight = PageSize.A4.getHeight() / pdfImage.getHeight();
            float scale = Math.min(scaleWidth, scaleHeight);

            pdfImage.scalePercent(scale * 100);

            float x = (PageSize.A4.getWidth() - pdfImage.getScaledWidth()) / 2;
            float y = (PageSize.A4.getHeight() - pdfImage.getScaledHeight()) / 2;
            pdfImage.setAbsolutePosition(x, y);

            document.add(pdfImage);
            document.close();

            byte[] result = pdfOut.toByteArray();

            pdfOut.close();
            compressedImageBytes = null;
            bufferedImage = null;
            System.gc();

            return result;

        } catch (Exception e) {
            logger.error("Error convirtiendo imagen {} a PDF: {}", originalFileName, e.getMessage());
            throw new Exception("Error al convertir imagen a PDF", e);

        } finally {
            if (document != null && document.isOpen()) {
                document.close();
            }
            if (pdfOut != null) {
                pdfOut.close();
            }
            if (bufferedImage != null) {
                bufferedImage.flush();
                bufferedImage = null;
            }
            System.gc();
        }
    }

    @Transactional(readOnly = true)
    public List<CotizacionDTO> listarCotizacionesPorTrato(Integer tratoId) {
        logger.info("Listando cotizaciones para trato ID: {}", tratoId);
        return cotizacionRepository.findByTratoIdOrderByFechaCreacionDesc(tratoId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public CotizacionDTO cambiarEstatus(Integer cotizacionId, EstatusCotizacionEnum nuevoEstatus) {
        logger.info("Cambiando estatus de cotización {} a {}", cotizacionId, nuevoEstatus);

        Cotizacion cotizacion = cotizacionRepository.findById(cotizacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada con id: " + cotizacionId));

        cotizacion.setEstatus(nuevoEstatus);
        Cotizacion updated = cotizacionRepository.save(cotizacion);

        return convertToDTO(updated);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> obtenerTratosDisponibles(Integer empresaId) {
        logger.info("Obteniendo tratos disponibles para empresa: {}", empresaId);

        List<Trato> tratos = tratoRepository.findTratosDisponiblesParaCotizacion(empresaId);

        return tratos.stream()
                .map(trato -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", trato.getId());
                    map.put("nombre", trato.getNombre());
                    map.put("fase", trato.getFase());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void cambiarEstatusAutomaticoPorTrato(Integer tratoId, EstatusCotizacionEnum nuevoEstatus) {
        List<Cotizacion> cotizaciones = cotizacionRepository.findByTratoIdOrderByFechaCreacionDesc(tratoId);

        for (Cotizacion cotizacion : cotizaciones) {
            if (cotizacion.getEstatus() != EstatusCotizacionEnum.ACEPTADA) {
                cotizacion.setEstatus(nuevoEstatus);
                cotizacionRepository.save(cotizacion);
            }
        }
    }

    @Transactional
    public CotizacionDTO cambiarEstatusAEnviada(Integer cotizacionId) {
        Cotizacion cotizacion = cotizacionRepository.findById(cotizacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada"));

        if (cotizacion.getEstatus() == EstatusCotizacionEnum.PENDIENTE) {
            cotizacion.setEstatus(EstatusCotizacionEnum.ENVIADA);
            Cotizacion updated = cotizacionRepository.save(cotizacion);
            return convertToDTO(updated);
        }

        return convertToDTO(cotizacion);
    }

    @Transactional
    public Map<String, Object> actualizarEstatusCotizacionesConCuentas() {
        logger.info("Buscando cotizaciones con cuentas por cobrar vinculadas");

        // Obtener todas las cotizaciones que tienen cuentas por cobrar
        List<Integer> cotizacionesConCuentas = cuentaPorCobrarRepository.findAll()
                .stream()
                .map(cuenta -> cuenta.getCotizacion().getId())
                .distinct()
                .collect(Collectors.toList());

        int actualizadas = 0;
        int yaAceptadas = 0;

        for (Integer cotizacionId : cotizacionesConCuentas) {
            Cotizacion cotizacion = cotizacionRepository.findById(cotizacionId).orElse(null);

            if (cotizacion != null) {
                if (cotizacion.getEstatus() != EstatusCotizacionEnum.ACEPTADA) {
                    logger.info("Actualizando cotización {} de {} a ACEPTADA",
                            cotizacion.getId(), cotizacion.getEstatus());
                    cotizacion.setEstatus(EstatusCotizacionEnum.ACEPTADA);
                    cotizacionRepository.save(cotizacion);
                    actualizadas++;
                } else {
                    yaAceptadas++;
                }
            }
        }

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("cotizacionesActualizadas", actualizadas);
        resultado.put("cotizacionesYaAceptadas", yaAceptadas);
        resultado.put("total", cotizacionesConCuentas.size());

        logger.info("Actualización completada: {} actualizadas, {} ya estaban aceptadas",
                actualizadas, yaAceptadas);

        return resultado;
    }

}