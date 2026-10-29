package ar.edu.utn.frsf.promptguard.spring;

import ar.edu.utn.frsf.promptguard.core.Politica;
import ar.edu.utn.frsf.promptguard.core.PoliticaHerramienta;
import ar.edu.utn.frsf.promptguard.core.ReglaParametro;

import java.util.LinkedHashMap;
import java.util.Map;

/** Traduce la configuración de propiedades a la política del núcleo. */
final class PoliticaFactory {

    private PoliticaFactory() {
    }

    static Politica desde(PromptGuardProperties propiedades) {
        Map<String, PoliticaHerramienta> herramientas = new LinkedHashMap<>();
        propiedades.herramientas().forEach((nombre, herramienta) -> {
            Map<String, ReglaParametro> parametros = new LinkedHashMap<>();
            herramienta.parametros().forEach((parametro, regla) ->
                    parametros.put(parametro, new ReglaParametro(regla.fuentes(), regla.listaBlanca())));
            herramientas.put(nombre, new PoliticaHerramienta(nombre, herramienta.sensibilidad(), herramienta.autoridad(),
                    herramienta.intencion(), parametros, herramienta.resultadoConfiable(),
                    herramienta.roles(), herramienta.recursos()));
        });
        return new Politica(herramientas, propiedades.confianzaMinimaPara());
    }
}
