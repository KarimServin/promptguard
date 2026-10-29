package ar.edu.utn.frsf.promptguard.spring;

import ar.edu.utn.frsf.promptguard.core.AutorizacionRecursos;
import ar.edu.utn.frsf.promptguard.core.ContextoSolicitud;
import ar.edu.utn.frsf.promptguard.core.Decision;
import ar.edu.utn.frsf.promptguard.core.EvaluadorAcciones;
import ar.edu.utn.frsf.promptguard.core.LlamadaHerramienta;
import ar.edu.utn.frsf.promptguard.core.ModoC4;
import ar.edu.utn.frsf.promptguard.core.Politica;
import ar.edu.utn.frsf.promptguard.spring.PromptGuardProperties.Modo;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Punto de decisión: recibe cada llamada que el modelo propone, la evalúa y, según el modo, la ejecuta,
 * la bloquea, la deriva o la deja pendiente de confirmación. Toda decisión queda auditada.
 */
public class PromptGuardRuntime {

    private static final TypeReference<Map<String, Object>> TIPO_ARGUMENTOS = new TypeReference<>() {
    };
    private static final String MENSAJE_BLOQUEO = "Acción no permitida por la política de seguridad: no se ejecutó. "
            + "Explicale al cliente qué puede hacer por otro canal.";
    private static final String MENSAJE_DERIVACION = "Esta operación corresponde a una persona de soporte: no se "
            + "ejecutó. Explicale al cliente cómo continuar el trámite con soporte.";

    private final EvaluadorAcciones evaluador;
    private final Politica politica;
    private final Modo modo;
    private final AccionesPendientes pendientes;
    private final RegistroAuditoria auditoria;
    private final Set<String> camposSensibles;
    private final ObjectMapper json = new ObjectMapper();

    public PromptGuardRuntime(Politica politica, Modo modo, ModoC4 c4, AccionesPendientes pendientes,
                              RegistroAuditoria auditoria,
                              Set<String> camposSensibles) {
        this(politica, modo, c4, pendientes, auditoria, camposSensibles, null);
    }

    public PromptGuardRuntime(Politica politica, Modo modo, ModoC4 c4, AccionesPendientes pendientes,
                              RegistroAuditoria auditoria,
                              Set<String> camposSensibles, AutorizacionRecursos autorizacion) {
        this.evaluador = new EvaluadorAcciones(politica, EvaluadorAcciones.TODAS, c4, autorizacion);
        this.politica = politica;
        this.modo = modo;
        this.pendientes = pendientes;
        this.auditoria = auditoria;
        this.camposSensibles = camposSensibles.stream()
                .map(campo -> campo.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Evalúa y, si corresponde, ejecuta la herramienta. Devuelve lo que ve el modelo: el resultado real o un
     * mensaje que explica por qué no se ejecutó.
     */
    public String ejecutar(ToolCallback herramienta, String input, ToolContext toolContext) {
        long inicio = System.nanoTime();
        String nombre = herramienta.getToolDefinition().name();
        Map<String, Object> argumentos = argumentos(input);
        ContextoSolicitud contexto = contexto(toolContext);

        Decision decision = evaluador.decidir(new LlamadaHerramienta(nombre, argumentos), contexto);
        boolean ejecutar = decision.permite() || modo == Modo.SHADOW;
        long latencia = (System.nanoTime() - inicio) / 1_000;
        auditoria.registrar(evento(contexto, nombre, argumentos, decision, ejecutar, latencia));

        if (ejecutar) {
            String resultado = herramienta.call(input, toolContext);
            if (decision.permite() && esConfiable(nombre)) {
                contexto.registrarResultadoConfiable(resultado);
            }
            return resultado;
        }
        return switch (decision.tipo()) {
            case CONFIRMAR -> {
                AccionPendiente accion = pendientes.crear(nombre, argumentos, contexto.id(), herramienta, input,
                        toolContext);
                yield "La acción " + nombre + " requiere confirmación de una persona antes de ejecutarse "
                        + "(id " + accion.id() + "). No se ejecutó: no la des por hecha.";
            }
            case DERIVAR -> MENSAJE_DERIVACION;
            case BLOQUEAR, PERMITIR -> MENSAJE_BLOQUEO;
        };
    }

    private boolean esConfiable(String nombre) {
        return politica.herramienta(nombre).map(p -> p.resultadoConfiable()).orElse(false);
    }

    private Map<String, Object> argumentos(String input) {
        if (input == null || input.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(input, TIPO_ARGUMENTOS);
        }
        catch (RuntimeException e) {
            // Argumentos ilegibles: se evalúan vacíos, así que cualquier parámetro exigido queda sin fundamento.
            return Map.of();
        }
    }

    private static ContextoSolicitud contexto(ToolContext toolContext) {
        Object valor = toolContext == null ? null : toolContext.getContext().get(PromptGuardAdvisor.CLAVE_CONTEXTO);
        if (valor instanceof ContextoSolicitud contexto) {
            return contexto;
        }
        ContextoSolicitud enHilo = ContextoActual.actual();
        if (enHilo != null) {
            return enHilo;
        }
        // Sin contexto de solicitud (el advisor no está en la cadena) no hay fundamento: falla cerrado.
        return ContextoSolicitud.vacio("sin-contexto");
    }

    private EventoAuditoria evento(ContextoSolicitud contexto, String nombre, Map<String, Object> argumentos,
                                   Decision decision, boolean ejecutada, long latenciaMicros) {
        return new EventoAuditoria(
                Instant.now(),
                contexto.id(),
                nombre,
                enmascarar(argumentos),
                modo.name().toLowerCase(Locale.ROOT),
                decision.tipo(),
                decision.regla(),
                decision.motivo(),
                ejecutada,
                contexto.fragmentos().stream()
                        .map(f -> f.fuente() + " (" + f.procedencia() + ")")
                        .toList(),
                huella(contexto.mensajesCliente()),
                latenciaMicros);
    }

    private Map<String, Object> enmascarar(Map<String, Object> argumentos) {
        Map<String, Object> visibles = new HashMap<>();
        argumentos.forEach((clave, valor) -> visibles.put(clave,
                camposSensibles.contains(clave.toLowerCase(Locale.ROOT)) ? "***" : valor));
        return visibles;
    }

    private static String huella(List<String> mensajes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", mensajes).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
