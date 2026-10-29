package ar.edu.utn.frsf.promptguard.core;

public enum TipoDecision {
    /** La herramienta se ejecuta. */
    PERMITIR,
    /** No se ejecuta; el modelo recibe un mensaje seguro. */
    BLOQUEAR,
    /** No se ejecuta hasta que una persona confirme los parámetros reales, fuera del modelo. */
    CONFIRMAR,
    /** No se ejecuta; el trámite pasa a una persona (REQUIERE_HUMANO). */
    DERIVAR
}
