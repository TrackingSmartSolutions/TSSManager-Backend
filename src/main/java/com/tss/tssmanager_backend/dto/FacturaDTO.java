package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.entity.Factura;
import lombok.Data;

@Data
public class FacturaDTO {
    private Integer id;
    private String folioFiscal;
    private String noSolicitud;
    private String archivoUrl;
    private Integer solicitudId;

    // Campos adicionales de la solicitud si los necesitas
    private String receptorNombre;
    private String emisorNombre;
    private String conceptos;

    public static FacturaDTO fromEntity(Factura factura) {
        FacturaDTO dto = new FacturaDTO();
        dto.setId(factura.getId());
        dto.setFolioFiscal(factura.getFolioFiscal());
        dto.setNoSolicitud(factura.getNoSolicitud());
        dto.setArchivoUrl(factura.getArchivoUrl());
        dto.setSolicitudId(factura.getSolicitudId());

        if (factura.getSolicitud() != null) {
            if (factura.getSolicitud().getCliente() != null) {
                dto.setReceptorNombre(factura.getSolicitud().getCliente().getNombre());
            }
            if (factura.getSolicitud().getEmisor() != null) {
                dto.setEmisorNombre(factura.getSolicitud().getEmisor().getNombre());
            }
            if (factura.getSolicitud().getCuentaPorCobrar() != null) {
                dto.setConceptos(factura.getSolicitud().getCuentaPorCobrar().getConceptos());
            }
        }

        return dto;
    }
}