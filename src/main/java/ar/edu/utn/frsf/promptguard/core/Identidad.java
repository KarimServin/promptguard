package ar.edu.utn.frsf.promptguard.core;

import java.util.Set;

/**
 * Quién está pidiendo la acción: el usuario ya autenticado por la aplicación y sus roles. PromptGuard no autentica;
 * solo consume la identidad que la aplicación le entrega.
 */
public record Identidad(String usuario, Set<String> roles) {

    public Identidad {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public Identidad(String usuario) {
        this(usuario, Set.of());
    }
}
