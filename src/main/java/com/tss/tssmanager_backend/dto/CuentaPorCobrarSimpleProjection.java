package com.tss.tssmanager_backend.dto;

public interface CuentaPorCobrarSimpleProjection {
    Integer getId();
    String getFolio();
    java.math.BigDecimal getMontoPagado();
    String getEstatus();
}