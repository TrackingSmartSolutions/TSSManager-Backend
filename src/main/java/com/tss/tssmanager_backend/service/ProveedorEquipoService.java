package com.tss.tssmanager_backend.service;

import com.tss.tssmanager_backend.entity.ProveedorEquipo;
import com.tss.tssmanager_backend.repository.ProveedorEquipoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProveedorEquipoService {

    @Autowired
    private ProveedorEquipoRepository repository;

    public Iterable<ProveedorEquipo> obtenerTodosLosProveedoresEquipo() {
        return repository.findAll();
    }

    public ProveedorEquipo obtenerProveedorEquipo(Integer id) {
        Optional<ProveedorEquipo> proveedor = repository.findById(id);
        if (proveedor.isPresent()) {
            return proveedor.get();
        }
        throw new EntityNotFoundException("ProveedorEquipo no encontrado con ID: " + id);
    }

    public ProveedorEquipo guardarProveedorEquipo(ProveedorEquipo proveedor) {
        return repository.save(proveedor);
    }

    public ProveedorEquipo actualizarProveedorEquipo(Integer id, ProveedorEquipo proveedorDetails) {
        ProveedorEquipo proveedor = obtenerProveedorEquipo(id);
        proveedor.setNombre(proveedorDetails.getNombre());
        proveedor.setContactoNombre(proveedorDetails.getContactoNombre());
        proveedor.setTelefono(proveedorDetails.getTelefono());
        proveedor.setCorreo(proveedorDetails.getCorreo());
        proveedor.setSitioWeb(proveedorDetails.getSitioWeb());
        return repository.save(proveedor);
    }

    public void eliminarProveedorEquipo(Integer id) {
        ProveedorEquipo proveedor = obtenerProveedorEquipo(id);
        repository.delete(proveedor);
    }
}