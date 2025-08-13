package com.tss.tssmanager_backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.tss.tssmanager_backend.dto.ReporteCuentasPorPagarDTO;
import com.tss.tssmanager_backend.dto.ReporteCuentasPorPagarDTO.CuentaReporteDTO;
import com.tss.tssmanager_backend.entity.CuentaPorPagar;
import com.tss.tssmanager_backend.repository.CuentaPorPagarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReporteCuentasPorPagarService {

    @Autowired
    private CuentaPorPagarRepository cuentaPorPagarRepository;

    private static final Color PRIMARY_BLUE = new Color(30, 60, 114);
    private static final Color ACCENT_BLUE = new Color(65, 131, 215);
    private static final Color SUCCESS_GREEN = new Color(34, 139, 34);
    private static final Color LIGHT_GRAY = new Color(248, 249, 250);
    private static final Color BORDER_GRAY = new Color(222, 226, 230);
    private static final Color TEXT_DARK = new Color(33, 37, 41);

    private static final Map<String, String> FORMAS_PAGO = Map.of(
            "01", "Efectivo",
            "03", "Transferencia electrónica de fondos",
            "04", "Tarjeta de crédito",
            "07", "Con Saldo Acumulado",
            "28", "Tarjeta de débito",
            "30", "Aplicación de anticipos",
            "99", "Por definir",
            "02", "Tarjeta Spin"
    );

    public ReporteCuentasPorPagarDTO generarDatosReporte(LocalDate fechaInicio, LocalDate fechaFin, String filtroEstatus) {
        List<CuentaPorPagar> cuentas = obtenerCuentasFiltradas(fechaInicio, fechaFin, filtroEstatus);

        ReporteCuentasPorPagarDTO reporte = new ReporteCuentasPorPagarDTO(fechaInicio, fechaFin, filtroEstatus);

        BigDecimal montoTotal = cuentas.stream()
                .map(CuentaPorPagar::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        reporte.setMontoTotal(montoTotal);

        Map<LocalDate, BigDecimal> montoPorDia = cuentas.stream()
                .collect(Collectors.groupingBy(
                        CuentaPorPagar::getFechaPago,
                        Collectors.reducing(BigDecimal.ZERO, CuentaPorPagar::getMonto, BigDecimal::add)
                ));
        reporte.setMontoPorDia(montoPorDia);

        Map<LocalDate, List<CuentaReporteDTO>> cuentasPorDia = cuentas.stream()
                .collect(Collectors.groupingBy(
                        CuentaPorPagar::getFechaPago,
                        Collectors.mapping(this::convertirACuentaReporte, Collectors.toList())
                ));
        reporte.setCuentasPorDia(cuentasPorDia);

        return reporte;
    }

    private List<CuentaPorPagar> obtenerCuentasFiltradas(LocalDate fechaInicio, LocalDate fechaFin, String filtroEstatus) {
        if ("Todas".equals(filtroEstatus)) {
            return cuentaPorPagarRepository.findByFechaPagoBetween(fechaInicio, fechaFin);
        } else {
            return cuentaPorPagarRepository.findByFechaPagoBetween(fechaInicio, fechaFin)
                    .stream()
                    .filter(cuenta -> filtroEstatus.equals(cuenta.getEstatus()))
                    .collect(Collectors.toList());
        }
    }

    private CuentaReporteDTO convertirACuentaReporte(CuentaPorPagar cuenta) {
        String numeroSim = cuenta.getSim() != null ? cuenta.getSim().getNumero() : "-";
        String categoria = cuenta.getTransaccion() != null && cuenta.getTransaccion().getCategoria() != null
                ? cuenta.getTransaccion().getCategoria().getDescripcion() : "-";
        String folioCompleto = cuenta.getFolio() + (cuenta.getSim() != null ? " -" + cuenta.getSim().getId() : "");

        return new CuentaReporteDTO(
                folioCompleto,
                cuenta.getCuenta().getNombre(),
                cuenta.getMonto(),
                FORMAS_PAGO.getOrDefault(cuenta.getFormaPago(), cuenta.getFormaPago()),
                cuenta.getEstatus(),
                categoria,
                numeroSim
        );
    }

    public byte[] generarReportePDF(ReporteCuentasPorPagarDTO datosReporte) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 30, 30, 40, 40);
        PdfWriter writer = PdfWriter.getInstance(document, baos);

        document.open();

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, PRIMARY_BLUE);
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 12, new Color(108, 117, 125));
        Font sectionTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, PRIMARY_BLUE);
        Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_DARK);
        Font moneyFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, SUCCESS_GREEN);

        addModernHeader(document, datosReporte, dateFormatter, currencyFormat,
                titleFont, subtitleFont, moneyFont);

        addResumenEjecutivo(document, datosReporte, dateFormatter, currencyFormat,
                sectionTitleFont, tableHeaderFont, normalFont);

        addDesgloseDia(document, datosReporte, dateFormatter, currencyFormat,
                sectionTitleFont, tableHeaderFont, normalFont);

        addSimpleFooter(document, normalFont);

        document.close();
        return baos.toByteArray();
    }

    private void addModernHeader(Document document, ReporteCuentasPorPagarDTO datosReporte,
                                 DateTimeFormatter dateFormatter, NumberFormat currencyFormat,
                                 Font titleFont, Font subtitleFont, Font moneyFont) throws DocumentException {

        // Título principal
        Paragraph title = new Paragraph("Reporte de Cuentas por Pagar", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(5);
        document.add(title);

        Paragraph periodo = new Paragraph(String.format("Del %s al %s",
                datosReporte.getFechaInicio().format(dateFormatter),
                datosReporte.getFechaFin().format(dateFormatter)), subtitleFont);
        periodo.setAlignment(Element.ALIGN_CENTER);
        periodo.setSpacingAfter(20);
        document.add(periodo);

        PdfPTable infoCard = new PdfPTable(3);
        infoCard.setWidthPercentage(100);
        infoCard.setWidths(new float[]{33, 34, 33});
        infoCard.setSpacingAfter(25);

        // Total
        PdfPCell totalCell = new PdfPCell();
        totalCell.setBorder(Rectangle.BOX);
        totalCell.setBorderColor(BORDER_GRAY);
        totalCell.setBackgroundColor(Color.WHITE);
        totalCell.setPadding(15);
        Paragraph totalP = new Paragraph();
        totalP.add(new Phrase("MONTO TOTAL\n", FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(108, 117, 125))));
        totalP.add(new Phrase(currencyFormat.format(datosReporte.getMontoTotal()), moneyFont));
        totalP.setAlignment(Element.ALIGN_CENTER);
        totalCell.addElement(totalP);
        infoCard.addCell(totalCell);

        // Filtro
        PdfPCell filtroCell = new PdfPCell();
        filtroCell.setBorder(Rectangle.BOX);
        filtroCell.setBorderColor(BORDER_GRAY);
        filtroCell.setBackgroundColor(LIGHT_GRAY);
        filtroCell.setPadding(15);
        Paragraph filtroP = new Paragraph();
        filtroP.add(new Phrase("FILTRO APLICADO\n", FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(108, 117, 125))));
        filtroP.add(new Phrase(datosReporte.getFiltroEstatus(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, TEXT_DARK)));
        filtroP.setAlignment(Element.ALIGN_CENTER);
        filtroCell.addElement(filtroP);
        infoCard.addCell(filtroCell);

        // Días
        PdfPCell diasCell = new PdfPCell();
        diasCell.setBorder(Rectangle.BOX);
        diasCell.setBorderColor(BORDER_GRAY);
        diasCell.setBackgroundColor(Color.WHITE);
        diasCell.setPadding(15);
        Paragraph diasP = new Paragraph();
        diasP.add(new Phrase("DÍAS CON MOVIMIENTOS\n", FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(108, 117, 125))));
        diasP.add(new Phrase(String.valueOf(datosReporte.getMontoPorDia().size()),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, ACCENT_BLUE)));
        diasP.setAlignment(Element.ALIGN_CENTER);
        diasCell.addElement(diasP);
        infoCard.addCell(diasCell);

        document.add(infoCard);
    }

    private void addResumenEjecutivo(Document document, ReporteCuentasPorPagarDTO datosReporte,
                                     DateTimeFormatter dateFormatter, NumberFormat currencyFormat,
                                     Font sectionTitleFont, Font tableHeaderFont, Font normalFont) throws DocumentException {

        // Título de sección
        Paragraph resumenTitle = new Paragraph("Resumen por Día", sectionTitleFont);
        resumenTitle.setSpacingAfter(10);
        document.add(resumenTitle);

        // Tabla de resumen limpia
        PdfPTable resumenTable = new PdfPTable(2);
        resumenTable.setWidthPercentage(100);
        resumenTable.setWidths(new float[]{50, 50});
        resumenTable.setSpacingAfter(25);

        // Headers
        addCleanHeaderCell(resumenTable, "Fecha", tableHeaderFont);
        addCleanHeaderCell(resumenTable, "Monto", tableHeaderFont);

        // Datos
        List<LocalDate> fechasOrdenadas = datosReporte.getMontoPorDia().keySet()
                .stream().sorted().collect(Collectors.toList());

        boolean alternate = false;
        for (LocalDate fecha : fechasOrdenadas) {
            BigDecimal monto = datosReporte.getMontoPorDia().get(fecha);

            Color bgColor = alternate ? LIGHT_GRAY : Color.WHITE;

            addCleanDataCell(resumenTable, fecha.format(dateFormatter), normalFont, bgColor, Element.ALIGN_CENTER);
            addCleanDataCell(resumenTable, currencyFormat.format(monto), normalFont, bgColor, Element.ALIGN_RIGHT);

            alternate = !alternate;
        }

        document.add(resumenTable);
    }

    private void addDesgloseDia(Document document, ReporteCuentasPorPagarDTO datosReporte,
                                DateTimeFormatter dateFormatter, NumberFormat currencyFormat,
                                Font sectionTitleFont, Font tableHeaderFont, Font normalFont) throws DocumentException {

        // Título de sección
        Paragraph detalleTitle = new Paragraph("Desglose Detallado", sectionTitleFont);
        detalleTitle.setSpacingAfter(15);
        document.add(detalleTitle);

        List<LocalDate> fechasOrdenadas = datosReporte.getMontoPorDia().keySet()
                .stream().sorted().collect(Collectors.toList());

        for (int i = 0; i < fechasOrdenadas.size(); i++) {
            LocalDate fecha = fechasOrdenadas.get(i);
            List<CuentaReporteDTO> cuentasDelDia = datosReporte.getCuentasPorDia().get(fecha);
            if (cuentasDelDia == null || cuentasDelDia.isEmpty()) continue;

            PdfPTable dayHeader = new PdfPTable(2);
            dayHeader.setWidthPercentage(100);
            dayHeader.setWidths(new float[]{60, 40});
            dayHeader.setSpacingBefore(i == 0 ? 0 : 15);
            dayHeader.setSpacingAfter(5);

            PdfPCell fechaCell = new PdfPCell(new Phrase(fecha.format(dateFormatter),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, PRIMARY_BLUE)));
            fechaCell.setBorder(Rectangle.NO_BORDER);
            fechaCell.setPadding(8);
            fechaCell.setBackgroundColor(LIGHT_GRAY);

            BigDecimal totalDia = datosReporte.getMontoPorDia().get(fecha);
            PdfPCell totalCell = new PdfPCell(new Phrase("Total: " + currencyFormat.format(totalDia),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, SUCCESS_GREEN)));
            totalCell.setBorder(Rectangle.NO_BORDER);
            totalCell.setPadding(8);
            totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalCell.setBackgroundColor(LIGHT_GRAY);

            dayHeader.addCell(fechaCell);
            dayHeader.addCell(totalCell);
            document.add(dayHeader);

            // Tabla de detalles
            PdfPTable detalleTable = new PdfPTable(6);
            detalleTable.setWidthPercentage(100);
            detalleTable.setWidths(new float[]{20, 20, 10, 18, 12, 14});
            detalleTable.setSpacingAfter(10);

            String[] headers = {"Categoría", "Cuenta", "SIM", "Forma Pago", "Estatus", "Monto"};
            for (String header : headers) {
                addCompactHeaderCell(detalleTable, header, tableHeaderFont);
            }

            boolean alt = false;
            for (CuentaReporteDTO cuenta : cuentasDelDia) {
                Color bg = alt ? new Color(252, 252, 253) : Color.WHITE;

                addCompactDataCell(detalleTable, cuenta.getCategoria(), normalFont, bg, Element.ALIGN_LEFT);
                addCompactDataCell(detalleTable, cuenta.getCuenta(), normalFont, bg, Element.ALIGN_LEFT);
                addCompactDataCell(detalleTable, cuenta.getNumeroSim(), normalFont, bg, Element.ALIGN_CENTER);
                addCompactDataCell(detalleTable, cuenta.getFormaPago(), normalFont, bg, Element.ALIGN_LEFT);
                addCompactDataCell(detalleTable, cuenta.getEstatus(), normalFont, bg, Element.ALIGN_CENTER);
                addCompactDataCell(detalleTable, currencyFormat.format(cuenta.getMonto()), normalFont, bg, Element.ALIGN_RIGHT);

                alt = !alt;
            }

            document.add(detalleTable);
        }
    }

    private void addSimpleFooter(Document document, Font normalFont) throws DocumentException {
        Paragraph footer = new Paragraph(String.format("Generado el %s",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))),
                FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(108, 117, 125)));
        footer.setAlignment(Element.ALIGN_RIGHT);
        footer.setSpacingBefore(30);
        document.add(footer);
    }

    private void addCleanHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(PRIMARY_BLUE);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(8);
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    private void addCleanDataCell(PdfPTable table, String text, Font font, Color bgColor, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(6);
        cell.setBorderColor(BORDER_GRAY);
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    private void addCompactHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(PRIMARY_BLUE);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    private void addCompactDataCell(PdfPTable table, String text, Font font, Color bgColor, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(4);
        cell.setBorderColor(new Color(233, 236, 239));
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }
}