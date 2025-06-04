package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    Usuario findByCorreoElectronico(String correoElectronico);
    Usuario findByNombreUsuario(String nombreUsuario);
}
