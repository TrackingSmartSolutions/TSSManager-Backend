package com.tss.tssmanager_backend.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.tss.tssmanager_backend.dto.CotizacionDTO;
import com.tss.tssmanager_backend.dto.EmpresaDTO;
import com.tss.tssmanager_backend.dto.UnidadCotizacionDTO;
import com.tss.tssmanager_backend.entity.Cotizacion;
import com.tss.tssmanager_backend.entity.Empresa;
import com.tss.tssmanager_backend.entity.UnidadCotizacion;
import com.tss.tssmanager_backend.exception.ResourceNotFoundException;
import com.tss.tssmanager_backend.repository.CotizacionRepository;
import com.tss.tssmanager_backend.repository.CuentaPorCobrarRepository;
import com.tss.tssmanager_backend.repository.EmpresaRepository;
import com.tss.tssmanager_backend.repository.UnidadCotizacionRepository;
import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
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

        BigDecimal subtotal = cotizacionDTO.getUnidades().stream()
                .map(u -> {
                    BigDecimal descuentoPorcentaje = u.getDescuento().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
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
            BigDecimal descuentoPorcentaje = u.getDescuento().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            BigDecimal descuentoMonto = u.getPrecioUnitario().multiply(descuentoPorcentaje).multiply(new BigDecimal(u.getCantidad()));
            unidad.setDescuento(u.getDescuento());
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

        BigDecimal subtotal = cotizacionDTO.getUnidades().stream()
                .map(u -> {
                    BigDecimal descuentoPorcentaje = u.getDescuento().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
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
            BigDecimal descuentoPorcentaje = u.getDescuento().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            BigDecimal descuentoMonto = u.getPrecioUnitario().multiply(descuentoPorcentaje).multiply(new BigDecimal(u.getCantidad()));
            unidad.setDescuento(u.getDescuento());
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
        return cotizacionRepository.findAll().stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private CotizacionDTO convertToDTO(Cotizacion cotizacion) {
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
        dto.setCantidadTotal(cotizacion.getUnidades().stream().mapToInt(UnidadCotizacion::getCantidad).sum());
        dto.setConceptosCount((int) cotizacion.getUnidades().stream().map(UnidadCotizacion::getConcepto).distinct().count());
        dto.setEmpresaData(empresaService.convertToEmpresaDTO(cotizacion.getCliente()));
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

    @Transactional(readOnly = true)
    public ByteArrayResource generateCotizacionPDF(Integer id) throws Exception {
        logger.info("Generando PDF para cotización con ID: {}", id);

        Cotizacion cotizacion = cotizacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada con id: " + id));

        Document document = new Document(PageSize.A4, 40, 40, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, out);

        document.open();

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
        headerTable.setSpacingAfter(25);

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
        titleCell.setBackgroundColor(blancoHueso);
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
                normalFont, boldFont, Color.WHITE);

        addTotalRow(totalsTable, "IVA (16%):", formatCurrency(cotizacion.getIva()),
                normalFont, boldFont, Color.WHITE);

        PdfPCell separatorCell = new PdfPCell();
        separatorCell.setColspan(2);
        separatorCell.setBorder(Rectangle.TOP);
        separatorCell.setBorderColor(azulCorporativo);
        separatorCell.setBorderWidth(2f);
        separatorCell.setFixedHeight(5);
        totalsTable.addCell(separatorCell);

        addTotalRow(totalsTable, "TOTAL:", formatCurrency(cotizacion.getTotal()),
                totalFont, totalFont, azulClaro);

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
        return new ByteArrayResource(out.toByteArray());
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

}