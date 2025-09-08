package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.EquiposEstatusDTO;
import com.tss.tssmanager_backend.entity.Equipo;
import com.tss.tssmanager_backend.enums.EstatusEquipoEnum;
import com.tss.tssmanager_backend.enums.PlataformaEquipoEnum;
import com.tss.tssmanager_backend.enums.TipoActivacionEquipoEnum;
import com.tss.tssmanager_backend.enums.TipoEquipoEnum;
import com.tss.tssmanager_backend.service.EquipoService;
import com.tss.tssmanager_backend.service.SimService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/equipos")
public class EquipoController {

    @Autowired
    private EquipoService service;

    @Autowired
    private SimService simService;

    @GetMapping
    public ResponseEntity<Iterable<Equipo>> obtenerTodosLosEquipos() {
        return ResponseEntity.ok(service.obtenerTodosLosEquipos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Equipo> obtenerEquipo(@PathVariable Integer id) {
        return ResponseEntity.ok(service.obtenerEquipo(id));
    }

    @PostMapping
    public ResponseEntity<Equipo> guardarEquipo(@RequestBody Map<String, Object> equipoMap) {
        Equipo equipo = convertMapToEquipo(equipoMap);
        return ResponseEntity.ok(service.guardarEquipo(equipo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Equipo> actualizarEquipo(@PathVariable Integer id, @RequestBody Map<String, Object> equipoMap) {
        Equipo equipo = convertMapToEquipo(equipoMap);
        return ResponseEntity.ok(service.actualizarEquipo(id, equipo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarEquipo(@PathVariable Integer id) {
        service.eliminarEquipo(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activar")
    public ResponseEntity<Void> activarEquipo(@PathVariable Integer id) {
        service.activarEquipo(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/renovar")
    public ResponseEntity<Void> renovarEquipo(@PathVariable Integer id) {
        service.renovarEquipo(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/count-by-modelo")
    public ResponseEntity<Map<Integer, Long>> contarEquiposPorModelo() {
        return ResponseEntity.ok(service.contarEquiposPorModelo());
    }

    @PostMapping("/estatus")
    public ResponseEntity<Void> guardarEstatus(@RequestBody List<Map<String, Object>> estatusList) {
        service.guardarEstatus(estatusList);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/estatus")
    public ResponseEntity<List<EquiposEstatusDTO>> obtenerEstatus() {
        return ResponseEntity.ok(service.obtenerEstatus());
    }

    @GetMapping("/estatus-paginado")
    public ResponseEntity<Page<EquiposEstatusDTO>> obtenerEstatusPaginado(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(service.obtenerEstatusPaginado(page, size));
    }

    private Equipo convertMapToEquipo(Map<String, Object> equipoMap) {
        Equipo equipo = new Equipo();
        equipo.setImei((String) equipoMap.get("imei"));
        equipo.setNombre((String) equipoMap.get("nombre"));

        // Conversión segura de modeloId
        Object modeloIdObj = equipoMap.get("modeloId");
        if (modeloIdObj instanceof String) {
            try {
                equipo.setModeloId(Integer.parseInt((String) modeloIdObj));
            } catch (NumberFormatException e) {
                equipo.setModeloId(null);
            }
        } else if (modeloIdObj instanceof Integer) {
            equipo.setModeloId((Integer) modeloIdObj);
        } else {
            equipo.setModeloId(null);
        }

        // Conversión segura de proveedorId
        Object proveedorIdObj = equipoMap.get("proveedorId");
        if (proveedorIdObj instanceof String) {
            try {
                equipo.setProveedorId(Integer.parseInt((String) proveedorIdObj));
            } catch (NumberFormatException e) {
                equipo.setProveedorId(null);
            }
        } else if (proveedorIdObj instanceof Integer) {
            equipo.setProveedorId((Integer) proveedorIdObj);
        } else {
            equipo.setProveedorId(null);
        }

        // Manejo de simReferenciada como objeto Sim
        Object simReferenciadaObj = equipoMap.get("simReferenciada");
        if (simReferenciadaObj != null) {
            if (simReferenciadaObj instanceof Integer) {
                Integer simId = (Integer) simReferenciadaObj;
                equipo.setSimReferenciada(simService.obtenerSim(simId));
            } else {
                equipo.setSimReferenciada(null);
            }
        } else {
            equipo.setSimReferenciada(null);
        }

        equipo.setTipo(TipoEquipoEnum.valueOf((String) equipoMap.get("tipo")));
        equipo.setEstatus(EstatusEquipoEnum.valueOf((String) equipoMap.get("estatus")));
        equipo.setTipoActivacion(TipoActivacionEquipoEnum.valueOf((String) equipoMap.get("tipoActivacion")));
        equipo.setPlataforma(PlataformaEquipoEnum.valueOf((String) equipoMap.get("plataforma")));

        // Manejo de clienteId y clienteDefault
        Object clienteIdObj = equipoMap.get("clienteId");
        Object clienteDefaultObj = equipoMap.get("clienteDefault");

        if (clienteIdObj == null && clienteDefaultObj != null && clienteDefaultObj instanceof String) {
            String clienteDefaultStr = (String) clienteDefaultObj;
            if ("AG".equals(clienteDefaultStr) || "BN".equals(clienteDefaultStr) || "PERDIDO".equals(clienteDefaultStr)) {
                equipo.setClienteId(null);
                equipo.setClienteDefault(clienteDefaultStr);
            } else {
                equipo.setClienteId(null);
                equipo.setClienteDefault(null);
            }
        } else if (clienteIdObj instanceof String) {
            String clientIdStr = (String) clienteIdObj;
            if ("AG".equals(clientIdStr) || "BN".equals(clientIdStr) || "PERDIDO".equals(clientIdStr)) {
                equipo.setClienteId(null);
                equipo.setClienteDefault(clientIdStr);
            } else {
                try {
                    equipo.setClienteId(Integer.parseInt(clientIdStr));
                    equipo.setClienteDefault(null);
                } catch (NumberFormatException e) {
                    equipo.setClienteId(null);
                    equipo.setClienteDefault(null);
                }
            }
        } else if (clienteIdObj instanceof Integer) {
            equipo.setClienteId((Integer) clienteIdObj);
            equipo.setClienteDefault(null);
        } else {
            equipo.setClienteId(null);
            equipo.setClienteDefault(null);
        }
        Object creditosUsadosObj = equipoMap.get("creditosUsados");
        if (creditosUsadosObj instanceof Integer) {
            equipo.setCreditosUsados((Integer) creditosUsadosObj);
        } else if (creditosUsadosObj instanceof String) {
            try {
                equipo.setCreditosUsados(Integer.parseInt((String) creditosUsadosObj));
            } catch (NumberFormatException e) {
                equipo.setCreditosUsados(0);
            }
        } else {
            equipo.setCreditosUsados(0);
        }
        return equipo;
    }

    @GetMapping("/dashboard-estatus")
    public ResponseEntity<Map<String, Object>> obtenerDashboardEstatus() {
        return ResponseEntity.ok(service.obtenerDashboardEstatusOptimizado());
    }

    @PostMapping("/{id}/activar-con-creditos")
    public ResponseEntity<Void> activarEquipoConCreditos(
            @PathVariable Integer id,
            @RequestBody Map<String, Object> request) {
        Integer creditosUsados = (Integer) request.get("creditosUsados");
        service.activarEquipoConCreditos(id, creditosUsados);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/renovar-con-creditos")
    public ResponseEntity<Void> renovarEquipoConCreditos(
            @PathVariable Integer id,
            @RequestBody Map<String, Object> request) {
        Integer creditosUsados = (Integer) request.get("creditosUsados");
        service.renovarEquipoConCreditos(id, creditosUsados);
        return ResponseEntity.ok().build();
    }
}