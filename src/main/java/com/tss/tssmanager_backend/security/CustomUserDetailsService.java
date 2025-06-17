package com.tss.tssmanager_backend.security;

import com.tss.tssmanager_backend.entity.Usuario;
import com.tss.tssmanager_backend.enums.EstatusUsuarioEnum;
import com.tss.tssmanager_backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Autowired
    public CustomUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println("Attempting to load user: " + username);
        Usuario usuario = usuarioRepository.findByNombreUsuario(username);
        if (usuario == null || usuario.getEstatus() == EstatusUsuarioEnum.INACTIVO) {
            System.out.println("User not found or inactive: " + username);
            throw new UsernameNotFoundException("Usuario o contraseña incorrectos");
        }
        System.out.println("Found user: " + usuario);
        return new CustomUserDetails(usuario); // Devuelve CustomUserDetails en lugar de User
    }
}