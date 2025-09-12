package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.PagedResponseDTO;
import com.tss.tssmanager_backend.dto.SimDTO;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.EsquemaTransaccionEnum;
import com.tss.tssmanager_backend.enums.PrincipalSimEnum;
import com.tss.tssmanager_backend.enums.ResponsableSimEnum;
import com.tss.tssmanager_backend.enums.TarifaSimEnum;
import com.tss.tssmanager_backend.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
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
    private CuentasTransaccionesRepository cuentaRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private CuentaPorPagarRepository cuentaPorPagarRepository;

    @Transactional
    public Sim guardarSim(Sim sim) {

        boolean esSimNueva = sim.getId() == null;
        if (existeNumeroSim(sim.getNumero(), sim.getId())) {
            throw new IllegalStateException("Ya existe una SIM con este número");
        }
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
        if (savedSim.getResponsable() == ResponsableSimEnum.TSS && esSimNueva) {
            try {
                System.out.println("Iniciando proceso de creación de transacción automática para SIM nueva: " + savedSim.getNumero());
                crearTransaccionAutomatica(savedSim);
                System.out.println("Proceso de transacción automática completado para SIM: " + savedSim.getNumero());
            } catch (Exception e) {
                System.err.println("Error al crear transacción automática para SIM " + savedSim.getNumero() + ": " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Error crítico al crear transacción automática: " + e.getMessage(), e);
            }
        }

        else if (savedSim.getResponsable() == ResponsableSimEnum.TSS && !esSimNueva) {
            try {
                actualizarCuentasPorPagarExistentes(savedSim);
            } catch (Exception e) {
                System.err.println("Error al actualizar cuentas por pagar para SIM " + savedSim.getNumero() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Limpiar caché al final del proceso
        limpiarCacheSims();

        return savedSim;
    }

    @CacheEvict(value = {"gruposDisponibles", "simsDisponibles"}, allEntries = true)
    public void limpiarCacheSims() {
        System.out.println("Cache de SIMs y grupos disponibles limpiado");
    }

    private void crearTransaccionAutomatica(Sim sim) {
        System.out.println("=== INICIO crearTransaccionAutomatica para SIM: " + sim.getNumero() + " ===");

        try {
            // Determinar la descripción de la categoría según la tarifa
            String nombreCuenta = "AG";
            if (sim.getEquipo() != null) {
                System.out.println("SIM tiene equipo vinculado: " + sim.getEquipo().getImei());
                // Primero verificar si tiene clienteId
                if (sim.getEquipo().getClienteId() != null) {
                    try {
                        Optional<Empresa> empresaOpt = empresaRepository.findById(sim.getEquipo().getClienteId());
                        if (empresaOpt.isPresent()) {
                            nombreCuenta = empresaOpt.get().getNombre();
                            System.out.println("Cuenta determinada por clienteId: " + nombreCuenta);
                        }
                    } catch (Exception e) {
                        System.err.println("Error obteniendo empresa por clienteId: " + e.getMessage());
                        nombreCuenta = "AG";
                    }
                }
                // Si no tiene clienteId pero sí clienteDefault, usar el clienteDefault
                else if (sim.getEquipo().getClienteDefault() != null) {
                    nombreCuenta = sim.getEquipo().getClienteDefault();
                    System.out.println("Cuenta determinada por clienteDefault: " + nombreCuenta);
                }
            } else {
                System.out.println("SIM sin equipo vinculado, usando cuenta por defecto: AG");
            }

            String descripcionCategoria;
            if (sim.getTarifa() == TarifaSimEnum.M2M_GLOBAL_15) {
                descripcionCategoria = "M2M";
            } else {
                descripcionCategoria = "TELCEL";
            }
            System.out.println("Categoría determinada: " + descripcionCategoria);

            Optional<CategoriaTransacciones> categoriaOpt = categoriaRepository.findByDescripcionIgnoreCase(descripcionCategoria);
            CategoriaTransacciones categoria;

            if (categoriaOpt.isPresent()) {
                categoria = categoriaOpt.get();
                System.out.println("Categoría encontrada: " + categoria.getId());
            } else {
                System.out.println("Creando nueva categoría: " + descripcionCategoria);
                categoria = new CategoriaTransacciones();
                categoria.setTipo(com.tss.tssmanager_backend.enums.TipoTransaccionEnum.GASTO);
                categoria.setDescripcion(descripcionCategoria);
                categoria = categoriaRepository.save(categoria);
                System.out.println("Nueva categoría creada con ID: " + categoria.getId());
            }

            CuentasTransacciones cuentaTransaccion = buscarOCrearCuentaConCategoria(nombreCuenta, categoria);
            System.out.println("Cuenta de transacción obtenida: " + cuentaTransaccion.getId() + " - " + cuentaTransaccion.getNombre());

            BigDecimal monto = sim.getRecarga() != null ? sim.getRecarga() : new BigDecimal("50.00");
            System.out.println("Monto de la transacción: " + monto);

            // Obtener fecha de pago basada en la vigencia de la SIM
            LocalDate fechaPago;
            if (sim.getVigencia() != null) {
                fechaPago = sim.getVigencia().toLocalDate();
            } else {
                fechaPago = LocalDate.now().plusDays(30);
            }
            System.out.println("Fecha de pago calculada: " + fechaPago);

            // Crear la transacción
            Transaccion transaccion = new Transaccion();
            transaccion.setFecha(LocalDate.now());
            transaccion.setTipo(com.tss.tssmanager_backend.enums.TipoTransaccionEnum.GASTO);
            transaccion.setCategoria(categoria);
            transaccion.setCuenta(cuentaTransaccion);
            transaccion.setEsquema(EsquemaTransaccionEnum.MENSUAL);
            transaccion.setNumeroPagos(1);
            transaccion.setFechaPago(fechaPago);
            transaccion.setMonto(monto);
            transaccion.setFormaPago("01");
            transaccion.setNotas("Transacción automática para SIM");

            System.out.println("Llamando a transaccionService.agregarTransaccion...");
            transaccionService.agregarTransaccion(transaccion);
            System.out.println("Transacción creada exitosamente con ID: " + transaccion.getId());

            // Pausa pequeña para asegurar que la transacción se haya guardado completamente
            Thread.sleep(100);

            // Buscar las cuentas por pagar creadas
            List<CuentaPorPagar> cuentasCreadas = cuentaPorPagarRepository.findByTransaccionId(transaccion.getId());
            System.out.println("Cuentas por pagar encontradas: " + cuentasCreadas.size());

            if (cuentasCreadas.isEmpty()) {
                System.err.println("ALERTA: No se encontraron cuentas por pagar para la transacción " + transaccion.getId());
                throw new RuntimeException("No se generaron cuentas por pagar para la transacción");
            }

            // Asociar cada cuenta por pagar a la SIM
            for (CuentaPorPagar cuenta : cuentasCreadas) {
                System.out.println("Asociando cuenta por pagar " + cuenta.getId() + " a SIM " + sim.getNumero());
                cuenta.setSim(sim);
                cuentaPorPagarRepository.save(cuenta);
                System.out.println("Cuenta por pagar " + cuenta.getId() + " asociada exitosamente");
            }

            System.out.println("SIM " + sim.getNumero() + " asociada exitosamente a " + cuentasCreadas.size() + " cuentas por pagar");
            System.out.println("=== FIN crearTransaccionAutomatica para SIM: " + sim.getNumero() + " ===");

        } catch (Exception e) {
            System.err.println("=== ERROR en crearTransaccionAutomatica para SIM: " + sim.getNumero() + " ===");
            System.err.println("Mensaje de error: " + e.getMessage());
            e.printStackTrace();
            System.err.println("=== FIN ERROR ===");
            throw new RuntimeException("Error crítico en creación de transacción automática: " + e.getMessage(), e);
        }
    }

    private CuentasTransacciones buscarOCrearCuentaConCategoria(String nombreCuenta, CategoriaTransacciones categoria) {
        // Buscar cuentas por nombre y categoría específica
        List<CuentasTransacciones> cuentasExistentes = cuentaRepository.findByNombreAndCategoria_Id(nombreCuenta, categoria.getId());

        if (!cuentasExistentes.isEmpty()) {
            System.out.println("Cuenta encontrada con categoría correcta: " + nombreCuenta + " - " + categoria.getDescripcion());
            return cuentasExistentes.get(0);
        }

        // Si no existe, crear nueva cuenta con la categoría correcta
        System.out.println("Creando nueva cuenta: " + nombreCuenta + " - " + categoria.getDescripcion());
        CuentasTransacciones nuevaCuenta = new CuentasTransacciones();
        nuevaCuenta.setNombre(nombreCuenta);
        nuevaCuenta.setCategoria(categoria);

        return cuentaRepository.save(nuevaCuenta);
    }

    private void actualizarCuentasPorPagarExistentes(Sim sim) {
        List<CuentaPorPagar> cuentasExistentes = cuentaPorPagarRepository.findBySimId(sim.getId());

        for (CuentaPorPagar cuenta : cuentasExistentes) {
            // Actualizar el monto si cambió la recarga
            if (sim.getRecarga() != null) {
                cuenta.setMonto(sim.getRecarga());
            }
            // Actualizar fecha de vencimiento si cambió la vigencia
            if (sim.getVigencia() != null) {
                cuenta.setFechaPago(sim.getVigencia().toLocalDate());
            }

            // Actualizar el nombre de la cuenta si cambió el equipo vinculado
            if (sim.getEquipo() != null) {
                String nuevoNombreCuenta = "AG";
                if (sim.getEquipo().getClienteId() != null) {
                    try {
                        Optional<Empresa> empresaOpt = empresaRepository.findById(sim.getEquipo().getClienteId());
                        if (empresaOpt.isPresent()) {
                            nuevoNombreCuenta = empresaOpt.get().getNombre();
                        }
                    } catch (Exception e) {
                        System.err.println("Error obteniendo empresa: " + e.getMessage());
                        nuevoNombreCuenta = "AG";
                    }
                } else if (sim.getEquipo().getClienteDefault() != null) {
                    nuevoNombreCuenta = sim.getEquipo().getClienteDefault();
                }

                // Buscar o crear la cuenta con el nuevo nombre
                CuentasTransacciones nuevaCuenta = buscarOCrearCuentaConCategoria(nuevoNombreCuenta, cuenta.getTransaccion().getCategoria());
                cuenta.getTransaccion().setCuenta(nuevaCuenta);
            }

            cuentaPorPagarRepository.save(cuenta);
        }
    }

    @Cacheable(value = "gruposDisponibles", unless = "#result.isEmpty()")
    public List<Integer> obtenerGruposDisponibles() {
        return obtenerGruposDisponiblesOptimizado();
    }


    public List<Integer> obtenerTodosLosGrupos() {
        return simRepository.findAllGroupsForFilter();
    }

    public List<SimDTO> obtenerTodasLasSimsConFiltros(Integer grupo, String numero) {
        List<Object[]> results = simRepository.findSimsPaginatedWithFilters(grupo, numero);

        List<SimDTO> content = results.stream()
                .map(this::convertFromOptimizedQuery)
                .collect(Collectors.toList());

        // Obtener saldos de forma más eficiente
        List<Integer> simIds = content.stream()
                .map(SimDTO::getId)
                .collect(Collectors.toList());

        Map<Integer, String> ultimosSaldos = obtenerUltimosSaldosParaLista(simIds);

        content.forEach(sim -> {
            String ultimoSaldo = ultimosSaldos.getOrDefault(sim.getId(), "N/A");
            sim.setUltimoSaldoRegistrado(ultimoSaldo);
        });

        return content;
    }

    public List<SimDTO> obtenerTodasLasSims() {
        List<Object[]> results = simRepository.findAllWithEquipoNative();
        return results.stream()
                .map(this::convertFromNativeQuery)
                .collect(Collectors.toList());
    }

    private SimDTO convertFromNativeQuery(Object[] row) {
        SimDTO dto = new SimDTO();
        // Mapear campos básicos de SIM
        dto.setId((Integer) row[0]);
        dto.setNumero((String) row[1]);
        dto.setTarifa(TarifaSimEnum.valueOf((String) row[2]));
        dto.setVigencia((Date) row[3]);
        dto.setRecarga((BigDecimal) row[4]);
        dto.setResponsable(ResponsableSimEnum.valueOf((String) row[5]));
        dto.setPrincipal(PrincipalSimEnum.valueOf((String) row[6]));
        dto.setGrupo((Integer) row[7]);
        dto.setContrasena((String) row[9]);

        // Mapear equipo si existe
        if (row[10] != null) { // equipo_imei
            dto.setEquipoImei((String) row[10]);
            dto.setEquipoNombre((String) row[11]);
        }

        return dto;
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

    @Cacheable(value = "simsDisponibles", unless = "#result.isEmpty()")
    public List<SimDTO> obtenerSimsDisponibles() {
        return simRepository.findAvailableSims().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public boolean existeNumeroSim(String numero, Integer excludeId) {
        if (excludeId != null) {
            return simRepository.findByNumeroOptimized(numero)
                    .map(sim -> !sim.getId().equals(excludeId))
                    .orElse(false);
        }
        return simRepository.findByNumeroOptimized(numero).isPresent();
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
        return historialSaldosSimRepository.findBySimNumeroOrderByFechaDesc(sim.getNumero());
    }

    private Integer generarNuevoGrupo() {
        Integer maxGroup = simRepository.findMaxGrupoExcludingClientes();
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


    private SimDTO convertFromOptimizedQuery(Object[] row) {
        SimDTO dto = new SimDTO();
        dto.setId((Integer) row[0]);
        dto.setNumero((String) row[1]);
        dto.setTarifa(TarifaSimEnum.valueOf((String) row[2]));
        dto.setVigencia((Date) row[3]);
        dto.setRecarga((BigDecimal) row[4]);
        dto.setResponsable(ResponsableSimEnum.valueOf((String) row[5]));
        dto.setPrincipal(PrincipalSimEnum.valueOf((String) row[6]));
        dto.setGrupo((Integer) row[7]);
        dto.setContrasena((String) row[9]);

        if (row[10] != null) {
            dto.setEquipoImei((String) row[10]);
            dto.setEquipoNombre((String) row[11]);
        }

        return dto;
    }

    @Transactional(readOnly = true)
    public SimDTO obtenerSimDTOPorId(Integer id) {
        return simRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new EntityNotFoundException("SIM no encontrada"));
    }

    @Cacheable(value = "gruposDisponibles", unless = "#result.isEmpty()")
        public List<Integer> obtenerGruposDisponiblesOptimizado() {
            return simRepository.findAllGroupsOptimized().stream()
                    .filter(group -> {
                        if (group == 0) return false;

                        Long principalCount = simRepository.countPrincipalesByGrupoNative(group);
                        Long nonPrincipalCount = simRepository.countNonPrincipalesByGrupoNative(group);

                        return (principalCount == 1 && nonPrincipalCount < 5) ||
                                (principalCount == 0 && nonPrincipalCount == 0);
                    })
                    .toList();
    }

    @Transactional(readOnly = true)
    public HistorialSaldosSim obtenerUltimoSaldo(Integer simId) {
        Sim sim = simRepository.findById(simId)
                .orElseThrow(() -> new EntityNotFoundException("SIM no encontrada con ID: " + simId));

        List<HistorialSaldosSim> historial = historialSaldosSimRepository.findBySimNumero(sim.getNumero());

        return historial.stream()
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<Integer, String> obtenerUltimosSaldosParaLista(List<Integer> simIds) {
        Map<Integer, String> ultimosSaldos = new HashMap<>();

        for (Integer simId : simIds) {
            try {
                Sim sim = simRepository.findById(simId).orElse(null);
                if (sim == null) continue;

                List<HistorialSaldosSim> historial = historialSaldosSimRepository.findBySimNumeroOrderByFechaDesc(sim.getNumero());

                if (!historial.isEmpty()) {
                    HistorialSaldosSim ultimoRegistro = historial.get(0);
                    if (ultimoRegistro != null) {
                        if (sim.getTarifa() == TarifaSimEnum.POR_SEGUNDO && ultimoRegistro.getSaldoActual() != null) {
                            ultimosSaldos.put(simId, "$" + ultimoRegistro.getSaldoActual().toString());
                        } else if ((sim.getTarifa() == TarifaSimEnum.SIN_LIMITE || sim.getTarifa() == TarifaSimEnum.M2M_GLOBAL_15)
                                && ultimoRegistro.getDatos() != null) {
                            int datosEnteros = ultimoRegistro.getDatos().intValue();
                            ultimosSaldos.put(simId, datosEnteros + " MB");
                        } else {
                            ultimosSaldos.put(simId, "N/A");
                        }
                    }
                     else {
                        ultimosSaldos.put(simId, "N/A");
                    }
                } else {
                    ultimosSaldos.put(simId, "Sin registros");
                }
            } catch (Exception e) {
                ultimosSaldos.put(simId, "Error");
            }
        }

        return ultimosSaldos;
    }
}