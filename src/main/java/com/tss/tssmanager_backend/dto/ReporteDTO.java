package com.tss.tssmanager_backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReporteDTO {
    private List<ActividadCount> actividades;
    private List<EmpresaCount> empresas;
    private List<Nota> notas;
    private String startDate;
    private String endDate;

    @Data
    public static class ActividadCount {
        private String name;
        private int value;
        private String color;

        public ActividadCount(String name, int value, String color) {
            this.name = name;
            this.value = value;
            this.color = color;
        }

    }
    @Data
    public static class EmpresaCount {
        private String name;
        private int value;

        public EmpresaCount(String name, int value) {
            this.name = name;
            this.value = value;
        }

    }

    @Data
    public static class Nota {
        private String empresa;
        private String notas;
        private String respuesta;
        private String interes;

        public Nota(String empresa, String notas, String respuesta, String interes) {
            this.empresa = empresa;
            this.notas = notas;
            this.respuesta = respuesta;
            this.interes = interes;
        }
    }

}