package ar.edu.utn.frsf.promptguard.core;

/**
 * Quién puede ejecutar la herramienta. Viene de la configuración, nunca de un documento recuperado.
 */
public enum Autoridad {
    /** El asistente la ejecuta por sí solo, si pasa los demás chequeos. */
    ASISTENTE,
    /** Nunca la ejecuta el modelo: se deriva a una persona. */
    REQUIERE_HUMANO,
    /** No se ejecuta bajo ninguna circunstancia. */
    PROHIBIDA
}
