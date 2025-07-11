package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.EstatusEmpresaEnum;
import com.tss.tssmanager_backend.enums.SectorEmpresaEnum;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class EmpresaConContactoDTO {
    private Integer id;
    private String nombre;
    private EstatusEmpresaEnum estatus;
    private String sitioWeb;
    private SectorEmpresaEnum sector;
    private String domicilioFisico;
    private String domicilioFiscal;
    private String rfc;
    private String razonSocial;
    private String regimenFiscal;
    private Instant fechaCreacion;
    private Instant fechaModificacion;
    private Instant fechaUltimaActividad;
    private List<ContactoDTO> contactos;

}