package com.tss.tssmanager_backend.repository;

import com.tss.tssmanager_backend.dto.BalanceResumenDTO;
import com.tss.tssmanager_backend.entity.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface TransaccionRepository extends JpaRepository<Transaccion, Integer> {
    // Obtener años disponibles
    @Query("SELECT DISTINCT YEAR(t.fechaPago) FROM Transaccion t WHERE t.fechaPago IS NOT NULL ORDER BY YEAR(t.fechaPago)")
    List<Integer> findDistinctYears();

    // Suma Ingresos Totales (filtrando Reposición)
    @Query("SELECT COALESCE(SUM(t.monto), 0) FROM Transaccion t " +
            "WHERE t.tipo = 'INGRESO' " +
            "AND LOWER(t.categoria.descripcion) <> 'reposición' " +
            "AND (:anio IS NULL OR YEAR(t.fechaPago) = :anio) " +
            "AND (:mes IS NULL OR MONTH(t.fechaPago) = :mes)")
    BigDecimal sumIngresosByFecha(@Param("anio") Integer anio, @Param("mes") Integer mes);

    // Suma Gastos Totales (con filtro específico de nota)
    @Query("SELECT COALESCE(SUM(t.monto), 0) FROM Transaccion t " +
            "WHERE t.tipo = 'GASTO' " +
            "AND t.notas LIKE '%Transacción generada desde Cuentas por Pagar%' " +
            "AND (:anio IS NULL OR YEAR(t.fechaPago) = :anio) " +
            "AND (:mes IS NULL OR MONTH(t.fechaPago) = :mes)")
    BigDecimal sumGastosByFecha(@Param("anio") Integer anio, @Param("mes") Integer mes);

    // Datos para el acumulado por Categoria y Cuenta
    @Query("SELECT new com.tss.tssmanager_backend.dto.BalanceResumenDTO$AcumuladoCuentaDTO(c.descripcion, cu.nombre, SUM(t.monto)) " +
            "FROM Transaccion t " +
            "JOIN t.categoria c " +
            "JOIN t.cuenta cu " +
            "WHERE LOWER(c.descripcion) <> 'reposición' " +
            "AND (t.tipo = 'INGRESO' OR (t.tipo = 'GASTO' AND t.notas LIKE '%Transacción generada desde Cuentas por Pagar%')) " +
            "AND (:anio IS NULL OR YEAR(t.fechaPago) = :anio) " +
            "AND (:mes IS NULL OR MONTH(t.fechaPago) = :mes) " +
            "GROUP BY c.descripcion, cu.nombre")
    List<BalanceResumenDTO.AcumuladoCuentaDTO> findAcumuladoPorCuenta(@Param("anio") Integer anio, @Param("mes") Integer mes);

    // Obtener transacciones básicas para gráfico
    @Query("SELECT t.fechaPago, t.monto, t.tipo, t.notas FROM Transaccion t " +
            "WHERE LOWER(t.categoria.descripcion) <> 'reposición' " +
            "AND (:anio IS NULL OR YEAR(t.fechaPago) = :anio) " +
            "AND (:mes IS NULL OR MONTH(t.fechaPago) = :mes)")
    List<Object[]> findDatosGrafico(@Param("anio") Integer anio, @Param("mes") Integer mes);

}
