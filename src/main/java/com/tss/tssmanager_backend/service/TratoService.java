package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.*;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.*;
import com.tss.tssmanager_backend.repository.*;
import com.tss.tssmanager_backend.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TratoService {

    @Autowired
    private TratoRepository tratoRepository;
    @Autowired
    private ActividadRepository actividadRepository;
    @Autowired
    private NotaTratoRepository notaTratoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EmpresaRepository empresaRepository;
    @Autowired
    private NotificacionService notificacionService;
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private EmailService emailService;

    public List<TratoDTO> listarTratos() {
        return tratoRepository.findAll().stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TratoDTO getTratoById(Integer id) {
        Trato trato = tratoRepository.findTratoWithContactoAndTelefonos(id)
                .orElseThrow(() -> new RuntimeException("Trato no encontrado con id: " + id));
        return convertToDTO(trato);
    }

    @Transactional
    public TratoDTO crearTrato(TratoDTO tratoDTO) {
        Trato trato = new Trato();
        trato.setNombre(tratoDTO.getNombre());
        trato.setEmpresaId(tratoDTO.getEmpresaId());
        if (tratoDTO.getContactoId() != null) {
            Contacto contacto = entityManager.find(Contacto.class, tratoDTO.getContactoId());
            if (contacto != null) {
                trato.setContacto(contacto);
            }
        }
        trato.setNumeroUnidades(tratoDTO.getNumeroUnidades());
        trato.setIngresosEsperados(tratoDTO.getIngresosEsperados());
        trato.setDescripcion(tratoDTO.getDescripcion());
        trato.setPropietarioId(getCurrentUserId());
        trato.setFechaCierre(LocalDateTime.now().plusMonths(2));
        trato.setProbabilidad(tratoDTO.getProbabilidad() != null ? tratoDTO.getProbabilidad() : 0);
        trato.setFase(tratoDTO.getFase() != null ? tratoDTO.getFase() : "CLASIFICACION");
        trato.setCorreosAutomaticosActivos(tratoDTO.getCorreosAutomaticosActivos() != null ? tratoDTO.getCorreosAutomaticosActivos() : true);
        trato.setFechaCreacion(Instant.now());
        trato.setFechaModificacion(Instant.now());
        trato.setFechaUltimaActividad(Instant.now());

        Trato savedTrato = tratoRepository.save(trato);
        entityManager.refresh(savedTrato);

        return convertToDTO(savedTrato);
    }

    @Transactional
    public TratoDTO moverFase(Integer id, String nuevaFase) {
        Trato trato = tratoRepository.findTratoWithContacto(id)
                .orElseThrow(() -> new RuntimeException("Trato no encontrado"));

        String faseAnterior = trato.getFase();
        Integer propietarioAnterior = trato.getPropietarioId();
        trato.setFase(nuevaFase);
        trato.setProbabilidad(getProbabilidadPorFase(nuevaFase));
        trato.setFechaModificacion(Instant.now());
        trato.setFechaUltimaActividad(Instant.now());

        // Información sobre escalamiento
        boolean escalado = false;
        Integer nuevoAdministrador = null;

        // Solo escalar si el propietario actual es EMPLEADO y la fase requiere escalamiento
        if (requiereEscalamiento(nuevaFase) && propietarioAnterior != null && !esAdministrador(propietarioAnterior)) {
            Integer adminId = getAdministradorPredeterminado();
            if (adminId != null) {
                trato.setPropietarioId(adminId);
                crearNotaEscalamiento(trato.getId(), faseAnterior, nuevaFase, adminId);
                escalado = true;
                nuevoAdministrador = adminId;
            }
        }

        Trato updatedTrato = tratoRepository.save(trato);
        notificacionService.generarNotificacionTratoGanado(updatedTrato); // Verifica si el trato se ganó al cambiar la fase
        if (requiereEscalamiento(nuevaFase) && !esAdministrador(propietarioAnterior)) {
            Integer adminId = getAdministradorPredeterminado();
            if (adminId != null) {
                trato.setPropietarioId(adminId);
                crearNotaEscalamiento(trato.getId(), faseAnterior, nuevaFase, adminId);
                escalado = true;
                nuevoAdministrador = adminId;
                notificacionService.generarNotificacionEscalamiento(updatedTrato, adminId);
            }
        }
        TratoDTO result = convertToDTO(updatedTrato);

        // Agregar información sobre escalamiento al DTO
        result.setEscalado(escalado);
        if (escalado && nuevoAdministrador != null) {
            Usuario administrador = usuarioRepository.findById(nuevoAdministrador).orElse(null);
            result.setNuevoAdministradorNombre(administrador != null ? administrador.getNombre() : "Administrador");
        }

        return result;
    }

    private boolean requiereEscalamiento(String fase) {
        return "COTIZACION_PROPUESTA_PRACTICA".equals(fase) ||
                "NEGOCIACION_REVISION".equals(fase) ||
                "CERRADO_GANADO".equals(fase);
    }

    private Integer getAdministradorPredeterminado() {
        try {
            Optional<Usuario> admin = usuarioRepository.findFirstByRolAndEstatusOrderById(
                    RolUsuarioEnum.ADMINISTRADOR,
                    EstatusUsuarioEnum.ACTIVO
            );
            return admin.map(Usuario::getId).orElse(null);
        } catch (Exception e) {
            System.err.println("Error al obtener administrador predeterminado: " + e.getMessage());
            return null;
        }
    }

    private boolean esAdministrador(Integer usuarioId) {
        if (usuarioId == null) return false;

        try {
            Optional<Usuario> usuario = usuarioRepository.findById(usuarioId);
            return usuario.map(u -> RolUsuarioEnum.ADMINISTRADOR.equals(u.getRol())).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    private void crearNotaEscalamiento(Integer tratoId, String faseAnterior, String nuevaFase, Integer adminId) {
        try {
            NotaTrato nota = new NotaTrato();
            nota.setTratoId(tratoId);
            nota.setUsuarioId(adminId);
            nota.setNota(String.format("Escalamiento automático: Trato movido de '%s' a '%s' y asignado automáticamente a administrador.",
                    formatearNombreFase(faseAnterior),
                    formatearNombreFase(nuevaFase)));
            nota.setFechaCreacion(Instant.now());
            notaTratoRepository.save(nota);
        } catch (Exception e) {
            System.err.println("Error al crear nota de escalamiento: " + e.getMessage());
        }
    }

    private String formatearNombreFase(String fase) {
        if (fase == null) return "Desconocida";

        switch (fase) {
            case "CLASIFICACION":
                return "Clasificación";
            case "PRIMER_CONTACTO":
                return "Primer Contacto";
            case "ENVIO_DE_INFORMACION":
                return "Envío de Información";
            case "REUNION":
                return "Reunión";
            case "COTIZACION_PROPUESTA_PRACTICA":
                return "Cotización/Propuesta Práctica";
            case "NEGOCIACION_REVISION":
                return "Negociación/Revisión";
            case "CERRADO_GANADO":
                return "Cerrado Ganado";
            case "RESPUESTA_POR_CORREO":
                return "Respuesta por Correo";
            case "INTERES_FUTURO":
                return "Interés Futuro";
            case "CERRADO_PERDIDO":
                return "Cerrado Perdido";
            default:
                return fase;
        }
    }

    @Transactional
    public ActividadDTO programarActividad(ActividadDTO actividadDTO) {
        Actividad actividad = new Actividad();
        actividad.setTratoId(actividadDTO.getTratoId());
        actividad.setTipo(actividadDTO.getTipo());
        actividad.setSubtipoTarea(actividadDTO.getSubtipoTarea());
        actividad.setAsignadoAId(actividadDTO.getAsignadoAId());
        actividad.setFechaLimite(actividadDTO.getFechaLimite());
        actividad.setHoraInicio(actividadDTO.getHoraInicio());
        actividad.setDuracion(actividadDTO.getDuracion());
        actividad.setModalidad(actividadDTO.getModalidad());
        actividad.setLugarReunion(actividadDTO.getLugarReunion());
        actividad.setMedio(actividadDTO.getMedio());
        actividad.setEnlaceReunion(actividadDTO.getEnlaceReunion());
        actividad.setFinalidad(actividadDTO.getFinalidad());
        actividad.setEstatus(EstatusActividadEnum.ABIERTA);
        actividad.setFechaCreacion(Instant.now());
        actividad.setFechaModificacion(Instant.now());

        // Asignar contactoId directamente
        if (actividadDTO.getContactoId() != null) {
            actividad.setContactoId(actividadDTO.getContactoId());
            // Validar que el contacto existe
            Contacto contacto = entityManager.find(Contacto.class, actividadDTO.getContactoId());
            if (contacto == null) {
                throw new RuntimeException("El contacto con ID " + actividadDTO.getContactoId() + " no existe.");
            }
            System.out.println("Contacto encontrado: " + contacto.getNombre());
        } else {
            System.out.println("No se proporcionó contactoId en la actividad");
        }

        Actividad savedActividad = actividadRepository.save(actividad);

        // Generar notificación
        notificacionService.generarNotificacionActividad(savedActividad);

        boolean esTipoReunion = savedActividad.getTipo() != null &&
                TipoActividadEnum.REUNION.equals(savedActividad.getTipo());

        boolean esModalidadVirtual = savedActividad.getModalidad() != null &&
                ModalidadActividadEnum.VIRTUAL.equals(savedActividad.getModalidad());

        if (esTipoReunion && esModalidadVirtual) {
            enviarCorreosReunionVirtual(savedActividad, actividadDTO.getTratoId());
        } else {
            if (!esTipoReunion) {
                System.out.println("  - Tipo no es REUNION (actual: " +
                        (savedActividad.getTipo() != null ? savedActividad.getTipo().name() : "NULL") + ")");
            }
            if (!esModalidadVirtual) {
                System.out.println("  - Modalidad no es VIRTUAL (actual: " +
                        (savedActividad.getModalidad() != null ? savedActividad.getModalidad().name() : "NULL") + ")");
            }
        }

        return convertToDTO(savedActividad);
    }

    @Transactional
    public ActividadDTO reprogramarActividad(Integer id, ActividadDTO actividadDTO) {
        Actividad actividad = actividadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Actividad no encontrada con id: " + id));

        // Agregar verificación de timestamp para evitar procesamiento duplicado
        if (actividad.getFechaModificacion() != null) {
            long tiempoTranscurrido = Instant.now().toEpochMilli() - actividad.getFechaModificacion().toEpochMilli();
            if (tiempoTranscurrido < 5000) { // 5 segundos de ventana
                return convertToDTO(actividad); // Retornar sin procesar
            }
        }

        actividad.setAsignadoAId(actividadDTO.getAsignadoAId() != null ? actividadDTO.getAsignadoAId() : actividad.getAsignadoAId());
        actividad.setFechaLimite(actividadDTO.getFechaLimite() != null ? actividadDTO.getFechaLimite() : actividad.getFechaLimite());
        actividad.setHoraInicio(actividadDTO.getHoraInicio() != null ? actividadDTO.getHoraInicio() : actividad.getHoraInicio());
        actividad.setDuracion(actividadDTO.getDuracion() != null ? actividadDTO.getDuracion() : actividad.getDuracion());
        actividad.setModalidad(actividadDTO.getModalidad() != null ? actividadDTO.getModalidad() : actividad.getModalidad());
        actividad.setLugarReunion(actividadDTO.getLugarReunion() != null ? actividadDTO.getLugarReunion() : actividad.getLugarReunion());
        actividad.setMedio(actividadDTO.getMedio() != null ? actividadDTO.getMedio() : actividad.getMedio());
        actividad.setEnlaceReunion(actividadDTO.getEnlaceReunion() != null ? actividadDTO.getEnlaceReunion() : actividad.getEnlaceReunion());
        actividad.setFinalidad(actividadDTO.getFinalidad() != null ? actividadDTO.getFinalidad() : actividad.getFinalidad());
        actividad.setSubtipoTarea(actividadDTO.getSubtipoTarea() != null ? actividadDTO.getSubtipoTarea() : actividad.getSubtipoTarea());
        actividad.setContactoId(actividadDTO.getContactoId() != null ? actividadDTO.getContactoId() : actividad.getContactoId());

        if (EstatusActividadEnum.CERRADA.equals(actividad.getEstatus())) {
            throw new RuntimeException("No se puede reprogramar una actividad cerrada.");
        }
        actividad.setEstatus(EstatusActividadEnum.ABIERTA);
        actividad.setFechaModificacion(Instant.now());

        if (actividadDTO.getContactoId() != null) {
            Contacto contacto = entityManager.find(Contacto.class, actividadDTO.getContactoId());
            if (contacto == null) {
                throw new RuntimeException("El contacto con ID " + actividadDTO.getContactoId() + " no existe.");
            }
        }

        Actividad updatedActividad = actividadRepository.save(actividad);

        boolean esTipoReunion = updatedActividad.getTipo() != null &&
                TipoActividadEnum.REUNION.equals(updatedActividad.getTipo());

        boolean esModalidadVirtual = updatedActividad.getModalidad() != null &&
                ModalidadActividadEnum.VIRTUAL.equals(updatedActividad.getModalidad());

        if (esTipoReunion && esModalidadVirtual) {
            enviarCorreosReunionVirtualReprogramada(updatedActividad, updatedActividad.getTratoId());
        } else {
            if (!esTipoReunion) {
                System.out.println("  - Tipo no es REUNION (actual: " +
                        (updatedActividad.getTipo() != null ? updatedActividad.getTipo().name() : "NULL") + ")");
            }
            if (!esModalidadVirtual) {
                System.out.println("  - Modalidad no es VIRTUAL (actual: " +
                        (updatedActividad.getModalidad() != null ? updatedActividad.getModalidad().name() : "NULL") + ")");
            }
        }
        return convertToDTO(updatedActividad);
    }

    public NotaTratoDTO agregarNota(Integer tratoId, String nota) {
        NotaTrato notaTrato = new NotaTrato();
        notaTrato.setTratoId(tratoId);
        notaTrato.setUsuarioId(getCurrentUserId());
        notaTrato.setNota(nota.replaceAll("^\"|\"$", "").replaceAll("\\\\\"", "\""));
        notaTrato.setFechaCreacion(Instant.now());
        NotaTrato savedNota = notaTratoRepository.save(notaTrato);
        return convertToDTO(savedNota);
    }

    @Transactional
    public NotaTratoDTO editarNota(Integer tratoId, Long notaId, String nota) {
        NotaTrato notaTrato = notaTratoRepository.findById(notaId)
                .orElseThrow(() -> new RuntimeException("Nota no encontrada con id: " + notaId));
        if (!notaTrato.getTratoId().equals(tratoId)) {
            throw new RuntimeException("La nota no pertenece al trato especificado");
        }
        notaTrato.setNota(nota.replaceAll("^\"|\"$", "").replaceAll("\\\\\"", "\""));
        notaTrato.setEditadoPor(getCurrentUserId());
        notaTrato.setFechaEdicion(Instant.now());
        NotaTrato updatedNota = notaTratoRepository.save(notaTrato);
        return convertToDTO(updatedNota);
    }

    @Transactional
    public void eliminarNota(Integer tratoId, Long notaId) {
        NotaTrato notaTrato = notaTratoRepository.findById(notaId)
                .orElseThrow(() -> new RuntimeException("Nota no encontrada con id: " + notaId));
        if (!notaTrato.getTratoId().equals(tratoId)) {
            throw new RuntimeException("La nota no pertenece al trato especificado");
        }
        notaTratoRepository.delete(notaTrato);
    }

    @Transactional
    public TratoDTO editarTrato(Integer id, TratoDTO tratoDTO) {
        Trato trato = tratoRepository.findById(id).orElseThrow(() -> new RuntimeException("Trato no encontrado"));
        trato.setNombre(tratoDTO.getNombre());
        trato.setEmpresaId(tratoDTO.getEmpresaId());
        if (tratoDTO.getContactoId() != null) {
            Contacto contacto = entityManager.find(Contacto.class, tratoDTO.getContactoId());
            if (contacto != null) {
                trato.setContacto(contacto);
            }
        }
        trato.setNumeroUnidades(tratoDTO.getNumeroUnidades());
        trato.setIngresosEsperados(tratoDTO.getIngresosEsperados());
        trato.setPropietarioId(tratoDTO.getPropietarioId());
        trato.setDescripcion(tratoDTO.getDescripcion());
        trato.setFechaModificacion(Instant.now());
        trato.setFechaUltimaActividad(Instant.now());

        Trato updatedTrato = tratoRepository.save(trato);
        return convertToDTO(updatedTrato);
    }

    public ActividadDTO completarActividad(Integer id, ActividadDTO actividadDTO) {
        Actividad actividad = actividadRepository.findById(id).orElseThrow(() -> new RuntimeException("Actividad no encontrada"));
        actividad.setEstatus(EstatusActividadEnum.CERRADA);
        actividad.setFechaCompletado(Instant.now());
        actividad.setUsuarioCompletadoId(getCurrentUserId());
        actividad.setRespuesta(actividadDTO.getRespuesta());
        actividad.setInteres(actividadDTO.getInteres());
        actividad.setInformacion(actividadDTO.getInformacion());
        actividad.setSiguienteAccion(actividadDTO.getSiguienteAccion());
        actividad.setNotas(actividadDTO.getNotas());
        actividad.setMedio(actividadDTO.getMedio());
        actividad.setFechaModificacion(Instant.now());

        Actividad updatedActividad = actividadRepository.save(actividad);
        return convertToDTO(updatedActividad);
    }

    private List<FaseDTO> generateFases(String currentFase) {
        List<FaseDTO> fases = new ArrayList<>();
        String[] faseOrder = {"CLASIFICACION", "PRIMER_CONTACTO", "ENVIO_DE_INFORMACION", "REUNION",
                "COTIZACION_PROPUESTA_PRACTICA", "NEGOCIACION_REVISION", "CERRADO_GANADO",
                "RESPUESTA_POR_CORREO", "INTERES_FUTURO", "CERRADO_PERDIDO"};
        for (int i = 0; i < faseOrder.length; i++) {
            fases.add(new FaseDTO(
                    faseOrder[i],
                    faseOrder[i].equals(currentFase),
                    i < Arrays.asList(faseOrder).indexOf(currentFase)
            ));
        }
        return fases;
    }

    @Transactional
    public List<TratoDTO> filtrarTratos(Integer empresaId, Integer propietarioId, Instant startDate, Instant endDate) {
        List<Trato> tratos;
        Instant start = (startDate != null) ? startDate : Instant.now().minusSeconds(60 * 60 * 24 * 365 * 10); // 10 years ago
        Instant end = (endDate != null) ? endDate : Instant.now();

        // Obtener el usuario actual para determinar si es empleado
        Integer currentUserId = getCurrentUserId();
        RolUsuarioEnum currentUserRole = getCurrentUserRole(); // Necesitarás implementar este método

        if (empresaId != null && propietarioId != null) {
            tratos = tratoRepository.findByEmpresaIdAndPropietarioIdAndFechaCreacionBetween(empresaId, propietarioId, start, end);
        } else if (empresaId != null) {
            tratos = tratoRepository.findByEmpresaIdAndFechaCreacionBetween(empresaId, start, end);
        } else if (propietarioId != null) {
            // Si es empleado, buscar tratos donde sea propietario O tenga actividades asignadas
            if (RolUsuarioEnum.EMPLEADO.equals(currentUserRole)) {
                tratos = tratoRepository.findByPropietarioIdOrAsignadoIdAndFechaCreacionBetween(propietarioId, start, end);
            } else {
                tratos = tratoRepository.findByPropietarioIdAndFechaCreacionBetween(propietarioId, start, end);
            }
        } else {
            // Si no hay filtros específicos y es empleado, aplicar la misma lógica
            if (RolUsuarioEnum.EMPLEADO.equals(currentUserRole)) {
                tratos = tratoRepository.findByPropietarioIdOrAsignadoIdAndFechaCreacionBetween(currentUserId, start, end);
            } else {
                tratos = tratoRepository.findByFechaCreacionBetween(start, end);
            }
        }
        return tratos.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private RolUsuarioEnum getCurrentUserRole() {
        return ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getRol();
    }

    @Transactional(readOnly = true)
    public List<TratoDTO> contarTratosPorPropietario(Instant startDate, Instant endDate) {
        Instant start = (startDate != null) ? startDate : Instant.now().minusSeconds(60 * 60 * 24 * 365 * 10);
        Instant end = (endDate != null) ? endDate : Instant.now();
        List<Object[]> results = tratoRepository.countTratosByPropietario(start, end);
        return results.stream().map(result -> {
            Integer propietarioId = (Integer) result[0];
            Long count = (Long) result[1];
            Usuario usuario = usuarioRepository.findById(propietarioId).orElse(null);
            TratoDTO dto = new TratoDTO();
            dto.setPropietarioId(propietarioId);
            dto.setPropietarioNombre(usuario != null ? usuario.getNombre() : "Usuario Desconocido");
            dto.setNumeroUnidades(count.intValue());
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TratoCountDTO> contarTratosPorFaseYPropietario(Integer propietarioId, Instant startDate, Instant endDate) {
        Instant start = (startDate != null) ? startDate : Instant.now().minusSeconds(60 * 60 * 24 * 365 * 10); // 10 years ago
        Instant end = (endDate != null) ? endDate : Instant.now();

        List<Object[]> results;
        if (propietarioId == null) {
            results = tratoRepository.countTratosByFase(start, end);
        } else {
            results = tratoRepository.countTratosByFaseAndPropietario(propietarioId, start, end);
        }

        return results.stream().map(result -> {
            String fase = (String) result[0];
            Long count = (Long) result[1];
            TratoCountDTO dto = new TratoCountDTO();
            dto.setFase(fase);
            dto.setCount(count.intValue());
            return dto;
        }).collect(Collectors.toList());
    }

    private TratoDTO convertToDTO(Trato trato) {
        TratoDTO dto = new TratoDTO();
        dto.setId(trato.getId());
        dto.setNombre(trato.getNombre());
        dto.setEmpresaId(trato.getEmpresaId());
        dto.setContactoId(trato.getContacto() != null ? trato.getContacto().getId() : null);
        dto.setNumeroUnidades(trato.getNumeroUnidades());
        dto.setIngresosEsperados(trato.getIngresosEsperados());
        dto.setDescripcion(trato.getDescripcion());
        dto.setPropietarioId(trato.getPropietarioId());
        dto.setFechaCierre(trato.getFechaCierre());
        dto.setNoTrato(trato.getNoTrato());
        dto.setProbabilidad(trato.getProbabilidad());
        dto.setFase(trato.getFase());
        dto.setCorreosAutomaticosActivos(trato.getCorreosAutomaticosActivos());
        dto.setFechaCreacion(trato.getFechaCreacion());
        dto.setFechaModificacion(trato.getFechaModificacion());
        dto.setFechaUltimaActividad(trato.getFechaUltimaActividad());

        if (trato.getPropietarioId() != null) {
            Usuario propietario = usuarioRepository.findById(trato.getPropietarioId()).orElse(null);
            dto.setPropietarioNombre(propietario != null ? propietario.getNombre() : "Usuario");
        } else {
            dto.setPropietarioNombre("Usuario");
        }

        if (trato.getEmpresaId() != null) {
            Empresa empresa = empresaRepository.findById(trato.getEmpresaId()).orElse(null);
            if (empresa != null) {
                dto.setEmpresaNombre(empresa.getNombre());
                dto.setDomicilio(empresa.getDomicilioFisico());
                dto.setSitioWeb(empresa.getSitioWeb());
                dto.setSector(empresa.getSector() != null ? empresa.getSector().name() : "No especificado");
            } else {
                dto.setEmpresaNombre("Empresa Asociada");
                dto.setDomicilio("No especificado");
                dto.setSitioWeb("No especificado");
                dto.setSector("No especificado");
            }
        } else {
            dto.setEmpresaNombre("Empresa Asociada");
            dto.setDomicilio("No especificado");
            dto.setSitioWeb("No especificado");
            dto.setSector("No especificado");
        }

        List<Actividad> actividades = actividadRepository.findByTratoId(trato.getId());
        if (actividades != null) {
            dto.setActividades(actividades.stream().map(this::convertToDTO).collect(Collectors.toList()));
            List<Actividad> actividadesAbiertas = actividades.stream()
                    .filter(a -> EstatusActividadEnum.ABIERTA.equals(a.getEstatus()))
                    .collect(Collectors.toList());
            dto.setActividadesAbiertas(new ActividadesAbiertasDTO(
                    actividadesAbiertas.stream()
                            .filter(a -> a.getTipo() != null && a.getTipo().name().equalsIgnoreCase("TAREA"))
                            .map(a -> enrichWithContacto(a, trato.getContacto()))
                            .collect(Collectors.toList()),
                    actividadesAbiertas.stream()
                            .filter(a -> a.getTipo() != null && a.getTipo().name().equalsIgnoreCase("LLAMADA"))
                            .map(a -> enrichWithContacto(a, trato.getContacto()))
                            .collect(Collectors.toList()),
                    actividadesAbiertas.stream()
                            .filter(a -> a.getTipo() != null && a.getTipo().name().equalsIgnoreCase("REUNION"))
                            .map(a -> enrichWithContacto(a, trato.getContacto()))
                            .collect(Collectors.toList())
            ));
            dto.setHistorialInteracciones(
                    actividades.stream()
                            .filter(a -> EstatusActividadEnum.CERRADA.equals(a.getEstatus()))
                            .map(a -> {
                                ActividadDTO interaccion = convertToDTO(a);
                                interaccion.setFechaCompletado(a.getFechaCompletado());
                                interaccion.setUsuarioCompletadoId(a.getUsuarioCompletadoId());
                                interaccion.setRespuesta(a.getRespuesta());
                                return interaccion;
                            })
                            .collect(Collectors.toList())
            );


        } else {
            dto.setActividades(new ArrayList<>());
            dto.setActividadesAbiertas(new ActividadesAbiertasDTO(new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
            dto.setHistorialInteracciones(new ArrayList<>());
        }

        dto.setFases(generateFases(trato.getFase()));

        if (trato.getContacto() != null) {
            Contacto contacto = trato.getContacto();
            dto.setContacto(new ContactoDTO(
                    contacto.getNombre(),
                    contacto.getTelefonos() != null ? contacto.getTelefonos().stream().findFirst().map(t -> t.getTelefono()).orElse("") : "",
                    contacto.getCelular() != null ? contacto.getCelular() : "",
                    contacto.getCorreos() != null ? contacto.getCorreos().stream().findFirst().map(c -> c.getCorreo()).orElse("") : ""
            ));
            dto.getContacto().setPropietarioId(contacto.getPropietario() != null ? contacto.getPropietario().getId() : null);
            dto.getContacto().setCreadoPor(contacto.getCreadoPor());
            dto.getContacto().setFechaCreacion(contacto.getFechaCreacion());
            dto.getContacto().setFechaModificacion(contacto.getFechaModificacion());
            dto.getContacto().setFechaUltimaActividad(contacto.getFechaUltimaActividad());
        }
        dto.setHistorialInteracciones(
                actividades.stream()
                        .filter(a -> EstatusActividadEnum.CERRADA.equals(a.getEstatus()))
                        .map(this::convertToDTO)
                        .collect(Collectors.toList())
        );

        List<NotaTrato> notas = notaTratoRepository.findByTratoIdOrderByFechaCreacionDesc(trato.getId().longValue());
        dto.setNotas(notas.stream().map(this::convertToDTO).collect(Collectors.toList()));

        return dto;
    }

    public ActividadDTO convertToDTO(Actividad actividad) {
        ActividadDTO dto = new ActividadDTO();
        dto.setId(actividad.getId());
        dto.setTratoId(actividad.getTratoId());
        dto.setTipo(actividad.getTipo());
        dto.setSubtipoTarea(actividad.getSubtipoTarea());
        dto.setAsignadoAId(actividad.getAsignadoAId());
        dto.setFechaLimite(actividad.getFechaLimite());
        dto.setHoraInicio(actividad.getHoraInicio());
        dto.setDuracion(actividad.getDuracion());
        dto.setModalidad(actividad.getModalidad());
        dto.setLugarReunion(actividad.getLugarReunion());
        dto.setMedio(actividad.getMedio());
        dto.setEnlaceReunion(actividad.getEnlaceReunion());
        dto.setFinalidad(actividad.getFinalidad());
        dto.setEstatus(actividad.getEstatus());
        dto.setFechaCompletado(actividad.getFechaCompletado());
        dto.setUsuarioCompletadoId(actividad.getUsuarioCompletadoId());
        dto.setRespuesta(actividad.getRespuesta());
        dto.setInteres(actividad.getInteres());
        dto.setInformacion(actividad.getInformacion());
        dto.setSiguienteAccion(actividad.getSiguienteAccion());
        dto.setNotas(actividad.getNotas());
        dto.setFechaCreacion(actividad.getFechaCreacion());
        dto.setFechaModificacion(actividad.getFechaModificacion());
        dto.setContactoId(actividad.getContactoId());
        return dto;
    }

    private ActividadDTO enrichWithContacto(Actividad actividad, Contacto tratoContacto) {
        ActividadDTO dto = convertToDTO(actividad);
        if (dto.getContactoId() == null && tratoContacto != null) {
            dto.setContactoId(tratoContacto.getId());
        }
        return dto;
    }

    private NotaTratoDTO convertToDTO(NotaTrato notaTrato) {
        NotaTratoDTO dto = new NotaTratoDTO();
        dto.setId(notaTrato.getId());
        dto.setTratoId(notaTrato.getTratoId());
        dto.setUsuarioId(notaTrato.getUsuarioId());
        dto.setNota(notaTrato.getNota().replaceAll("^\"|\"$", "").replaceAll("\\\\\"", "\"")); // Limpiar comillas
        dto.setFechaCreacion(notaTrato.getFechaCreacion());
        dto.setEditadoPor(notaTrato.getEditadoPor());
        dto.setFechaEdicion(notaTrato.getFechaEdicion());

        if (notaTrato.getUsuarioId() != null) {
            Usuario usuario = usuarioRepository.findById(notaTrato.getUsuarioId()).orElse(null);
            dto.setAutorNombre(usuario != null ? usuario.getNombre() : "Usuario Desconocido");
        } else {
            dto.setAutorNombre("Usuario Desconocido");
        }
        if (notaTrato.getEditadoPor() != null) {
            Usuario editor = usuarioRepository.findById(notaTrato.getEditadoPor()).orElse(null);
            dto.setEditadoPorNombre(editor != null ? editor.getNombre() : "Usuario Desconocido");
        } else {
            dto.setEditadoPorNombre(null);
        }
        return dto;
    }

    private Integer getCurrentUserId() {
        return ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
    }

    private Integer getProbabilidadPorFase(String fase) {
        switch (fase) {
            case "CLASIFICACION":
                return 0;
            case "PRIMER_CONTACTO":
                return 10;
            case "ENVIO_DE_INFORMACION":
                return 30;
            case "REUNION":
                return 50;
            case "COTIZACION_PROPUESTA_PRACTICA":
                return 70;
            case "NEGOCIACION_REVISION":
                return 85;
            case "CERRADO_GANADO":
                return 100;
            case "RESPUESTA_POR_CORREO":
                return 0;
            case "INTERES_FUTURO":
                return 0;
            case "CERRADO_PERDIDO":
                return 0;
            default:
                return 0;
        }
    }


    private void enviarCorreosReunionVirtual(Actividad actividad, Integer tratoId) {
        try {
            // Obtener información del trato y contacto
            Optional<Trato> tratoOptional = tratoRepository.findTratoWithContacto(tratoId);

            if (!tratoOptional.isPresent()) {
                System.err.println("ERROR: No se encontró el trato con ID: " + tratoId);
                return;
            }
            Trato trato = tratoOptional.get();
            if (trato.getContacto() == null) {
                System.err.println("ERROR: El trato no tiene contacto asociado");
                return;
            }
            Contacto contacto = trato.getContacto();

            // Obtener usuario asignado
            Usuario usuarioAsignado = null;
            if (actividad.getAsignadoAId() != null) {
                usuarioAsignado = usuarioRepository.findById(actividad.getAsignadoAId()).orElse(null);
                if (usuarioAsignado != null) {
                    System.out.println("Usuario asignado encontrado: " + usuarioAsignado.getNombre());
                    System.out.println("Email usuario asignado: " + usuarioAsignado.getCorreoElectronico());
                } else {
                    System.out.println("ADVERTENCIA: No se encontró usuario asignado con ID: " + actividad.getAsignadoAId());
                }
            } else {
                System.out.println("ADVERTENCIA: La actividad no tiene usuario asignado");
            }

            // Preparar datos del correo
            String asunto = "Reunión Virtual Programada con Tracking Smart Solutions";
            String cuerpoCorreo = generarCuerpoCorreoReunion(actividad, trato, usuarioAsignado);
            // Obtener correo del contacto
            String correoContacto = obtenerCorreoContacto(contacto);
            // Enviar correo al contacto
            if (correoContacto != null && !correoContacto.trim().isEmpty()) {
                try {
                    EmailRecord recordContacto = emailService.enviarCorreo(correoContacto, asunto, cuerpoCorreo, null, tratoId);
                } catch (Exception e) {
                    System.err.println("ERROR enviando correo al contacto: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("NO SE ENVÍA correo al contacto - Email vacío o nulo");
            }

            // Enviar correo al usuario asignado
            if (usuarioAsignado != null &&
                    usuarioAsignado.getCorreoElectronico() != null &&
                    !usuarioAsignado.getCorreoElectronico().trim().isEmpty()) {
                try {
                    EmailRecord recordUsuario = emailService.enviarCorreo(
                            usuarioAsignado.getCorreoElectronico(), asunto, cuerpoCorreo, null, tratoId);
                } catch (Exception e) {
                    System.err.println("ERROR enviando correo al usuario: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("NO SE ENVÍA correo al usuario - Usuario nulo o email vacío");
                if (usuarioAsignado == null) {
                    System.out.println("  - Usuario asignado es NULL");
                } else if (usuarioAsignado.getCorreoElectronico() == null) {
                    System.out.println("  - Email del usuario es NULL");
                } else if (usuarioAsignado.getCorreoElectronico().trim().isEmpty()) {
                    System.out.println("  - Email del usuario está vacío");
                }
            }

        } catch (Exception e) {
            System.err.println("ERROR GENERAL en enviarCorreosReunionVirtualDebug: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String obtenerCorreoContacto(Contacto contacto) {
        if (contacto.getCorreos() == null) {
            return null;
        }
        if (contacto.getCorreos().isEmpty()) {
            return null;
        }

        // Imprimir todos los correos disponibles
        for (int i = 0; i < contacto.getCorreos().size(); i++) {
            var correoObj = contacto.getCorreos().get(i);
        }

        String primerCorreo = contacto.getCorreos().stream()
                .findFirst()
                .map(c -> c.getCorreo())
                .orElse(null);
        return primerCorreo;
    }

    private String generarCuerpoCorreoReunion(Actividad actividad, Trato trato, Usuario usuarioAsignado) {
        StringBuilder cuerpo = new StringBuilder();
        cuerpo.append("<html><body>");
        cuerpo.append("<h2>Reunión Virtual Programada</h2>");

        if (actividad.getFechaLimite() != null) {
            cuerpo.append("<p><strong>Fecha:</strong> ").append(actividad.getFechaLimite()).append("</p>");
        } else {
            System.out.println("ADVERTENCIA: Fecha límite es NULL");
        }

        if (actividad.getHoraInicio() != null) {
            cuerpo.append("<p><strong>Hora:</strong> ").append(actividad.getHoraInicio()).append("</p>");
        } else {
            System.out.println("ADVERTENCIA: Hora inicio es NULL");
        }

        if (actividad.getDuracion() != null) {
            String duracionTexto = actividad.getDuracion().contains(":") ? actividad.getDuracion() + " horas" : actividad.getDuracion() + " minutos";
            cuerpo.append("<p><strong>Duración:</strong> ").append(duracionTexto).append("</p>");
        } else {
            System.out.println("ADVERTENCIA: Duración es NULL");
        }

        if (actividad.getEnlaceReunion() != null && !actividad.getEnlaceReunion().isEmpty()) {
            cuerpo.append("<p><strong>Enlace de reunión:</strong> <a href=\"").append(actividad.getEnlaceReunion()).append("\">").append(actividad.getEnlaceReunion()).append("</a></p>");
        } else {
            System.out.println("ADVERTENCIA: Enlace de reunión es NULL o vacío");
        }

        if (usuarioAsignado != null) {
            cuerpo.append("<p><strong>Responsable:</strong> ").append(usuarioAsignado.getNombre()).append("</p>");
        } else {
            System.out.println("ADVERTENCIA: Usuario asignado es NULL");
        }
        cuerpo.append("<p>Esta reunión ha sido programada automáticamente. Por favor, confirme su asistencia.</p>");
        cuerpo.append("</body></html>");

        String cuerpoFinal = cuerpo.toString();
        return cuerpoFinal;
    }

    private void enviarCorreosReunionVirtualReprogramada(Actividad actividad, Integer tratoId) {
        try {
            // Obtener información del trato y contacto
            Optional<Trato> tratoOptional = tratoRepository.findTratoWithContacto(tratoId);

            if (!tratoOptional.isPresent()) {
                System.err.println("ERROR: No se encontró el trato con ID: " + tratoId);
                return;
            }
            Trato trato = tratoOptional.get();
            if (trato.getContacto() == null) {
                System.err.println("ERROR: El trato no tiene contacto asociado");
                return;
            }
            Contacto contacto = trato.getContacto();

            // Obtener usuario asignado
            Usuario usuarioAsignado = null;
            if (actividad.getAsignadoAId() != null) {
                usuarioAsignado = usuarioRepository.findById(actividad.getAsignadoAId()).orElse(null);
                if (usuarioAsignado != null) {
                    System.out.println("Usuario asignado encontrado: " + usuarioAsignado.getNombre());
                    System.out.println("Email usuario asignado: " + usuarioAsignado.getCorreoElectronico());
                } else {
                    System.out.println("ADVERTENCIA: No se encontró usuario asignado con ID: " + actividad.getAsignadoAId());
                }
            } else {
                System.out.println("ADVERTENCIA: La actividad no tiene usuario asignado");
            }

            // Preparar datos del correo
            String asunto = "Reunión Virtual Reprogramada con Tracking Smart Solutions";
            String cuerpoCorreo = generarCuerpoCorreoReunionReprogramada(actividad, trato, usuarioAsignado);
            // Obtener correo del contacto
            String correoContacto = obtenerCorreoContacto(contacto);
            // Enviar correo al contacto
            if (correoContacto != null && !correoContacto.trim().isEmpty()) {
                try {
                    EmailRecord recordContacto = emailService.enviarCorreo(correoContacto, asunto, cuerpoCorreo, null, tratoId);
                } catch (Exception e) {
                    System.err.println("ERROR enviando correo al contacto: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("NO SE ENVÍA correo al contacto - Email vacío o nulo");
            }

            // Enviar correo al usuario asignado
            if (usuarioAsignado != null &&
                    usuarioAsignado.getCorreoElectronico() != null &&
                    !usuarioAsignado.getCorreoElectronico().trim().isEmpty()) {
                try {
                    EmailRecord recordUsuario = emailService.enviarCorreo(
                            usuarioAsignado.getCorreoElectronico(), asunto, cuerpoCorreo, null, tratoId);
                } catch (Exception e) {
                    System.err.println("ERROR enviando correo al usuario: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("NO SE ENVÍA correo al usuario - Usuario nulo o email vacío");
                if (usuarioAsignado == null) {
                    System.out.println("  - Usuario asignado es NULL");
                } else if (usuarioAsignado.getCorreoElectronico() == null) {
                    System.out.println("  - Email del usuario es NULL");
                } else if (usuarioAsignado.getCorreoElectronico().trim().isEmpty()) {
                    System.out.println("  - Email del usuario está vacío");
                }
            }

        } catch (Exception e) {
            System.err.println("ERROR GENERAL en enviarCorreosReunionVirtualReprogramada: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String generarCuerpoCorreoReunionReprogramada(Actividad actividad, Trato trato, Usuario usuarioAsignado) {
        StringBuilder cuerpo = new StringBuilder();
        cuerpo.append("<html><body>");
        cuerpo.append("<h2>Reunión Virtual Reprogramada</h2>");

        if (actividad.getFechaLimite() != null) {
            cuerpo.append("<p><strong>Nueva Fecha:</strong> ").append(actividad.getFechaLimite()).append("</p>");
        } else {
            System.out.println("ADVERTENCIA: Fecha límite es NULL");
        }

        if (actividad.getHoraInicio() != null) {
            cuerpo.append("<p><strong>Nueva Hora:</strong> ").append(actividad.getHoraInicio()).append("</p>");
        } else {
            System.out.println("ADVERTENCIA: Hora inicio es NULL");
        }

        if (actividad.getDuracion() != null) {
            String duracionTexto = actividad.getDuracion().contains(":") ? actividad.getDuracion() + " horas" : actividad.getDuracion() + " minutos";
            cuerpo.append("<p><strong>Duración:</strong> ").append(duracionTexto).append("</p>");
        } else {
            System.out.println("ADVERTENCIA: Duración es NULL");
        }

        if (actividad.getEnlaceReunion() != null && !actividad.getEnlaceReunion().isEmpty()) {
            cuerpo.append("<p><strong>Enlace de reunión:</strong> <a href=\"").append(actividad.getEnlaceReunion()).append("\">").append(actividad.getEnlaceReunion()).append("</a></p>");
        } else {
            System.out.println("ADVERTENCIA: Enlace de reunión es NULL o vacío");
        }

        if (usuarioAsignado != null) {
            cuerpo.append("<p><strong>Responsable:</strong> ").append(usuarioAsignado.getNombre()).append("</p>");
        } else {
            System.out.println("ADVERTENCIA: Usuario asignado es NULL");
        }
        cuerpo.append("<p>Esta reunión ha sido reprogramada. Por favor, tome nota de los nuevos horarios y confirme su asistencia.</p>");
        cuerpo.append("</body></html>");

        String cuerpoFinal = cuerpo.toString();
        return cuerpoFinal;
    }

}