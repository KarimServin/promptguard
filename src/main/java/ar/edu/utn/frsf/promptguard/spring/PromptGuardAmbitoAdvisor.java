package ar.edu.utn.frsf.promptguard.spring;

import ar.edu.utn.frsf.promptguard.core.ContextoSolicitud;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.Ordered;

import java.util.List;
import java.util.UUID;

/**
 * Abre el ámbito de la solicitud antes que el ToolCallingAdvisor de ChatClient. Así el contexto está disponible también
 * mientras se ejecutan las herramientas, que ese advisor lanza por fuera de la llamada al modelo. Se registra junto con
 * {@link PromptGuardAdvisor}, que completa los fragmentos recuperados.
 */
public class PromptGuardAmbitoAdvisor implements CallAdvisor {

    /** Antes que cualquier otro advisor, incluido ToolCallingAdvisor. */
    public static final int ORDEN = Ordered.HIGHEST_PRECEDENCE + 1;

    private final ProveedorIdentidad identidades;

    public PromptGuardAmbitoAdvisor() {
        this(null);
    }

    public PromptGuardAmbitoAdvisor(ProveedorIdentidad identidades) {
        this.identidades = identidades;
    }

    @Override
    public String getName() {
        return "PromptGuardAmbitoAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDEN;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        try (ContextoActual.Ambito ambito = ContextoActual.abrir(nuevoContexto(request, identidades))) {
            return chain.nextCall(request);
        }
    }

    static ContextoSolicitud nuevoContexto(ChatClientRequest request) {
        return nuevoContexto(request, null);
    }

    static ContextoSolicitud nuevoContexto(ChatClientRequest request, ProveedorIdentidad identidades) {
        ContextoSolicitud contexto = new ContextoSolicitud(UUID.randomUUID().toString(), mensajesCliente(request),
                List.of());
        if (identidades != null) {
            identidades.identificar(request).ifPresent(contexto::identificar);
        }
        return contexto;
    }

    static List<String> mensajesCliente(ChatClientRequest request) {
        return request.prompt().getInstructions().stream()
                .filter(UserMessage.class::isInstance)
                .map(Message::getText)
                .toList();
    }
}
