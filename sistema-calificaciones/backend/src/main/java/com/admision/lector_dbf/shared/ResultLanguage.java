package com.admision.lector_dbf.shared;

/** Denominaciones compartidas por documentos y consulta individual. */
public record ResultLanguage(String heading,String positive,String negative,String positivePlural,String negativePlural) {
    public static ResultLanguage forType(String type) {
        if ("ADMISION".equals(type)) return new ResultLanguage("Ingreso","Ingresó","No ingresó","Ingresantes","No ingresantes");
        if ("SELECCION".equals(type)) return new ResultLanguage("Selección","Seleccionado","No seleccionado","Seleccionados","No seleccionados");
        return new ResultLanguage("Aprobación","Aprobó","No aprobó","Aprobados","No aprobados");
    }
    public static String state(String state) {
        return switch (state) {
            case "BORRADOR" -> "Borrador"; case "CONFIGURADA" -> "Configurada"; case "EN_PROCESO" -> "En proceso";
            case "CERRADA" -> "Cerrada"; case "PUBLICADA" -> "Publicada"; case "ARCHIVADA" -> "Archivada";
            default -> state;
        };
    }
}
