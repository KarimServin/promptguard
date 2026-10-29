package ar.edu.utn.frsf.promptguard.core;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Comparación tolerante de textos y valores: minúsculas, sin tildes, sin espacios, símbolos de moneda ni
 * separadores de miles/decimales. Así "$50.000", "50000" y "50 000" son el mismo valor.
 */
final class Normalizador {

    /** Separa un texto en palabras o valores; el punto, el guion y la arroba son parte del valor. */
    private static final String SEPARADORES = "[\\s,;:(){}\\[\\]\"'<>¿?¡!=]+";

    private Normalizador() {
    }

    static String normalizar(String texto) {
        return sinTildes(texto).replaceAll("[\\s$.,]", "");
    }

    /** Verdadero si {@code valor} no está vacío y aparece en {@code texto}, aunque sea dentro de otra palabra. */
    static boolean contiene(String texto, String valor) {
        String buscado = normalizar(valor);
        return !buscado.isEmpty() && normalizar(texto).contains(buscado);
    }

    /**
     * Verdadero si {@code valor} aparece en {@code texto} como un valor completo, no como parte de otro. Sirve para
     * fundar argumentos: "perez@gmail.com" no está en "ana.perez@gmail.com", ni "23-456" en "123-456". En un correo
     * los puntos cuentan; en un monto o un identificador no.
     */
    static boolean contieneValor(String texto, String valor) {
        String buscado = valor.strip();
        if (buscado.isEmpty()) {
            return false;
        }
        if (buscado.matches(".*\\s.*")) {
            return contiene(texto, valor);
        }
        String clave = clave(buscado);
        if (clave.isEmpty()) {
            return false;
        }
        for (String token : texto.split(SEPARADORES)) {
            if (clave(token).equals(clave)) {
                return true;
            }
        }
        return false;
    }

    private static String clave(String valor) {
        String limpio = sinTildes(valor).replaceAll("^[.$]+|[.$]+$", "");
        return limpio.contains("@") ? limpio : limpio.replaceAll("[$.,]", "");
    }

    private static String sinTildes(String texto) {
        return Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
