package com.tss.tssmanager_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardCreditosDTO {
    private Map<String, BigDecimal> saldosPorPlataforma;
    private List<CreditoPlataformaDTO> estadoCuenta;
    private List<Map<String, Object>> historialSaldos;
}