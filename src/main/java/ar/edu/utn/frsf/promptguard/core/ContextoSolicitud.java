package ar.edu.utn.frsf.promptguard.core;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lo que el guard sabe de una solicitud: qué pidió el cliente, qué fragmentos recuperó el RAG y qué
 * devolvieron las herramientas confiables. Es la única fuente de fundamento para las decisiones.
 */
public final class ContextoSolicitud {

    private final String id;
    private final List<String> mensajesCliente;
    private volatile List<Fragmento> fragmentos;
    private final List<String> resultadosConfiables = new CopyOnWriteArrayList<>();
    private volatile Identidad identidad;

    public ContextoSolicitud(String id, List<String> mensajesCliente, List<Fragmento> fragmentos) {
        this.id = id;
        this.mensajesCliente = List.copyOf(mensajesCliente);
        this.fragmentos = List.copyOf(fragmentos);
    }

    /** Contexto sin mensajes ni fragmentos: ante la falta de contexto, nada queda fundado (fallar cerrado). */
    public static ContextoSolicitud vacio(String id) {
        return new ContextoSolicitud(id, List.of(), List.of());
    }

    public String id() {
        return id;
    }

    /** Fija el usuario autenticado que hace la solicitud. Sin identidad, C5 falla cerrado en las herramientas que la exigen. */
    public void identificar(Identidad identidad) {
        this.identidad = identidad;
    }

    public Optional<Identidad> identidad() {
        return Optional.ofNullable(identidad);
    }

    public List<String> mensajesCliente() {
        return mensajesCliente;
    }

    /**
     * Reemplaza los fragmentos recuperados. El advisor de RAG se ejecuta dentro de la misma solicitud, y el ToolCalling
     * vuelve a llamar al modelo tras cada herramienta: por eso los fragmentos se reemplazan en lugar de acumularse.
     */
    public void reemplazarFragmentos(List<Fragmento> nuevos) {
        this.fragmentos = List.copyOf(nuevos);
    }

    public List<Fragmento> fragmentos() {
        return fragmentos;
    }

    /** Verdadero si algún mensaje del cliente contiene alguno de los términos de intención. */
    public boolean clientePidio(List<String> terminos) {
        return terminos.stream().anyMatch(termino -> mensajesCliente.stream()
                .anyMatch(mensaje -> Normalizador.contiene(mensaje, termino)));
    }

    /** Verdadero si el valor aparece en un mensaje del cliente. */
    public boolean clienteDio(String valor) {
        return mensajesCliente.stream().anyMatch(mensaje -> Normalizador.contieneValor(mensaje, valor));
    }

    /** Verdadero si el valor aparece en la salida de alguna herramienta confiable ya ejecutada en esta solicitud. */
    public boolean fundadoEnResultadoConfiable(String valor) {
        return resultadosConfiables.stream().anyMatch(resultado -> Normalizador.contieneValor(resultado, valor));
    }

    /** Nivel de confianza del fragmento menos confiable. Sin fragmentos, no hay nada no confiable: el nivel máximo. */
    public int confianzaMinima() {
        return fragmentos.stream()
                .mapToInt(fragmento -> fragmento.procedencia().nivel())
                .min()
                .orElse(Procedencia.CONFIABLE.nivel());
    }

    public void registrarResultadoConfiable(String resultado) {
        if (resultado != null && !resultado.isBlank()) {
            resultadosConfiables.add(resultado);
        }
    }
}
