package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.PagedResponseDTO;
import com.tss.tssmanager_backend.dto.SimDTO;
import com.tss.tssmanager_backend.entity.Equipo;
import com.tss.tssmanager_backend.entity.HistorialSaldosSim;
import com.tss.tssmanager_backend.entity.Sim;
import com.tss.tssmanager_backend.enums.PrincipalSimEnum;
import com.tss.tssmanager_backend.enums.ResponsableSimEnum;
import com.tss.tssmanager_backend.enums.TarifaSimEnum;
import com.tss.tssmanager_backend.repository.EquipoRepository;
import com.tss.tssmanager_backend.service.SimService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sims")
public class SimController {

    @Autowired
    private SimService simService;

    @Autowired
    private EquipoRepository equipoRepository;

    @GetMapping
    public ResponseEntity<List<SimDTO>> obtenerTodasLasSims() {
        return ResponseEntity.ok(simService.obtenerTodasLasSims());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SimDTO> obtenerSimPorId(@PathVariable Integer id) {
        return simService.obtenerSimPorId(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crearSim(@RequestBody Map<String, Object> simData) {
        try {
            String numero = (String) simData.get("numero");
            if (simService.existeNumeroSim(numero, null)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Número duplicado");
                errorResponse.put("message", "Ya existe una SIM con este número");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            Sim sim = new Sim();
            sim.setNumero((String) simData.get("numero"));
            sim.setTarifa(TarifaSimEnum.valueOf((String) simData.get("tarifa")));
            sim.setResponsable(ResponsableSimEnum.valueOf((String) simData.get("responsable")));
            sim.setPrincipal(PrincipalSimEnum.valueOf((String) simData.get("principal")));
            sim.setGrupo(simData.get("grupo") != null ? Integer.parseInt(simData.get("grupo").toString()) : null);
            sim.setRecarga(simData.get("recarga") != null ? new BigDecimal(simData.get("recarga").toString()) : null);
            sim.setContrasena((String) simData.get("contrasena"));

            if (simData.get("vigencia") != null) {
                String vigenciaStr = (String) simData.get("vigencia");
                LocalDate localDate = LocalDate.parse(vigenciaStr);
                sim.setVigencia(Date.valueOf(localDate));
            }

            // Manejo del equipo por IMEI
            if (simData.get("equipo") instanceof Map) {
                Map<String, Object> equipoData = (Map<String, Object>) simData.get("equipo");
                String imei = (String) equipoData.get("imei");
                Integer equipoId = (Integer) equipoData.get("id");

                System.out.println("=== EQUIPO DATA ===");
                System.out.println("IMEI: " + imei + ", ID: " + equipoId);

                if (imei != null && !imei.trim().isEmpty()) {
                    Equipo equipo = equipoRepository.findByImei(imei)
                            .orElseThrow(() -> new EntityNotFoundException("Equipo no encontrado con IMEI: " + imei));
                    sim.setEquipo(equipo);
                    System.out.println("Equipo encontrado: " + equipo.getNombre());
                } else if (equipoId != null) {
                    Equipo equipo = equipoRepository.findById(equipoId)
                            .orElseThrow(() -> new EntityNotFoundException("Equipo no encontrado con ID: " + equipoId));
                    sim.setEquipo(equipo);
                }
            } else if (simData.get("equipo") == null) {
                sim.setEquipo(null);
            }

            Sim simGuardada = simService.guardarSim(sim);
            return ResponseEntity.ok(simGuardada);

        } catch (IllegalStateException e) {
            System.err.println("IllegalStateException: " + e.getMessage());
            e.printStackTrace();

            // Crear respuesta de error más específica
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Estado inválido");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());

            return ResponseEntity.badRequest().body(errorResponse);

        } catch (IllegalArgumentException e) {
            System.err.println("IllegalArgumentException: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Argumento inválido");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());

            return ResponseEntity.badRequest().body(errorResponse);

        } catch (EntityNotFoundException e) {
            System.err.println("EntityNotFoundException: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Entidad no encontrada");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());

            return ResponseEntity.badRequest().body(errorResponse);

        } catch (Exception e) {
            System.err.println("Exception general: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error interno");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Sim> actualizarSim(@PathVariable Integer id, @RequestBody Map<String, Object> simData) {
        try {
            Sim sim = simService.obtenerSim(id);
            sim.setNumero((String) simData.get("numero"));
            sim.setTarifa(TarifaSimEnum.valueOf((String) simData.get("tarifa")));
            sim.setResponsable(ResponsableSimEnum.valueOf((String) simData.get("responsable")));
            sim.setPrincipal(PrincipalSimEnum.valueOf((String) simData.get("principal")));
            sim.setGrupo(simData.get("grupo") != null ? Integer.parseInt(simData.get("grupo").toString()) : sim.getGrupo());
            sim.setRecarga(simData.get("recarga") != null ? new BigDecimal(simData.get("recarga").toString()) : sim.getRecarga());
            sim.setContrasena((String) simData.get("contrasena"));
            if (simData.get("vigencia") != null) {
                String vigenciaStr = (String) simData.get("vigencia");
                LocalDate localDate = LocalDate.parse(vigenciaStr);
                sim.setVigencia(Date.valueOf(localDate));
            }

            // Manejo del equipo por IMEI
            if (simData.get("equipo") instanceof Map) {
                Map<String, Object> equipoData = (Map<String, Object>) simData.get("equipo");
                String imei = (String) equipoData.get("imei");
                Integer equipoId = (Integer) equipoData.get("id");

                if (imei != null && !imei.trim().isEmpty()) {
                    Equipo equipo = equipoRepository.findByImei(imei)
                            .orElseThrow(() -> new EntityNotFoundException("Equipo no encontrado con IMEI: " + imei));
                    sim.setEquipo(equipo);
                } else if (equipoId != null) {
                    Equipo equipo = equipoRepository.findById(equipoId)
                            .orElseThrow(() -> new EntityNotFoundException("Equipo no encontrado con ID: " + equipoId));
                    sim.setEquipo(equipo);
                } else {
                    sim.setEquipo(null); // Desvincular si el IMEI/ID es null
                }
            } else if (simData.get("equipo") == null) {
                sim.setEquipo(null); // Desvincular si no se envía equipo
            }

            Sim simActualizada = simService.guardarSim(sim);
            return ResponseEntity.ok(simActualizada);
        } catch (IllegalStateException | IllegalArgumentException | EntityNotFoundException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarSim(@PathVariable Integer id) {
        try {
            simService.eliminarSim(id);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/disponibles")
    public ResponseEntity<List<SimDTO>> obtenerSimsDisponibles() {
        return ResponseEntity.ok(simService.obtenerSimsDisponibles());
    }

    @GetMapping("/grupos-disponibles")
    public ResponseEntity<List<Integer>> obtenerGruposDisponibles() {
        return ResponseEntity.ok(simService.obtenerGruposDisponibles());
    }

    @GetMapping("/validar-numero/{numero}")
    public ResponseEntity<Map<String, Boolean>> validarNumero(
            @PathVariable String numero,
            @RequestParam(required = false) Integer excludeId) {
        boolean existe = simService.existeNumeroSim(numero, excludeId);
        Map<String, Boolean> response = Map.of("disponible", !existe);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/saldo")
    public ResponseEntity<Map<String, Object>> guardarSaldo(
            @PathVariable Integer id,
            @RequestParam(required = false) BigDecimal saldoActual,
            @RequestParam(required = false) BigDecimal datos,
            @RequestParam(required = false) String fecha) {
        try {
            Date fechaDate = fecha != null ? Date.valueOf(LocalDate.parse(fecha)) : Date.valueOf(LocalDate.now());
            simService.guardarSaldo(id, saldoActual, datos, fechaDate);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Saldo registrado correctamente");
            response.put("simActualizada", simService.obtenerSimDTOPorId(id));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}/historial")
    public ResponseEntity<List<HistorialSaldosSim>> obtenerHistorialSaldos(@PathVariable Integer id) {
        List<HistorialSaldosSim> historial = simService.obtenerHistorialSaldos(id);
        return ResponseEntity.ok(historial);
    }

    @GetMapping("/filtered")
    public ResponseEntity<List<SimDTO>> obtenerSimsFiltradas(
            @RequestParam(required = false) Integer grupo,
            @RequestParam(required = false) String numero) {
        List<SimDTO> sims = simService.obtenerTodasLasSimsConFiltros(grupo, numero);
        return ResponseEntity.ok(sims);
    }


    @GetMapping("/grupos-todos")
    public ResponseEntity<List<Integer>> obtenerTodosLosGrupos() {
        return ResponseEntity.ok(simService.obtenerTodosLosGrupos());
    }

    @GetMapping("/equipos-disponibles")
    public ResponseEntity<List<Map<String, Object>>> obtenerEquiposParaSims() {
        try {
            List<Object[]> results = equipoRepository.findEquiposForSimSelection();
            List<Map<String, Object>> equipos = results.stream()
                    .map(row -> {
                        Map<String, Object> equipo = new HashMap<>();
                        equipo.put("id", row[0]);
                        equipo.put("imei", row[1]);
                        equipo.put("nombre", row[2]);
                        equipo.put("tipo", row[3]);
                        equipo.put("estatus", row[4]);
                        equipo.put("clienteId", row[5]);
                        equipo.put("clienteDefault", row[6]);
                        equipo.put("simReferenciada", row[7]);
                        return equipo;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(equipos);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    @GetMapping("/{id}/ultimo-saldo")
    public ResponseEntity<Map<String, Object>> obtenerUltimoSaldo(@PathVariable Integer id) {
        try {
            HistorialSaldosSim ultimoSaldo = simService.obtenerUltimoSaldo(id);
            if (ultimoSaldo != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("id", ultimoSaldo.getId());
                response.put("sim", ultimoSaldo.getSim());
                response.put("fecha", ultimoSaldo.getFecha());
                response.put("saldoActual", ultimoSaldo.getSaldoActual());
                response.put("datos", ultimoSaldo.getDatos());

                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.noContent().build();
            }
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/verificar-caida-saldo")
    public ResponseEntity<Map<String, Object>> verificarCaidaSaldo(@PathVariable Integer id) {
        try {
            Sim sim = simService.obtenerSim(id);

            // Solo verificar para tarifas POR_SEGUNDO
            if (sim.getTarifa() != TarifaSimEnum.POR_SEGUNDO) {
                return ResponseEntity.ok(Map.of("tieneCaida", false));
            }
            List<HistorialSaldosSim> historial = simService.obtenerHistorialSaldos(id);
            // Necesitamos al menos 2 registros para comparar
            if (historial.size() < 2) {
                return ResponseEntity.ok(Map.of("tieneCaida", false));
            }

            HistorialSaldosSim ultimo = historial.get(0);
            HistorialSaldosSim penultimo = historial.get(1);

            if (Boolean.TRUE.equals(ultimo.getRevisado())) {
                return ResponseEntity.ok(Map.of("tieneCaida", false));
            }

            if (ultimo.getSaldoActual() == null || penultimo.getSaldoActual() == null) {
                return ResponseEntity.ok(Map.of("tieneCaida", false));
            }

            BigDecimal diferencia = penultimo.getSaldoActual().subtract(ultimo.getSaldoActual());
            boolean tieneCaida = diferencia.compareTo(new BigDecimal("10")) > 0;

            Map<String, Object> response = new HashMap<>();
            response.put("tieneCaida", tieneCaida);
            response.put("diferencia", diferencia);
            response.put("saldoActual", ultimo.getSaldoActual());
            response.put("saldoAnterior", penultimo.getSaldoActual());

            return ResponseEntity.ok(response);

        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/aprobar-alerta")
    public ResponseEntity<Void> aprobarAlerta(@PathVariable Integer id) {
        try {
            simService.aprobarAlertaSaldo(id);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/historial/{id}")
    public ResponseEntity<Void> eliminarHistorialSaldo(@PathVariable Integer id) {
        try {
            simService.eliminarHistorialSaldo(id);
            return ResponseEntity.ok().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/historial/{id}")
    public ResponseEntity<Map<String, Object>> actualizarHistorialSaldo(
            @PathVariable Integer id,
            @RequestParam(required = false) BigDecimal saldoActual,
            @RequestParam(required = false) BigDecimal datos,
            @RequestParam(required = false) String fecha) {
        try {
            Date fechaDate = fecha != null ? Date.valueOf(LocalDate.parse(fecha)) : null;
            simService.actualizarHistorialSaldo(id, saldoActual, datos, fechaDate);
            return ResponseEntity.ok(Map.of("message", "Historial actualizado correctamente"));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}