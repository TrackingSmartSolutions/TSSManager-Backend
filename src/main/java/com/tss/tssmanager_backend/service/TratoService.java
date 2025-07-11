package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.dto.*;
import com.tss.tssmanager_backend.entity.*;
import com.tss.tssmanager_backend.enums.EstatusActividadEnum;
import com.tss.tssmanager_backend.enums.EstatusEmpresaEnum;
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
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public List<TratoDTO> filtrarTratos(Integer propietarioId, Instant startDate, Instant endDate) {
        List<Trato> tratos;
        Instant start = (startDate != null) ? startDate : Instant.now().minusSeconds(60 * 60 * 24 * 365 * 10); // 10 years ago
        Instant end = (endDate != null) ? endDate : Instant.now();
        if (propietarioId != null) {
            tratos = tratoRepository.findByPropietarioIdAndFechaCreacionBetween(propietarioId, start, end);
        } else {
            tratos = tratoRepository.findByFechaCreacionBetween(start, end);
        }
        return tratos.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

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
        trato.setFase(nuevaFase);
        trato.setProbabilidad(getProbabilidadPorFase(nuevaFase));
        trato.setFechaModificacion(Instant.now());
        trato.setFechaUltimaActividad(Instant.now());
        Trato updatedTrato = tratoRepository.save(trato);
        return convertToDTO(updatedTrato);
    }

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
            // Opcional: validar que el contacto existe si es necesario
            Contacto contacto = entityManager.find(Contacto.class, actividadDTO.getContactoId());
            if (contacto == null) {
                throw new RuntimeException("El contacto con ID " + actividadDTO.getContactoId() + " no existe.");
            }
        }

        Actividad savedActividad = actividadRepository.save(actividad);
        return convertToDTO(savedActividad);
    }

    @Transactional
    public ActividadDTO reprogramarActividad(Integer id, ActividadDTO actividadDTO) {
        Actividad actividad = actividadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Actividad no encontrada con id: " + id));

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

        if (empresaId != null && propietarioId != null) {
            tratos = tratoRepository.findByEmpresaIdAndPropietarioIdAndFechaCreacionBetween(empresaId, propietarioId, start, end);
        } else if (empresaId != null) {
            tratos = tratoRepository.findByEmpresaIdAndFechaCreacionBetween(empresaId, start, end);
        } else if (propietarioId != null) {
            tratos = tratoRepository.findByPropietarioIdAndFechaCreacionBetween(propietarioId, start, end);
        } else {
            tratos = tratoRepository.findByFechaCreacionBetween(start, end);
        }
        return tratos.stream().map(this::convertToDTO).collect(Collectors.toList());
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
            case "CLASIFICACION": return 0;
            case "PRIMER_CONTACTO": return 10;
            case "ENVIO_DE_INFORMACION": return 30;
            case "REUNION": return 50;
            case "COTIZACION_PROPUESTA_PRACTICA": return 70;
            case "NEGOCIACION_REVISION": return 85;
            case "CERRADO_GANADO": return 100;
            case "RESPUESTA_POR_CORREO": return 0;
            case "INTERES_FUTURO": return 0;
            case "CERRADO_PERDIDO": return 0;
            default: return 0;
        }
    }
}