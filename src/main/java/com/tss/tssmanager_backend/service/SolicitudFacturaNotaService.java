package com.tss.tssmanager_backend.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.tss.tssmanager_backend.dto.FacturaDTO;
import com.tss.tssmanager_backend.dto.SolicitudFacturaNotaDTO;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.EstatusPagoEnum;
import com.tss.tssmanager_backend.enums.TipoDocumentoSolicitudEnum;
import com.tss.tssmanager_backend.exception.ResourceNotFoundException;
import com.tss.tssmanager_backend.repository.*;
import com.tss.tssmanager_backend.config.CloudinaryConfig;
import com.tss.tssmanager_backend.entity.UnidadCotizacion;
import com.tss.tssmanager_backend.utils.DateUtils;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import com.cloudinary.Cloudinary;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

@Service
public class SolicitudFacturaNotaService {

    private static final Logger logger = LoggerFactory.getLogger(SolicitudFacturaNotaService.class);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SolicitudFacturaNotaRepository solicitudRepository;

    @Autowired
    private EmisorRepository emisorRepository;

    @Autowired
    private FacturaRepository facturaRepository;

    @Autowired
    private CotizacionRepository cotizacionRepository;

    @Autowired
    private CotizacionService cotizacionService;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private CuentaPorCobrarRepository cuentaPorCobrarRepository;

    @Autowired
    private CloudinaryConfig cloudinaryConfig;

    private byte[] membreteCache = null;
    private final Object membreteLock = new Object();

    // Mapeos estáticos para los campos clave
    private static final Map<String, String> METODOS_PAGO = new HashMap<>() {{
        put("PUE", "Pago en una sola exhibición (PUE)");
        put("PPD", "Pago en parcialidades o diferido (PPD)");
    }};

    private static final Map<String, String> FORMAS_PAGO = new HashMap<>() {{
        put("01", "Efectivo");
        put("02", "Tarjeta Spin");
        put("03", "Transferencia electrónica de fondos");
        put("04", "Tarjeta de crédito");
        put("07", "Con Saldo Acumulado");
        put("28", "Tarjeta de débito");
        put("30", "Aplicación de anticipos");
        put("99", "Por definir");
    }};

    private static final Map<String, String> CLAVES_PRODUCTO_SERVICIO = new HashMap<>() {{
        put("25173108", "Sistemas de navegación vehicular (Sistema GPS)");
        put("25173107", "Sistemas de posicionamiento global de vehículos");
        put("43211710", "Dispositivos de identificación de radio frecuencia");
        put("43212116", "Impresoras de etiquetas de identificación de radio frecuencia rfid");
        put("81111810", "Servicios de codificación de software");
        put("81111501", "Diseño de aplicaciones de software de la unidad central");
        put("81111510", "Servicios de desarrollo de aplicaciones para servidores");
        put("81112106", "Proveedores de servicios de aplicación");
        put("81112105", "Servicios de hospedaje de operación de sitios web");
        put("20121910", "Sistemas de telemetría");
    }};

    private static final Map<String, String> CLAVES_UNIDAD = new HashMap<>() {{
        put("H87", "Pieza");
        put("E48", "Unidad de servicio");
        put("ACT", "Actividad");
        put("MON", "Mes");
        put("LOT", "Lote");
    }};

    private static final Map<String, String> USOS_CFDI = new HashMap<>() {{
        put("G01", "Adquisición de mercancías");
        put("G02", "Devoluciones, descuentos o bonificaciones");
        put("G03", "Gastos en General");
        put("I01", "Construcciones");
        put("I02", "Mobiliario y Equipo de Oficina por inversiones");
        put("I03", "Equipo de transporte");
        put("I04", "Equipo de cómputo y accesorios");
        put("I05", "Dados, troqueles, moldes, matrices y herramientas");
        put("I06", "Comunicaciones telefónicas");
        put("I07", "Comunicaciones satelitales");
        put("I08", "Otra maquinaria y equipo");
        put("D01", "Honorarios médicos, dentales y hospitalarios");
        put("D02", "Gastos médicos por incapacidad o discapacidad");
        put("D03", "Gastos funerales");
        put("D04", "Donativos");
        put("D05", "Intereses reales efectivamente pagados por créditos hipotecarios (casa habitación)");
        put("D06", "Aportaciones voluntarias al SAR");
        put("D07", "Primas por seguros de gastos médicos");
        put("D08", "Gastos por transportación escolar obligatoria");
        put("D09", "Depósitos en cuentas para el ahorro, primas que tengan como base planes de pensiones");
        put("D10", "Pagos por servicios educativos (colegiaturas)");
        put("P01", "Por definir");
    }};

    // Mapeo para régimen fiscal
    private static final Map<String, String> REGIMEN_FISCAL = new HashMap<>() {{
        put("605", "Sueldos y Salarios e Ingresos Asimilados a Salarios");
        put("606", "Arrendamiento");
        put("608", "Demás ingresos");
        put("611", "Ingresos por Dividendos (socios y accionistas)");
        put("612", "Personas Físicas con Actividades Empresariales y Profesionales");
        put("614", "Ingresos por intereses");
        put("615", "Régimen de los ingresos por obtención de premios");
        put("616", "Sin obligaciones fiscales");
        put("621", "Incorporación Fiscal");
        put("622", "Actividades Agrícolas, Ganaderas, Silvícolas y Pesqueras");
        put("626", "Régimen Simplificado de Confianza (Persona Fisica)");
        put("627", "Régimen Simplificado de Confianza (Persona Moral)");
        put("629", "De los Regímenes Fiscales Preferentes y de las Empresas Multinacionales");
        put("630", "Enajenación de acciones en bolsa de valores");
        put("601", "General de Ley Personas Morales");
        put("603", "Personas Morales con Fines no Lucrativos");
        put("607", "Régimen de Enajenación o Adquisición de Bienes");
        put("609", "Consolidación");
        put("620", "Sociedades Cooperativas de Producción que optan por Diferir sus Ingresos");
        put("623", "Opcional para Grupos de Sociedades");
        put("624", "Coordinados");
        put("628", "Hidrocarburos");
    }};

