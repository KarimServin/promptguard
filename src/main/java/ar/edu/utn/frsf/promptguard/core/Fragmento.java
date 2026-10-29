package ar.edu.utn.frsf.promptguard.core;

/** Fragmento recuperado por el RAG, con su procedencia. */
public record Fragmento(String fuente, Procedencia procedencia) {
}
