package ar.edu.utn.frsf.promptguard.core;

import java.util.Map;
import java.util.Optional;

/**
 * Política completa. Una herramienta que no figura aquí no se ejecuta (denegar por defecto).
 *
 * @param herramientas          políticas por nombre de herramienta
 * @param confianzaMinimaPara   nivel mínimo de procedencia de los fragmentos para ejecutar sin confirmar,
 *                              según la sensibilidad (un valor ausente equivale a 0)
 */
public record Politica(Map<String, PoliticaHerramienta> herramientas, Map<Sensibilidad, Integer> confianzaMinimaPara) {

    public Politica {
        herramientas = Map.copyOf(herramientas);
        confianzaMinimaPara = Map.copyOf(confianzaMinimaPara);
    }

    public Optional<PoliticaHerramienta> herramienta(String nombre) {
        return Optional.ofNullable(herramientas.get(nombre));
    }

    public int confianzaMinimaPara(Sensibilidad sensibilidad) {
        return confianzaMinimaPara.getOrDefault(sensibilidad, 0);
    }
}
