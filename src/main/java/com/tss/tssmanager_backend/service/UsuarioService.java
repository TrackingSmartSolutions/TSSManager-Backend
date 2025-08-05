package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.UsuarioDTO;
import com.tss.tssmanager_backend.entity.Usuario;
import com.tss.tssmanager_backend.enums.EstatusUsuarioEnum;
import com.tss.tssmanager_backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UsuarioService {

    private static final Logger logger = LoggerFactory.getLogger(UsuarioService.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

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
}