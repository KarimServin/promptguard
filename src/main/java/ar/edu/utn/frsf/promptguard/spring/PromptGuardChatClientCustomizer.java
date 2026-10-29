package ar.edu.utn.frsf.promptguard.spring;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.util.ReflectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Registra en el ChatClient todas las herramientas de la aplicación y, si PromptGuard está activo, las envuelve y
 * agrega el advisor. Así la aplicación no declara nada: basta con anotar sus métodos con {@link Tool}.
 *
 * <p>Con PromptGuard apagado las herramientas se registran igual, sin envolver, para que el baseline no cambie.
 */
public class PromptGuardChatClientCustomizer implements ChatClientCustomizer {

    private final ListableBeanFactory beans;
    private final ObjectProvider<PromptGuardRuntime> runtime;
    private final ObjectProvider<PromptGuardAdvisor> advisor;
    private final ObjectProvider<PromptGuardAmbitoAdvisor> ambito;

    public PromptGuardChatClientCustomizer(ListableBeanFactory beans, ObjectProvider<PromptGuardRuntime> runtime,
                                           ObjectProvider<PromptGuardAdvisor> advisor,
                                           ObjectProvider<PromptGuardAmbitoAdvisor> ambito) {
        this.beans = beans;
        this.runtime = runtime;
        this.advisor = advisor;
        this.ambito = ambito;
    }

    @Override
    public void customize(ChatClient.Builder builder) {
        List<ToolCallback> herramientas = herramientas();
        if (!herramientas.isEmpty()) {
            builder.defaultTools(herramientas.toArray());
        }
        // El ámbito va primero (es el más externo); luego el advisor que completa los fragmentos.
        ambito.ifAvailable(a -> builder.defaultAdvisors(a));
        advisor.ifAvailable(a -> builder.defaultAdvisors(a));
    }

    /** Herramientas de la aplicación, envueltas por PromptGuard si está activo. */
    public List<ToolCallback> herramientas() {
        List<ToolCallback> todas = new ArrayList<>();
        for (String nombre : beans.getBeanNamesForType(Object.class, false, false)) {
            Class<?> tipo = beans.getType(nombre);
            if (tipo == null) {
                continue;
            }
            if (ToolCallback.class.isAssignableFrom(tipo)) {
                todas.add((ToolCallback) beans.getBean(nombre));
            }
            else if (ToolCallbackProvider.class.isAssignableFrom(tipo)) {
                todas.addAll(List.of(((ToolCallbackProvider) beans.getBean(nombre)).getToolCallbacks()));
            }
            else if (tieneMetodosTool(tipo)) {
                todas.addAll(List.of(ToolCallbacks.from(beans.getBean(nombre))));
            }
        }
        PromptGuardRuntime guard = runtime.getIfAvailable();
        if (guard == null) {
            return todas;
        }
        return todas.stream().<ToolCallback>map(h -> new ProtectedToolCallback(h, guard)).toList();
    }

    private static boolean tieneMetodosTool(Class<?> tipo) {
        boolean[] encontrado = {false};
        ReflectionUtils.doWithMethods(tipo, metodo -> {
            if (metodo.isAnnotationPresent(Tool.class)) {
                encontrado[0] = true;
            }
        });
        return encontrado[0];
    }
}
