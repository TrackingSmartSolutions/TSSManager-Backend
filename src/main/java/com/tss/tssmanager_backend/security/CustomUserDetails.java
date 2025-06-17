package com.tss.tssmanager_backend.security;

import com.tss.tssmanager_backend.entity.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class CustomUserDetails extends Usuario implements UserDetails {

    public CustomUserDetails(Usuario usuario) {
        this.setId(usuario.getId());
        this.setNombre(usuario.getNombre());
        this.setApellidos(usuario.getApellidos());
        this.setNombreUsuario(usuario.getNombreUsuario());
        this.setCorreoElectronico(usuario.getCorreoElectronico());
        this.setContrasena(usuario.getContrasena());
        this.setRol(usuario.getRol());
        this.setEstatus(usuario.getEstatus());
        this.setUltimaActividad(usuario.getUltimaActividad());
        this.setFechaCreacion(usuario.getFechaCreacion());
        this.setFechaModificacion(usuario.getFechaModificacion());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + this.getRol().name()));
    }

    @Override
    public String getPassword() {
        return this.getContrasena();
    }

    @Override
    public String getUsername() {
        return this.getNombreUsuario();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return this.getEstatus() == com.tss.tssmanager_backend.enums.EstatusUsuarioEnum.ACTIVO;
    }
}