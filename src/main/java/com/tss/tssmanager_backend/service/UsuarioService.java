package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.UsuarioDTO;
import com.tss.tssmanager_backend.entity.Actividad;
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

import java.sql.Time;
import java.time.LocalDate;
import java.util.ArrayList;
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

    @Transactional(readOnly = true)
    public List<UsuarioDTO> listarUsuariosActivos() {
        logger.info("Listando usuarios activos");
        List<Usuario> usuarios = usuarioRepository.findByEstatusOrderById(EstatusUsuarioEnum.ACTIVO);
        List<UsuarioDTO> result = usuarios.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        logger.info("Se encontraron {} usuarios activos", result.size());
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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> verificarConflictos(Integer idUsuarioOrigen, Integer idUsuarioDestino) {
        List<Actividad> actividadesOrigen = actividadRepository.findByAsignadoAIdAndEstatus(idUsuarioOrigen, EstatusActividadEnum.ABIERTA);
        List<Map<String, Object>> conflictos = new ArrayList<>();

        for (Actividad actOrigen : actividadesOrigen) {
            if (actOrigen.getFechaLimite() == null || actOrigen.getHoraInicio() == null) continue;

            List<Actividad> actividadesDestino = actividadRepository.findConflictingActivities(idUsuarioDestino, actOrigen.getFechaLimite());

            boolean hayChoque = false;
            // Lógica simple de empalme (puedes refinarla con la duración si la tienes precisa)
            for (Actividad actDestino : actividadesDestino) {
                if (actDestino.getHoraInicio() != null) {
                    // Si la diferencia es menor a 1 hora (ejemplo), lo consideramos conflicto
                    long diferenciaMinutos = Math.abs(java.time.Duration.between(actOrigen.getHoraInicio().toLocalTime(), actDestino.getHoraInicio().toLocalTime()).toMinutes());
                    if (diferenciaMinutos < 60) {
                        hayChoque = true;
                        break;
                    }
                }
            }

            if (hayChoque) {
                Map<String, Object> conflicto = new HashMap<>();
                conflicto.put("actividad", convertToActividadMap(actOrigen)); // Necesitarás un helper simple o usar tu DTO
                conflicto.put("motivo", "El usuario destino ya tiene actividades cerca de las " + actOrigen.getHoraInicio());
                conflictos.add(conflicto);
            }
        }
        return conflictos;
    }

    private Map<String, Object> convertToActividadMap(Actividad a) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", a.getId());
        map.put("tipo", a.getTipo());
        map.put("fechaLimite", a.getFechaLimite());
        map.put("horaInicio", a.getHoraInicio());
        map.put("duracion", a.getDuracion());
        map.put("descripcion", a.getTipo() + " - " + (a.getNotas() != null ? a.getNotas() : ""));
        return map;
    }

    @Transactional
    public void desactivarUsuarioConResolucion(Integer usuarioId, Integer usuarioDestinoId, List<Map<String, Object>> actividadesReprogramadas) {

        if (actividadesReprogramadas != null) {
            for (Map<String, Object> item : actividadesReprogramadas) {
                Integer actId = Integer.valueOf(item.get("id").toString());

                String nuevaFecha = (String) item.get("fecha");
                String nuevaHora = (String) item.get("hora");

                Actividad actividad = actividadRepository.findById(actId).orElse(null);
                if (actividad != null) {
                    actividad.setFechaLimite(LocalDate.parse(nuevaFecha));
                    String horaFormateada = nuevaHora.length() == 5 ? nuevaHora + ":00" : nuevaHora;
                    actividad.setHoraInicio(Time.valueOf(horaFormateada));

                    actividad.setAsignadoAId(usuarioDestinoId);
                    actividadRepository.save(actividad);
                }
            }
        }

        desactivarUsuarioConReasignacion(usuarioId, usuarioDestinoId);
    }
}