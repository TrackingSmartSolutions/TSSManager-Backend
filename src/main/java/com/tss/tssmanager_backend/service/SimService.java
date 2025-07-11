package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.SimDTO;
import com.tss.tssmanager_backend.entity.Equipo;
import com.tss.tssmanager_backend.entity.HistorialSaldosSim;
import com.tss.tssmanager_backend.entity.Sim;
import com.tss.tssmanager_backend.enums.PrincipalSimEnum;
import com.tss.tssmanager_backend.enums.ResponsableSimEnum;
import com.tss.tssmanager_backend.enums.TarifaSimEnum;
import com.tss.tssmanager_backend.repository.EquipoRepository;
import com.tss.tssmanager_backend.repository.HistorialSaldosSimRepository;
import com.tss.tssmanager_backend.repository.SimRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SimService {

    @Autowired
    private SimRepository simRepository;

    @Autowired
    private EquipoRepository equipoRepository;

    @Autowired
    private HistorialSaldosSimRepository historialSaldosSimRepository;

    @Transactional
    public Sim guardarSim(Sim sim) {
        if (sim.getResponsable() == ResponsableSimEnum.CLIENTE) {
            sim.setGrupo(99);
            sim.setVigencia(null);
            sim.setRecarga(null);
            sim.setContrasena(null);
            sim.setPrincipal(PrincipalSimEnum.NO);
        } else {
            if (sim.getId() != null) {
                Sim simExistente = simRepository.findById(sim.getId())
                        .orElseThrow(() -> new EntityNotFoundException("SIM no encontrada."));
                if (simExistente.getGrupo() != null) {
                    sim.setGrupo(simExistente.getGrupo());
                }
            }
            if (sim.getPrincipal() == PrincipalSimEnum.SI && sim.getId() == null) {
                sim.setGrupo(generarNuevoGrupo());
            } else if (sim.getGrupo() != null) {
                Long principalCount = simRepository.countPrincipalesByGrupo(sim.getGrupo());
                Long nonPrincipalCount = simRepository.countNonPrincipalesByGrupo(sim.getGrupo());
                if (principalCount >= 1 && sim.getPrincipal() == PrincipalSimEnum.SI) {
                    throw new IllegalStateException("El grupo seleccionado ya tiene un SIM principal.");
                }
                if (nonPrincipalCount >= 5 && sim.getPrincipal() == PrincipalSimEnum.NO) {
                    throw new IllegalStateException("El grupo seleccionado ya tiene el máximo de 5 SIMs no principales.");
                }
                if (principalCount + nonPrincipalCount >= 6) {
                    throw new IllegalStateException("El grupo seleccionado ya tiene el máximo de 6 SIMs.");
                }
            }

            if (sim.getVigencia() == null) {
                sim.setVigencia(Date.valueOf(LocalDate.now()));
            }
            if (sim.getRecarga() == null) {
                sim.setRecarga(new BigDecimal("50.00"));
            }
            if (sim.getContrasena() == null || sim.getContrasena().trim().isEmpty()) {
                sim.setContrasena("tss2025");
            }
        }

        Sim savedSim = simRepository.save(sim);

        if (savedSim.getEquipo() != null) {
            System.out.println("Vinculando equipo con ID: " + savedSim.getEquipo().getId() + " a SIM con ID: " + savedSim.getId());
        } else if (savedSim.getId() != null && sim.getEquipo() == null) {
            System.out.println("Desvinculando equipo de SIM con ID: " + savedSim.getId());
        }

        return savedSim;
    }

    public List<Integer> obtenerGruposDisponibles() {
        List<Integer> allGroups = simRepository.findAllGroups();
        return allGroups.stream()
                .filter(group -> {
                    Long principalCount = simRepository.countPrincipalesByGrupo(group);
                    Long nonPrincipalCount = simRepository.countNonPrincipalesByGrupo(group);
                    return principalCount + nonPrincipalCount < 6;
                })
                .toList();
    }

    public List<SimDTO> obtenerTodasLasSims() {
        return simRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Optional<SimDTO> obtenerSimPorId(Integer id) {
        return simRepository.findById(id)
                .map(this::convertToDTO);
    }

    @Transactional
    public void eliminarSim(Integer id) {
        Sim sim = simRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SIM no encontrada."));
        if (sim.getEquipo() != null) {
            throw new IllegalStateException("No se puede eliminar la SIM porque está vinculada a un equipo.");
        }
        historialSaldosSimRepository.deleteBySimId(id);
        simRepository.delete(sim);
    }

    public List<SimDTO> obtenerSimsDisponibles() {
        return simRepository.findAvailableSims().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void guardarSaldo(Integer simId, BigDecimal saldoActual, BigDecimal datos, Date fecha) {
        Sim sim = simRepository.findById(simId)
                .orElseThrow(() -> new EntityNotFoundException("SIM no encontrada."));
        HistorialSaldosSim historial = new HistorialSaldosSim();
        historial.setSim(sim);
        historial.setSaldoActual(sim.getTarifa() == TarifaSimEnum.POR_SEGUNDO ? saldoActual : null);
        historial.setDatos(sim.getTarifa() == TarifaSimEnum.SIN_LIMITE ? datos : null);
        historial.setFecha(fecha != null ? fecha : Date.valueOf(LocalDate.now()));
        historialSaldosSimRepository.save(historial);
    }

    @Transactional(readOnly = true)
    public Sim obtenerSim(Integer id) {
        return simRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SIM no encontrada con ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<HistorialSaldosSim> obtenerHistorialSaldos(Integer simId) {
        Sim sim = simRepository.findById(simId)
                .orElseThrow(() -> new EntityNotFoundException("SIM no encontrada con ID: " + simId));
        return historialSaldosSimRepository.findBySimId(simId);
    }

    private Integer generarNuevoGrupo() {
        Integer maxGroup = simRepository.findMaxGrupo();
        return (maxGroup != null ? maxGroup : 0) + 1;
    }

    private SimDTO convertToDTO(Sim sim) {
        SimDTO dto = new SimDTO();
        dto.setId(sim.getId());
        dto.setNumero(sim.getNumero());
        dto.setTarifa(sim.getTarifa());
        dto.setResponsable(sim.getResponsable());
        dto.setPrincipal(sim.getPrincipal());
        dto.setGrupo(sim.getGrupo());
        dto.setRecarga(sim.getRecarga());
        dto.setVigencia(sim.getVigencia());
        dto.setContrasena(sim.getContrasena());
        if (sim.getEquipo() != null) {
            dto.setEquipoId(sim.getEquipo().getId());
        }
        return dto;
    }
}