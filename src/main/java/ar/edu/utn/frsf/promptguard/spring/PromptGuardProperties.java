package ar.edu.utn.frsf.promptguard.spring;

import ar.edu.utn.frsf.promptguard.core.Autoridad;
import ar.edu.utn.frsf.promptguard.core.FuenteArgumento;
import ar.edu.utn.frsf.promptguard.core.ModoC4;
import ar.edu.utn.frsf.promptguard.core.Procedencia;
import ar.edu.utn.frsf.promptguard.core.Sensibilidad;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuración de PromptGuard (prefijo {@code promptguard}). Las herramientas que no figuran aquí
 * se bloquean: la política deniega por defecto.
 *
 * @param modo                  {@link Modo#ENFORCE} aplica las decisiones; {@link Modo#SHADOW} solo las registra
 * @param confirmacionTtl       cuánto vive una acción pendiente de confirmación
 * @param c4                    cómo se aplica C4: {@link ModoC4#ESTRICTA} (por defecto) o {@link ModoC4#REFINADA}
 * @param claveProcedencia      metadato del documento que indica su procedencia
 * @param procedencia           traducción de valores del metadato a {@link Procedencia}
 * @param confianzaMinimaPara   procedencia mínima de los fragmentos para ejecutar sin confirmar, por sensibilidad
 * @param camposSensibles       nombres de parámetros que se enmascaran en la auditoría
 * @param herramientas          política por herramienta
 */
@ConfigurationProperties("promptguard")
public record PromptGuardProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("enforce") Modo modo,
        @DefaultValue("5m") Duration confirmacionTtl,
        @DefaultValue("estricta") ModoC4 c4,
        @DefaultValue("origen") String claveProcedencia,
        Map<String, Procedencia> procedencia,
        Map<Sensibilidad, Integer> confianzaMinimaPara,
        Set<String> camposSensibles,
        Map<String, Herramienta> herramientas) {

    public PromptGuardProperties {
        modo = modo == null ? Modo.ENFORCE : modo;
        confirmacionTtl = confirmacionTtl == null ? Duration.ofMinutes(5) : confirmacionTtl;
        c4 = c4 == null ? ModoC4.ESTRICTA : c4;
        claveProcedencia = claveProcedencia == null ? "origen" : claveProcedencia;
        procedencia = procedencia == null || procedencia.isEmpty()
                ? Map.of("CONFIABLE", Procedencia.CONFIABLE, "NO_CONFIABLE", Procedencia.EXTERNO)
                : Map.copyOf(procedencia);
        confianzaMinimaPara = confianzaMinimaPara == null || confianzaMinimaPara.isEmpty()
                ? Map.of(Sensibilidad.ALTA, 3, Sensibilidad.MEDIA, 2)
                : Map.copyOf(confianzaMinimaPara);
        camposSensibles = camposSensibles == null || camposSensibles.isEmpty()
                ? Set.of("email", "correo", "destino", "cuerpo", "telefono", "asunto")
                : Set.copyOf(camposSensibles);
        herramientas = herramientas == null ? Map.of() : Map.copyOf(herramientas);
    }

    /**
     * Política de una herramienta. Por defecto es la más restrictiva: alta sensibilidad y requiere persona.
     * {@code roles} y {@code recursos} activan C5: roles que pueden usarla y tipo de recurso de cada parámetro
     * (el usuario debe tener derecho sobre ese valor, según la {@code AutorizacionRecursos} de la aplicación).
     */
    public record Herramienta(
            @DefaultValue("alta") Sensibilidad sensibilidad,
            @DefaultValue("requiere_humano") Autoridad autoridad,
            List<String> intencion,
            Map<String, Parametro> parametros,
            boolean resultadoConfiable,
            List<String> roles,
            Map<String, String> recursos) {

        public Herramienta {
            sensibilidad = sensibilidad == null ? Sensibilidad.ALTA : sensibilidad;
            autoridad = autoridad == null ? Autoridad.REQUIERE_HUMANO : autoridad;
            intencion = intencion == null ? List.of() : List.copyOf(intencion);
            parametros = parametros == null ? Map.of() : Map.copyOf(parametros);
            roles = roles == null ? List.of() : List.copyOf(roles);
            recursos = recursos == null ? Map.of() : Map.copyOf(recursos);
        }
    }

    /** Fuentes aceptadas para el valor de un parámetro sensible. */
    public record Parametro(List<FuenteArgumento> fuentes, Set<String> listaBlanca) {

        public Parametro {
            fuentes = fuentes == null ? List.of() : List.copyOf(fuentes);
            listaBlanca = listaBlanca == null ? Set.of() : Set.copyOf(listaBlanca);
        }
    }

    public enum Modo {
        ENFORCE,
        SHADOW
    }
}
