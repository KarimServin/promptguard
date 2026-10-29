package ar.edu.utn.frsf.promptguard.spring;

import ar.edu.utn.frsf.promptguard.core.AutorizacionRecursos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Integración de PromptGuard. Con solo agregar la dependencia la aplicación obtiene sus herramientas registradas
 * en el ChatClient. Con {@code promptguard.enabled=true} (el valor por defecto) además se aplica la política.
 */
@AutoConfiguration
@EnableConfigurationProperties(PromptGuardProperties.class)
public class PromptGuardAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PromptGuardAutoConfiguration.class);

    @Bean
    public PromptGuardChatClientCustomizer promptGuardChatClientCustomizer(ListableBeanFactory beans,
                                                                           ObjectProvider<PromptGuardRuntime> runtime,
                                                                           ObjectProvider<PromptGuardAdvisor> advisor,
                                                                           ObjectProvider<PromptGuardAmbitoAdvisor> ambito) {
        return new PromptGuardChatClientCustomizer(beans, runtime, advisor, ambito);
    }

    @Bean
    @ConditionalOnProperty(prefix = "promptguard", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public AccionesPendientes accionesPendientes(PromptGuardProperties propiedades) {
        return new AccionesPendientes(propiedades.confirmacionTtl(), Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(prefix = "promptguard", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public RegistroAuditoria registroAuditoria(ApplicationEventPublisher publicador) {
        return new RegistroAuditoria(publicador);
    }

    @Bean
    @ConditionalOnProperty(prefix = "promptguard", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public PromptGuardRuntime promptGuardRuntime(PromptGuardProperties propiedades, AccionesPendientes pendientes,
                                                 RegistroAuditoria auditoria,
                                                 ObjectProvider<AutorizacionRecursos> autorizacion) {
        if (propiedades.herramientas().isEmpty()) {
            log.warn("PromptGuard no tiene herramientas configuradas: todas las llamadas se bloquearán.");
        }
        if (propiedades.modo() == PromptGuardProperties.Modo.SHADOW) {
            log.warn("PromptGuard en modo sombra: las herramientas se ejecutan aunque la política las bloquee.");
        }
        return new PromptGuardRuntime(PoliticaFactory.desde(propiedades), propiedades.modo(), propiedades.c4(), pendientes,
                auditoria,
                propiedades.camposSensibles(), autorizacion.getIfAvailable());
    }

    @Bean
    @ConditionalOnProperty(prefix = "promptguard", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PromptGuardAmbitoAdvisor promptGuardAmbitoAdvisor(ObjectProvider<ProveedorIdentidad> identidades) {
        return new PromptGuardAmbitoAdvisor(identidades.getIfAvailable());
    }

    @Bean
    @ConditionalOnProperty(prefix = "promptguard", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PromptGuardAdvisor promptGuardAdvisor(PromptGuardProperties propiedades,
                                                 ObjectProvider<ProveedorIdentidad> identidades) {
        return new PromptGuardAdvisor(propiedades.claveProcedencia(), propiedades.procedencia(),
                identidades.getIfAvailable());
    }
}
