package ar.edu.utn.frsf.promptguard.spring;

import java.time.Instant;
import java.util.Map;

/**
 * Descripción de una acción que espera confirmación. Los argumentos son los reales, no los que el modelo
 * diga después.
 */
public record AccionPendiente(String id, String herramienta, Map<String, Object> argumentos, String solicitudId,
                              Instant expiraEn) {
}
