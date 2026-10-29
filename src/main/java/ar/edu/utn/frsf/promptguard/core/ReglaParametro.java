package ar.edu.utn.frsf.promptguard.core;

import java.util.List;
import java.util.Set;

/**
 * Exige que el valor de un parámetro esté fundado en alguna de sus fuentes permitidas.
 *
 * @param fuentes     fuentes aceptadas; basta con que el valor aparezca en una de ellas
 * @param listaBlanca valores fijos aceptados cuando la fuente incluye {@link FuenteArgumento#LISTA_BLANCA}
 */
public record ReglaParametro(List<FuenteArgumento> fuentes, Set<String> listaBlanca) {

    public ReglaParametro {
        fuentes = List.copyOf(fuentes);
        listaBlanca = Set.copyOf(listaBlanca);
    }
}
