package com.tss.tssmanager_backend.dto;

import lombok.Data;

@Data
public class EmpresasCreadasDTO {
    private Integer usuarioId;
    private String usuario;
    private Integer nuevas;
    private Integer contactadas;
    private Integer infoEnviada;

    // Constructores
    public EmpresasCreadasDTO() {}

    public EmpresasCreadasDTO(Integer usuarioId, String usuario, Integer nuevas,
                              Integer contactadas, Integer infoEnviada) {
        this.usuarioId = usuarioId;
        this.usuario = usuario;
        this.nuevas = nuevas;
        this.contactadas = contactadas;
        this.infoEnviada = infoEnviada;
    }
}