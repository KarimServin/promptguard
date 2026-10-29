package ar.edu.utn.frsf.promptguard.core;

/**
 * Nivel de confianza de un fragmento recuperado. Lo asigna la ingesta (metadato del documento);
 * lo que no se etiqueta es {@link #DESCONOCIDO}.
 */
public enum Procedencia {
    CONFIABLE(3),
    INTERNO_EDITABLE(2),
    EXTERNO(1),
    DESCONOCIDO(0);

    private final int nivel;

    Procedencia(int nivel) {
        this.nivel = nivel;
    }

    public int nivel() {
        return nivel;
    }
}
