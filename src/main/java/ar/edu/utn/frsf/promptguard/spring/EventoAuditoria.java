package ar.edu.utn.frsf.promptguard.spring;

import ar.edu.utn.frsf.promptguard.core.TipoDecision;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Registro de una llamada a herramienta. Se publica como evento de Spring; la aplicación decide si lo
 * persiste, lo expone o lo envía a un sistema de observabilidad.
 *
 * @param decision           decisión del núcleo (lo que se habría hecho)
 * @param ejecutada          si la herramienta se ejecutó
 * @param fragmentos         fuentes recuperadas con su procedencia, para explicar la decisión
 * @param huellaMensajes     hash corto de los mensajes del cliente (sin guardar el texto)
 */
public record EventoAuditoria(
        Instant instante,
        String solicitudId,
        String herramienta,
        Map<String, Object> argumentos,
        String modo,
        TipoDecision decision,
        String regla,
        String motivo,
        boolean ejecutada,
        List<String> fragmentos,
        String huellaMensajes,
        long latenciaGuardMicros) {
}
