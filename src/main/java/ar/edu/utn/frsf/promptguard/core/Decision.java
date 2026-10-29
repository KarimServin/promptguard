package ar.edu.utn.frsf.promptguard.core;

/**
 * Veredicto sobre una llamada a herramienta.
 *
 * @param tipo   qué hacer con la llamada
 * @param regla  chequeo que decidió (C0 a C4), o "ninguna" si se permitió
 * @param motivo detalle legible para la auditoría
 */
public record Decision(TipoDecision tipo, String regla, String motivo) {

    public static Decision permitir() {
        return new Decision(TipoDecision.PERMITIR, "ninguna", "cumple la política");
    }

    public static Decision bloquear(String regla, String motivo) {
        return new Decision(TipoDecision.BLOQUEAR, regla, motivo);
    }

    public static Decision confirmar(String regla, String motivo) {
        return new Decision(TipoDecision.CONFIRMAR, regla, motivo);
    }

    public static Decision derivar(String regla, String motivo) {
        return new Decision(TipoDecision.DERIVAR, regla, motivo);
    }

    public boolean permite() {
        return tipo == TipoDecision.PERMITIR;
    }
}
