package com.tss.tssmanager_backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public class TratoDTO {
    private Integer id;
    private String nombre;
    private Integer empresaId;
    private Integer contactoId;
    private Integer numeroUnidades;
    private BigDecimal ingresosEsperados;
    private String descripcion;
    private Integer propietarioId;
    private LocalDateTime fechaCierre;
    private String noTrato;
    private Integer probabilidad;
    private String fase;
    private Boolean correosAutomaticosActivos;
    private Instant fechaCreacion;
    private Instant fechaModificacion;
    private Instant fechaUltimaActividad;
    private String propietarioNombre;
    private String empresaNombre;
    private List<ActividadDTO> actividades;
    private List<FaseDTO> fases;
    private List<ActividadDTO> historialInteracciones;
    private List<NotaTratoDTO> notas;
    private ActividadesAbiertasDTO actividadesAbiertas;
    private ContactoDTO contacto;
    private String domicilio;
    private String sitioWeb;
    private String sector;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public Integer getEmpresaId() { return empresaId; }
    public void setEmpresaId(Integer empresaId) { this.empresaId = empresaId; }
    public Integer getContactoId() { return contactoId; }
    public void setContactoId(Integer contactoId) { this.contactoId = contactoId; }
    public Integer getNumeroUnidades() { return numeroUnidades; }
    public void setNumeroUnidades(Integer numeroUnidades) { this.numeroUnidades = numeroUnidades; }
    public BigDecimal getIngresosEsperados() { return ingresosEsperados; }
    public void setIngresosEsperados(BigDecimal ingresosEsperados) { this.ingresosEsperados = ingresosEsperados; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Integer getPropietarioId() { return propietarioId; }
    public void setPropietarioId(Integer propietarioId) { this.propietarioId = propietarioId; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }
    public String getNoTrato() { return noTrato; }
    public void setNoTrato(String noTrato) { this.noTrato = noTrato; }
    public Integer getProbabilidad() { return probabilidad; }
    public void setProbabilidad(Integer probabilidad) { this.probabilidad = probabilidad; }
    public String getFase() { return fase; }
    public void setFase(String fase) { this.fase = fase; }
    public Boolean getCorreosAutomaticosActivos() { return correosAutomaticosActivos; }
    public void setCorreosAutomaticosActivos(Boolean correosAutomaticosActivos) { this.correosAutomaticosActivos = correosAutomaticosActivos; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public Instant getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(Instant fechaModificacion) { this.fechaModificacion = fechaModificacion; }
    public Instant getFechaUltimaActividad() { return fechaUltimaActividad; }
    public void setFechaUltimaActividad(Instant fechaUltimaActividad) { this.fechaUltimaActividad = fechaUltimaActividad; }
    public String getPropietarioNombre() { return propietarioNombre; }
    public void setPropietarioNombre(String propietarioNombre) { this.propietarioNombre = propietarioNombre; }
    public String getEmpresaNombre() { return empresaNombre; }
    public void setEmpresaNombre(String empresaNombre) { this.empresaNombre = empresaNombre; }
    public List<ActividadDTO> getActividades() { return actividades; }
    public void setActividades(List<ActividadDTO> actividades) { this.actividades = actividades; }
    public List<FaseDTO> getFases() { return fases; }
    public void setFases(List<FaseDTO> fases) { this.fases = fases; }
    public List<ActividadDTO> getHistorialInteracciones() { return historialInteracciones; }
    public void setHistorialInteracciones(List<ActividadDTO> historialInteracciones) { this.historialInteracciones = historialInteracciones; }
    public List<NotaTratoDTO> getNotas() { return notas; }
    public void setNotas(List<NotaTratoDTO> notas) { this.notas = notas; }
    public ActividadesAbiertasDTO getActividadesAbiertas() { return actividadesAbiertas; }
    public void setActividadesAbiertas(ActividadesAbiertasDTO actividadesAbiertas) { this.actividadesAbiertas = actividadesAbiertas; }
    public ContactoDTO getContacto() { return contacto; }
    public void setContacto(ContactoDTO contacto) { this.contacto = contacto; }
    public String getDomicilio() { return domicilio; }
    public void setDomicilio(String domicilio) { this.domicilio = domicilio; }
    public String getSitioWeb() { return sitioWeb; }
    public void setSitioWeb(String sitioWeb) { this.sitioWeb = sitioWeb; }
    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }
}

