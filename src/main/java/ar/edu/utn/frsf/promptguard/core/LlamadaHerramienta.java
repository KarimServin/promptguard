package ar.edu.utn.frsf.promptguard.core;

import java.util.Map;

/** Invocación de herramienta que el modelo propone; todavía no se ejecutó. */
public record LlamadaHerramienta(String nombre, Map<String, Object> argumentos) {

    public LlamadaHerramienta {
        argumentos = argumentos == null ? Map.of() : Map.copyOf(argumentos);
    }
}
