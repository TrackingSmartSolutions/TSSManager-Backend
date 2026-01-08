package com.tss.tssmanager_backend.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.tss.tssmanager_backend.dto.FilaResumenDTO;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReporteCuentasPorPagarService {

    @Autowired
    private CuentaPorPagarRepository cuentaPorPagarRepository;

    @Autowired
    private com.tss.tssmanager_backend.repository.SimRepository simRepository;

    @Autowired
    private com.tss.tssmanager_backend.service.SimService simService;

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

        BigDecimal montoTotal = BigDecimal.ZERO;
        BigDecimal montoSaldoAcumulado = BigDecimal.ZERO;

        for (CuentaPorPagar cuenta : cuentas) {
            boolean usarSaldo = false;

            if (cuenta.getSim() != null) {
                BigDecimal saldoActual = simService.obtenerSaldoNumerico(cuenta.getSim().getId());
                boolean montoEs50 = cuenta.getMonto().compareTo(new BigDecimal("50")) == 0;
                boolean esquemaEsPorSegundo = cuenta.getSim().getTarifa() != null &&
                        cuenta.getSim().getTarifa() == com.tss.tssmanager_backend.enums.TarifaSimEnum.POR_SEGUNDO;
                boolean tieneSaldoSuficiente = saldoActual.compareTo(new BigDecimal("210")) > 0;

                usarSaldo = montoEs50 && esquemaEsPorSegundo && tieneSaldoSuficiente;
            }

            if (usarSaldo) {
                montoSaldoAcumulado = montoSaldoAcumulado.add(cuenta.getMonto());
            } else {
                montoTotal = montoTotal.add(cuenta.getMonto());
            }
        }

        reporte.setMontoTotal(montoTotal);
        reporte.setMontoSaldoAcumulado(montoSaldoAcumulado);

        Map<LocalDate, BigDecimal> montoPorDia = new HashMap<>();

        for (CuentaPorPagar cuenta : cuentas) {
            boolean usarSaldo = false;

            if (cuenta.getSim() != null) {
                BigDecimal saldoActual = simService.obtenerSaldoNumerico(cuenta.getSim().getId());
                boolean montoEs50 = cuenta.getMonto().compareTo(new BigDecimal("50")) == 0;
                boolean esquemaEsPorSegundo = cuenta.getSim().getTarifa() != null &&
                        cuenta.getSim().getTarifa() == com.tss.tssmanager_backend.enums.TarifaSimEnum.POR_SEGUNDO;
                boolean tieneSaldoSuficiente = saldoActual.compareTo(new BigDecimal("210")) > 0;

                usarSaldo = montoEs50 && esquemaEsPorSegundo && tieneSaldoSuficiente;
            }

            if (!usarSaldo) {
                LocalDate fecha = cuenta.getFechaPago();
                BigDecimal montoActual = montoPorDia.getOrDefault(fecha, BigDecimal.ZERO);
                montoPorDia.put(fecha, montoActual.add(cuenta.getMonto()));
            }
        }

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

        // Definición de fuentes (Igual que tenías)
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, PRIMARY_BLUE);
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 12, new Color(108, 117, 125));
        Font sectionTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, PRIMARY_BLUE);
        Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_DARK);
        Font moneyFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, SUCCESS_GREEN);

        Font saldoAcumuladoFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(34, 139, 34)); // Verde fuerte

        addModernHeader(document, datosReporte, dateFormatter, currencyFormat, titleFont, subtitleFont, moneyFont);

        addResumenEjecutivo(document, datosReporte, dateFormatter, currencyFormat, sectionTitleFont, tableHeaderFont, normalFont);

        List<CuentaPorPagar> cuentasRaw = obtenerCuentasFiltradas(datosReporte.getFechaInicio(), datosReporte.getFechaFin(), datosReporte.getFiltroEstatus());

        List<CuentaPorPagar> otrosPagos = new ArrayList<>();
        List<DatosTelcelEnriquecidos> pagosTelcel = new ArrayList<>();

        for (CuentaPorPagar c : cuentasRaw) {
            boolean tieneSim = c.getSim() != null;
            boolean esCategoriaTelcel = c.getTransaccion() != null &&
                    c.getTransaccion().getCategoria() != null &&
                    c.getTransaccion().getCategoria().getDescripcion().toUpperCase().contains("TELCEL");

            if (tieneSim || esCategoriaTelcel) {
                if (tieneSim) {
                    Integer grupoId = c.getSim().getGrupo();
                    String numeroPrincipal = "Sin Grupo / Individual";

                    if (grupoId != null && grupoId > 0) {
                        numeroPrincipal = simRepository.findNumeroPrincipalByGrupo(grupoId).orElse("No asignado");
                    }

                    BigDecimal saldoActual = simService.obtenerSaldoNumerico(c.getSim().getId());

                    boolean montoEs50 = c.getMonto().compareTo(new BigDecimal("50")) == 0;
                    boolean esquemaEsPorSegundo = c.getSim().getTarifa() != null &&
                            c.getSim().getTarifa() == com.tss.tssmanager_backend.enums.TarifaSimEnum.POR_SEGUNDO;
                    boolean tieneSaldoSuficiente = saldoActual.compareTo(new BigDecimal("210")) > 0;

                    boolean usarSaldo = montoEs50 && esquemaEsPorSegundo && tieneSaldoSuficiente;

                    pagosTelcel.add(new DatosTelcelEnriquecidos(c, numeroPrincipal, usarSaldo, grupoId != null ? grupoId : 9999));
                } else {
                    otrosPagos.add(c);
                }
            } else {
                otrosPagos.add(c);
            }
        }

        addSeccionOtrosPagos(document, otrosPagos, currencyFormat, dateFormatter, sectionTitleFont, tableHeaderFont, normalFont);

        addSeccionRecargasTelcel(document, pagosTelcel, currencyFormat, dateFormatter, sectionTitleFont, tableHeaderFont, normalFont, saldoAcumuladoFont);

        Paragraph notaAclaratoria = new Paragraph();
        notaAclaratoria.setSpacingBefore(20);
        notaAclaratoria.setSpacingAfter(10);

        Chunk asterisco = new Chunk("* ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, PRIMARY_BLUE));
        Chunk textoNota = new Chunk(
                "Las recargas de saldo acumulado implican la activación de un paquete sin límite de $200, " +
                        "pagando con el saldo que se tiene en la línea.",
                FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(108, 117, 125))
        );

        notaAclaratoria.add(asterisco);
        notaAclaratoria.add(textoNota);
        document.add(notaAclaratoria);

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

        PdfPTable infoCard = new PdfPTable(4);
        infoCard.setWidthPercentage(100);
        infoCard.setWidths(new float[]{25, 25, 25, 25});
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

        PdfPCell saldoCell = new PdfPCell();
        saldoCell.setBorder(Rectangle.BOX);
        saldoCell.setBorderColor(BORDER_GRAY);
        saldoCell.setBackgroundColor(new Color(220, 255, 220));
        saldoCell.setPadding(15);
        Paragraph saldoP = new Paragraph();
        saldoP.add(new Phrase("SALDO APLICADO\n", FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(108, 117, 125))));
        saldoP.add(new Phrase(currencyFormat.format(datosReporte.getMontoSaldoAcumulado()),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(34, 139, 34))));
        saldoP.setAlignment(Element.ALIGN_CENTER);
        saldoCell.addElement(saldoP);
        infoCard.addCell(saldoCell);

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
            detalleTable.setWidths(new float[]{18, 18, 15, 16, 12, 15});
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
                LocalDateTime.now(ZoneId.of("America/Mexico_City")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))),
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

    // Clase auxiliar simple para transportar los datos calculados
    private static class DatosTelcelEnriquecidos {
        CuentaPorPagar cuenta;
        String numeroPrincipal;
        boolean usarSaldo;
        Integer grupoId;

        public DatosTelcelEnriquecidos(CuentaPorPagar c, String np, boolean us, Integer g) {
            this.cuenta = c; this.numeroPrincipal = np; this.usarSaldo = us; this.grupoId = g;
        }
    }

    private void addSeccionOtrosPagos(Document document, List<CuentaPorPagar> cuentas, NumberFormat currencyFormat,
                                      DateTimeFormatter dateFormatter, Font titleFont, Font headerFont, Font normalFont) throws DocumentException {
        if (cuentas.isEmpty()) return;

        Paragraph title = new Paragraph("Otros Pagos y Servicios", titleFont);
        title.setSpacingBefore(20);
        title.setSpacingAfter(10);
        document.add(title);

        PdfPTable table = new PdfPTable(5); // Ajustamos columnas
        table.setWidthPercentage(100);
        table.setWidths(new float[]{15, 25, 20, 20, 20});

        String[] headers = {"Fecha", "Cuenta", "Categoría", "Forma Pago", "Monto"};
        for (String h : headers) addCleanHeaderCell(table, h, headerFont);

        boolean alt = false;
        for (CuentaPorPagar c : cuentas) {
            Color bg = alt ? LIGHT_GRAY : Color.WHITE;

            String categoria = (c.getTransaccion() != null && c.getTransaccion().getCategoria() != null)
                    ? c.getTransaccion().getCategoria().getDescripcion() : "-";
            String formaPago = FORMAS_PAGO.getOrDefault(c.getFormaPago(), c.getFormaPago());

            addCleanDataCell(table, c.getFechaPago().format(dateFormatter), normalFont, bg, Element.ALIGN_CENTER);
            addCleanDataCell(table, c.getCuenta().getNombre(), normalFont, bg, Element.ALIGN_LEFT);
            addCleanDataCell(table, categoria, normalFont, bg, Element.ALIGN_CENTER);
            addCleanDataCell(table, formaPago, normalFont, bg, Element.ALIGN_CENTER);
            addCleanDataCell(table, currencyFormat.format(c.getMonto()), normalFont, bg, Element.ALIGN_RIGHT);

            alt = !alt;
        }
        document.add(table);
    }

    private void addSeccionRecargasTelcel(Document document, List<DatosTelcelEnriquecidos> datos, NumberFormat currencyFormat,
                                          DateTimeFormatter dateFormatter, Font titleFont, Font headerFont, Font normalFont, Font saldoFont) throws DocumentException {
        if (datos.isEmpty()) return;

        Paragraph title = new Paragraph("Recargas Telcel (Agrupadas)", titleFont);
        title.setSpacingBefore(20);
        title.setSpacingAfter(10);
        document.add(title);

        // Agrupar por ID de Grupo
        Map<Integer, List<DatosTelcelEnriquecidos>> agrupadoPorGrupo = datos.stream()
                .collect(Collectors.groupingBy(d -> d.grupoId));

        // Iterar sobre los grupos
        for (Map.Entry<Integer, List<DatosTelcelEnriquecidos>> entry : agrupadoPorGrupo.entrySet()) {
            List<DatosTelcelEnriquecidos> listaGrupo = entry.getValue();
            Integer grupoId = entry.getKey();
            String numeroPrincipal = listaGrupo.get(0).numeroPrincipal; // Todos en el grupo tienen el mismo padre

            // Subtítulo del grupo con el dato clave (Número Principal)
            PdfPTable groupHeaderTable = new PdfPTable(1);
            groupHeaderTable.setWidthPercentage(100);
            groupHeaderTable.setSpacingBefore(15);
            groupHeaderTable.setSpacingAfter(5);

            PdfPCell cellHeader = new PdfPCell(new Phrase(
                    String.format("Grupo %d - Línea Principal: %s", grupoId, numeroPrincipal),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE)
            ));
            cellHeader.setBackgroundColor(ACCENT_BLUE); // Azul un poco más claro para subsecciones
            cellHeader.setPadding(6);
            cellHeader.setBorder(Rectangle.NO_BORDER);
            groupHeaderTable.addCell(cellHeader);
            document.add(groupHeaderTable);

            // Tabla de datos del grupo
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{15, 15, 20, 15, 15, 20}); // Ajuste de anchos

            String[] headers = {"Fecha", "SIM", "Cuenta/Cliente", "Estatus", "Saldo Actual", "Acción/Monto"};
            for (String h : headers) addCompactHeaderCell(table, h, headerFont);

            boolean alt = false;
            for (DatosTelcelEnriquecidos d : listaGrupo) {
                Color bg = alt ? new Color(248, 249, 250) : Color.WHITE;
                CuentaPorPagar c = d.cuenta;

                addCompactDataCell(table, c.getFechaPago().format(dateFormatter), normalFont, bg, Element.ALIGN_CENTER);
                addCompactDataCell(table, c.getSim().getNumero(), normalFont, bg, Element.ALIGN_CENTER);
                addCompactDataCell(table, c.getCuenta().getNombre(), normalFont, bg, Element.ALIGN_LEFT);
                addCompactDataCell(table, c.getEstatus(), normalFont, bg, Element.ALIGN_CENTER);

                BigDecimal saldoReal = simService.obtenerSaldoNumerico(c.getSim().getId());
                addCompactDataCell(table, currencyFormat.format(saldoReal), normalFont, bg, Element.ALIGN_RIGHT);

                if (d.usarSaldo) {
                    PdfPCell cellSaldo = new PdfPCell(new Phrase("SALDO ACUMULADO", saldoFont));
                    cellSaldo.setBackgroundColor(new Color(220, 255, 220)); // Verde muy suave de fondo
                    cellSaldo.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellSaldo.setPadding(4);
                    cellSaldo.setBorderColor(BORDER_GRAY);
                    table.addCell(cellSaldo);
                } else {
                    // Monto normal
                    addCompactDataCell(table, currencyFormat.format(c.getMonto()), normalFont, bg, Element.ALIGN_RIGHT);
                }

                alt = !alt;
            }
            document.add(table);
        }
    }

    public byte[] generarReporteResumidoPDF(LocalDate fechaInicio, LocalDate fechaFin, String filtroEstatus) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 30, 30, 40, 40);
        PdfWriter.getInstance(document, baos);
        document.open();

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, PRIMARY_BLUE);
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 12, new Color(108, 117, 125));
        Font sectionTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, PRIMARY_BLUE);
        Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_DARK);
        Font moneyFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, SUCCESS_GREEN);

        List<CuentaPorPagar> cuentas = obtenerCuentasFiltradas(fechaInicio, fechaFin, filtroEstatus);
        List<FilaResumenDTO> resumen = agruparParaResumen(cuentas);
        BigDecimal montoTotal = calcularMontoTotal(cuentas);

        addEncabezadoResumido(document, fechaInicio, fechaFin, montoTotal, filtroEstatus,
                dateFormatter, currencyFormat, titleFont, subtitleFont, moneyFont);

        addTablaResumen(document, resumen, currencyFormat, dateFormatter,
                sectionTitleFont, tableHeaderFont, normalFont);

        addSimpleFooter(document, normalFont);

        document.close();
        return baos.toByteArray();
    }

    private List<FilaResumenDTO> agruparParaResumen(List<CuentaPorPagar> cuentas) {
        Map<String, FilaResumenDTO> agrupado = new LinkedHashMap<>();

        for (CuentaPorPagar cuenta : cuentas) {
            LocalDate fecha = cuenta.getFechaPago();
            String categoria = (cuenta.getTransaccion() != null && cuenta.getTransaccion().getCategoria() != null)
                    ? cuenta.getTransaccion().getCategoria().getDescripcion() : "Sin categoría";
            String cliente = cuenta.getCuenta().getNombre();

            String clave = fecha + "|" + categoria + "|" + cliente;

            if (agrupado.containsKey(clave)) {
                FilaResumenDTO fila = agrupado.get(clave);
                agrupado.put(clave, new FilaResumenDTO(
                        fecha,
                        categoria,
                        cliente,
                        fila.getTotalCuentas() + 1,
                        fila.getMonto().add(cuenta.getMonto())
                ));
            } else {
                agrupado.put(clave, new FilaResumenDTO(fecha, categoria, cliente, 1, cuenta.getMonto()));
            }
        }

        return new ArrayList<>(agrupado.values());
    }

    private BigDecimal calcularMontoTotal(List<CuentaPorPagar> cuentas) {
        return cuentas.stream()
                .map(CuentaPorPagar::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void addEncabezadoResumido(Document document, LocalDate fechaInicio, LocalDate fechaFin,
                                       BigDecimal montoTotal, String filtroEstatus,
                                       DateTimeFormatter dateFormatter, NumberFormat currencyFormat,
                                       Font titleFont, Font subtitleFont, Font moneyFont) throws DocumentException {
        Paragraph title = new Paragraph("Reporte Resumido de Cuentas por Pagar", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(5);
        document.add(title);

        Paragraph periodo = new Paragraph(String.format("Del %s al %s",
                fechaInicio.format(dateFormatter), fechaFin.format(dateFormatter)), subtitleFont);
        periodo.setAlignment(Element.ALIGN_CENTER);
        periodo.setSpacingAfter(20);
        document.add(periodo);

        PdfPTable infoCard = new PdfPTable(2);
        infoCard.setWidthPercentage(100);
        infoCard.setWidths(new float[]{50, 50});
        infoCard.setSpacingAfter(25);

        // Total
        PdfPCell totalCell = new PdfPCell();
        totalCell.setBorder(Rectangle.BOX);
        totalCell.setBorderColor(BORDER_GRAY);
        totalCell.setBackgroundColor(Color.WHITE);
        totalCell.setPadding(15);
        Paragraph totalP = new Paragraph();
        totalP.add(new Phrase("MONTO TOTAL\n", FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(108, 117, 125))));
        totalP.add(new Phrase(currencyFormat.format(montoTotal), moneyFont));
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
        filtroP.add(new Phrase(filtroEstatus, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, TEXT_DARK)));
        filtroP.setAlignment(Element.ALIGN_CENTER);
        filtroCell.addElement(filtroP);
        infoCard.addCell(filtroCell);

        document.add(infoCard);
    }

    private void addTablaResumen(Document document, List<FilaResumenDTO> resumen,
                                 NumberFormat currencyFormat, DateTimeFormatter dateFormatter,
                                 Font sectionTitleFont, Font tableHeaderFont, Font normalFont) throws DocumentException {

        Map<LocalDate, List<FilaResumenDTO>> resumenPorDia = resumen.stream()
                .collect(Collectors.groupingBy(FilaResumenDTO::getFecha));

        List<LocalDate> fechasOrdenadas = resumenPorDia.keySet().stream()
                .sorted()
                .collect(Collectors.toList());

        for (int i = 0; i < fechasOrdenadas.size(); i++) {
            LocalDate fecha = fechasOrdenadas.get(i);
            List<FilaResumenDTO> filasDelDia = resumenPorDia.get(fecha);

            BigDecimal totalDia = filasDelDia.stream()
                    .map(FilaResumenDTO::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            PdfPTable dayHeader = new PdfPTable(2);
            dayHeader.setWidthPercentage(100);
            dayHeader.setWidths(new float[]{60, 40});
            dayHeader.setSpacingBefore(i == 0 ? 0 : 20);
            dayHeader.setSpacingAfter(5);

            PdfPCell fechaCell = new PdfPCell(new Phrase(fecha.format(dateFormatter),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, PRIMARY_BLUE)));
            fechaCell.setBorder(Rectangle.NO_BORDER);
            fechaCell.setPadding(8);
            fechaCell.setBackgroundColor(LIGHT_GRAY);

            PdfPCell totalCell = new PdfPCell(new Phrase("Total: " + currencyFormat.format(totalDia),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, SUCCESS_GREEN)));
            totalCell.setBorder(Rectangle.NO_BORDER);
            totalCell.setPadding(8);
            totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalCell.setBackgroundColor(LIGHT_GRAY);

            dayHeader.addCell(fechaCell);
            dayHeader.addCell(totalCell);
            document.add(dayHeader);

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{30, 30, 20, 20});
            table.setSpacingAfter(10);

            // Headers
            addCleanHeaderCell(table, "Categoría", tableHeaderFont);
            addCleanHeaderCell(table, "Cuenta/Cliente", tableHeaderFont);
            addCleanHeaderCell(table, "Total Cuentas", tableHeaderFont);
            addCleanHeaderCell(table, "Monto Total", tableHeaderFont);

            // Datos
            boolean alternate = false;
            for (FilaResumenDTO fila : filasDelDia) {
                Color bg = alternate ? LIGHT_GRAY : Color.WHITE;

                addCleanDataCell(table, fila.getCategoria(), normalFont, bg, Element.ALIGN_LEFT);
                addCleanDataCell(table, fila.getCliente(), normalFont, bg, Element.ALIGN_LEFT);
                addCleanDataCell(table, String.valueOf(fila.getTotalCuentas()), normalFont, bg, Element.ALIGN_CENTER);
                addCleanDataCell(table, currencyFormat.format(fila.getMonto()), normalFont, bg, Element.ALIGN_RIGHT);

                alternate = !alternate;
            }

            document.add(table);
        }
    }
}