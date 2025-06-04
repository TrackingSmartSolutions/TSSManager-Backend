package com.tss.tssmanager_backend.enums;

public enum SectorEmpresaEnum {
    AGRICULTURA("11"),
    MINERIA("21"),
    ENERGIA("22"),
    CONSTRUCCION("23"),
    MANUFACTURA("31-33"),
    COMERCIO_MAYOR("43"),
    COMERCIO_MENOR("46"),
    TRANSPORTE("48-49"),
    MEDIOS("51"),
    FINANCIERO("52"),
    INMOBILIARIO("53"),
    PROFESIONAL("54"),
    CORPORATIVO("55"),
    APOYO_NEGOCIOS("56"),
    EDUCACION("61"),
    SALUD("62"),
    ESPARCIMIENTO("71"),
    ALOJAMIENTO("72"),
    OTROS_SERVICIOS("81"),
    GUBERNAMENTAL("93");

    private final String codigo;

    SectorEmpresaEnum(String codigo) {
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}