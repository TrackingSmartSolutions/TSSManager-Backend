package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.UsuarioDTO;
import com.tss.tssmanager_backend.entity.Usuario;
import com.tss.tssmanager_backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UsuarioService {

    private static final Logger logger = LoggerFactory.getLogger(UsuarioService.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<UsuarioDTO> listarUsuarios() {
        logger.info("Listando todos los usuarios");
        List<Usuario> usuarios = usuarioRepository.findAll();
        List<UsuarioDTO> result = usuarios.stream().map(this::convertToDTO).collect(Collectors.toList());
        logger.info("Se encontraron {} usuarios", result.size());
        return result;
    }

    private UsuarioDTO convertToDTO(Usuario usuario) {
        UsuarioDTO dto = new UsuarioDTO();
        dto.setId(usuario.getId());
        dto.setNombreUsuario(usuario.getNombreUsuario());
        return dto;
    }
}