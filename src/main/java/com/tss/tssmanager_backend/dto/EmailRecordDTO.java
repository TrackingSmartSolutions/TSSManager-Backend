package com.tss.tssmanager_backend.dto;

import lombok.Data;
import java.time.ZonedDateTime;
import java.util.List;

@Data
public class EmailRecordDTO {
    private Integer id;
    private String destinatario;
    private String asunto;
    private String cuerpo;
    private ZonedDateTime fechaEnvio;
    private Integer tratoId;
    private boolean exito;
    private String status;
    private List<EstadoDestinatarioDTO> estadosDestinatarios;

    @Data
    public static class EstadoDestinatarioDTO {
        private Integer id;
        private String email;
        private String status;
        private ZonedDateTime updatedAt;
    }
}