    // Métodos para Emisor
    @Transactional
    public Emisor crearEmisor(Emisor emisor, MultipartFile constanciaRegimen) throws Exception {
        logger.info("Creando nuevo emisor: {}", emisor.getNombre());
        if (constanciaRegimen != null && !constanciaRegimen.isEmpty()) {
            Cloudinary cloudinary = cloudinaryConfig.cloudinary();

            Map<String, Object> uploadParams = new HashMap<>();
            uploadParams.put("resource_type", "raw");

            Map uploadResult = cloudinary.uploader().upload(constanciaRegimen.getBytes(), uploadParams);
            emisor.setConstanciaRegimenFiscalUrl(uploadResult.get("url").toString());
        }
        return emisorRepository.save(emisor);
    }

    @Transactional
    public Emisor actualizarEmisor(Integer id, Emisor emisor, MultipartFile constanciaRegimen) throws Exception {
        logger.info("Actualizando emisor con ID: {}", id);
        Emisor existingEmisor = emisorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Emisor no encontrado con id: " + id));
        existingEmisor.setNombre(emisor.getNombre());
        existingEmisor.setRazonSocial(emisor.getRazonSocial());
        existingEmisor.setDireccion(emisor.getDireccion());
        existingEmisor.setRfc(emisor.getRfc());
        existingEmisor.setTelefono(emisor.getTelefono());
        if (constanciaRegimen != null && !constanciaRegimen.isEmpty()) {
            Cloudinary cloudinary = cloudinaryConfig.cloudinary();
            Map uploadResult = cloudinary.uploader().upload(constanciaRegimen.getBytes(), Map.of("resource_type", "raw"));
            existingEmisor.setConstanciaRegimenFiscalUrl(uploadResult.get("url").toString());
        }
        return emisorRepository.save(existingEmisor);
    }

    @Transactional
    public void eliminarEmisor(Integer id) {
        logger.info("Eliminando emisor con ID: {}", id);
        Emisor emisor = emisorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Emisor no encontrado con id: " + id));
        emisorRepository.delete(emisor);
    }

    @Transactional(readOnly = true)
    public List<Emisor> listarEmisores() {
        logger.info("Listando todos los emisores");
        return emisorRepository.findAll();
    }

