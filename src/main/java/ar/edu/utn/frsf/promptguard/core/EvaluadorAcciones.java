package ar.edu.utn.frsf.promptguard.core;

import java.util.Map;
import java.util.Set;

/**
 * Núcleo determinista de PromptGuard. Decide si una llamada que propuso el modelo puede ejecutarse.
 * No usa un LLM: el modelo propone, el código dispone.
 *
 * <pre>
 * C0 la herramienta está registrada en la política (siempre activo)
 * C1 autoridad: PROHIBIDA bloquea; REQUIERE_HUMANO deriva
 * C5 autorización: el usuario autenticado tiene el rol y el derecho sobre los recursos que la herramienta toca
 * C2 intención: el cliente pidió la acción (si la política lo exige)
 * C3 fundamento: cada parámetro configurado viene del cliente, de un resultado confiable o de la lista blanca
 * C4 procedencia x sensibilidad: con fragmentos de baja confianza, las herramientas sensibles piden confirmación
 * </pre>
 *
 * <p>C1 a C4 controlan <em>de dónde viene</em> la instrucción; C5 controla <em>si quien la pide tiene derecho</em> a
 * hacerla, aunque la haya pedido él mismo. Se evalúa tras C1 y antes de C2.
 *
 * <p>Las reglas C1 a C5 se pueden apagar individualmente. Es para la ablación (medir cuánto aporta cada control); en
 * producción deben estar todas activas.
 */
public final class EvaluadorAcciones {

    /** Todas las reglas activas. */
    public static final Set<String> TODAS = Set.of("C1", "C2", "C3", "C4", "C5");

    private final Politica politica;
    private final Set<String> reglasActivas;
    private final ModoC4 modoC4;
    private final AutorizacionRecursos autorizacion;

    public EvaluadorAcciones(Politica politica) {
        this(politica, TODAS, ModoC4.ESTRICTA);
    }

    public EvaluadorAcciones(Politica politica, Set<String> reglasActivas, ModoC4 modoC4) {
        this(politica, reglasActivas, modoC4, null);
    }

    public EvaluadorAcciones(Politica politica, Set<String> reglasActivas, ModoC4 modoC4,
                             AutorizacionRecursos autorizacion) {
        this.politica = politica;
        this.reglasActivas = Set.copyOf(reglasActivas);
        this.modoC4 = modoC4;
        this.autorizacion = autorizacion;
    }

    public Decision decidir(LlamadaHerramienta llamada, ContextoSolicitud contexto) {
        var herramienta = politica.herramienta(llamada.nombre());
        if (herramienta.isEmpty()) {
            return Decision.bloquear("C0", "herramienta no registrada en la política");
        }
        PoliticaHerramienta pol = herramienta.get();

        if (activa("C1")) {
            if (pol.autoridad() == Autoridad.PROHIBIDA) {
                return Decision.bloquear("C1", "herramienta prohibida al asistente");
            }
            if (pol.autoridad() == Autoridad.REQUIERE_HUMANO) {
                return Decision.derivar("C1", "la operación corresponde a una persona");
            }
        }

        if (activa("C5") && pol.exigeAutorizacion()) {
            Decision noAutorizada = autorizar(pol, llamada, contexto);
            if (noAutorizada != null) {
                return noAutorizada;
            }
        }

        boolean pideIntencion = !pol.intencion().isEmpty();
        if (activa("C2") && pideIntencion && !contexto.clientePidio(pol.intencion())) {
            String motivo = "el cliente no pidió esta acción";
            return pol.sensibilidad() == Sensibilidad.BAJA
                    ? Decision.bloquear("C2", motivo)
                    : Decision.confirmar("C2", motivo);
        }

        if (activa("C3")) {
            for (Map.Entry<String, ReglaParametro> regla : pol.parametros().entrySet()) {
                Object valor = llamada.argumentos().get(regla.getKey());
                if (!fundado(valor, regla.getValue(), contexto)) {
                    return Decision.bloquear("C3", "argumento no fundado en el cliente ni en fuentes confiables: "
                            + regla.getKey());
                }
            }
        }

        if (activa("C4") && !c4Omitida(pol)) {
            int requerido = politica.confianzaMinimaPara(pol.sensibilidad());
            if (contexto.confianzaMinima() < requerido) {
                return Decision.confirmar("C4", "el contexto incluye fragmentos de procedencia no confiable");
            }
        }

        return Decision.permitir();
    }

    /**
     * En modo refinado, C4 no se aplica cuando la política exige tanto la intención del cliente como el fundamento de
     * sus argumentos y ambos chequeos ya pasaron: la acción la pidió el cliente con datos propios.
     */
    private boolean c4Omitida(PoliticaHerramienta pol) {
        return modoC4 == ModoC4.REFINADA
                && activa("C2") && activa("C3")
                && !pol.intencion().isEmpty()
                && !pol.parametros().isEmpty();
    }

    /**
     * C5: el usuario autenticado debe tener el rol que pide la política y derecho sobre cada recurso que la herramienta
     * va a tocar. Devuelve null si está autorizado. Sin identidad, sin verificador o sin el argumento a verificar, no
     * hay forma de comprobarlo y se bloquea.
     */
    private Decision autorizar(PoliticaHerramienta pol, LlamadaHerramienta llamada, ContextoSolicitud contexto) {
        Identidad identidad = contexto.identidad().orElse(null);
        if (identidad == null) {
            return Decision.bloquear("C5", "no hay un usuario autenticado para esta solicitud");
        }
        if (!pol.roles().isEmpty() && pol.roles().stream().noneMatch(identidad.roles()::contains)) {
            return Decision.bloquear("C5", "el rol del usuario no permite esta herramienta");
        }
        for (Map.Entry<String, String> recurso : pol.recursos().entrySet()) {
            Object valor = llamada.argumentos().get(recurso.getKey());
            if (valor == null || autorizacion == null
                    || !autorizacion.permite(identidad.usuario(), recurso.getValue(), valor.toString())) {
                return Decision.bloquear("C5", "el usuario no tiene derecho sobre el recurso: " + recurso.getKey());
            }
        }
        return null;
    }

    private boolean activa(String regla) {
        return reglasActivas.contains(regla);
    }

    private static boolean fundado(Object valor, ReglaParametro regla, ContextoSolicitud contexto) {
        if (valor == null) {
            return false;
        }
        String texto = valor.toString();
        for (FuenteArgumento fuente : regla.fuentes()) {
            boolean fundado = switch (fuente) {
                case USUARIO -> contexto.clienteDio(texto);
                case HERRAMIENTA_CONFIABLE -> contexto.fundadoEnResultadoConfiable(texto);
                case LISTA_BLANCA -> regla.listaBlanca().contains(texto);
            };
            if (fundado) {
                return true;
            }
        }
        return false;
    }
}
