package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.TipoDocumentoSolicitudEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class SolicitudFacturaNotaDTO {
    private Integer id;
    private String identificador;
    private Date fechaEmision;
    private String metodoPago;
    private String formaPago;
    private TipoDocumentoSolicitudEnum tipo;
    private String claveProductoServicio;
    private String claveUnidad;
    private Integer emisorId;
    private Integer cuentaPorCobrarId;
    private Integer cotizacionId;
    private Integer clienteId;
    private String receptor;
    private String concepto;
    private BigDecimal subtotal;
    private BigDecimal iva;
    private String importeLetra;
    private BigDecimal total;
    private String folio;
    private String usoCfdi;


    public static SolicitudFacturaNotaDTO fromEntity(SolicitudFacturaNota solicitud) {
        SolicitudFacturaNotaDTO dto = new SolicitudFacturaNotaDTO();
        dto.setId(solicitud.getId());
        dto.setIdentificador(solicitud.getIdentificador());
        dto.setFechaEmision(solicitud.getFechaEmision());
        dto.setMetodoPago(solicitud.getMetodoPago());
        dto.setFormaPago(solicitud.getFormaPago());
        dto.setTipo(solicitud.getTipo());
        dto.setClaveProductoServicio(solicitud.getClaveProductoServicio());
        dto.setClaveUnidad(solicitud.getClaveUnidad());
        dto.setEmisorId(solicitud.getEmisor() != null ? solicitud.getEmisor().getId() : null);
        dto.setCuentaPorCobrarId(solicitud.getCuentaPorCobrar() != null ? solicitud.getCuentaPorCobrar().getId() : null);
        dto.setClienteId(solicitud.getCliente() != null ? solicitud.getCliente().getId() : null);
        dto.setCotizacionId(solicitud.getCotizacion() != null ? solicitud.getCotizacion().getId() : null);
        dto.setSubtotal(solicitud.getSubtotal());
        dto.setIva(solicitud.getIva());
        dto.setImporteLetra(solicitud.getImporteLetra());
        dto.setTotal(solicitud.getTotal());
        dto.setUsoCfdi(solicitud.getUsoCfdi());

        // Asignar valores dinámicos de las entidades relacionadas
        if (solicitud.getCuentaPorCobrar() != null) {
            dto.setReceptor(solicitud.getCuentaPorCobrar().getCliente() != null ? solicitud.getCuentaPorCobrar().getCliente().getNombre() : "N/A");
            dto.setConcepto(solicitud.getCuentaPorCobrar().getConceptos() != null ? solicitud.getCuentaPorCobrar().getConceptos() : "N/A");
            dto.setFolio(solicitud.getCuentaPorCobrar().getFolio() != null ? solicitud.getCuentaPorCobrar().getFolio() : "N/A");
        }
        return dto;
    }

    public SolicitudFacturaNota toEntity() {
        SolicitudFacturaNota solicitud = new SolicitudFacturaNota();
        solicitud.setId(this.getId());
        solicitud.setIdentificador(this.getIdentificador());
        if (this.getFechaEmision() != null) {
            solicitud.setFechaEmision(new java.sql.Date(this.getFechaEmision().getTime()));
        }
        solicitud.setMetodoPago(this.getMetodoPago());
        solicitud.setFormaPago(this.getFormaPago());
        solicitud.setTipo(this.getTipo());
        solicitud.setClaveProductoServicio(this.getClaveProductoServicio());
        solicitud.setClaveUnidad(this.getClaveUnidad());
        solicitud.setSubtotal(this.getSubtotal());
        solicitud.setIva(this.getIva());
        solicitud.setTotal(this.getTotal());
        solicitud.setImporteLetra(this.getImporteLetra());
        solicitud.setUsoCfdi(this.getUsoCfdi());

        // Configurar relaciones con IDs
        if (this.getEmisorId() != null) {
            Emisor emisor = new Emisor();
            emisor.setId(this.getEmisorId());
            solicitud.setEmisor(emisor);
        }
        if (this.getCuentaPorCobrarId() != null) {
            CuentaPorCobrar cuentaPorCobrar = new CuentaPorCobrar();
            cuentaPorCobrar.setId(this.getCuentaPorCobrarId());
            solicitud.setCuentaPorCobrar(cuentaPorCobrar);
        }
        if (this.getCotizacionId() != null) {
            Cotizacion cotizacion = new Cotizacion();
            cotizacion.setId(this.getCotizacionId());
            solicitud.setCotizacion(cotizacion);
        }
        if (this.getClienteId() != null) {
            Empresa cliente = new Empresa();
            cliente.setId(this.getClienteId());
            solicitud.setCliente(cliente);
        }

        return solicitud;
    }
}