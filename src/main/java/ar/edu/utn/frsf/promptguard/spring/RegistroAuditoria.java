package ar.edu.utn.frsf.promptguard.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/** Escribe cada decisión en el log y la publica como {@link EventoAuditoria}. */
public class RegistroAuditoria {

    private static final Logger log = LoggerFactory.getLogger(RegistroAuditoria.class);

    private final ApplicationEventPublisher publicador;

    public RegistroAuditoria(ApplicationEventPublisher publicador) {
        this.publicador = publicador;
    }

    public void registrar(EventoAuditoria evento) {
        log.info("promptguard modo={} solicitud={} herramienta={} decision={} regla={} ejecutada={} motivo=\"{}\"",
                evento.modo(), evento.solicitudId(), evento.herramienta(), evento.decision(), evento.regla(),
                evento.ejecutada(), evento.motivo());
        publicador.publishEvent(evento);
    }
}
