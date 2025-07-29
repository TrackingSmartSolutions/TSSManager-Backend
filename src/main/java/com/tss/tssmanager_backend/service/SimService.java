package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.SimDTO;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.EsquemaTransaccionEnum;
import com.tss.tssmanager_backend.enums.PrincipalSimEnum;
import com.tss.tssmanager_backend.enums.ResponsableSimEnum;
import com.tss.tssmanager_backend.enums.TarifaSimEnum;
import com.tss.tssmanager_backend.repository.*;
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

    @Autowired
    private TransaccionService transaccionService;

    @Autowired
    private CategoriaTransaccionesRepository categoriaRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Transactional
    public Sim guardarSim(Sim sim) {
        if (sim.getResponsable() == ResponsableSimEnum.CLIENTE) {
            sim.setGrupo(99);
            sim.setVigencia(null);
            sim.setRecarga(null);
            sim.setContrasena(null);
            sim.setPrincipal(PrincipalSimEnum.NO);
        } else {
            // Para SIMs de TSS
            if (sim.getId() != null) {
                // Es una actualización - obtener SIM existente
                if (sim.getTarifa() == TarifaSimEnum.M2M_GLOBAL_15) {
                    sim.setGrupo(0);
                    sim.setPrincipal(PrincipalSimEnum.NO);
                } else {
                    Sim simExistente = simRepository.findById(sim.getId())
                            .orElseThrow(() -> new EntityNotFoundException("SIM no encontrada."));

                    // Si cambia de NO principal a SI principal, generar nuevo grupo
                    if (simExistente.getPrincipal() == PrincipalSimEnum.NO &&
                            sim.getPrincipal() == PrincipalSimEnum.SI) {
                        sim.setGrupo(generarNuevoGrupo());
                    }
                    // Si cambia de SI principal a NO principal, debe especificar grupo
                    else if (simExistente.getPrincipal() == PrincipalSimEnum.SI &&
                            sim.getPrincipal() == PrincipalSimEnum.NO) {
                        if (sim.getGrupo() == null) {
                            throw new IllegalStateException("Debe especificar un grupo para SIM no principal.");
                        }
                        // Validar que el grupo seleccionado tenga espacio para SIMs no principales
                        Long nonPrincipalCount = simRepository.countNonPrincipalesByGrupoExcluding(sim.getGrupo(), sim.getId());
                        if (nonPrincipalCount >= 5) {
                            throw new IllegalStateException("El grupo seleccionado ya tiene el máximo de 5 SIMs no principales.");
                        }
                    }
                    // Si sigue siendo principal, mantener su grupo actual
                    else if (sim.getPrincipal() == PrincipalSimEnum.SI) {
                        sim.setGrupo(simExistente.getGrupo());
                    }
                    // Si sigue siendo no principal, validar el grupo
                    else if (sim.getPrincipal() == PrincipalSimEnum.NO && sim.getGrupo() != null) {
                        // Validar que el grupo tenga espacio (excluyendo la SIM actual)
                        Long nonPrincipalCount = simRepository.countNonPrincipalesByGrupoExcluding(sim.getGrupo(), sim.getId());
                        if (nonPrincipalCount >= 5) {
                            throw new IllegalStateException("El grupo seleccionado ya tiene el máximo de 5 SIMs no principales.");
                        }
                    }
                }
            } else {
                // Es una creación nueva
                // Lógica especial para SIMs M2M Global 15
                if (sim.getTarifa() == TarifaSimEnum.M2M_GLOBAL_15) {
                    sim.setGrupo(0);
                    sim.setPrincipal(PrincipalSimEnum.NO);
                } else if (sim.getPrincipal() == PrincipalSimEnum.SI) {
                    sim.setGrupo(generarNuevoGrupo());
                } else if (sim.getGrupo() != null) {
                    // Validar espacio en el grupo para SIM no principal
                    Long principalCount = simRepository.countPrincipalesByGrupo(sim.getGrupo());
                    Long nonPrincipalCount = simRepository.countNonPrincipalesByGrupo(sim.getGrupo());

                    // Para SIM NO principal, solo validar:
                    // 1. Que no exceda el límite de 5 SIMs no principales
                    // 2. Que el total no exceda 6 SIMs (1 principal + 5 no principales)
                    if (nonPrincipalCount >= 5) {
                        throw new IllegalStateException("El grupo seleccionado ya tiene el máximo de 5 SIMs no principales.");
                    }
                    if (principalCount + nonPrincipalCount >= 6) {
                        throw new IllegalStateException("El grupo seleccionado ya tiene el máximo de 6 SIMs.");
                    }
                } else {
                    throw new IllegalStateException("Debe especificar un grupo para SIM no principal.");
                }
            }

            // Establecer valores por defecto para SIMs de TSS
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
            System.out.println("Vinculando equipo con IMEI: " + savedSim.getEquipo().getImei() + " a SIM con ID: " + savedSim.getId());
        } else if (savedSim.getId() != null && sim.getEquipo() == null) {
            System.out.println("Desvinculando equipo de SIM con ID: " + savedSim.getId());
        }

        // Crear transacción automática para SIMs de TSS
        if (savedSim.getResponsable() == ResponsableSimEnum.TSS) {
            try {
                crearTransaccionAutomatica(savedSim);
            } catch (Exception e) {
                System.err.println("Error al crear transacción automática para SIM " + savedSim.getNumero() + ": " + e.getMessage());
                // No fallar la creación de la SIM por un error en la transacción
            }
        }

        return savedSim;
    }

    private void crearTransaccionAutomatica(Sim sim) {
        // Buscar o crear la categoría "Recarga de Saldos"
        Optional<CategoriaTransacciones> categoriaOpt = categoriaRepository.findByDescripcion("Recarga de Saldos");
        CategoriaTransacciones categoria;

        if (categoriaOpt.isPresent()) {
            categoria = categoriaOpt.get();
        } else {
            categoria = new CategoriaTransacciones();
            categoria.setTipo(com.tss.tssmanager_backend.enums.TipoTransaccionEnum.GASTO);
            categoria.setDescripcion("Recarga de Saldos");
            categoria = categoriaRepository.save(categoria);
        }

        // Determinar nombre de la cuenta
        String nombreCuenta = "AG";
        if (sim.getEquipo() != null && sim.getEquipo().getClienteId() != null) {
            try {
                Optional<Empresa> empresaOpt = empresaRepository.findById(sim.getEquipo().getClienteId());
                if (empresaOpt.isPresent()) {
                    nombreCuenta = empresaOpt.get().getNombre();
                }
            } catch (Exception e) {
                System.err.println("Error obteniendo empresa: " + e.getMessage());
                nombreCuenta = "AG";
            }
        }

        BigDecimal monto = sim.getRecarga() != null ? sim.getRecarga() : new BigDecimal("50.00");

        // Obtener fecha de pago basada en la vigencia de la SIM
        LocalDate fechaPago;
        if (sim.getVigencia() != null) {
            fechaPago = sim.getVigencia().toLocalDate();
        } else {
            fechaPago = LocalDate.now().plusDays(30);
        }

        // Crear la transacción
        Transaccion transaccion = new Transaccion();
        transaccion.setFecha(LocalDate.now());
        transaccion.setTipo(com.tss.tssmanager_backend.enums.TipoTransaccionEnum.GASTO);
        transaccion.setCategoria(categoria);
        transaccion.setNombreCuenta(nombreCuenta);
        transaccion.setEsquema(EsquemaTransaccionEnum.MENSUAL);
        transaccion.setNumeroPagos(1);
        transaccion.setFechaPago(fechaPago);
        transaccion.setMonto(monto);
        transaccion.setFormaPago("01");
        transaccion.setNotas(sim.getNumero());

        transaccionService.agregarTransaccion(transaccion);
    }

    public List<Integer> obtenerGruposDisponibles() {
        List<Integer> allGroups = simRepository.findAllGroups();
        return allGroups.stream()
                .filter(group -> {
                    // El grupo 0 no se incluye en grupos disponibles para selección normal
                    if (group == 0) return false;

                    Long principalCount = simRepository.countPrincipalesByGrupo(group);
                    Long nonPrincipalCount = simRepository.countNonPrincipalesByGrupo(group);

                    // Un grupo está disponible si:
                    // 1. Tiene una SIM principal Y menos de 5 SIMs no principales
                    // 2. O no tiene SIM principal aún (para futuras SIMs principales)
                    return (principalCount == 1 && nonPrincipalCount < 5) ||
                            (principalCount == 0 && nonPrincipalCount == 0);
                })
                .toList();
    }

    public List<SimDTO> obtenerTodasLasSims() {
        return simRepository.findAllWithEquipo().stream()
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
        historialSaldosSimRepository.deleteBySimNumero(sim.getNumero());
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
        historial.setDatos((sim.getTarifa() == TarifaSimEnum.SIN_LIMITE || sim.getTarifa() == TarifaSimEnum.M2M_GLOBAL_15) ? datos : null);
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
        return historialSaldosSimRepository.findBySimNumero(sim.getNumero());
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
            // Nuevo mapeo usando IMEI
            dto.setEquipoImei(sim.getEquipo().getImei());
            dto.setEquipoNombre(sim.getEquipo().getNombre());
            // Mantener compatibilidad temporal
            dto.setEquipoId(sim.getEquipo().getId());
        }

        return dto;
    }
}