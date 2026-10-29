package ar.edu.utn.frsf.promptguard.core;

import java.util.List;
import java.util.Map;

/**
 * Política de una herramienta.
 *
 * @param nombre            nombre con el que el modelo invoca la herramienta
 * @param sensibilidad      daño potencial
 * @param autoridad         quién puede ejecutarla
 * @param intencion         términos que el cliente debe haber usado para pedirla; vacío = no se exige
 * @param parametros        reglas de fundamento por nombre de parámetro
 * @param resultadoConfiable si su salida puede usarse como fuente de fundamento para otras herramientas
 * @param roles             roles que pueden pedirla; vacío = no se exige ningún rol
 * @param recursos          tipo de recurso por nombre de parámetro: el usuario debe tener derecho sobre ese valor
 */
public record PoliticaHerramienta(
        String nombre,
        Sensibilidad sensibilidad,
        Autoridad autoridad,
        List<String> intencion,
        Map<String, ReglaParametro> parametros,
        boolean resultadoConfiable,
        List<String> roles,
        Map<String, String> recursos) {

    public PoliticaHerramienta {
        intencion = List.copyOf(intencion);
        parametros = Map.copyOf(parametros);
        roles = roles == null ? List.of() : List.copyOf(roles);
        recursos = recursos == null ? Map.of() : Map.copyOf(recursos);
    }

    /** Política sin autorización por usuario (C5 no se exige). */
    public PoliticaHerramienta(String nombre, Sensibilidad sensibilidad, Autoridad autoridad, List<String> intencion,
                               Map<String, ReglaParametro> parametros, boolean resultadoConfiable) {
        this(nombre, sensibilidad, autoridad, intencion, parametros, resultadoConfiable, List.of(), Map.of());
    }

    /** Verdadero si la política pide verificar quién es el usuario (rol o derecho sobre un recurso). */
    public boolean exigeAutorizacion() {
        return !roles.isEmpty() || !recursos.isEmpty();
    }
}
