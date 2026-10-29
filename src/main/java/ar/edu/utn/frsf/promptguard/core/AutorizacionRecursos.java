package ar.edu.utn.frsf.promptguard.core;

/**
 * Contrato que implementa la aplicación para decir qué recursos le corresponden a cada usuario. PromptGuard no
 * conoce cuentas, tarjetas ni pedidos: solo pregunta si el usuario tiene derecho sobre el recurso que la herramienta
 * quiere tocar.
 */
@FunctionalInterface
public interface AutorizacionRecursos {

    /**
     * @param usuario     usuario autenticado
     * @param tipoRecurso tipo de recurso declarado en la política (por ejemplo "cuenta" o "tarjeta")
     * @param valor       identificador que trae el argumento de la herramienta
     * @return verdadero solo si el usuario puede operar ese recurso
     */
    boolean permite(String usuario, String tipoRecurso, String valor);
}
