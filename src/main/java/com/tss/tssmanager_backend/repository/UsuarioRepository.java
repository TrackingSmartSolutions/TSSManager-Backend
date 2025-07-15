package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Usuario;
import com.tss.tssmanager_backend.enums.EstatusUsuarioEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    Usuario findByCorreoElectronico(String correoElectronico);
    Usuario findByNombreUsuario(String nombreUsuario);
    Usuario findByNombre(String nombre);

    Optional<Usuario> findByIdAndEstatusNot(Integer id, EstatusUsuarioEnum estatus);
    void deleteByIdAndEstatusNot(Integer id, String estatus);

}
