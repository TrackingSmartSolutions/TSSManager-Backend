package com.tss.tssmanager_backend.dto;

import com.tss.tssmanager_backend.enums.EstatusEmpresaEnum;
import com.tss.tssmanager_backend.enums.SectorEmpresaEnum;
import java.time.Instant;

public class EmpresaDTO {
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
    private PropietarioDTO propietario;
    private Integer propietarioId;

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public EstatusEmpresaEnum getEstatus() { return estatus; }
    public void setEstatus(EstatusEmpresaEnum estatus) { this.estatus = estatus; }
    public String getSitioWeb() { return sitioWeb; }
    public void setSitioWeb(String sitioWeb) { this.sitioWeb = sitioWeb; }
    public SectorEmpresaEnum getSector() { return sector; }
    public void setSector(SectorEmpresaEnum sector) { this.sector = sector; }
    public String getDomicilioFisico() { return domicilioFisico; }
    public void setDomicilioFisico(String domicilioFisico) { this.domicilioFisico = domicilioFisico; }
    public String getDomicilioFiscal() { return domicilioFiscal; }
    public void setDomicilioFiscal(String domicilioFiscal) { this.domicilioFiscal = domicilioFiscal; }
    public String getRfc() { return rfc; }
    public void setRfc(String rfc) { this.rfc = rfc; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public String getRegimenFiscal() { return regimenFiscal; }
    public void setRegimenFiscal(String regimenFiscal) { this.regimenFiscal = regimenFiscal; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public Instant getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(Instant fechaModificacion) { this.fechaModificacion = fechaModificacion; }
    public Instant getFechaUltimaActividad() { return fechaUltimaActividad; }
    public void setFechaUltimaActividad(Instant fechaUltimaActividad) { this.fechaUltimaActividad = fechaUltimaActividad; }
    public PropietarioDTO getPropietario() { return propietario; }
    public void setPropietario(PropietarioDTO propietario) { this.propietario = propietario; }
    public Integer getPropietarioId() { return propietarioId; }
    public void setPropietarioId(Integer propietarioId) { this.propietarioId = propietarioId; }
}