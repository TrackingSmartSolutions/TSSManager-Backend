package com.tss.tssmanager_backend.dto;
import com.tss.tssmanager_backend.entity.Factura;
import lombok.Data;

@Data
public class FacturaDTO {
    private Integer id;
    private String folioFiscal;
    private String noSolicitud;
    private String archivoUrl;
    private String nombreArchivo;
    private Integer solicitudId;
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

        if (factura.getNombreArchivoOriginal() != null && !factura.getNombreArchivoOriginal().isEmpty()) {
            dto.setNombreArchivo(factura.getNombreArchivoOriginal());
        } else {
            dto.setNombreArchivo(extraerNombreArchivo(factura.getArchivoUrl()));
        }

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

    private static String extraerNombreArchivo(String cloudinaryUrl) {
        try {
            String[] parts = cloudinaryUrl.split("/");
            if (parts.length > 0) {
                String ultimaParte = parts[parts.length - 1];
                if (ultimaParte.contains(".")) {
                    return ultimaParte;
                }
                if (parts.length > 1) {
                    String penultimaParte = parts[parts.length - 2];
                    if (penultimaParte.contains(".")) {
                        return penultimaParte;
                    }
                }
            }
            return "factura.pdf";
        } catch (Exception e) {
            return "factura.pdf";
        }
    }
}