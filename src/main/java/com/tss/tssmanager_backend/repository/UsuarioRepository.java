package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.entity.Usuario;
import com.tss.tssmanager_backend.enums.EstatusUsuarioEnum;
import com.tss.tssmanager_backend.enums.RolUsuarioEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    Usuario findByCorreoElectronico(String correoElectronico);
    Usuario findByNombreUsuario(String nombreUsuario);
    Usuario findByNombre(String nombre);

    Optional<Usuario> findByIdAndEstatusNot(Integer id, EstatusUsuarioEnum estatus);
    void deleteByIdAndEstatusNot(Integer id, String estatus);

    @Query("SELECT u FROM Usuario u WHERE u.rol = :rol AND u.estatus = :estatus ORDER BY u.id ASC")
    List<Usuario> findByRolAndEstatusOrderById(RolUsuarioEnum rol, EstatusUsuarioEnum estatus);

    @Query("SELECT u FROM Usuario u WHERE u.rol = :rol AND u.estatus = :estatus ORDER BY u.id ASC LIMIT 1")
    Optional<Usuario> findFirstByRolAndEstatusOrderById(RolUsuarioEnum rol, EstatusUsuarioEnum estatus);

    @Query("SELECT u FROM Usuario u WHERE u.estatus = :estatus ORDER BY u.id ASC")
    List<Usuario> findByEstatusOrderById(EstatusUsuarioEnum estatus);

    boolean existsByCorreoElectronicoAndEstatus(String correoElectronico, EstatusUsuarioEnum estatus);

}
