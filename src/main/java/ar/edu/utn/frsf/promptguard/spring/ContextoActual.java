package ar.edu.utn.frsf.promptguard.spring;

import ar.edu.utn.frsf.promptguard.core.ContextoSolicitud;

/**
 * Contexto de la solicitud en curso, visible para las herramientas que se ejecutan dentro de la misma llamada.
 *
 * <p>El ToolContext no alcanza: ChatClient ejecuta las herramientas en su propio ToolCallingAdvisor, que no siempre
 * recibe las opciones que armó PromptGuardAdvisor. El ámbito abarca toda la llamada del advisor, incluida la ejecución
 * de herramientas, que ocurre en el mismo hilo.
 */
final class ContextoActual {

    private static final ThreadLocal<ContextoSolicitud> ACTUAL = new ThreadLocal<>();

    private ContextoActual() {
    }

    /** Abre el ámbito; cerrarlo restaura el contexto anterior (las solicitudes pueden anidarse). */
    static Ambito abrir(ContextoSolicitud contexto) {
        ContextoSolicitud anterior = ACTUAL.get();
        ACTUAL.set(contexto);
        return () -> {
            if (anterior == null) {
                ACTUAL.remove();
            }
            else {
                ACTUAL.set(anterior);
            }
        };
    }

    static ContextoSolicitud actual() {
        return ACTUAL.get();
    }

    interface Ambito extends AutoCloseable {
        @Override
        void close();
    }
}
