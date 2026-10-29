package ar.edu.utn.frsf.promptguard.core;

/** De dónde puede venir el valor de un parámetro sensible. Los fragmentos recuperados nunca son una fuente. */
public enum FuenteArgumento {
    /** El valor aparece en un mensaje del cliente. */
    USUARIO,
    /** El valor aparece en el resultado de una herramienta marcada como confiable (por ejemplo, el core). */
    HERRAMIENTA_CONFIABLE,
    /** El valor está en la lista blanca fija de la política (por ejemplo, un correo oficial de soporte). */
    LISTA_BLANCA
}
