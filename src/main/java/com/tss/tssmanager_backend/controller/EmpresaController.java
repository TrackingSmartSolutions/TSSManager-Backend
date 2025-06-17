package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.ContactoDTO;
import com.tss.tssmanager_backend.dto.EmpresaConContactoDTO;
import com.tss.tssmanager_backend.dto.EmpresaDTO;
import com.tss.tssmanager_backend.entity.Empresa;
import com.tss.tssmanager_backend.entity.Usuario;
import com.tss.tssmanager_backend.enums.EstatusEmpresaEnum;
import com.tss.tssmanager_backend.exception.ResourceNotFoundException;
import com.tss.tssmanager_backend.service.EmpresaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/empresas")
public class EmpresaController {

    private static final Logger logger = LoggerFactory.getLogger(EmpresaController.class);

    @Autowired
    private EmpresaService empresaService;

    @PostMapping
    public ResponseEntity<EmpresaDTO> agregarEmpresa(@RequestBody EmpresaDTO empresaDTO) {
        try {
            logger.debug("Solicitud para agregar empresa: {}", empresaDTO);
            Empresa empresa = convertToEntity(empresaDTO);
            EmpresaDTO nuevaEmpresaDTO = empresaService.agregarEmpresaConContactos(empresa, null); // Pasamos null para contactos
            return ResponseEntity.ok(nuevaEmpresaDTO);
        } catch (IllegalArgumentException | SecurityException e) {
            logger.error("Error al agregar empresa: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            logger.error("Error interno al agregar empresa: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmpresaDTO> editarEmpresa(@PathVariable Integer id, @RequestBody EmpresaDTO empresaDTO) {
        try {
            logger.debug("Solicitud para editar empresa con ID: {}", id);
            Empresa empresa = convertToEntity(empresaDTO);
            EmpresaDTO empresaActualizadaDTO = empresaService.editarEmpresa(id, empresa);
            return ResponseEntity.ok(empresaActualizadaDTO);
        } catch (ResourceNotFoundException e) {
            logger.error("Empresa no encontrada con ID: {}", id, e);
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            logger.error("Error al editar empresa: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            logger.error("Error interno al editar empresa: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarEmpresa(@PathVariable Integer id) {
        try {
            logger.debug("Solicitud para eliminar empresa con ID: {}", id);
            empresaService.eliminarEmpresa(id);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            logger.error("Empresa no encontrada con ID: {}", id, e);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            logger.error("Error al eliminar empresa: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error interno al eliminar empresa: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<EmpresaDTO>> listarEmpresas(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String estatus) {
        try {
            logger.debug("Solicitud para listar empresas con nombre: {} y estatus: {}", nombre, estatus);
            List<EmpresaDTO> empresas = empresaService.listarEmpresas(nombre, estatus != null ? EstatusEmpresaEnum.valueOf(estatus) : null);
            return ResponseEntity.ok(empresas);
        } catch (IllegalArgumentException e) {
            logger.error("Parámetro inválido al listar empresas: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            logger.error("Error interno al listar empresas: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping("/{empresaId}/contactos")
    public ResponseEntity<ContactoDTO> agregarContacto(@PathVariable Integer empresaId, @RequestBody ContactoDTO contactoDTO) {
        try {
            logger.debug("Solicitud para agregar contacto a empresa con ID: {}", empresaId);
            ContactoDTO nuevoContactoDTO = empresaService.agregarContacto(empresaId, contactoDTO);
            return ResponseEntity.ok(nuevoContactoDTO);
        } catch (ResourceNotFoundException e) {
            logger.error("Empresa no encontrada con ID: {}", empresaId, e);
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            logger.error("Usuario no autenticado al agregar contacto: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            logger.error("Error interno al agregar contacto: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PutMapping("/contactos/{id}")
    public ResponseEntity<ContactoDTO> editarContacto(@PathVariable Integer id, @RequestBody ContactoDTO contactoDTO) {
        try {
            logger.debug("Solicitud para editar contacto con ID: {}", id);
            if (contactoDTO == null) {
                logger.error("El cuerpo de la solicitud para editar el contacto con ID: {} es nulo", id);
                return ResponseEntity.badRequest().body(null);
            }
            logger.debug("Datos recibidos para editar contacto: {}", contactoDTO);
            ContactoDTO contactoActualizadoDTO = empresaService.editarContacto(id, contactoDTO);
            return ResponseEntity.ok(contactoActualizadoDTO);
        } catch (ResourceNotFoundException e) {
            logger.error("Contacto no encontrado con ID: {}", id, e);
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException | SecurityException e) {
            logger.error("Error al editar contacto con ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            logger.error("Error interno al editar contacto con ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/contactos/{id}")
    public ResponseEntity<Void> eliminarContacto(@PathVariable Integer id) {
        try {
            logger.debug("Solicitud para eliminar contacto con ID: {}", id);
            empresaService.eliminarContacto(id);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            logger.error("Contacto no encontrado con ID: {}", id, e);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            logger.error("Error al eliminar contacto: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error interno al eliminar contacto: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{empresaId}/contactos")
    public ResponseEntity<List<ContactoDTO>> listarContactosPorEmpresa(@PathVariable Integer empresaId) {
        try {
            logger.debug("Solicitud para listar contactos de empresa con ID: {}", empresaId);
            List<ContactoDTO> contactos = empresaService.listarContactosPorEmpresa(empresaId);
            return ResponseEntity.ok(contactos);
        } catch (Exception e) {
            logger.error("Error interno al listar contactos: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private Empresa convertToEntity(EmpresaDTO empresaDTO) {
        Empresa empresa = new Empresa();
        empresa.setId(empresaDTO.getId());
        empresa.setNombre(empresaDTO.getNombre());
        empresa.setEstatus(empresaDTO.getEstatus());
        empresa.setSitioWeb(empresaDTO.getSitioWeb());
        empresa.setSector(empresaDTO.getSector());
        empresa.setDomicilioFisico(empresaDTO.getDomicilioFisico());
        empresa.setDomicilioFiscal(empresaDTO.getDomicilioFiscal());
        empresa.setRfc(empresaDTO.getRfc());
        empresa.setRazonSocial(empresaDTO.getRazonSocial());
        empresa.setRegimenFiscal(empresaDTO.getRegimenFiscal());


        if (empresaDTO.getPropietarioId() != null) {
            Usuario propietario = new Usuario();
            propietario.setId(empresaDTO.getPropietarioId());
            empresa.setPropietario(propietario);
        } else if (empresaDTO.getPropietario() != null && empresaDTO.getPropietario().getId() != null) {
            Usuario propietario = new Usuario();
            propietario.setId(empresaDTO.getPropietario().getId());
            empresa.setPropietario(propietario);
        }
        return empresa;
    }

    private Empresa convertToEntity(EmpresaConContactoDTO empresaConContactoDTO) {
        Empresa empresa = new Empresa();
        empresa.setId(empresaConContactoDTO.getId());
        empresa.setNombre(empresaConContactoDTO.getNombre());
        empresa.setEstatus(empresaConContactoDTO.getEstatus());
        empresa.setSitioWeb(empresaConContactoDTO.getSitioWeb());
        empresa.setSector(empresaConContactoDTO.getSector());
        empresa.setDomicilioFisico(empresaConContactoDTO.getDomicilioFisico());
        empresa.setDomicilioFiscal(empresaConContactoDTO.getDomicilioFiscal());
        empresa.setRfc(empresaConContactoDTO.getRfc());
        empresa.setRazonSocial(empresaConContactoDTO.getRazonSocial());
        empresa.setRegimenFiscal(empresaConContactoDTO.getRegimenFiscal());
        return empresa;
    }

    @GetMapping("/{id}/has-tratos")
    public ResponseEntity<Boolean> hasTratos(@PathVariable Integer id) {
        try {
            logger.debug("Solicitud para verificar si empresa con ID: {} tiene tratos", id);
            boolean hasTratos = empresaService.hasTratos(id);
            return ResponseEntity.ok(hasTratos);
        } catch (Exception e) {
            logger.error("Error al verificar tratos para empresa con ID: {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(false);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmpresaDTO> getEmpresaById(@PathVariable Integer id) {
        try {
            logger.debug("Solicitud para obtener empresa con ID: {}", id);
            EmpresaDTO empresaDTO = empresaService.getEmpresaById(id);
            return ResponseEntity.ok(empresaDTO);
        } catch (ResourceNotFoundException e) {
            logger.error("Empresa no encontrada con ID: {}", id, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error interno al obtener empresa con ID: {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}