    // Métodos para SolicitudFacturaNota
    @Transactional
    public SolicitudFacturaNotaDTO crearSolicitud(SolicitudFacturaNota solicitud) throws Exception {
        logger.info("Creando nueva solicitud de tipo: {}", solicitud.getTipo());
        Emisor emisor = emisorRepository.findById(solicitud.getEmisor().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Emisor no encontrado"));
        CuentaPorCobrar cuenta = cuentaPorCobrarRepository.findById(solicitud.getCuentaPorCobrar().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta por cobrar no encontrada"));

        if (cuenta.getConceptos() != null) {
            String conceptosTexto = cuenta.getConceptos().stream()
                    .map(ConceptoCuenta::getConcepto)
                    .collect(Collectors.joining(", "));
            solicitud.setConceptosSeleccionados(conceptosTexto);
        }

        if (solicitud.getCliente() == null) {
            solicitud.setCliente(cuenta.getCliente());
        } else if (solicitud.getCliente().getId() == null) {
            solicitud.setCliente(cuenta.getCliente());
        }

        if (solicitud.getTipo() == TipoDocumentoSolicitudEnum.SOLICITUD_DE_FACTURA) {
            validateEmpresaFiscal(solicitud.getCliente());
            if (solicitud.getUsoCfdi() == null || solicitud.getUsoCfdi().trim().isEmpty()) {
                throw new IllegalArgumentException("El uso de CFDI es obligatorio para solicitudes de factura.");
            }
        }
        solicitud.setEmisor(emisor);
        solicitud.setCuentaPorCobrar(cuenta);
        solicitud.setFechaEmision(Date.valueOf(DateUtils.nowInMexico().toLocalDate()));
        solicitud.setFechaModificacion(LocalDateTime.now());

        BigDecimal subtotal = cuenta.getCantidadCobrar();

        // Calcular IVA
        BigDecimal iva = subtotal.multiply(new BigDecimal("0.16"));

        // Calcular retenciones si aplica
        BigDecimal isrEstatal = BigDecimal.ZERO;
        BigDecimal isrFederal = BigDecimal.ZERO;

        // Aplicar retenciones si el régimen es 601 o 627
        if (solicitud.getTipo() == TipoDocumentoSolicitudEnum.SOLICITUD_DE_FACTURA &&
                (solicitud.getCliente().getRegimenFiscal().equals("601") ||
                        solicitud.getCliente().getRegimenFiscal().equals("627"))) {

            String domicilioFiscal = solicitud.getCliente().getDomicilioFiscal().toLowerCase();
            boolean hasGuanajuato = domicilioFiscal.contains("gto") || domicilioFiscal.contains("guanajuato");
            boolean cpMatch = domicilioFiscal.matches(".*\\b(36|37|38)\\d{4}\\b.*");

            if (cpMatch || hasGuanajuato) {
                isrEstatal = subtotal.multiply(new BigDecimal("0.02"));
                isrFederal = subtotal.multiply(new BigDecimal("0.0125"));
            } else if (!cpMatch && !hasGuanajuato) {
                isrFederal = subtotal.multiply(new BigDecimal("0.0125"));
            }
        }

        BigDecimal total = subtotal.add(iva).subtract(isrEstatal).subtract(isrFederal);

        solicitud.setSubtotal(subtotal);
        solicitud.setIva(iva);
        solicitud.setTotal(total);
        solicitud.setImporteLetra(cotizacionService.convertToLetter(total));

        SolicitudFacturaNota savedSolicitud = solicitudRepository.save(solicitud);
        entityManager.flush();
        entityManager.refresh(savedSolicitud);

        return SolicitudFacturaNotaDTO.fromEntity(savedSolicitud);
    }

    @Transactional
    public SolicitudFacturaNotaDTO actualizarSolicitud(Integer id, SolicitudFacturaNota solicitud) throws Exception {
        logger.info("Actualizando solicitud con ID: {}", id);
        SolicitudFacturaNota existingSolicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada con id: " + id));

        // Actualizar campos básicos
        existingSolicitud.setMetodoPago(solicitud.getMetodoPago());
        existingSolicitud.setFormaPago(solicitud.getFormaPago());
        existingSolicitud.setTipo(solicitud.getTipo());
        existingSolicitud.setClaveProductoServicio(solicitud.getClaveProductoServicio());
        existingSolicitud.setClaveUnidad(solicitud.getClaveUnidad());
        existingSolicitud.setFechaEmision(Date.valueOf(DateUtils.nowInMexico().toLocalDate()));        existingSolicitud.setFechaModificacion(LocalDateTime.now());
        existingSolicitud.setUsoCfdi(solicitud.getUsoCfdi());

        // Validar usoCfdi si el tipo es SOLICITUD_DE_FACTURA
        if (solicitud.getTipo() == TipoDocumentoSolicitudEnum.SOLICITUD_DE_FACTURA) {
            if (solicitud.getUsoCfdi() == null || solicitud.getUsoCfdi().trim().isEmpty()) {
                throw new IllegalArgumentException("El uso de CFDI es obligatorio para solicitudes de factura.");
            }
        }

        // Actualizar relaciones basadas en IDs
        if (solicitud.getEmisor() != null && solicitud.getEmisor().getId() != null) {
            existingSolicitud.setEmisor(emisorRepository.findById(solicitud.getEmisor().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Emisor no encontrado")));
        }
        if (solicitud.getCuentaPorCobrar() != null && solicitud.getCuentaPorCobrar().getId() != null) {
            existingSolicitud.setCuentaPorCobrar(cuentaPorCobrarRepository.findById(solicitud.getCuentaPorCobrar().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cuenta por cobrar no encontrada")));
            if (solicitud.getCliente() == null || solicitud.getCliente().getId() == null) {
                existingSolicitud.setCliente(existingSolicitud.getCuentaPorCobrar().getCliente());
            }
        }
        if (solicitud.getCotizacion() != null && solicitud.getCotizacion().getId() != null) {
            existingSolicitud.setCotizacion(cotizacionRepository.findById(solicitud.getCotizacion().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada")));

            // Usar valores de la cuenta por cobrar actualizada
            BigDecimal subtotal = existingSolicitud.getCuentaPorCobrar().getCantidadCobrar();
            BigDecimal iva = subtotal.multiply(new BigDecimal("0.16"));

            BigDecimal isrEstatal = BigDecimal.ZERO;
            BigDecimal isrFederal = BigDecimal.ZERO;

            // Aplicar retenciones si el régimen es 601 o 627
            if (solicitud.getTipo() == TipoDocumentoSolicitudEnum.SOLICITUD_DE_FACTURA &&
                    (existingSolicitud.getCliente().getRegimenFiscal().equals("601") ||
                            existingSolicitud.getCliente().getRegimenFiscal().equals("627"))) {

                String domicilioFiscal = existingSolicitud.getCliente().getDomicilioFiscal().toLowerCase();
                boolean hasGuanajuato = domicilioFiscal.contains("gto") || domicilioFiscal.contains("guanajuato");
                boolean cpMatch = domicilioFiscal.matches(".*\\b(36|37|38)\\d{4}\\b.*");

                if (cpMatch || hasGuanajuato) {
                    isrEstatal = subtotal.multiply(new BigDecimal("0.02"));
                    isrFederal = subtotal.multiply(new BigDecimal("0.0125"));
                } else if (!cpMatch && !hasGuanajuato) {
                    isrFederal = subtotal.multiply(new BigDecimal("0.0125"));
                }
            }

            BigDecimal total = subtotal.add(iva).subtract(isrEstatal).subtract(isrFederal);

            existingSolicitud.setSubtotal(subtotal);
            existingSolicitud.setIva(iva);
            existingSolicitud.setTotal(total);
            existingSolicitud.setImporteLetra(cotizacionService.convertToLetter(total));
        } else {
            existingSolicitud.setSubtotal(BigDecimal.ZERO);
            existingSolicitud.setIva(BigDecimal.ZERO);
            existingSolicitud.setTotal(BigDecimal.ZERO);
            existingSolicitud.setImporteLetra("");
        }
        if (solicitud.getCliente() != null && solicitud.getCliente().getId() != null) {
            existingSolicitud.setCliente(empresaRepository.findById(solicitud.getCliente().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado")));
        }

        SolicitudFacturaNota savedSolicitud = solicitudRepository.save(existingSolicitud);
        entityManager.flush();
        entityManager.refresh(savedSolicitud);

        return SolicitudFacturaNotaDTO.fromEntity(savedSolicitud);
    }

    @Transactional
    public void eliminarSolicitud(Integer id) {
        logger.info("Eliminando solicitud con ID: {}", id);

        Optional<EstatusPagoEnum> estatusCuenta = cuentaPorCobrarRepository.findEstatusBySolicitudId(id);

        if (estatusCuenta.isPresent() && estatusCuenta.get() == EstatusPagoEnum.PAGADO) {
            throw new IllegalStateException(
                    "No se puede eliminar esta solicitud porque la cuenta por cobrar asociada ya ha sido marcada como pagada."
            );
        }
        SolicitudFacturaNota solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada con id: " + id));
        solicitudRepository.delete(solicitud);
    }

    @Transactional(readOnly = true)
    public List<SolicitudFacturaNotaDTO> listarSolicitudes() {
        logger.info("Listando todas las solicitudes");
        List<SolicitudFacturaNota> solicitudes = solicitudRepository.findAllWithRelations();
        return solicitudes.stream()
                .map(SolicitudFacturaNotaDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SolicitudFacturaNota findById(Integer id) {
        logger.info("Buscando solicitud con ID: {}", id);
        return solicitudRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada con id: " + id));
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
    public ByteArrayResource generateSolicitudPDF(Integer id) throws Exception {
        logger.info("Generando PDF para solicitud con ID: {}", id);

        SolicitudFacturaNota solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada con id: " + id));

        Document document = new Document(PageSize.A4, 40, 40, 90, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        PdfWriter writer = PdfWriter.getInstance(document, out);

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

                membrete = null; // Liberar referencia

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
        Color rojoDestacado = new Color(204, 0, 0);

        Font tituloFont = new Font(Font.HELVETICA, 20, Font.BOLD, azulCorporativo);
        Font subtituloFont = new Font(Font.HELVETICA, 14, Font.BOLD, grisOscuro);
        Font headerTableFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
        Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL, grisOscuro);
        Font boldFont = new Font(Font.HELVETICA, 10, Font.BOLD, grisOscuro);
        Font totalFont = new Font(Font.HELVETICA, 12, Font.BOLD, azulCorporativo);
        Font destacadoFont = new Font(Font.HELVETICA, 10, Font.NORMAL, rojoDestacado);

        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{3f, 1f});
        headerTable.setSpacingAfter(15);

        String tipoDocumento = solicitud.getTipo() == TipoDocumentoSolicitudEnum.SOLICITUD_DE_FACTURA ?
                "SOLICITUD DE FACTURA" : "NOTA DE REMISIÓN";

        PdfPCell titleCell = new PdfPCell(new Phrase(tipoDocumento, tituloFont));
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setPadding(15);

        PdfPCell idCell = new PdfPCell(new Phrase("ID: " + solicitud.getIdentificador(), subtituloFont));
        idCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        idCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        idCell.setBorder(Rectangle.NO_BORDER);
        idCell.setPadding(15);

        headerTable.addCell(titleCell);
        headerTable.addCell(idCell);

        document.add(headerTable);

        PdfPTable infoEmisorTable = new PdfPTable(1);
        infoEmisorTable.setWidthPercentage(100);
        infoEmisorTable.setSpacingAfter(20);

        Emisor emisor = solicitud.getEmisor();
        String fechaFormateada = solicitud.getFechaEmision().toLocalDate()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        Paragraph emisorInfo = new Paragraph();
        emisorInfo.add(new Chunk("EMISOR: ", boldFont));
        emisorInfo.add(new Chunk(emisor.getNombre(), normalFont));
        emisorInfo.add(new Chunk("     RFC: ", boldFont));
        emisorInfo.add(new Chunk(emisor.getRfc(), normalFont));
        emisorInfo.add(new Chunk("     FECHA DE EMISIÓN: ", boldFont));
        emisorInfo.add(new Chunk(fechaFormateada, normalFont));

        PdfPCell emisorCell = new PdfPCell(emisorInfo);
        emisorCell.setBorder(Rectangle.BOX);
        emisorCell.setBorderColor(azulCorporativo);
        emisorCell.setBorderWidth(1f);
        emisorCell.setPadding(12);
        emisorCell.setBackgroundColor(azulClaro);

        infoEmisorTable.addCell(emisorCell);
        document.add(infoEmisorTable);

        PdfPTable receptorTable = new PdfPTable(1);
        receptorTable.setWidthPercentage(100);
        receptorTable.setSpacingAfter(20);

        PdfPCell receptorTitleCell = new PdfPCell(new Phrase("RECEPTOR", headerTableFont));
        receptorTitleCell.setBackgroundColor(azulCorporativo);
        receptorTitleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        receptorTitleCell.setPadding(10);
        receptorTitleCell.setBorder(Rectangle.BOX);
        receptorTable.addCell(receptorTitleCell);

        String receptorData;
        if (solicitud.getTipo() == TipoDocumentoSolicitudEnum.SOLICITUD_DE_FACTURA) {
            Empresa cliente = solicitud.getCliente();
            receptorData = buildReceptorDataFactura(cliente, solicitud);
        } else {
            CuentaPorCobrar cuenta = solicitud.getCuentaPorCobrar();
            receptorData = buildReceptorDataNota(cuenta, solicitud);
        }

        Paragraph receptorContent = createFormattedReceptorContent(receptorData, destacadoFont, boldFont);
        PdfPCell receptorContentCell = new PdfPCell(receptorContent);
        receptorContentCell.setPadding(15);
        receptorContentCell.setBorder(Rectangle.BOX);
        receptorContentCell.setBorderColor(azulCorporativo);
        receptorContentCell.setBorderWidth(1f);
        receptorContentCell.setBackgroundColor(blancoHueso);
        receptorTable.addCell(receptorContentCell);

        document.add(receptorTable);

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

        CuentaPorCobrar cuentaReal = solicitud.getCuentaPorCobrar();
        org.hibernate.Hibernate.initialize(cuentaReal.getConceptos());
        List<ConceptoCuenta> conceptosAMostrar = cuentaReal.getConceptos();

        boolean isEvenRow = false;

        if (conceptosAMostrar != null && !conceptosAMostrar.isEmpty()) {
            for (ConceptoCuenta concepto : conceptosAMostrar) {
                Color rowColor = isEvenRow ? blancoHueso : Color.WHITE;

                // Celda: Cantidad
                conceptosTable.addCell(createStyledTableCell(
                        String.valueOf(concepto.getCantidad()),
                        Element.ALIGN_CENTER, normalFont, rowColor));

                // Celda: Unidad
                conceptosTable.addCell(createStyledTableCell(
                        concepto.getUnidad(),
                        Element.ALIGN_CENTER, normalFont, rowColor));

                // Celda: Concepto (Nombre del servicio/producto)
                PdfPCell conceptoCell = new PdfPCell(new Phrase(concepto.getConcepto(), normalFont));
                conceptoCell.setHorizontalAlignment(Element.ALIGN_LEFT);
                conceptoCell.setPadding(8);
                conceptoCell.setBackgroundColor(rowColor);
                conceptoCell.setBorder(Rectangle.BOX);
                conceptoCell.setBorderColor(grisMedio);
                conceptoCell.setBorderWidth(0.5f);
                conceptosTable.addCell(conceptoCell);

                // Celda: Precio Unitario
                conceptosTable.addCell(createStyledTableCell(
                        formatCurrency(concepto.getPrecioUnitario()),
                        Element.ALIGN_RIGHT, normalFont, rowColor));

                // Celda: Descuento (Calculado en base al porcentaje guardado)
                BigDecimal descPorcentaje = concepto.getDescuento() != null ? concepto.getDescuento() : BigDecimal.ZERO;
                BigDecimal montoDescuentoTotal = concepto.getPrecioUnitario()
                        .multiply(new BigDecimal(concepto.getCantidad()))
                        .multiply(descPorcentaje.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP));

                conceptosTable.addCell(createStyledTableCell(
                        formatCurrency(montoDescuentoTotal),
                        Element.ALIGN_RIGHT, normalFont, rowColor));

                // Celda: Importe (Total del renglón)
                conceptosTable.addCell(createStyledTableCell(
                        formatCurrency(concepto.getImporteTotal()),
                        Element.ALIGN_RIGHT, boldFont, rowColor));

                isEvenRow = !isEvenRow;
            }
        }

        document.add(conceptosTable);

        createTotalsSection(document, solicitud, solicitud.getCotizacion(), azulCorporativo, azulClaro, blancoHueso,
                normalFont, boldFont, totalFont, headerTableFont, rojoDestacado);

        document.close();

        byte[] pdfBytes = out.toByteArray();
        out.close();
        out = null;

        if (pdfBytes.length > 1024 * 1024) {
            System.gc();
        }

        return new ByteArrayResource(pdfBytes);
    }

    private String buildReceptorDataFactura(Empresa cliente, SolicitudFacturaNota solicitud) {
        String regimenFiscalDescripcion = REGIMEN_FISCAL.getOrDefault(cliente.getRegimenFiscal(), cliente.getRegimenFiscal());
        return "Razón Social|" + cliente.getRazonSocial() + "|" +
                "Domicilio Fiscal|" + cliente.getDomicilioFiscal() + "|" +
                "RFC|" + cliente.getRfc() + "|" +
                "Régimen Fiscal|" + regimenFiscalDescripcion + "|" +
                "Método de Pago|" + METODOS_PAGO.getOrDefault(solicitud.getMetodoPago(), solicitud.getMetodoPago()) + "|" +
                "Forma de Pago|" + FORMAS_PAGO.getOrDefault(solicitud.getFormaPago(), solicitud.getFormaPago()) + "|" +
                "Uso CFDI|" + USOS_CFDI.getOrDefault(solicitud.getUsoCfdi(), solicitud.getUsoCfdi()) + "|" +
                "Clave Prod/Serv|" + CLAVES_PRODUCTO_SERVICIO.getOrDefault(solicitud.getClaveProductoServicio(), solicitud.getClaveProductoServicio()) + "|" +
                "Clave de Unidad|" + CLAVES_UNIDAD.getOrDefault(solicitud.getClaveUnidad(), solicitud.getClaveUnidad());
    }

    private String buildReceptorDataNota(CuentaPorCobrar cuenta, SolicitudFacturaNota solicitud) {
        return "Nombre del Cliente|" + cuenta.getCliente().getNombre() + "|" +
                "Domicilio|" + cuenta.getCliente().getDomicilioFisico() + "|" +
                "Método de Pago|" + METODOS_PAGO.getOrDefault(solicitud.getMetodoPago(), solicitud.getMetodoPago()) + "|" +
                "Forma de Pago|" + FORMAS_PAGO.getOrDefault(solicitud.getFormaPago(), solicitud.getFormaPago()) + "|" +
                "Clave Prod/Serv|" + CLAVES_PRODUCTO_SERVICIO.getOrDefault(solicitud.getClaveProductoServicio(), solicitud.getClaveProductoServicio()) + "|" +
                "Clave de Unidad|" + CLAVES_UNIDAD.getOrDefault(solicitud.getClaveUnidad(), solicitud.getClaveUnidad());
    }

    private Paragraph createFormattedReceptorContent(String data, Font destacadoFont, Font boldFont) {
        Paragraph paragraph = new Paragraph();
        paragraph.setLeading(16);

        String[] pairs = data.split("\\|");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i + 1 < pairs.length) {
                Chunk title = new Chunk(pairs[i] + ": ", boldFont);
                paragraph.add(title);
                Chunk value = new Chunk(pairs[i + 1], destacadoFont);
                paragraph.add(value);
                paragraph.add(Chunk.NEWLINE);
            }
        }
        return paragraph;
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
        cell.setNoWrap(true);
        cell.setMinimumHeight(20f);
        return cell;
    }

    private void createTotalsSection(Document document, SolicitudFacturaNota solicitud, Cotizacion cotizacion,
                                     Color azulCorporativo, Color azulClaro, Color blancoHueso,
                                     Font normalFont, Font boldFont, Font totalFont, Font headerTableFont,
                                     Color rojoDestacado) throws DocumentException {

        PdfPTable totalsTable = new PdfPTable(2);
        totalsTable.setWidthPercentage(100);
        totalsTable.setWidths(new float[]{3f, 2f});
        totalsTable.setSpacingAfter(20);

        PdfPTable leftTable = new PdfPTable(1);
        leftTable.setWidthPercentage(100);

        PdfPCell importeLetraHeaderCell = new PdfPCell(new Phrase("IMPORTE CON LETRA", headerTableFont));
        importeLetraHeaderCell.setBackgroundColor(azulCorporativo);
        importeLetraHeaderCell.setPadding(10);
        importeLetraHeaderCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        importeLetraHeaderCell.setBorder(Rectangle.BOX);
        leftTable.addCell(importeLetraHeaderCell);

        PdfPCell importeLetraContentCell = new PdfPCell(new Phrase(solicitud.getImporteLetra(), boldFont));
        importeLetraContentCell.setPadding(15);
        importeLetraContentCell.setMinimumHeight(60);
        importeLetraContentCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        importeLetraContentCell.setBackgroundColor(blancoHueso);
        importeLetraContentCell.setBorder(Rectangle.BOX);
        importeLetraContentCell.setBorderColor(azulCorporativo);
        leftTable.addCell(importeLetraContentCell);

        PdfPTable rightTable = new PdfPTable(2);
        rightTable.setWidthPercentage(100);

        PdfPCell totalsHeaderCell = new PdfPCell(new Phrase("TOTALES", headerTableFont));
        totalsHeaderCell.setBackgroundColor(azulCorporativo);
        totalsHeaderCell.setPadding(10);
        totalsHeaderCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalsHeaderCell.setColspan(2);
        totalsHeaderCell.setBorder(Rectangle.BOX);
        rightTable.addCell(totalsHeaderCell);

        if (solicitud.getTipo() == TipoDocumentoSolicitudEnum.NOTA) {
            addTotalRow(rightTable, "Total:", formatCurrency(solicitud.getSubtotal()),
                    totalFont, totalFont, azulClaro);
        } else {
            addTotalRow(rightTable, "Subtotal:", formatCurrency(solicitud.getSubtotal()),
                    normalFont, boldFont, Color.WHITE);

            addTotalRow(rightTable, "IVA (16%):", formatCurrency(solicitud.getIva()),
                    normalFont, boldFont, Color.WHITE);

            // Aplicar retenciones si el régimen es 601 o 627
            if (solicitud.getTipo() == TipoDocumentoSolicitudEnum.SOLICITUD_DE_FACTURA &&
                    (solicitud.getCliente().getRegimenFiscal().equals("601") ||
                            solicitud.getCliente().getRegimenFiscal().equals("627"))) {

                String domicilioFiscal = solicitud.getCliente().getDomicilioFiscal().toLowerCase();
                boolean hasGuanajuato = domicilioFiscal.contains("gto") || domicilioFiscal.contains("guanajuato");
                boolean cpMatch = domicilioFiscal.matches(".*\\b(36|37|38)\\d{4}\\b.*");

                if (cpMatch || hasGuanajuato) {
                    BigDecimal isrEstatal = solicitud.getSubtotal().multiply(new BigDecimal("0.02"));
                    BigDecimal isrFederal = solicitud.getSubtotal().multiply(new BigDecimal("0.0125"));

                    if (isrFederal.compareTo(BigDecimal.ZERO) > 0) {
                        addTotalRow(rightTable, "ISR Federal (1.25%):", formatCurrency(isrFederal),
                                normalFont, boldFont, Color.WHITE);
                    }

                    if (isrEstatal.compareTo(BigDecimal.ZERO) > 0) {
                        addTotalRow(rightTable, "ISR Estatal (2%):", formatCurrency(isrEstatal),
                                normalFont, boldFont, Color.WHITE);
                    }
                } else if (!cpMatch && !hasGuanajuato) {
                    BigDecimal isrFederal = solicitud.getSubtotal().multiply(new BigDecimal("0.0125"));

                    if (isrFederal.compareTo(BigDecimal.ZERO) > 0) {
                        addTotalRow(rightTable, "ISR Federal (1.25%):", formatCurrency(isrFederal),
                                normalFont, boldFont, Color.WHITE);
                    }
                }
            }


            PdfPCell separatorCell = new PdfPCell();
            separatorCell.setColspan(2);
            separatorCell.setBorder(Rectangle.TOP);
            separatorCell.setBorderColor(azulCorporativo);
            separatorCell.setBorderWidth(2f);
            separatorCell.setFixedHeight(5);
            rightTable.addCell(separatorCell);

            addTotalRow(rightTable, "TOTAL:", formatCurrency(solicitud.getTotal()),
                    totalFont, totalFont, azulClaro);
        }

        PdfPCell leftCell = new PdfPCell(leftTable);
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPaddingRight(10);

        PdfPCell rightCell = new PdfPCell(rightTable);
        rightCell.setBorder(Rectangle.NO_BORDER);

        totalsTable.addCell(leftCell);
        totalsTable.addCell(rightCell);

        document.add(totalsTable);
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

    // Métodos para Factura
    @Transactional
    public FacturaDTO timbrarFactura(Factura factura, MultipartFile archivo) throws Exception {
        logger.info("Timbrando factura para solicitud: {}", factura.getNoSolicitud());

        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo de la factura es requerido");
        }

        if (facturaRepository.findByNoSolicitud(factura.getNoSolicitud()).isPresent()) {
            throw new IllegalStateException("Esta solicitud ya ha sido timbrada previamente");
        }

        String nombreOriginal = archivo.getOriginalFilename();
        String extension = nombreOriginal != null && nombreOriginal.contains(".")
                ? nombreOriginal.substring(nombreOriginal.lastIndexOf("."))
                : ".pdf";
        String nombreArchivo = "factura_" + factura.getNoSolicitud() + "_" + System.currentTimeMillis() + extension;

        Cloudinary cloudinary = cloudinaryConfig.cloudinary();

        Map<String, Object> uploadParams = new HashMap<>();
        uploadParams.put("resource_type", "raw");
        uploadParams.put("public_id", "facturas/" + nombreArchivo.replace(extension, ""));
        uploadParams.put("overwrite", false);

        Map uploadResult = cloudinary.uploader().upload(archivo.getBytes(), uploadParams);

        factura.setArchivoUrl(uploadResult.get("url").toString());
        factura.setNombreArchivoOriginal(nombreOriginal != null ? nombreOriginal : "factura.pdf");

        SolicitudFacturaNota solicitud = solicitudRepository.findByIdentificador(factura.getNoSolicitud())
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada con identificador: " + factura.getNoSolicitud()));

        factura.setSolicitudId(solicitud.getId());
        Factura savedFactura = facturaRepository.save(factura);

        entityManager.flush();
        entityManager.refresh(savedFactura);

        if (savedFactura.getSolicitud() != null) {
            Hibernate.initialize(savedFactura.getSolicitud().getCliente());
            Hibernate.initialize(savedFactura.getSolicitud().getEmisor());
            Hibernate.initialize(savedFactura.getSolicitud().getCuentaPorCobrar());
        }

        logger.info("Factura timbrada exitosamente con ID: {}", savedFactura.getId());
        return FacturaDTO.fromEntity(savedFactura);
    }

    @Transactional(readOnly = true)
    public List<FacturaDTO> listarFacturas() {
        logger.info("Listando todas las facturas");
        List<Factura> facturas = facturaRepository.findAll();
        return facturas.stream()
                .map(FacturaDTO::fromEntity)
                .collect(Collectors.toList());
    }

    private void validateEmpresaFiscal(Empresa empresa) {
        if (empresa.getDomicilioFiscal() == null || empresa.getRfc() == null ||
                empresa.getRazonSocial() == null || empresa.getRegimenFiscal() == null) {
            throw new ResourceNotFoundException("No se puede generar la Solicitud de Factura porque la empresa no tiene completos los datos fiscales requeridos (Domicilio Fiscal, RFC, Razón Social, Régimen Fiscal). Por favor, edite los datos de la empresa.");
        }
    }

    private List<UnidadCotizacion> filtrarUnidadesPorConceptos(Cotizacion cotizacion, String conceptosSeleccionados) {
        if (conceptosSeleccionados == null || conceptosSeleccionados.trim().isEmpty()) {
            return cotizacion.getUnidades();
        }

        List<String> conceptos = List.of(conceptosSeleccionados.split(", "));

        // Debug logging
        logger.info("Conceptos a filtrar: {}", conceptos);
        logger.info("Conceptos disponibles en cotización:");
        cotizacion.getUnidades().forEach(u ->
                logger.info("  - '{}'", u.getConcepto())
        );

        List<UnidadCotizacion> unidadesFiltradas = cotizacion.getUnidades().stream()
                .filter(unidad -> conceptos.stream()
                        .anyMatch(concepto -> {
                            boolean match = unidad.getConcepto().trim().equalsIgnoreCase(concepto.trim()) ||
                                    unidad.getConcepto().toLowerCase().contains(concepto.toLowerCase().trim()) ||
                                    concepto.toLowerCase().contains(unidad.getConcepto().toLowerCase().trim());
                            if (match) {
                                logger.info("Match encontrado: '{}' <-> '{}'", unidad.getConcepto(), concepto);
                            }
                            return match;
                        }))
                .collect(Collectors.toList());

        logger.info("Unidades filtradas: {}", unidadesFiltradas.size());

        // Si no se encontró ninguna coincidencia, devolver todas las unidades
        if (unidadesFiltradas.isEmpty()) {
            logger.warn("No se encontraron coincidencias exactas, devolviendo todas las unidades");
            return cotizacion.getUnidades();
        }

        return unidadesFiltradas;
    }

    private void addConceptosPersonalizados(PdfPTable conceptosTable, SolicitudFacturaNota solicitud,
                                            Color blancoHueso, Font normalFont, Font boldFont, Color grisMedio,
                                            String conceptosTexto) {
        if (conceptosTexto == null || conceptosTexto.trim().isEmpty()) return;

        String[] conceptos = conceptosTexto.split(",\\s*");
        logger.info("Procesando {} conceptos personalizados", conceptos.length);

        Map<String, UnidadCotizacion> unidadesOriginales = new HashMap<>();
        if (solicitud.getCotizacion() != null && solicitud.getCotizacion().getUnidades() != null) {
            for (UnidadCotizacion unidad : solicitud.getCotizacion().getUnidades()) {
                String key = unidad.getConcepto().toLowerCase().trim();
                unidadesOriginales.put(key, unidad);
            }
        }

        BigDecimal subtotalSolicitud = solicitud.getSubtotal();
        BigDecimal dineroAsignado = BigDecimal.ZERO;

        boolean isEvenRow = false;

        for (int i = 0; i < conceptos.length; i++) {
            String conceptoLimpio = conceptos[i].trim();
            if (conceptoLimpio.isEmpty()) continue;

            Color rowColor = isEvenRow ? blancoHueso : Color.WHITE;

            BigDecimal precioUnitarioFinal = BigDecimal.ZERO;
            BigDecimal importeFinal = BigDecimal.ZERO;
            BigDecimal descuentoMonto = BigDecimal.ZERO;
            BigDecimal cantidad = BigDecimal.ONE;
            String unidadMedida = "Servicio";

            UnidadCotizacion unidadMatch = unidadesOriginales.get(conceptoLimpio.toLowerCase());

            if (unidadMatch == null) {
                for (Map.Entry<String, UnidadCotizacion> entry : unidadesOriginales.entrySet()) {
                    String keyBD = entry.getKey();
                    if (keyBD.contains(conceptoLimpio.toLowerCase()) || conceptoLimpio.toLowerCase().contains(keyBD)) {
                        unidadMatch = entry.getValue();
                        break;
                    }
                }
            }

            if (unidadMatch == null && conceptos.length == 1 && !unidadesOriginales.isEmpty()) {
                unidadMatch = unidadesOriginales.values().iterator().next();
            }

            if (unidadMatch != null) {
                cantidad = new BigDecimal(unidadMatch.getCantidad());
                unidadMedida = unidadMatch.getUnidad();
                precioUnitarioFinal = unidadMatch.getPrecioUnitario();

                BigDecimal porcentajeDescuento = unidadMatch.getDescuento() != null ? unidadMatch.getDescuento() : BigDecimal.ZERO;

                BigDecimal importeBruto = precioUnitarioFinal.multiply(cantidad);

                if (porcentajeDescuento.compareTo(BigDecimal.ZERO) > 0) {
                    descuentoMonto = importeBruto.multiply(porcentajeDescuento.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                }

                importeFinal = unidadMatch.getImporteTotal();

            } else {
                if (conceptos.length == 1) {
                    precioUnitarioFinal = subtotalSolicitud;
                    importeFinal = subtotalSolicitud;
                } else if (i == conceptos.length - 1) {
                    BigDecimal restante = subtotalSolicitud.subtract(dineroAsignado);
                    if (restante.compareTo(BigDecimal.ZERO) > 0) {
                        precioUnitarioFinal = restante;
                        importeFinal = restante;
                    }
                }
            }

            dineroAsignado = dineroAsignado.add(importeFinal);

            conceptosTable.addCell(createStyledTableCell(
                    String.valueOf(cantidad), Element.ALIGN_CENTER, normalFont, rowColor));

            conceptosTable.addCell(createStyledTableCell(
                    unidadMedida, Element.ALIGN_CENTER, normalFont, rowColor));

            PdfPCell conceptoCell = new PdfPCell(new Phrase(conceptoLimpio, normalFont));
            conceptoCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            conceptoCell.setVerticalAlignment(Element.ALIGN_TOP);
            conceptoCell.setPadding(8);
            conceptoCell.setBackgroundColor(rowColor);
            conceptoCell.setBorder(Rectangle.BOX);
            conceptoCell.setBorderColor(grisMedio);
            conceptoCell.setBorderWidth(0.5f);
            conceptosTable.addCell(conceptoCell);

            conceptosTable.addCell(createStyledTableCell(
                    formatCurrency(precioUnitarioFinal), Element.ALIGN_RIGHT, normalFont, rowColor));

            conceptosTable.addCell(createStyledTableCell(
                    formatCurrency(descuentoMonto), Element.ALIGN_RIGHT, normalFont, rowColor));

            conceptosTable.addCell(createStyledTableCell(
                    formatCurrency(importeFinal), Element.ALIGN_RIGHT, boldFont, rowColor));

            isEvenRow = !isEvenRow;
        }
    }

    private void recalcularTotalesConConceptosFiltrados(SolicitudFacturaNota solicitud,
                                                        List<UnidadCotizacion> unidadesFiltradas) {
        BigDecimal subtotal = unidadesFiltradas.stream()
                .map(UnidadCotizacion::getImporteTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal iva = subtotal.multiply(new BigDecimal("0.16"));
        BigDecimal isrEstatal = BigDecimal.ZERO;
        BigDecimal isrFederal = BigDecimal.ZERO;

        // Aplicar retenciones si el régimen es 601 o 627
        if (solicitud.getTipo() == TipoDocumentoSolicitudEnum.SOLICITUD_DE_FACTURA &&
                (solicitud.getCliente().getRegimenFiscal().equals("601") ||
                        solicitud.getCliente().getRegimenFiscal().equals("627"))) {

            String domicilioFiscal = solicitud.getCliente().getDomicilioFiscal().toLowerCase();
            boolean hasGuanajuato = domicilioFiscal.contains("gto") || domicilioFiscal.contains("guanajuato");
            boolean cpMatch = domicilioFiscal.matches(".*\\b(36|37|38)\\d{4}\\b.*");

            if (cpMatch || hasGuanajuato) {
                isrEstatal = subtotal.multiply(new BigDecimal("0.02"));
                isrFederal = subtotal.multiply(new BigDecimal("0.0125"));
            } else if (!cpMatch && !hasGuanajuato) {
                isrFederal = subtotal.multiply(new BigDecimal("0.0125"));
            }
        }

        BigDecimal total = subtotal.add(iva).subtract(isrEstatal).subtract(isrFederal);

        solicitud.setSubtotal(subtotal);
        solicitud.setIva(iva);
        solicitud.setTotal(total);
        solicitud.setImporteLetra(cotizacionService.convertToLetter(total));
    }

    @Transactional
    public void actualizarConceptosPersonalizados(Integer id, String conceptosPersonalizados) {
        logger.info("Actualizando conceptos personalizados para solicitud con ID: {}", id);
        SolicitudFacturaNota solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada con id: " + id));

        solicitud.setConceptosPersonalizados(conceptosPersonalizados);
        solicitud.setFechaModificacion(LocalDateTime.now());

        solicitudRepository.save(solicitud);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenerConceptosSolicitud(Integer solicitudId) {
        logger.info("Obteniendo conceptos para solicitud con ID: {}", solicitudId);

        SolicitudFacturaNota solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada con id: " + solicitudId));

        Map<String, Object> response = new HashMap<>();

        // Obtener conceptos de la cotización vinculada
        List<String> conceptosCotizacion = new ArrayList<>();
        if (solicitud.getCotizacion() != null) {
            // Forzar la inicialización de la colección lazy
            Hibernate.initialize(solicitud.getCotizacion().getUnidades());
            if (solicitud.getCotizacion().getUnidades() != null) {
                conceptosCotizacion = solicitud.getCotizacion().getUnidades().stream()
                        .map(UnidadCotizacion::getConcepto)
                        .collect(Collectors.toList());
            }
        }

        response.put("conceptosCotizacion", conceptosCotizacion);
        response.put("conceptosSeleccionados", solicitud.getConceptosSeleccionados());
        response.put("conceptosPersonalizados", solicitud.getConceptosPersonalizados());

        return response;
    }
}