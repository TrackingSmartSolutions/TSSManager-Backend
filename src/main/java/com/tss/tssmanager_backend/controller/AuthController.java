package com.tss.tssmanager_backend.controller;

import com.tss.tssmanager_backend.dto.LoginRequest;
import com.tss.tssmanager_backend.dto.LoginResponse;
import com.tss.tssmanager_backend.dto.UsuarioDTO;
import com.tss.tssmanager_backend.entity.Usuario;
import com.tss.tssmanager_backend.repository.UsuarioRepository;
import com.tss.tssmanager_backend.security.JwtUtil;
import com.tss.tssmanager_backend.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}