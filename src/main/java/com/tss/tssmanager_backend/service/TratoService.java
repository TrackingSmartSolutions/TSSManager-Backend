package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.*;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.*;
import com.tss.tssmanager_backend.repository.*;
import com.tss.tssmanager_backend.security.CustomUserDetails;
import com.tss.tssmanager_backend.utils.DateUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
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


    private static final int MARGEN_CONFLICTO_MINUTOS = 9;

    @Cacheable(value = "tratos", key = "#id")
    public Trato findById(Integer id) {
        return tratoRepository.findById(id).orElse(null);
    }

    @Cacheable(value = "tratos", key = "'empresa_' + #empresaId")
    public List<Trato> findByEmpresaId(Integer empresaId) {
        return tratoRepository.findByEmpresaId(empresaId);
    }

    @CacheEvict(value = "tratos", key = "#trato.id")
    public Trato save(Trato trato) {
        return tratoRepository.save(trato);
    }

    @CacheEvict(value = "tratos", allEntries = true)
    public void clearCache() {
        // Método para limpiar todo el cache de tratos
    }

    public List<TratoDTO> listarTratos() {
        return tratoRepository.findAll().stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TratoDTO getTratoById(Integer id) {
        List<Object[]> result = tratoRepository.findTratoCompleteByIdWithAllData(id);

        if (result.isEmpty()) {
            throw new RuntimeException("Trato no encontrado con id: " + id);
        }

        return convertToCompleteTratoDTOFromQuery(result, id);
    }

    private TratoDTO convertToCompleteTratoDTOFromQuery(List<Object[]> results, Integer tratoId) {
        TratoDTO dto = null;
        Map<Integer, ActividadDTO> actividadesMap = new HashMap<>();
        List<NotaTratoDTO> notas = new ArrayList<>();

        for (Object[] row : results) {

            if (dto == null) {
                dto = new TratoDTO();
                dto.setId((Integer) row[0]);
                dto.setNombre((String) row[1]);
                dto.setEmpresaId((Integer) row[2]);
                dto.setNumeroUnidades((Integer) row[3]);
                dto.setIngresosEsperados((BigDecimal) row[4]);
                dto.setDescripcion((String) row[5]);
                dto.setPropietarioId((Integer) row[6]);
                dto.setFechaCierre(((Timestamp) row[7]).toLocalDateTime());
                dto.setNoTrato((String) row[8]);
                dto.setProbabilidad((Integer) row[9]);
                dto.setFase((String) row[10]);
                dto.setCorreosAutomaticosActivos((Boolean) row[11]);
                dto.setFechaCreacion(convertToInstant(row[12]));
                dto.setFechaModificacion(convertToInstant(row[13]));
                dto.setFechaUltimaActividad(convertToInstant(row[14]));

                dto.setPropietarioNombre((String) row[15]);
                dto.setEmpresaNombre((String) row[16]);

                // Agregar información adicional de empresa si existe
                if (row[17] != null) dto.setDomicilio((String) row[17]);
                if (row[18] != null) dto.setSitioWeb((String) row[18]);
                if (row[19] != null) {
                    dto.setSectorNombre((String) row[19]);
                } else {
                    dto.setSectorNombre("No especificado");
                }

                dto.setContactoId((Integer) row[20]);

                // Información de contacto
                if (row[21] != null) {
                    ContactoDTO contacto = new ContactoDTO();
                    contacto.setId((Integer) row[20]);
                    contacto.setNombre((String) row[21]);
                    contacto.setCelular((String) row[22]);

                    String telefono = (String) row[23];
                    String correo = (String) row[24];

                    dto.setContacto(new ContactoDTO(
                            (String) row[21],
                            telefono != null ? telefono : "",
                            (String) row[22],
                            correo != null ? correo : ""
                    ));

                    dto.getContacto().setId((Integer) row[20]);
                }
                dto.setFases(generateFases(dto.getFase()));
            }

            if (row[25] != null) {
                Integer actividadId = (Integer) row[25];
                if (!actividadesMap.containsKey(actividadId)) {
                    ActividadDTO actividad = new ActividadDTO();
                    actividad.setId(actividadId);
                    actividad.setTratoId((Integer) row[26]);

                    if (row[27] != null) {
                        String tipoStr = (String) row[27];
                        actividad.setTipo(TipoActividadEnum.valueOf(tipoStr));
                    }
                    if (row[28] != null) {
                        String subtipoStr = (String) row[28];
                        actividad.setSubtipoTarea(SubtipoTareaEnum.valueOf(subtipoStr));
                    }

                    actividad.setAsignadoAId((Integer) row[29]);
                    actividad.setFechaLimite(convertToLocalDate(row[30]));
                    actividad.setHoraInicio((Time) row[31]);
                    actividad.setDuracion((String) row[32]);

                    if (row[33] != null) {
                        String modalidadStr = (String) row[33];
                        actividad.setModalidad(ModalidadActividadEnum.valueOf(modalidadStr));
                    }

                    actividad.setLugarReunion((String) row[34]);
                    if (row[35] != null) {
                        String medioStr = (String) row[35];
                        actividad.setMedio(MedioReunionEnum.valueOf(medioStr));
                    }

                    actividad.setEnlaceReunion((String) row[36]);
                    if (row[37] != null) {
                        String finalidadStr = (String) row[37];
                        actividad.setFinalidad(FinalidadActividadEnum.valueOf(finalidadStr));
                    }
                    if (row[38] != null) {
                        String estatusStr = (String) row[38];
                        actividad.setEstatus(EstatusActividadEnum.valueOf(estatusStr));
                    }

                    actividad.setFechaCompletado(convertToInstant(row[39]));
                    actividad.setUsuarioCompletadoId((Integer) row[40]);

                    if (row[41] != null) {
                        String respuestaStr = (String) row[41];
                        actividad.setRespuesta(RespuestaEnum.valueOf(respuestaStr));
                    }
                    if (row[42] != null) {
                        String interesStr = (String) row[42];
                        actividad.setInteres(InteresEnum.valueOf(interesStr));
                    }
                    if (row[43] != null) {
                        String informacionStr = (String) row[43];
                        actividad.setInformacion(InformacionEnum.valueOf(informacionStr));
                    }
                    if (row[44] != null) {
                        String siguienteAccionStr = (String) row[44];
                        actividad.setSiguienteAccion(SiguienteAccionEnum.valueOf(siguienteAccionStr));
                    }

                    actividad.setNotas((String) row[45]);
                    actividad.setFechaCreacion(convertToInstant(row[46]));
                    actividad.setFechaModificacion(convertToInstant(row[47]));
                    actividad.setContactoId((Integer) row[48]);
                    actividad.setAsignadoANombre((String) row[49]);

                    actividadesMap.put(actividadId, actividad);
                }
            }

            if (row[50] != null) {
                NotaTratoDTO nota = new NotaTratoDTO();
                nota.setId((Integer) row[50]);
                nota.setTratoId((Integer) row[51]);
                nota.setUsuarioId((Integer) row[52]);
                nota.setNota(((String) row[53]).replaceAll("^\"|\"$", "").replaceAll("\\\\\"", "\""));
                nota.setFechaCreacion(convertToInstant(row[54]));
                nota.setEditadoPor((Integer) row[55]);
                nota.setFechaEdicion(convertToInstant(row[56]));
                nota.setAutorNombre((String) row[57]);
                nota.setEditadoPorNombre((String) row[58]);

                if (notas.stream().noneMatch(n -> n.getId().equals(nota.getId()))) {
                    notas.add(nota);
                }
            }
        }

        if (dto != null) {
            List<ActividadDTO> todasActividades = new ArrayList<>(actividadesMap.values());
            dto.setActividades(todasActividades);

            // Separar actividades abiertas por tipo
            List<ActividadDTO> abiertas = todasActividades.stream()
                    .filter(a -> EstatusActividadEnum.ABIERTA.equals(a.getEstatus()))
                    .collect(Collectors.toList());

            List<ActividadDTO> tareas = abiertas.stream()
                    .filter(a -> TipoActividadEnum.TAREA.equals(a.getTipo()))
                    .collect(Collectors.toList());

            List<ActividadDTO> llamadas = abiertas.stream()
                    .filter(a -> TipoActividadEnum.LLAMADA.equals(a.getTipo()))
                    .collect(Collectors.toList());

            List<ActividadDTO> reuniones = abiertas.stream()
                    .filter(a -> TipoActividadEnum.REUNION.equals(a.getTipo()))
                    .collect(Collectors.toList());

            dto.setActividadesAbiertas(new ActividadesAbiertasDTO(tareas, llamadas, reuniones));

            // Historial de interacciones
            List<ActividadDTO> historial = todasActividades.stream()
                    .filter(a -> EstatusActividadEnum.CERRADA.equals(a.getEstatus()))
                    .sorted((a1, a2) -> {
                        // Ordenar por fecha de completado descendente (más reciente primero)
                        if (a1.getFechaCompletado() != null && a2.getFechaCompletado() != null) {
                            return a2.getFechaCompletado().compareTo(a1.getFechaCompletado());
                        }
                        // Si no hay fecha de completado, usar fecha de creación
                        if (a1.getFechaCreacion() != null && a2.getFechaCreacion() != null) {
                            return a2.getFechaCreacion().compareTo(a1.getFechaCreacion());
                        }
                        return 0;
                    })
                    .collect(Collectors.toList());
            dto.setHistorialInteracciones(historial);

            dto.setNotas(notas.stream()
                    .map(this::convertNotaTratoToDTO)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    private NotaTratoDTO convertNotaTratoToDTO(NotaTratoDTO notaDTO) {
        notaDTO.setNota(notaDTO.getNota().replaceAll("^\"|\"$", "").replaceAll("\\\\\"", "\""));
        return notaDTO;
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
        if (actividadDTO.getFechaLimite() != null && actividadDTO.getHoraInicio() != null) {
            boolean hayConflicto = existeConflictoHorario(
                    actividadDTO.getAsignadoAId(),
                    actividadDTO.getFechaLimite(),
                    actividadDTO.getHoraInicio(),
                    actividadDTO.getDuracion(),
                    null
            );

            if (hayConflicto) {
                throw new RuntimeException("Ya existe una actividad programada en este horario para el usuario asignado.");
            }
        }
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
        actividad.setNotas(actividadDTO.getNotas());
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

        return convertToDTO(savedActividad);
    }

    @Transactional
    public ActividadDTO reprogramarActividad(Integer id, ActividadDTO actividadDTO) {
        Actividad actividad = actividadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Actividad no encontrada con id: " + id));

        LocalDate nuevaFecha = actividadDTO.getFechaLimite() != null ? actividadDTO.getFechaLimite() : actividad.getFechaLimite();
        Time nuevaHora = actividadDTO.getHoraInicio() != null ? actividadDTO.getHoraInicio() : actividad.getHoraInicio();
        String nuevaDuracion = actividadDTO.getDuracion() != null ? actividadDTO.getDuracion() : actividad.getDuracion();
        Integer nuevoAsignado = actividadDTO.getAsignadoAId() != null ? actividadDTO.getAsignadoAId() : actividad.getAsignadoAId();

        if (nuevaFecha != null && nuevaHora != null) {
            boolean hayConflicto = existeConflictoHorario(nuevoAsignado, nuevaFecha, nuevaHora, nuevaDuracion, id);

            if (hayConflicto) {
                throw new RuntimeException("Ya existe una actividad programada en este horario para el usuario asignado.");
            }
        }

        if (actividad.getFechaModificacion() != null) {
            long tiempoTranscurrido = Instant.now().toEpochMilli() - actividad.getFechaModificacion().toEpochMilli();
            if (tiempoTranscurrido < 5000) {
                return convertToDTO(actividad);
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
        actividad.setNotas(actividadDTO.getNotas() != null ? actividadDTO.getNotas() : actividad.getNotas());

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

        return convertToDTO(updatedActividad);
    }

    @Transactional
    public void eliminarActividad(Integer id) {
        Actividad actividad = actividadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Actividad no encontrada con id: " + id));
        if (EstatusActividadEnum.CERRADA.equals(actividad.getEstatus())) {
            throw new RuntimeException("No se puede eliminar una actividad que ya está completada");
        }
        Integer tratoId = actividad.getTratoId();

        actividadRepository.delete(actividad);

        if (tratoId != null) {
            Trato trato = tratoRepository.findById(tratoId).orElse(null);
            if (trato != null) {
                trato.setFechaModificacion(Instant.now());
                tratoRepository.save(trato);
            }
        }
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
        actividad.setFechaCompletado(DateUtils.nowInMexico().atZone(ZoneId.of("America/Mexico_City")).toInstant());
        actividad.setUsuarioCompletadoId(getCurrentUserId());
        actividad.setRespuesta(actividadDTO.getRespuesta());
        actividad.setInteres(actividadDTO.getInteres());
        actividad.setInformacion(actividadDTO.getInformacion());
        actividad.setSiguienteAccion(actividadDTO.getSiguienteAccion());
        actividad.setNotas(actividadDTO.getNotas());
        actividad.setMedio(actividadDTO.getMedio());
        actividad.setFechaModificacion(Instant.now());

        Actividad updatedActividad = actividadRepository.save(actividad);

        if (actividadDTO.getNotas() != null && !actividadDTO.getNotas().trim().isEmpty()) {
            NotaTrato notaTrato = new NotaTrato();
            notaTrato.setTratoId(actividad.getTratoId());
            notaTrato.setUsuarioId(getCurrentUserId());
            notaTrato.setNota(actividadDTO.getNotas().trim());
            notaTrato.setFechaCreacion(Instant.now());
            notaTratoRepository.save(notaTrato);
        }

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

    @Transactional(readOnly = true)
    public List<TratoDTO> filtrarTratos(Integer empresaId, Integer propietarioId, Instant startDate, Instant endDate) {
        Instant start = (startDate != null) ? startDate : Instant.now().minusSeconds(60 * 60 * 24 * 365 * 10);
        Instant end = (endDate != null) ? endDate : Instant.now();

        Integer currentUserId = getCurrentUserId();
        RolUsuarioEnum currentUserRole = getCurrentUserRole();

        List<Object[]> results;

        if (RolUsuarioEnum.EMPLEADO.equals(currentUserRole)) {
            // Para empleados: filtrar por empresa Y por ser propietario o tener actividades asignadas
            if (empresaId != null) {
                results = tratoRepository.findTratosForEmpleadoByEmpresa(currentUserId, empresaId, start, end);
            } else {
                results = tratoRepository.findTratosForEmpleado(currentUserId, start, end);
            }
        } else {
            results = tratoRepository.findTratosOptimized(empresaId, propietarioId, start, end);
        }

        // Convertir resultados a DTOs de forma optimizada
        List<TratoDTO> tratos = convertToTratosDTOOptimized(results);

        // Cargar actividades por lotes solo para los tratos que las necesiten
        loadActivitiesInBatch(tratos);

        return tratos;
    }

    private RolUsuarioEnum getCurrentUserRole() {
        return ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getRol();
    }

    private List<TratoDTO> convertToTratosDTOOptimized(List<Object[]> results) {
        List<TratoDTO> tratos = new ArrayList<>();
        Instant currentTime = Instant.now();

        for (Object[] row : results) {
            try {
                TratoDTO dto = new TratoDTO();

                // Mapear campos básicos directamente desde la query
                dto.setId((Integer) row[0]);
                dto.setNombre((String) row[1]);
                dto.setEmpresaId((Integer) row[2]);
                dto.setNumeroUnidades((Integer) row[3]);
                dto.setIngresosEsperados((BigDecimal) row[4]);
                dto.setDescripcion((String) row[5]);
                dto.setPropietarioId((Integer) row[6]);
                dto.setFechaCierre(((Timestamp) row[7]).toLocalDateTime());
                dto.setNoTrato((String) row[8]);
                dto.setProbabilidad((Integer) row[9]);
                dto.setFase((String) row[10]);
                dto.setCorreosAutomaticosActivos((Boolean) row[11]);

                // Usar conversión segura para fechas
                dto.setFechaCreacion(convertToInstant(row[12]));
                dto.setFechaModificacion(convertToInstant(row[13]));
                dto.setFechaUltimaActividad(convertToInstant(row[14]));

                // Datos relacionados ya obtenidos en la query
                dto.setPropietarioNombre((String) row[15]);
                dto.setEmpresaNombre((String) row[16]);
                dto.setContactoId((Integer) row[17]);

                // Configurar información de contacto básica
                if (row[18] != null) {
                    ContactoDTO contacto = new ContactoDTO();
                    contacto.setNombre((String) row[18]);
                    dto.setContacto(contacto);
                }

                // Determinar si está desatendido basado en los datos de la query
                Long actividadesAbiertasCount = ((Number) row[19]).longValue();
                Boolean hasActivities = (Boolean) row[20];

                Instant fechaUltimaActividad = dto.getFechaUltimaActividad() != null ?
                        dto.getFechaUltimaActividad() : dto.getFechaCreacion();

                long minutesInactive = ChronoUnit.MINUTES.between(fechaUltimaActividad, currentTime);
                dto.setIsNeglected(!hasActivities && minutesInactive > 10080);
                dto.setHasActivities(hasActivities);

                // Inicializar listas vacías (se llenarán después si es necesario)
                dto.setActividades(new ArrayList<>());
                dto.setActividadesAbiertas(new ActividadesAbiertasDTO(
                        new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
                dto.setHistorialInteracciones(new ArrayList<>());
                dto.setNotas(new ArrayList<>());
                dto.setFases(generateFases(dto.getFase()));

                tratos.add(dto);
            } catch (Exception e) {
                System.err.println("Error en convertToTratosDTOOptimized: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Ordenar tratos por fecha de cierre (más próxima primero)
        tratos.sort((t1, t2) -> {
            if (t1.getFechaCierre() == null && t2.getFechaCierre() == null) return 0;
            if (t1.getFechaCierre() == null) return 1;
            if (t2.getFechaCierre() == null) return -1;
            return t1.getFechaCierre().compareTo(t2.getFechaCierre());
        });

        return tratos;
    }

    private void loadActivitiesInBatch(List<TratoDTO> tratos) {
        if (tratos.isEmpty()) return;

        // Obtener IDs de tratos que tienen actividades
        List<Integer> tratoIdsWithActivities = tratos.stream()
                .filter(TratoDTO::getHasActivities)
                .map(TratoDTO::getId)
                .collect(Collectors.toList());

        if (tratoIdsWithActivities.isEmpty()) return;

        // Cargar todas las actividades de una vez
        List<Actividad> allActivities = tratoRepository.findActivitiesByTratoIds(tratoIdsWithActivities);

        // Agrupar actividades por trato
        Map<Integer, List<Actividad>> activitiesByTrato = allActivities.stream()
                .collect(Collectors.groupingBy(Actividad::getTratoId));

        // Asignar actividades a cada trato
        for (TratoDTO trato : tratos) {
            List<Actividad> tratoActivities = activitiesByTrato.get(trato.getId());
            if (tratoActivities != null && !tratoActivities.isEmpty()) {

                // Convertir actividades a DTOs
                List<ActividadDTO> actividadesDTOs = tratoActivities.stream()
                        .map(this::convertToDTO)
                        .collect(Collectors.toList());

                trato.setActividades(actividadesDTOs);

                // Separar actividades abiertas por tipo
                List<ActividadDTO> abiertas = actividadesDTOs.stream()
                        .filter(a -> EstatusActividadEnum.ABIERTA.equals(a.getEstatus()))
                        .collect(Collectors.toList());

                List<ActividadDTO> tareas = abiertas.stream()
                        .filter(a -> TipoActividadEnum.TAREA.equals(a.getTipo()))
                        .collect(Collectors.toList());

                List<ActividadDTO> llamadas = abiertas.stream()
                        .filter(a -> TipoActividadEnum.LLAMADA.equals(a.getTipo()))
                        .collect(Collectors.toList());

                List<ActividadDTO> reuniones = abiertas.stream()
                        .filter(a -> TipoActividadEnum.REUNION.equals(a.getTipo()))
                        .collect(Collectors.toList());

                trato.setActividadesAbiertas(new ActividadesAbiertasDTO(tareas, llamadas, reuniones));

                // Historial de interacciones (actividades cerradas)
                List<ActividadDTO> historial = actividadesDTOs.stream()
                        .filter(a -> EstatusActividadEnum.CERRADA.equals(a.getEstatus()))
                        .collect(Collectors.toList());

                trato.setHistorialInteracciones(historial);
            }
        }
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

                if (empresa.getSector() != null) {
                    dto.setSectorNombre(empresa.getSector().getNombreSector());
                    dto.setSectorId(empresa.getSector().getId());
                } else {
                    dto.setSectorNombre("No especificado");
                    dto.setSectorId(null);
                }
            } else {
                dto.setEmpresaNombre("Empresa Asociada");
                dto.setDomicilio("No especificado");
                dto.setSitioWeb("No especificado");
                dto.setSectorNombre("No especificado");
                dto.setSectorId(null);
            }
        } else {
            dto.setEmpresaNombre("Empresa Asociada");
            dto.setDomicilio("No especificado");
            dto.setSitioWeb("No especificado");
            dto.setSectorNombre("No especificado");
            dto.setSectorId(null);
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
                            .sorted((a1, a2) -> {
                                if (a1.getFechaCompletado() != null && a2.getFechaCompletado() != null) {
                                    return a2.getFechaCompletado().compareTo(a1.getFechaCompletado());
                                }
                                if (a1.getFechaCreacion() != null && a2.getFechaCreacion() != null) {
                                    return a2.getFechaCreacion().compareTo(a1.getFechaCreacion());
                                }
                                return 0;
                            })
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


    private void enviarCorreosReunion(Actividad actividad, Integer tratoId) {
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

        cuerpo.append("<h2>Confirmación de cita con Tracking Smart Solutions</h2>");
        cuerpo.append("<p>¡Hola!</p>");
        cuerpo.append("<p>Espero que esté teniendo un excelente día. Le escribo para confirmar nuestra reunión programada");

        // Agregar fecha y hora si están disponibles
        if (actividad.getFechaLimite() != null && actividad.getHoraInicio() != null) {
            String fechaFormateada = formatearFechaAmigable(actividad.getFechaLimite());
            cuerpo.append(" para el ").append(fechaFormateada).append(" a las ").append(actividad.getHoraInicio());
        }

        cuerpo.append(" con nuestro experto en soluciones para flotas de Tracking Smart Solutions y platicar sobre los desafíos logísticos que presenta hoy en día, así como discutir la manera en que podemos aportar valor a su negocio.</p>");

        // Duración si está disponible
        if (actividad.getDuracion() != null) {
            String duracionTexto = actividad.getDuracion().contains(":") ?
                    actividad.getDuracion() + " horas" : actividad.getDuracion() + " minutos";
            cuerpo.append("<p><strong>Duración estimada:</strong> ").append(duracionTexto).append("</p>");
        }

        // Información de la reunión según modalidad
        if (actividad.getModalidad() == ModalidadActividadEnum.VIRTUAL) {
            if (actividad.getEnlaceReunion() != null && !actividad.getEnlaceReunion().isEmpty()) {
                cuerpo.append("<p>Aquí le comparto el enlace para unirse a la videollamada:</p>");
                cuerpo.append("<p><a href=\"").append(actividad.getEnlaceReunion()).append("\" style=\"background-color: #007bff; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;\">Unirse a la reunión</a></p>");
                cuerpo.append("<p>O copie y pegue este enlace en su navegador:<br>").append(actividad.getEnlaceReunion()).append("</p>");
            }
        } else if (actividad.getModalidad() == ModalidadActividadEnum.PRESENCIAL) {
            if (actividad.getLugarReunion() != null && !actividad.getLugarReunion().isEmpty()) {
                cuerpo.append("<p><strong>Lugar de la reunión:</strong></p>");
                cuerpo.append("<p>").append(actividad.getLugarReunion()).append("</p>");
            }
        }

        // Mensaje de cierre
        cuerpo.append("<p>Si necesita hacer algún cambio en el horario o tiene alguna pregunta previa, no dude en contactarme.</p>");
        cuerpo.append("<p>Saludos cordiales,</p>");

        // Nombre del responsable
        if (usuarioAsignado != null) {
            cuerpo.append("<p>").append(usuarioAsignado.getNombre()).append("<br>");
            cuerpo.append("Tracking Smart Solutions</p>");
        } else {
            cuerpo.append("<p>Equipo de Tracking Smart Solutions</p>");
        }

        cuerpo.append("</body></html>");

        String cuerpoFinal = cuerpo.toString();
        System.out.println("Cuerpo del correo generado:\n" + cuerpoFinal);
        return cuerpoFinal;
    }


    private String generarCuerpoCorreoReunionReprogramada(Actividad actividad, Trato trato, Usuario usuarioAsignado) {
        StringBuilder cuerpo = new StringBuilder();
        cuerpo.append("<html><body>");

        // Mensaje principal para reprogramación
        cuerpo.append("<h2>Cambio de horario - Reunión con Tracking Smart Solutions</h2>");
        cuerpo.append("<p>¡Hola!</p>");
        cuerpo.append("<p>Espero que esté teniendo un excelente día. Le escribo para informarle sobre el cambio de horario en nuestra reunión");

        // Agregar nueva fecha y hora
        if (actividad.getFechaLimite() != null && actividad.getHoraInicio() != null) {
            String fechaFormateada = formatearFechaAmigable(actividad.getFechaLimite());
            cuerpo.append(". <strong>Nueva fecha y hora:</strong> ").append(fechaFormateada).append(" a las ").append(actividad.getHoraInicio());
        }

        cuerpo.append(".</p>");
        cuerpo.append("<p>Seguimos muy entusiasmados por conversar con usted sobre los desafíos logísticos de su empresa y cómo Tracking Smart Solutions puede aportar valor a su negocio.</p>");

        // Duración si está disponible
        if (actividad.getDuracion() != null) {
            String duracionTexto = actividad.getDuracion().contains(":") ?
                    actividad.getDuracion() + " horas" : actividad.getDuracion() + " minutos";
            cuerpo.append("<p><strong>Duración estimada:</strong> ").append(duracionTexto).append("</p>");
        }

        // Información de la reunión según modalidad
        if (actividad.getModalidad() == ModalidadActividadEnum.VIRTUAL) {
            if (actividad.getEnlaceReunion() != null && !actividad.getEnlaceReunion().isEmpty()) {
                cuerpo.append("<p>El enlace para unirse a la videollamada sigue siendo el mismo:</p>");
                cuerpo.append("<p><a href=\"").append(actividad.getEnlaceReunion()).append("\" style=\"background-color: #007bff; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;\">Unirse a la reunión</a></p>");
                cuerpo.append("<p>O copie y pegue este enlace en su navegador:<br>").append(actividad.getEnlaceReunion()).append("</p>");
            }
        } else if (actividad.getModalidad() == ModalidadActividadEnum.PRESENCIAL) {
            if (actividad.getLugarReunion() != null && !actividad.getLugarReunion().isEmpty()) {
                cuerpo.append("<p><strong>Lugar de la reunión:</strong></p>");
                cuerpo.append("<p>").append(actividad.getLugarReunion()).append("</p>");
            }
        }

        // Mensaje de cierre
        cuerpo.append("<p>Agradecemos su comprensión y flexibilidad. Si tiene alguna pregunta o necesita hacer algún ajuste adicional, no dude en contactarme.</p>");
        cuerpo.append("<p>Saludos cordiales,</p>");

        // Nombre del responsable
        if (usuarioAsignado != null) {
            cuerpo.append("<p>").append(usuarioAsignado.getNombre()).append("<br>");
            cuerpo.append("Tracking Smart Solutions</p>");
        } else {
            cuerpo.append("<p>Equipo de Tracking Smart Solutions</p>");
        }

        cuerpo.append("</body></html>");

        return cuerpo.toString();
    }

    // Método auxiliar para formatear la fecha de manera más amigable
    private String formatearFechaAmigable(LocalDate fecha) {
        if (fecha == null) return "";

        String[] diasSemana = {"lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};
        String[] meses = {"enero", "febrero", "marzo", "abril", "mayo", "junio",
                "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

        int diaSemana = fecha.getDayOfWeek().getValue() - 1; // 0 = lunes
        int dia = fecha.getDayOfMonth();
        int mes = fecha.getMonthValue() - 1; // 0 = enero

        return String.format("%s %d de %s", diasSemana[diaSemana], dia, meses[mes]);
    }


    private void enviarCorreosReunionReprogramada(Actividad actividad, Integer tratoId) {
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

    @Transactional
    public void enviarCorreoReunion(Integer actividadId, Integer tratoId) {
        Actividad actividad = actividadRepository.findById(actividadId)
                .orElseThrow(() -> new RuntimeException("Actividad no encontrada"));
        enviarCorreosReunion(actividad, tratoId);
    }

    @Transactional
    public void enviarCorreoReunionReprogramada(Integer actividadId, Integer tratoId) {
        Actividad actividad = actividadRepository.findById(actividadId)
                .orElseThrow(() -> new RuntimeException("Actividad no encontrada"));
        enviarCorreosReunionReprogramada(actividad, tratoId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> verificarDatosContacto(Integer tratoId) {
        Trato trato = tratoRepository.findTratoWithContacto(tratoId)
                .orElseThrow(() -> new RuntimeException("Trato no encontrado"));

        if (trato.getContacto() == null) {
            throw new RuntimeException("El trato no tiene contacto asociado");
        }

        Contacto contacto = trato.getContacto();
        boolean tieneCorreo = contacto.getCorreos() != null && !contacto.getCorreos().isEmpty() &&
                contacto.getCorreos().stream().anyMatch(c -> c.getCorreo() != null && !c.getCorreo().trim().isEmpty());
        boolean tieneCelular = contacto.getCelular() != null && !contacto.getCelular().trim().isEmpty();

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("tieneCorreo", tieneCorreo);
        resultado.put("tieneCelular", tieneCelular);
        resultado.put("nombreContacto", contacto.getNombre());
        resultado.put("celular", contacto.getCelular());

        return resultado;
    }

    @Transactional(readOnly = true)
    public Map<String, String> generarMensajeWhatsApp(Integer tratoId, Integer actividadId, boolean esReprogramacion) {
        Trato trato = tratoRepository.findTratoWithContacto(tratoId)
                .orElseThrow(() -> new RuntimeException("Trato no encontrado"));

        Actividad actividad = actividadRepository.findById(actividadId)
                .orElseThrow(() -> new RuntimeException("Actividad no encontrada"));

        if (trato.getContacto() == null) {
            throw new RuntimeException("El trato no tiene contacto asociado");
        }

        Contacto contacto = trato.getContacto();
        if (contacto.getCelular() == null || contacto.getCelular().trim().isEmpty()) {
            throw new RuntimeException("El contacto no tiene número de celular");
        }

        Usuario usuarioAsignado = null;
        if (actividad.getAsignadoAId() != null) {
            usuarioAsignado = usuarioRepository.findById(actividad.getAsignadoAId()).orElse(null);
        }

        String mensaje = generarMensajeWhatsAppReunion(actividad, trato, usuarioAsignado, esReprogramacion);
        String celularLimpio = contacto.getCelular().replaceAll("[^0-9]", ""); // Solo números
        String celular = "52" + celularLimpio;


        Map<String, String> resultado = new HashMap<>();
        resultado.put("mensaje", mensaje);
        resultado.put("celular", celular);
        resultado.put("urlWhatsApp", "https://wa.me/" + celular + "?text=" + java.net.URLEncoder.encode(mensaje, java.nio.charset.StandardCharsets.UTF_8));

        return resultado;
    }

    private String generarMensajeWhatsAppReunion(Actividad actividad, Trato trato, Usuario usuarioAsignado, boolean esReprogramacion) {
        StringBuilder mensaje = new StringBuilder();

        if (esReprogramacion) {
            mensaje.append("*Cambio de horario - Reunión con Tracking Smart Solutions*\n\n");
            mensaje.append("¡Hola! Espero que esté teniendo un excelente día. Le escribo para informarle sobre el cambio de horario en nuestra reunión");
        } else {
            mensaje.append("*Confirmación de cita con Tracking Smart Solutions*\n\n");
            mensaje.append("¡Hola! Espero que esté teniendo un excelente día. Le escribo para confirmar nuestra reunión programada");
        }

        if (actividad.getFechaLimite() != null && actividad.getHoraInicio() != null) {
            String fechaFormateada = formatearFechaAmigable(actividad.getFechaLimite());
            if (esReprogramacion) {
                mensaje.append(".\n\n*Nueva fecha y hora:* ").append(fechaFormateada).append(" a las ").append(actividad.getHoraInicio());
            } else {
                mensaje.append(" para el ").append(fechaFormateada).append(" a las ").append(actividad.getHoraInicio());
            }
        }

        if (!esReprogramacion) {
            mensaje.append(" con nuestro experto en soluciones para flotas de Tracking Smart Solutions para platicar sobre los desafíos logísticos que presenta hoy en día.");
        }

        if (actividad.getDuracion() != null) {
            String duracionTexto = actividad.getDuracion().contains(":") ?
                    actividad.getDuracion() + " horas" : actividad.getDuracion() + " minutos";
            mensaje.append("\n\n*Duración estimada:* ").append(duracionTexto);
        }

        if (actividad.getModalidad() == ModalidadActividadEnum.VIRTUAL) {
            mensaje.append("\n\n*Reunión Virtual*");
            if (actividad.getEnlaceReunion() != null && !actividad.getEnlaceReunion().isEmpty()) {
                mensaje.append("\n*Enlace:* ").append(actividad.getEnlaceReunion());
            }
        } else if (actividad.getModalidad() == ModalidadActividadEnum.PRESENCIAL) {
            mensaje.append("\n\n*Reunión Presencial*");
            if (actividad.getLugarReunion() != null && !actividad.getLugarReunion().isEmpty()) {
                mensaje.append("\n*Lugar:* ").append(actividad.getLugarReunion());
            }
        }

        if (esReprogramacion) {
            mensaje.append("\n\nAgradecemos su comprensión y flexibilidad. Si tiene alguna pregunta o necesita hacer algún ajuste adicional, no dude en contactarme.");
        } else {
            mensaje.append("\n\nSi necesita hacer algún cambio en el horario o tiene alguna pregunta previa, no dude en contactarme.");
        }

        mensaje.append("\n\nSaludos cordiales,");
        if (usuarioAsignado != null) {
            mensaje.append("\n").append(usuarioAsignado.getNombre());
        } else {
            mensaje.append("\nEquipo de Tracking Smart Solutions");
        }
        mensaje.append("\nTracking Smart Solutions");

        return mensaje.toString();
    }

    @Transactional(readOnly = true)
    public List<TratoBasicoDTO> filtrarTratosBasico(Integer empresaId, Integer propietarioId,
                                                    Instant startDate, Instant endDate) {
        Instant start = (startDate != null) ? startDate : Instant.now().minusSeconds(60 * 60 * 24 * 365 * 10);
        Instant end = (endDate != null) ? endDate : Instant.now();

        Integer currentUserId = getCurrentUserId();
        RolUsuarioEnum currentUserRole = getCurrentUserRole();

        List<Object[]> results;

        if (RolUsuarioEnum.EMPLEADO.equals(currentUserRole)) {
            if (empresaId != null) {
                results = tratoRepository.findTratosBasicoForEmpleadoByEmpresa(currentUserId, empresaId, start, end);
            } else {
                results = tratoRepository.findTratosBasicoForEmpleado(currentUserId, start, end);
            }
        } else {
            results = tratoRepository.findTratosBasico(empresaId, propietarioId, start, end);
        }

        return convertToTratosBasicoDTOs(results);
    }

    private List<TratoBasicoDTO> convertToTratosBasicoDTOs(List<Object[]> results) {
        List<TratoBasicoDTO> tratos = new ArrayList<>();
        Instant currentTime = Instant.now();

        for (Object[] row : results) {
            try {
                TratoBasicoDTO dto = new TratoBasicoDTO();

                dto.setId((Integer) row[0]);
                dto.setNombre((String) row[1]);
                dto.setPropietarioId((Integer) row[2]);
                dto.setFechaCierre(((Timestamp) row[3]).toLocalDateTime());
                dto.setNoTrato((String) row[4]);
                dto.setIngresoEsperado((BigDecimal) row[5]);
                dto.setFase((String) row[6]);

                dto.setFechaUltimaActividad(convertToInstant(row[7]));
                dto.setFechaCreacion(convertToInstant(row[8]));
                dto.setFechaModificacion(convertToInstant(row[9]));

                dto.setPropietarioNombre((String) row[10]);
                dto.setEmpresaNombre((String) row[11]);
                dto.setContactoId((Integer) row[12]);

                // Datos de actividades
                Integer actividadesCount = ((Number) row[13]).intValue();
                Integer actividadesAbiertasCount = ((Number) row[14]).intValue();
                dto.setHasActivities(actividadesCount > 0);
                dto.setActividadesAbiertasCount(actividadesAbiertasCount);

                // Próxima actividad
                dto.setProximaActividadTipo((String) row[15]);
                if (row[16] != null) {
                    // Cambiar la conversión de Date a LocalDate
                    dto.setProximaActividadFecha(convertToLocalDate(row[16]));
                }
                // Calcular si está desatendido
                Instant fechaUltimaActividad = dto.getFechaUltimaActividad() != null ?
                        dto.getFechaUltimaActividad() : dto.getFechaCreacion();
                long minutesInactive = ChronoUnit.MINUTES.between(fechaUltimaActividad, currentTime);
                dto.setIsNeglected(!dto.getHasActivities() && minutesInactive > 10080);

                tratos.add(dto);
            } catch (Exception e) {
                System.err.println("Error en convertToTratosBasicoDTOs: " + e.getMessage());
                e.printStackTrace();
                System.err.println("Row data: " + Arrays.toString(row));
            }
        }

        return tratos;
    }

    // Método helper para conversión segura de fechas
    private Instant convertToInstant(Object dateObject) {
        if (dateObject == null) {
            return null;
        }

        try {
            if (dateObject instanceof Instant) {
                return (Instant) dateObject;
            } else if (dateObject instanceof Timestamp) {
                return ((Timestamp) dateObject).toInstant();
            } else if (dateObject instanceof java.sql.Date) {
                return new Timestamp(((java.sql.Date) dateObject).getTime()).toInstant();
            } else if (dateObject instanceof Date) {
                return ((Date) dateObject).toInstant();
            } else {
                System.err.println("Tipo de fecha no reconocido: " + dateObject.getClass().getName());
                return null;
            }
        } catch (Exception e) {
            System.err.println("Error al convertir fecha: " + e.getMessage() + ", objeto: " + dateObject);
            return null;
        }
    }

    // Método para cargar trato completo cuando se necesite
    @Transactional(readOnly = true)
    public TratoDTO getTratoConDetalles(Integer id) {
        Trato trato = tratoRepository.findTratoWithContactoAndTelefonos(id)
                .orElseThrow(() -> new RuntimeException("Trato no encontrado con id: " + id));
        return convertToDTO(trato);
    }

    @Transactional(readOnly = true)
    public Page<TratoDTO> filtrarTratosPaginados(Integer empresaId, Integer propietarioId,
                                                 Instant startDate, Instant endDate, Pageable pageable) {

        List<TratoDTO> allTratos = filtrarTratos(empresaId, propietarioId, startDate, endDate);

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allTratos.size());

        if (start >= allTratos.size()) {
            return new PageImpl<>(new ArrayList<>(), pageable, allTratos.size());
        }

        List<TratoDTO> pageContent = allTratos.subList(start, end);
        return new PageImpl<>(pageContent, pageable, allTratos.size());
    }

    // Método auxiliar para conversión segura de fechas a LocalDate
    private LocalDate convertToLocalDate(Object dateObject) {
        if (dateObject == null) {
            return null;
        }

        try {
            if (dateObject instanceof LocalDate) {
                return (LocalDate) dateObject;
            } else if (dateObject instanceof java.sql.Date) {
                return ((java.sql.Date) dateObject).toLocalDate();
            } else if (dateObject instanceof java.util.Date) {
                return ((java.util.Date) dateObject).toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            } else if (dateObject instanceof Timestamp) {
                return ((Timestamp) dateObject).toLocalDateTime().toLocalDate();
            } else {
                System.err.println("Tipo de fecha no reconocido para LocalDate: " + dateObject.getClass().getName());
                return null;
            }
        } catch (Exception e) {
            System.err.println("Error al convertir fecha a LocalDate: " + e.getMessage() + ", objeto: " + dateObject);
            return null;
        }
    }

    public boolean existeConflictoHorario(Integer asignadoAId, LocalDate fecha, Time horaInicio, String duracion, Integer actividadIdExcluir) {
        try {
            List<Actividad> actividadesDelDia = actividadRepository.findByAsignadoAIdAndFechaLimiteAndEstatus(
                    asignadoAId, fecha, EstatusActividadEnum.ABIERTA);

            if (actividadesDelDia.isEmpty()) return false;

            LocalTime horaInicioNueva = horaInicio.toLocalTime();
            LocalTime horaFinNueva = calcularHoraFin(horaInicioNueva, duracion);

            for (Actividad actividad : actividadesDelDia) {
                // Excluir la misma actividad en caso de reprogramación
                if (actividadIdExcluir != null && actividad.getId().equals(actividadIdExcluir)) {
                    continue;
                }

                if (actividad.getHoraInicio() != null) {
                    LocalTime horaInicioExistente = actividad.getHoraInicio().toLocalTime();
                    LocalTime horaFinExistente = calcularHoraFin(horaInicioExistente, actividad.getDuracion());

                    // Verificar solapamiento
                    if (hayConflicto(horaInicioNueva, horaFinNueva, horaInicioExistente, horaFinExistente)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error verificando conflicto de horario: " + e.getMessage());
            return false;
        }
    }

    private LocalTime calcularHoraFin(LocalTime horaInicio, String duracion) {
        if (duracion == null || duracion.isEmpty()) {
            return horaInicio.plusMinutes(30); // Duración por defecto
        }

        try {
            String[] partes = duracion.split(":");
            int horas = Integer.parseInt(partes[0]);
            int minutos = partes.length > 1 ? Integer.parseInt(partes[1]) : 0;
            return horaInicio.plusHours(horas).plusMinutes(minutos);
        } catch (Exception e) {
            return horaInicio.plusMinutes(30);
        }
    }


    private boolean hayConflicto(LocalTime inicio1, LocalTime fin1, LocalTime inicio2, LocalTime fin2) {
        long diferenciaMinutos = Math.abs(Duration.between(inicio1, inicio2).toMinutes());

        if (diferenciaMinutos >= MARGEN_CONFLICTO_MINUTOS) {
            return false;
        }
        return inicio1.isBefore(fin2) && inicio2.isBefore(fin1);
    }

    @Transactional
    public Map<String, Object> crearInteraccionGenerica(Integer tratoId, InteraccionGenericaDTO interaccionDTO) {
        Actividad actividadTemporal = new Actividad();
        actividadTemporal.setTratoId(tratoId);
        actividadTemporal.setTipo(interaccionDTO.getTipo());
        actividadTemporal.setAsignadoAId(getCurrentUserId());
        actividadTemporal.setEstatus(EstatusActividadEnum.CERRADA);
        actividadTemporal.setFechaCompletado(Instant.now());
        actividadTemporal.setUsuarioCompletadoId(getCurrentUserId());
        actividadTemporal.setRespuesta(interaccionDTO.getRespuesta());
        actividadTemporal.setInteres(interaccionDTO.getInteres());
        actividadTemporal.setInformacion(interaccionDTO.getInformacion());
        actividadTemporal.setSiguienteAccion(interaccionDTO.getSiguienteAccion());
        actividadTemporal.setMedio(interaccionDTO.getMedio());
        actividadTemporal.setNotas(interaccionDTO.getNotas());
        actividadTemporal.setFechaCreacion(Instant.now());
        actividadTemporal.setFechaModificacion(Instant.now());

        Actividad savedActividad = actividadRepository.save(actividadTemporal);

        if (interaccionDTO.getNotas() != null && !interaccionDTO.getNotas().trim().isEmpty()) {
            NotaTrato notaTrato = new NotaTrato();
            notaTrato.setTratoId(tratoId);
            notaTrato.setUsuarioId(getCurrentUserId());
            notaTrato.setNota(interaccionDTO.getNotas().trim());
            notaTrato.setFechaCreacion(Instant.now());
            notaTratoRepository.save(notaTrato);
        }

        Trato trato = tratoRepository.findById(tratoId).orElseThrow(() -> new RuntimeException("Trato no encontrado"));
        trato.setFechaUltimaActividad(Instant.now());
        tratoRepository.save(trato);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Interacción registrada exitosamente");
        response.put("actividadId", savedActividad.getId());

        return response;
    }

    @Transactional
    public ActividadDTO editarInteraccion(Integer id, ActividadDTO actividadDTO) {
        Actividad actividad = actividadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Interacción no encontrada"));

        if (!EstatusActividadEnum.CERRADA.equals(actividad.getEstatus())) {
            throw new RuntimeException("Solo se pueden editar interacciones completadas");
        }

        actividad.setRespuesta(actividadDTO.getRespuesta());
        actividad.setInteres(actividadDTO.getInteres());
        actividad.setInformacion(actividadDTO.getInformacion());
        actividad.setSiguienteAccion(actividadDTO.getSiguienteAccion());
        actividad.setNotas(actividadDTO.getNotas());
        actividad.setMedio(actividadDTO.getMedio());
        actividad.setFechaModificacion(Instant.now());

        Actividad updatedActividad = actividadRepository.save(actividad);

        // Actualizar o crear nota si hay cambios en las notas
        if (actividadDTO.getNotas() != null) {
            // Buscar si ya existe una nota para esta actividad
            List<NotaTrato> notasExistentes = notaTratoRepository.findByTratoIdOrderByFechaCreacionDesc(
                    actividad.getTratoId().longValue());

            // Buscar la nota más reciente del mismo usuario con el mismo contenido anterior
            Optional<NotaTrato> notaExistente = notasExistentes.stream()
                    .filter(nota -> nota.getUsuarioId().equals(getCurrentUserId()))
                    .filter(nota -> nota.getFechaCreacion().isAfter(
                            actividad.getFechaCreacion().minusSeconds(300))) // 5 minutos de tolerancia
                    .findFirst();

            if (notaExistente.isPresent() && !actividadDTO.getNotas().trim().isEmpty()) {
                // Actualizar nota existente
                NotaTrato nota = notaExistente.get();
                nota.setNota(actividadDTO.getNotas().trim());
                nota.setEditadoPor(getCurrentUserId());
                nota.setFechaEdicion(Instant.now());
                notaTratoRepository.save(nota);
            } else if (!actividadDTO.getNotas().trim().isEmpty()) {
                // Crear nueva nota
                NotaTrato notaTrato = new NotaTrato();
                notaTrato.setTratoId(actividad.getTratoId());
                notaTrato.setUsuarioId(getCurrentUserId());
                notaTrato.setNota(actividadDTO.getNotas().trim());
                notaTrato.setFechaCreacion(Instant.now());
                notaTratoRepository.save(notaTrato);
            }
        }

        return convertToDTO(updatedActividad);
    }
}