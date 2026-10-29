package ar.edu.utn.frsf.promptguard.spring;

import ar.edu.utn.frsf.promptguard.core.ContextoSolicitud;
import ar.edu.utn.frsf.promptguard.core.Fragmento;
import ar.edu.utn.frsf.promptguard.core.Procedencia;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.Ordered;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Completa el contexto de la solicitud con los fragmentos que recuperó el RAG, con su procedencia. Debe ir
 * <strong>después</strong> del advisor de RAG (QuestionAnswerAdvisor), que deja los documentos en el contexto de la
 * solicitud. El ámbito de la solicitud lo abre {@link PromptGuardAmbitoAdvisor}.
 */
public class PromptGuardAdvisor implements CallAdvisor {

    /** Clave dentro del ToolContext con la que cada herramienta obtiene el contexto de su solicitud. */
    public static final String CLAVE_CONTEXTO = "promptguard.solicitud";

    /** Clave con la que QuestionAnswerAdvisor deja los documentos recuperados en el contexto de la solicitud. */
    static final String CLAVE_DOCUMENTOS_RAG = "qa_retrieved_documents";

    /** Orden alto: se ejecuta después de los advisors de RAG y de memoria. */
    public static final int ORDEN = Ordered.LOWEST_PRECEDENCE - 100;

    private final String claveProcedencia;
    private final Map<String, Procedencia> procedencia;

    private final ProveedorIdentidad identidades;

    public PromptGuardAdvisor(String claveProcedencia, Map<String, Procedencia> procedencia) {
        this(claveProcedencia, procedencia, null);
    }

    public PromptGuardAdvisor(String claveProcedencia, Map<String, Procedencia> procedencia,
                              ProveedorIdentidad identidades) {
        this.claveProcedencia = claveProcedencia;
        this.procedencia = procedencia;
        this.identidades = identidades;
    }

    @Override
    public String getName() {
        return "PromptGuardAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDEN;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // Sin el advisor de ámbito, la solicitud se abre acá: las herramientas igual ven su contexto.
        if (ContextoActual.actual() == null) {
            try (ContextoActual.Ambito ambito = ContextoActual.abrir(PromptGuardAmbitoAdvisor.nuevoContexto(request, identidades))) {
                return continuar(request, chain);
            }
        }
        return continuar(request, chain);
    }

    private ChatClientResponse continuar(ChatClientRequest request, CallAdvisorChain chain) {
        ContextoSolicitud contexto = ContextoActual.actual();
        contexto.reemplazarFragmentos(fragmentos(request));
        ChatClientRequest conContexto = request.mutate()
                .prompt(conContextoDeHerramientas(request.prompt(), contexto))
                .build();
        return chain.nextCall(conContexto);
    }

    private List<Fragmento> fragmentos(ChatClientRequest request) {
        Object recuperados = request.context().get(CLAVE_DOCUMENTOS_RAG);
        if (!(recuperados instanceof List<?> documentos)) {
            return List.of();
        }
        return documentos.stream()
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .map(this::fragmento)
                .toList();
    }

    private Fragmento fragmento(Document documento) {
        Map<String, Object> metadatos = documento.getMetadata();
        String fuente = String.valueOf(metadatos.getOrDefault("fuente", "desconocida"));
        String origen = String.valueOf(metadatos.getOrDefault(claveProcedencia, ""));
        return new Fragmento(fuente, procedencia(origen));
    }

    /** Valor configurado; si no hay traducción, se acepta el nombre del enum; si no, es desconocida. */
    private Procedencia procedencia(String origen) {
        if (procedencia.containsKey(origen)) {
            return procedencia.get(origen);
        }
        try {
            return Procedencia.valueOf(origen.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e) {
            return Procedencia.DESCONOCIDO;
        }
    }

    /**
     * Agrega el contexto a las opciones de herramientas. Las opciones de la solicitud llegan como ChatOptions
     * genéricas, así que se copian los campos comunes para no perderlos.
     */
    private static Prompt conContextoDeHerramientas(Prompt prompt, ContextoSolicitud contexto) {
        ChatOptions base = prompt.getOptions();
        ToolCallingChatOptions.Builder<?> builder;
        if (base instanceof ToolCallingChatOptions herramientas) {
            builder = herramientas.mutate();
        }
        else {
            builder = ToolCallingChatOptions.builder();
            if (base != null) {
                builder.model(base.getModel())
                        .temperature(base.getTemperature())
                        .topP(base.getTopP())
                        .topK(base.getTopK())
                        .maxTokens(base.getMaxTokens())
                        .frequencyPenalty(base.getFrequencyPenalty())
                        .presencePenalty(base.getPresencePenalty())
                        .stopSequences(base.getStopSequences());
            }
        }
        return new Prompt(prompt.getInstructions(), builder.toolContext(CLAVE_CONTEXTO, contexto).build());
    }
}
