package ar.edu.utn.frsf.promptguard.spring;

import ar.edu.utn.frsf.promptguard.core.Identidad;
import org.springframework.ai.chat.client.ChatClientRequest;

import java.util.Optional;

/**
 * Lo implementa la aplicación (como un bean) para decir quién hace la solicitud: por ejemplo, leyendo el usuario
 * autenticado de Spring Security o del token de la petición HTTP. Se invoca en el hilo que llama al ChatClient.
 * Si devuelve vacío, las herramientas que exigen autorización se bloquean.
 */
@FunctionalInterface
public interface ProveedorIdentidad {

    Optional<Identidad> identificar(ChatClientRequest request);
}
