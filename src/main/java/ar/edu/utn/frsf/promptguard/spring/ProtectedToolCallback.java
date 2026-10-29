package ar.edu.utn.frsf.promptguard.spring;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/** Envuelve una herramienta: la definición no cambia, la ejecución pasa por PromptGuard. */
final class ProtectedToolCallback implements ToolCallback {

    private final ToolCallback delegado;
    private final PromptGuardRuntime runtime;

    ProtectedToolCallback(ToolCallback delegado, PromptGuardRuntime runtime) {
        this.delegado = delegado;
        this.runtime = runtime;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegado.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegado.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return runtime.ejecutar(delegado, toolInput, toolContext);
    }
}
