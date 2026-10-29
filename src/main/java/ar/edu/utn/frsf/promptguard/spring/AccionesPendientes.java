package ar.edu.utn.frsf.promptguard.spring;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acciones que esperan la confirmación de una persona, fuera del modelo. La persona confirma por un canal
 * autenticado de la aplicación; al confirmar se ejecuta la acción tal como se guardó (mismos argumentos),
 * una sola vez y dentro del plazo.
 */
public final class AccionesPendientes {

    private final Map<String, Entrada> acciones = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock reloj;

    public AccionesPendientes(Duration ttl, Clock reloj) {
        this.ttl = ttl;
        this.reloj = reloj;
    }

    /** Guarda la llamada propuesta y devuelve la descripción que debe mostrarse a la persona. */
    public AccionPendiente crear(String herramienta, Map<String, Object> argumentos, String solicitudId,
                                 ToolCallback delegado, String input, ToolContext toolContext) {
        Instant ahora = reloj.instant();
        var vista = new AccionPendiente(UUID.randomUUID().toString(), herramienta, Map.copyOf(argumentos),
                solicitudId, ahora.plus(ttl));
        acciones.put(vista.id(), new Entrada(vista, delegado, input, toolContext));
        return vista;
    }

    /** Datos exactos de la acción pendiente, para mostrárselos a la persona antes de confirmar. */
    public Optional<AccionPendiente> consultar(String id) {
        return Optional.ofNullable(acciones.get(id)).map(Entrada::vista).filter(v -> !vencida(v));
    }

    /** Acciones vigentes que esperan confirmación. */
    public List<AccionPendiente> listar() {
        return acciones.values().stream().map(Entrada::vista).filter(v -> !vencida(v)).toList();
    }

    /**
     * Ejecuta la acción guardada. Se quita del registro antes de ejecutarla, así no puede repetirse.
     *
     * @throws IllegalArgumentException si no existe o ya se usó
     * @throws IllegalStateException    si venció
     */
    public String confirmar(String id) {
        Entrada entrada = acciones.remove(id);
        if (entrada == null) {
            throw new IllegalArgumentException("No hay una acción pendiente con id " + id);
        }
        if (vencida(entrada.vista())) {
            throw new IllegalStateException("La acción pendiente " + id + " venció");
        }
        return entrada.delegado().call(entrada.input(), entrada.toolContext());
    }

    /** La persona la rechazó: se descarta sin ejecutar. */
    public boolean cancelar(String id) {
        return acciones.remove(id) != null;
    }

    private boolean vencida(AccionPendiente accion) {
        return reloj.instant().isAfter(accion.expiraEn());
    }

    private record Entrada(AccionPendiente vista, ToolCallback delegado, String input, ToolContext toolContext) {
    }
}
