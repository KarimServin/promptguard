package ar.edu.utn.frsf.promptguard.core;

/** Cómo se aplica C4 (procedencia x sensibilidad). */
public enum ModoC4 {
    /**
     * Cualquier fragmento de baja confianza en el contexto exige confirmación para las herramientas sensibles.
     * Es el comportamiento medido en las primeras corridas.
     */
    ESTRICTA,
    /**
     * Como el diseño: si la herramienta exige que el cliente la haya pedido (C2) y que sus datos vengan del cliente (C3)
     * y ambos chequeos pasaron, la mera presencia de contenido no confiable no exige confirmación.
     */
    REFINADA
}
