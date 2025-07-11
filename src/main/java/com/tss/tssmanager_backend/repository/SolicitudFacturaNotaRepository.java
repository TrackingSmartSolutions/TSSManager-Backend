package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.SolicitudFacturaNota;
import com.tss.tssmanager_backend.enums.EstatusPagoEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitudFacturaNotaRepository extends JpaRepository<SolicitudFacturaNota, Integer> {
    List<SolicitudFacturaNota> findByCuentaPorCobrar_EstatusNot(EstatusPagoEnum estatus);
    Optional<SolicitudFacturaNota> findByIdentificador(String identificador);
}