package com.tss.tssmanager_backend.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class AlmacenamientoRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<Object[]> obtenerEstadisticasAlmacenamiento() {
        String sql = "SELECT * FROM obtener_estadisticas_almacenamiento()";
        Query query = entityManager.createNativeQuery(sql);
        return query.getResultList();
    }

    public Double calcularTamanoTabla(String tablaNombre) {
        String sql = "SELECT calcular_tamano_tabla(:tablaNombre)";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("tablaNombre", tablaNombre);
        Object result = query.getSingleResult();
        return result != null ? ((Number) result).doubleValue() : 0.0;
    }

    public Integer limpiarTratosCerradosPerdidos() {
        String sql = "SELECT limpiar_tratos_cerrados_perdidos()";
        Query query = entityManager.createNativeQuery(sql);
        Object result = query.getSingleResult();
        return result != null ? ((Number) result).intValue() : 0;
    }

    public Integer limpiarRegistrosAntiguos(String tablaNombre, Integer usuarioId, String tipoLimpieza) {
        String sql = "SELECT limpiar_registros_antiguos(:tablaNombre, :usuarioId, :tipoLimpieza)";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("tablaNombre", tablaNombre);
        query.setParameter("usuarioId", usuarioId);
        query.setParameter("tipoLimpieza", tipoLimpieza);
        Object result = query.getSingleResult();
        return result != null ? ((Number) result).intValue() : 0;
    }
}