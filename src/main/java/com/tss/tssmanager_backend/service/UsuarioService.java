package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.UsuarioDTO;
import com.tss.tssmanager_backend.entity.Usuario;
import com.tss.tssmanager_backend.enums.EstatusActividadEnum;
import com.tss.tssmanager_backend.enums.EstatusUsuarioEnum;
import com.tss.tssmanager_backend.repository.ActividadRepository;
import com.tss.tssmanager_backend.repository.TratoRepository;
import com.tss.tssmanager_backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UsuarioService {

    private static final Logger logger = LoggerFactory.getLogger(UsuarioService.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TratoRepository tratoRepository;

    @Autowired
    private ActividadRepository actividadRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Cacheable(value = "usuarios", key = "#id")
    public Usuario findById(Integer id) {
        return usuarioRepository.findById(id).orElse(null);
    }

    @Cacheable(value = "usuarios", key = "#nombreUsuario")
    public Usuario findByNombreUsuario(String nombreUsuario) {
        return usuarioRepository.findByNombreUsuario(nombreUsuario);
    }

    @CacheEvict(value = "usuarios", key = "#usuario.id")
    public Usuario save(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    @CacheEvict(value = "usuarios", allEntries = true)
    public void clearCache() {
    }

    @Transactional(readOnly = true)
    public List<UsuarioDTO> listarUsuarios() {
        logger.info("Listando todos los usuarios");
        List<Usuario> usuarios = usuarioRepository.findAll();
        List<UsuarioDTO> result = usuarios.stream().map(this::convertToDTO).collect(Collectors.toList());
        logger.info("Se encontraron {} usuarios", result.size());
        return result;
    }

    @Transactional
    public Usuario guardarUsuario(Usuario usuario) {
        if (usuario.getId() == null) {
            usuario.setContrasena(passwordEncoder.encode(usuario.getContrasena()));
            usuario.setEstatus(EstatusUsuarioEnum.ACTIVO);
        } else {
            Usuario usuarioExistente = usuarioRepository.findById(usuario.getId())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            usuarioExistente.setNombre(usuario.getNombre());
            usuarioExistente.setApellidos(usuario.getApellidos());
            usuarioExistente.setCorreoElectronico(usuario.getCorreoElectronico());
            usuarioExistente.setRol(usuario.getRol());
            if (usuario.getContrasena() != null && !usuario.getContrasena().isEmpty()) {
                usuarioExistente.setContrasena(passwordEncoder.encode(usuario.getContrasena()));
            }
            return usuarioRepository.save(usuarioExistente);
        }
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void eliminarUsuario(Integer id) {
        Usuario usuario = usuarioRepository.findByIdAndEstatusNot(id, EstatusUsuarioEnum.INACTIVO)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado o ya inactivo"));
        usuario.setEstatus(EstatusUsuarioEnum.INACTIVO);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void restablecerContrasena(Integer id, String nuevaContrasena) {
        Usuario usuario = usuarioRepository.findByIdAndEstatusNot(id, EstatusUsuarioEnum.INACTIVO)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado o inactivo"));
        usuario.setContrasena(passwordEncoder.encode(nuevaContrasena));
        usuarioRepository.save(usuario);
    }


    private UsuarioDTO convertToDTO(Usuario usuario) {
        UsuarioDTO dto = new UsuarioDTO();
        dto.setId(usuario.getId());
        dto.setNombreUsuario(usuario.getNombreUsuario());
        dto.setNombre(usuario.getNombre());
        dto.setApellidos(usuario.getApellidos());
        dto.setCorreoElectronico(usuario.getCorreoElectronico());
        dto.setRol(usuario.getRol().name());
        dto.setEstatus(usuario.getEstatus().name());
        dto.setFechaCreacion(usuario.getFechaCreacion());
        dto.setFechaModificacion(usuario.getFechaModificacion());
        return dto;
    }

    @Transactional
    public void desactivarUsuarioConReasignacion(Integer usuarioId, Integer usuarioDestinoId) {
        // Verificar que ambos usuarios existan
        Usuario usuarioOrigen = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario origen no encontrado"));
        Usuario usuarioDestino = usuarioRepository.findById(usuarioDestinoId)
                .orElseThrow(() -> new RuntimeException("Usuario destino no encontrado"));

        if (usuarioDestino.getEstatus() != EstatusUsuarioEnum.ACTIVO) {
            throw new RuntimeException("El usuario destino debe estar activo");
        }

        // Reasignar tratos
        tratoRepository.updatePropietarioId(usuarioId, usuarioDestinoId);

        // Reasignar actividades
        actividadRepository.updateAsignadoAId(usuarioId, usuarioDestinoId);

        // Desactivar usuario
        usuarioOrigen.setEstatus(EstatusUsuarioEnum.INACTIVO);
        usuarioRepository.save(usuarioOrigen);

        logger.info("Usuario {} desactivado. Tratos y actividades reasignados a usuario {}",
                usuarioId, usuarioDestinoId);
    }

    public Map<String, Integer> obtenerContadoresAsignacion(Integer usuarioId) {
        // Contar tratos donde es propietario
        Long tratosCount = tratoRepository.countByPropietarioId(usuarioId);

        // Contar actividades abiertas asignadas
        Long actividadesAbiertasCount = actividadRepository.countByAsignadoAIdAndEstatus(usuarioId, EstatusActividadEnum.ABIERTA);

        Map<String, Integer> counts = new HashMap<>();
        counts.put("tratos", tratosCount.intValue());
        counts.put("actividades", actividadesAbiertasCount.intValue());

        return counts;
    }
}