package com.tss.tssmanager_backend.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class RegenerarRequestDTO {
    private Integer transaccionId;
    private LocalDate fechaUltimoPago;

}