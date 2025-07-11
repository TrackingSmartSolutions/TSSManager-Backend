package com.tss.tssmanager_backend.entity;

import com.tss.tssmanager_backend.enums.TipoTransaccionEnum;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "\"Categorias_Transacciones\"")
public class CategoriaTransacciones {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoTransaccionEnum tipo;

    @Column(name = "descripcion", nullable = false, length = 100)
    private String descripcion;


}
