package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.LoginRequest;
import com.tss.tssmanager_backend.dto.LoginResponse;
import com.tss.tssmanager_backend.dto.UsuarioDTO;
import com.tss.tssmanager_backend.entity.Usuario;
import com.tss.tssmanager_backend.enums.EstatusUsuarioEnum;
import com.tss.tssmanager_backend.repository.UsuarioRepository;
import com.tss.tssmanager_backend.security.JwtUtil;
import com.tss.tssmanager_backend.service.NotificacionService;
import com.tss.tssmanager_backend.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private NotificacionService notificacionService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getNombreUsuario(), loginRequest.getContrasena())
            );
            String token = jwtUtil.generateToken(loginRequest.getNombreUsuario());
            Usuario usuario = usuarioRepository.findByNombreUsuario(loginRequest.getNombreUsuario());
            String message = "Bienvenido/a " + usuario.getNombre();
            return ResponseEntity.ok(new LoginResponse(token, message, usuario.getRol().name()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body("Usuario o contraseña incorrectos");
        }
    }

    @GetMapping("/users")
    public ResponseEntity<List<UsuarioDTO>> getAllUsers() {
        List<UsuarioDTO> users = usuarioService.listarUsuarios();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/active")
    public ResponseEntity<List<UsuarioDTO>> getActiveUsers() {
        List<UsuarioDTO> users = usuarioService.listarUsuariosActivos();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<Usuario> getUserById(@PathVariable Integer userId) {
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return ResponseEntity.ok(usuario);
    }

    @GetMapping("/users/by-username/{nombreUsuario}")
    public ResponseEntity<Usuario> getUserByUsername(@PathVariable String nombreUsuario) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario);
        if (usuario == null) {
            throw new RuntimeException("Usuario no encontrado con nombreUsuario: " + nombreUsuario);
        }
        return ResponseEntity.ok(usuario);
    }

    @PostMapping("/users")
    public ResponseEntity<Usuario> createUser(@RequestBody Usuario usuario) {
        Usuario savedUsuario = usuarioService.guardarUsuario(usuario);
        return ResponseEntity.ok(savedUsuario);
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<Usuario> updateUser(@PathVariable Integer userId, @RequestBody Usuario usuario) {
        usuario.setId(userId);
        Usuario updatedUsuario = usuarioService.guardarUsuario(usuario);
        return ResponseEntity.ok(updatedUsuario);
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<Usuario> toggleUserStatus(
            @PathVariable Integer userId,
            @RequestParam(required = false) Integer reasignarA) {

        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Si se está desactivando un usuario y hay un usuario destino
        if (usuario.getEstatus() == EstatusUsuarioEnum.ACTIVO && reasignarA != null) {
            usuarioService.desactivarUsuarioConReasignacion(userId, reasignarA);
        } else {
            usuario.setEstatus(usuario.getEstatus() == EstatusUsuarioEnum.ACTIVO ?
                    EstatusUsuarioEnum.INACTIVO : EstatusUsuarioEnum.ACTIVO);
            usuarioRepository.save(usuario);
        }

        Usuario updatedUsuario = usuarioRepository.findById(userId).get();
        return ResponseEntity.ok(updatedUsuario);
    }

    @PatchMapping("/users/{userId}/password")
    public ResponseEntity<Usuario> resetPassword(@PathVariable Integer userId, @RequestBody Map<String, String> passwordData) {
        String nuevaContrasena = passwordData.get("nuevaContrasena");
        usuarioService.restablecerContrasena(userId, nuevaContrasena);
        return ResponseEntity.ok(usuarioRepository.findById(userId).get());
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Integer userId) {
        usuarioService.eliminarUsuario(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/solicitar-cambio-contrasena")
    public ResponseEntity<?> solicitarCambioContrasena(@RequestBody Map<String, String> request) {
        String correo = request.get("correo");
        if (correo == null || correo.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "El correo electrónico es requerido"
            ));
        }
        try {
            Usuario usuario = usuarioRepository.findByCorreoElectronico(correo);
            if (usuario == null) {
                // Retornar error específico para correo no encontrado
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "message", "El correo electrónico no está registrado en el sistema"
                ));
            }
            // Generar la notificación para administradores
            notificacionService.generarNotificacionCambiarContrasena(usuario.getNombre(), usuario.getCorreoElectronico());
            return ResponseEntity.ok().body(Map.of(
                    "success", true,
                    "message", "Solicitud enviada correctamente"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Error interno del servidor"
            ));
        }
    }

    @GetMapping("/users/{userId}/assignment-counts")
    public ResponseEntity<Map<String, Integer>> getAssignmentCounts(@PathVariable Integer userId) {
        try {
            Map<String, Integer> counts = usuarioService.obtenerContadoresAsignacion(userId);
            return ResponseEntity.ok(counts);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", 0));
        }
    }

    @GetMapping("/users/{userId}/check-conflicts")
    public ResponseEntity<?> checkConflicts(@PathVariable Integer userId, @RequestParam Integer targetUserId) {
        List<Map<String, Object>> conflictos = usuarioService.verificarConflictos(userId, targetUserId);
        return ResponseEntity.ok(conflictos);
    }

    @PostMapping("/users/{userId}/deactivate-resolved")
    public ResponseEntity<?> deactivateWithResolution(
            @PathVariable Integer userId,
            @RequestBody Map<String, Object> payload) {

        try {
            Object targetIdObj = payload.get("targetUserId");
            if (targetIdObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "targetUserId es requerido"));
            }
            Integer targetUserId = Integer.valueOf(targetIdObj.toString());

            List<Map<String, Object>> resoluciones = (List<Map<String, Object>>) payload.get("resoluciones");

            usuarioService.desactivarUsuarioConResolucion(userId, targetUserId, resoluciones);
            return ResponseEntity.ok(Map.of("message", "Usuario desactivado y actividades reasignadas correctamente"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

