package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.ContactoDTO;
import com.tss.tssmanager_backend.dto.EmpresaDTO;
import com.tss.tssmanager_backend.dto.CorreoDTO;
import com.tss.tssmanager_backend.dto.PropietarioDTO;
import com.tss.tssmanager_backend.dto.TelefonoDTO;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.EstatusEmpresaEnum;
import com.tss.tssmanager_backend.enums.RolContactoEnum;
import com.tss.tssmanager_backend.exception.ResourceNotFoundException;
import com.tss.tssmanager_backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmpresaService {

    private static final Logger logger = LoggerFactory.getLogger(EmpresaService.class);

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private ContactoRepository contactoRepository;

    @Autowired
    private CorreoContactoRepository correoContactoRepository;

    @Autowired
    private TelefonoContactoRepository telefonoContactoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Transactional
    public EmpresaDTO agregarEmpresaConContactos(Empresa empresa, List<ContactoDTO> contactosDTO) {
        logger.info("Agregando nueva empresa con contactos: {}", empresa.getNombre());
        Usuario usuarioLogueado = getUsuarioLogueado();
        if (usuarioLogueado == null) {
            logger.error("Usuario no autenticado al intentar agregar empresa");
            throw new SecurityException("Usuario no autenticado");
        }

        empresa.setPropietario(usuarioLogueado);
        empresa.setCreadoPor(usuarioLogueado.getNombreUsuario());
        empresa.setModificadoPor(usuarioLogueado.getNombreUsuario());
        empresa.setFechaCreacion(LocalDateTime.now());
        empresa.setFechaModificacion(LocalDateTime.now());
        empresa.setFechaUltimaActividad(LocalDateTime.now());

        // Si se proporciona propietarioId en la entidad (a través del DTO), usarlo
        if (empresa.getPropietario() == null && contactosDTO != null && !contactosDTO.isEmpty() && contactosDTO.get(0).getPropietarioId() != null) {
            Usuario propietario = usuarioRepository.findById(contactosDTO.get(0).getPropietarioId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + contactosDTO.get(0).getPropietarioId()));
            empresa.setPropietario(propietario);
        }

        validateEmpresa(empresa);
        Empresa savedEmpresa = empresaRepository.save(empresa);

        // Procesar contactos si se proporcionan
        if (contactosDTO != null && !contactosDTO.isEmpty()) {
            for (ContactoDTO contactoDTO : contactosDTO) {
                Contacto contacto = new Contacto();
                contacto.setEmpresa(savedEmpresa);
                contacto.setPropietario(empresa.getPropietario()); // Usar el propietario de la empresa por defecto
                contacto.setCreadoPor(usuarioLogueado.getNombreUsuario());
                contacto.setModificadoPor(usuarioLogueado.getNombreUsuario());
                contacto.setNombre(contactoDTO.getNombre());
                contacto.setRol(contactoDTO.getRol());
                contacto.setCelular(contactoDTO.getCelular());
                contacto.setFechaCreacion(LocalDateTime.now());
                contacto.setFechaModificacion(LocalDateTime.now());
                contacto.setFechaUltimaActividad(LocalDateTime.now());

                // Si se proporciona propietarioId en el DTO, usarlo
                if (contactoDTO.getPropietarioId() != null) {
                    Usuario propietario = usuarioRepository.findById(contactoDTO.getPropietarioId())
                            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + contactoDTO.getPropietarioId()));
                    contacto.setPropietario(propietario);
                }

                if (contacto.getNombre() == null || contacto.getNombre().trim().isEmpty()) {
                    contacto.setNombre("Contacto de " + (contacto.getRol() != null ? contacto.getRol().name() : RolContactoEnum.RECEPCION.name()));
                }

                if (contactoDTO.getCorreos() != null) {
                    List<CorreoContacto> correos = contactoDTO.getCorreos().stream()
                            .map(dto -> {
                                if (dto.getCorreo() == null || dto.getCorreo().trim().isEmpty()) {
                                    throw new IllegalArgumentException("El correo no puede estar vacío");
                                }
                                CorreoContacto correo = new CorreoContacto();
                                correo.setCorreo(dto.getCorreo());
                                correo.setContacto(contacto);
                                return correo;
                            })
                            .collect(Collectors.toList());
                    contacto.setCorreos(correos);
                }

                if (contactoDTO.getTelefonos() != null) {
                    List<TelefonoContacto> telefonos = contactoDTO.getTelefonos().stream()
                            .map(dto -> {
                                if (dto.getTelefono() == null || dto.getTelefono().trim().isEmpty()) {
                                    throw new IllegalArgumentException("El teléfono no puede estar vacío");
                                }
                                TelefonoContacto telefono = new TelefonoContacto();
                                telefono.setTelefono(dto.getTelefono());
                                telefono.setContacto(contacto);
                                return telefono;
                            })
                            .collect(Collectors.toList());
                    contacto.setTelefonos(telefonos);
                }

                contactoRepository.save(contacto);
            }
        }

        logger.info("Empresa con contactos agregada exitosamente con ID: {}", savedEmpresa.getId());
        return convertToEmpresaDTO(savedEmpresa);
    }

    @Transactional
    public EmpresaDTO editarEmpresa(Integer id, Empresa empresaActualizada) {
        logger.info("Editando empresa con ID: {}", id);
        Empresa empresa = empresaRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Empresa no encontrada con ID: {}", id);
                    return new ResourceNotFoundException("Empresa no encontrada con id: " + id);
                });

        empresa.setNombre(empresaActualizada.getNombre());
        empresa.setEstatus(empresaActualizada.getEstatus());
        empresa.setSitioWeb(empresaActualizada.getSitioWeb());
        empresa.setSector(empresaActualizada.getSector());
        empresa.setDomicilioFisico(empresaActualizada.getDomicilioFisico());
        empresa.setDomicilioFiscal(empresaActualizada.getDomicilioFiscal());
        empresa.setRfc(empresaActualizada.getRfc());
        empresa.setRazonSocial(empresaActualizada.getRazonSocial());
        empresa.setRegimenFiscal(empresaActualizada.getRegimenFiscal());
        empresa.setFechaModificacion(LocalDateTime.now());
        empresa.setFechaUltimaActividad(LocalDateTime.now());


        if (empresaActualizada.getPropietario() != null && empresaActualizada.getPropietario().getId() != null) {
            Usuario propietario = usuarioRepository.findById(empresaActualizada.getPropietario().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + empresaActualizada.getPropietario().getId()));
            empresa.setPropietario(propietario);
        } else if (empresaActualizada.getPropietario() != null && empresaActualizada.getPropietario().getId() == null) {
            logger.warn("Propietario con ID nulo ignorado al editar empresa con ID: {}", id);
        }

        String usuarioLogueado = getUsuarioLogueadoName();
        if (usuarioLogueado != null) {
            empresa.setModificadoPor(usuarioLogueado);
        } else {
            logger.warn("Usuario logueado no encontrado, usando propietario como modificadoPor");
            empresa.setModificadoPor(empresa.getCreadoPor());
        }

        validateEmpresa(empresa);
        Empresa savedEmpresa = empresaRepository.save(empresa);
        logger.info("Empresa con ID: {} editada exitosamente", id);
        return convertToEmpresaDTO(savedEmpresa);
    }

    @Transactional
    public void eliminarEmpresa(Integer id) {
        logger.info("Eliminando empresa con ID: {}", id);
        Empresa empresa = empresaRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Empresa no encontrada con ID: {}", id);
                    return new ResourceNotFoundException("Empresa no encontrada con id: " + id);
                });
        if (contactoRepository.countByEmpresaId(id) > 0) {
            logger.error("No se puede eliminar la empresa con ID: {} porque tiene contactos asociados", id);
            throw new IllegalStateException("No se puede eliminar una empresa con contactos asociados");
        }
        empresaRepository.delete(empresa);
        logger.info("Empresa con ID: {} eliminada exitosamente", id);
    }

    public List<EmpresaDTO> listarEmpresas(String nombre, EstatusEmpresaEnum estatus) {
        logger.info("Listando empresas con nombre: {} y estatus: {}", nombre, estatus);
        List<Empresa> empresas;
        if (nombre != null && !nombre.trim().isEmpty() && estatus != null) {
            empresas = empresaRepository.findByNombreContainingIgnoreCaseAndEstatus(nombre, estatus);
        } else if (estatus != null) {
            empresas = empresaRepository.findByEstatus(estatus);
        } else if (nombre != null && !nombre.trim().isEmpty()) {
            empresas = empresaRepository.findByNombreContainingIgnoreCase(nombre);
        } else {
            empresas = empresaRepository.findAll();
        }
        List<EmpresaDTO> result = empresas.stream().map(this::convertToEmpresaDTO).collect(Collectors.toList());
        logger.info("Se encontraron {} empresas", result.size());
        return result;
    }

    @Transactional
    public ContactoDTO agregarContacto(Integer empresaId, ContactoDTO contactoDTO) {
        logger.info("Agregando contacto para empresa con ID: {}", empresaId);
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> {
                    logger.error("Empresa no encontrada con ID: {}", empresaId);
                    return new ResourceNotFoundException("Empresa no encontrada con id: " + empresaId);
                });
        String usuarioLogueado = getUsuarioLogueadoName();
        if (usuarioLogueado == null) {
            logger.error("Usuario no autenticado al intentar agregar contacto");
            throw new SecurityException("Usuario no autenticado");
        }

        Contacto contacto = new Contacto();
        contacto.setEmpresa(empresa);
        contacto.setPropietario(empresa.getPropietario()); // Usar el propietario de la empresa por defecto
        contacto.setCreadoPor(usuarioLogueado);
        contacto.setModificadoPor(usuarioLogueado);
        contacto.setNombre(contactoDTO.getNombre());
        contacto.setRol(contactoDTO.getRol());
        contacto.setCelular(contactoDTO.getCelular());
        contacto.setFechaCreacion(LocalDateTime.now());
        contacto.setFechaModificacion(LocalDateTime.now());
        contacto.setFechaUltimaActividad(LocalDateTime.now());

        // Si se proporciona propietarioId, usarlo
        if (contactoDTO.getPropietarioId() != null) {
            Usuario propietario = usuarioRepository.findById(contactoDTO.getPropietarioId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + contactoDTO.getPropietarioId()));
            contacto.setPropietario(propietario);
        }

        if (contacto.getNombre() == null || contacto.getNombre().trim().isEmpty()) {
            contacto.setNombre("Contacto de " + (contacto.getRol() != null ? contacto.getRol().name() : RolContactoEnum.RECEPCION.name()));
        }

        if (contactoDTO.getCorreos() != null) {
            List<CorreoContacto> correos = contactoDTO.getCorreos().stream()
                    .map(dto -> {
                        if (dto.getCorreo() == null || dto.getCorreo().trim().isEmpty()) {
                            throw new IllegalArgumentException("El correo no puede estar vacío");
                        }
                        CorreoContacto correo = new CorreoContacto();
                        correo.setCorreo(dto.getCorreo());
                        correo.setContacto(contacto);
                        return correo;
                    })
                    .collect(Collectors.toList());
            contacto.setCorreos(correos);
        }

        if (contactoDTO.getTelefonos() != null) {
            List<TelefonoContacto> telefonos = contactoDTO.getTelefonos().stream()
                    .map(dto -> {
                        if (dto.getTelefono() == null || dto.getTelefono().trim().isEmpty()) {
                            throw new IllegalArgumentException("El teléfono no puede estar vacío");
                        }
                        TelefonoContacto telefono = new TelefonoContacto();
                        telefono.setTelefono(dto.getTelefono());
                        telefono.setContacto(contacto);
                        return telefono;
                    })
                    .collect(Collectors.toList());
            contacto.setTelefonos(telefonos);
        }

        Contacto savedContacto = contactoRepository.save(contacto);

        empresa.setFechaUltimaActividad(LocalDateTime.now());
        empresaRepository.save(empresa);
        logger.info("Contacto agregado exitosamente con ID: {}", savedContacto.getId());
        return convertToContactoDTO(savedContacto);
    }

    @Transactional
    public ContactoDTO editarContacto(Integer id, ContactoDTO contactoDTO) {
        logger.info("Editando contacto con ID: {}", id);
        Contacto contacto = contactoRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Contacto no encontrado con ID: {}", id);
                    return new ResourceNotFoundException("Contacto no encontrado con id: " + id);
                });

        contacto.setNombre(contactoDTO.getNombre());
        contacto.setRol(contactoDTO.getRol());
        contacto.setCelular(contactoDTO.getCelular());
        if (contacto.getNombre() == null || contacto.getNombre().trim().isEmpty()) {
            contacto.setNombre("Contacto de " + (contacto.getRol() != null ? contacto.getRol().name() : RolContactoEnum.RECEPCION.name()));
        }
        contacto.setFechaModificacion(LocalDateTime.now());
        contacto.setFechaUltimaActividad(LocalDateTime.now());

        // Actualizar propietario si se proporciona propietarioId
        if (contactoDTO.getPropietarioId() != null) {
            Usuario propietario = usuarioRepository.findById(contactoDTO.getPropietarioId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + contactoDTO.getPropietarioId()));
            contacto.setPropietario(propietario);
        }

        String usuarioLogueado = getUsuarioLogueadoName();
        if (usuarioLogueado != null) {
            contacto.setModificadoPor(usuarioLogueado);
        } else if (contactoDTO.getModificadoPor() != null && !contactoDTO.getModificadoPor().isEmpty()) {
            if (usuarioRepository.findByNombreUsuario(contactoDTO.getModificadoPor()) == null) {
                logger.error("Usuario modificador no encontrado: {}", contactoDTO.getModificadoPor());
                throw new IllegalArgumentException("Usuario modificador no encontrado: " + contactoDTO.getModificadoPor());
            }
            contacto.setModificadoPor(contactoDTO.getModificadoPor());
        } else {
            logger.warn("No se puede determinar el usuario modificador, usando creadoPor como fallback");
            contacto.setModificadoPor(contacto.getCreadoPor());
        }

        if (contactoDTO.getCorreos() != null) {
            contacto.getCorreos().clear();
            List<CorreoContacto> nuevosCorreos = contactoDTO.getCorreos().stream()
                    .map(dto -> {
                        if (dto.getCorreo() == null || dto.getCorreo().trim().isEmpty()) {
                            throw new IllegalArgumentException("El correo no puede estar vacío");
                        }
                        CorreoContacto correo = new CorreoContacto();
                        correo.setCorreo(dto.getCorreo());
                        correo.setContacto(contacto);
                        return correo;
                    })
                    .collect(Collectors.toList());
            contacto.setCorreos(nuevosCorreos);
        }

        if (contactoDTO.getTelefonos() != null) {
            contacto.getTelefonos().clear();
            List<TelefonoContacto> nuevosTelefonos = contactoDTO.getTelefonos().stream()
                    .map(dto -> {
                        if (dto.getTelefono() == null || dto.getTelefono().trim().isEmpty()) {
                            throw new IllegalArgumentException("El teléfono no puede estar vacío");
                        }
                        TelefonoContacto telefono = new TelefonoContacto();
                        telefono.setTelefono(dto.getTelefono());
                        telefono.setContacto(contacto);
                        return telefono;
                    })
                    .collect(Collectors.toList());
            contacto.setTelefonos(nuevosTelefonos);
        }

        Contacto savedContacto = contactoRepository.save(contacto);
        Empresa empresa = savedContacto.getEmpresa();
        empresa.setFechaUltimaActividad(LocalDateTime.now());
        empresaRepository.save(empresa);

        logger.info("Contacto con ID: {} editado exitosamente", id);
        return convertToContactoDTO(savedContacto);
    }

    @Transactional
    public void eliminarContacto(Integer id) {
        logger.info("Eliminando contacto con ID: {}", id);
        Contacto contacto = contactoRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Contacto no encontrado con ID: {}", id);
                    return new ResourceNotFoundException("Contacto no encontrado con id: " + id);
                });
        Empresa empresa = contacto.getEmpresa();
        if (contactoRepository.countByEmpresaId(empresa.getId()) <= 1) {
            logger.error("No se puede eliminar el último contacto de la empresa con ID: {}", empresa.getId());
            throw new IllegalStateException("No se puede eliminar el último contacto de la empresa");
        }
        contactoRepository.delete(contacto);
        empresa.setFechaUltimaActividad(LocalDateTime.now());
        empresaRepository.save(empresa);
        logger.info("Contacto con ID: {} eliminado exitosamente", id);
    }

    @Transactional(readOnly = true)
    public List<ContactoDTO> listarContactosPorEmpresa(Integer empresaId) {
        logger.info("Listando contactos para empresa con ID: {}", empresaId);
        List<Contacto> contactos = contactoRepository.findByEmpresaId(empresaId);
        List<ContactoDTO> result = contactos.stream().map(this::convertToContactoDTO).collect(Collectors.toList());
        logger.info("Se encontraron {} contactos para la empresa con ID: {}", result.size(), empresaId);
        return result;
    }

    private EmpresaDTO convertToEmpresaDTO(Empresa empresa) {
        EmpresaDTO dto = new EmpresaDTO();
        dto.setId(empresa.getId());
        dto.setNombre(empresa.getNombre());
        dto.setEstatus(empresa.getEstatus());
        dto.setSitioWeb(empresa.getSitioWeb());
        dto.setSector(empresa.getSector());
        dto.setDomicilioFisico(empresa.getDomicilioFisico());
        dto.setDomicilioFiscal(empresa.getDomicilioFiscal());
        dto.setRfc(empresa.getRfc());
        dto.setRazonSocial(empresa.getRazonSocial());
        dto.setRegimenFiscal(empresa.getRegimenFiscal());
        dto.setFechaCreacion(empresa.getFechaCreacion());
        dto.setFechaModificacion(empresa.getFechaModificacion());
        dto.setFechaUltimaActividad(empresa.getFechaUltimaActividad());
        // Incluir el propietario
        if (empresa.getPropietario() != null) {
            PropietarioDTO propietarioDTO = new PropietarioDTO();
            propietarioDTO.setId(empresa.getPropietario().getId());
            propietarioDTO.setNombreUsuario(empresa.getPropietario().getNombreUsuario());
            dto.setPropietario(propietarioDTO);
        }
        return dto;
    }

    private ContactoDTO convertToContactoDTO(Contacto contacto) {
        ContactoDTO dto = new ContactoDTO();
        dto.setId(contacto.getId());
        dto.setNombre(contacto.getNombre());
        dto.setRol(contacto.getRol());
        dto.setCelular(contacto.getCelular());
        dto.setFechaCreacion(contacto.getFechaCreacion());
        dto.setFechaModificacion(contacto.getFechaModificacion());
        dto.setFechaUltimaActividad(contacto.getFechaUltimaActividad());
        dto.setCorreos(contacto.getCorreos().stream()
                .map(correo -> {
                    CorreoDTO correoDTO = new CorreoDTO();
                    correoDTO.setCorreo(correo.getCorreo());
                    return correoDTO;
                })
                .collect(Collectors.toList()));
        dto.setTelefonos(contacto.getTelefonos().stream()
                .map(telefono -> {
                    TelefonoDTO telefonoDTO = new TelefonoDTO();
                    telefonoDTO.setTelefono(telefono.getTelefono());
                    return telefonoDTO;
                })
                .collect(Collectors.toList()));
        dto.setCreadoPor(contacto.getCreadoPor());
        dto.setModificadoPor(contacto.getModificadoPor());
        // Incluir el propietario
        if (contacto.getPropietario() != null) {
            PropietarioDTO propietarioDTO = new PropietarioDTO();
            propietarioDTO.setId(contacto.getPropietario().getId());
            propietarioDTO.setNombreUsuario(contacto.getPropietario().getNombreUsuario());
            dto.setPropietario(propietarioDTO);
        }
        return dto;
    }

    private void validateEmpresa(Empresa empresa) {
        if (empresa.getNombre() == null || empresa.getNombre().trim().isEmpty() ||
                empresa.getDomicilioFisico() == null || empresa.getDomicilioFisico().trim().isEmpty()) {
            logger.error("Validación fallida: Nombre y Domicilio Físico son obligatorios para empresa");
            throw new IllegalArgumentException("Nombre y Domicilio Físico son obligatorios");
        }
        if (empresa.getEstatus() == EstatusEmpresaEnum.CLIENTE &&
                (empresa.getDomicilioFiscal() == null || empresa.getDomicilioFiscal().trim().isEmpty() ||
                        empresa.getRfc() == null || empresa.getRfc().trim().isEmpty() ||
                        empresa.getRazonSocial() == null || empresa.getRazonSocial().trim().isEmpty() ||
                        empresa.getRegimenFiscal() == null || empresa.getRegimenFiscal().trim().isEmpty())) {
            logger.error("Validación fallida: Campos fiscales son obligatorios para estatus Cliente");
            throw new IllegalArgumentException("Campos fiscales son obligatorios para estatus Cliente");
        }
    }

    private String getUsuarioLogueadoName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            logger.warn("No se encontró usuario autenticado en el contexto de seguridad");
            return null;
        }
        logger.debug("Usuario autenticado: {}", auth.getName());
        return auth.getName();
    }

    private Usuario getUsuarioLogueado() {
        String nombreUsuario = getUsuarioLogueadoName();
        return nombreUsuario != null ? usuarioRepository.findByNombreUsuario(nombreUsuario) : null;
    }

    @Deprecated
    public EmpresaDTO agregarEmpresa(Empresa empresa) {
        logger.warn("Usando método obsoleto agregarEmpresa. Usa agregarEmpresaConContactos en su lugar.");
        return agregarEmpresaConContactos(empresa, null);
    }
}