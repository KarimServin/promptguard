package ar.edu.utn.frsf.promptguard.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Microbenchmark de la latencia de {@link EvaluadorAcciones}: el costo que PromptGuard agrega a cada llamada a
 * herramienta, sin contar al modelo. Imprime los percentiles; la meta del diseño es p95 menor a 10 ms.
 * No es un benchmark JMH: mide cada llamada con nanoTime tras un calentamiento.
 */
class LatenciaEvaluadorTest {

    private static final int CALENTAMIENTO = 100_000;
    private static final int MEDICIONES = 200_000;

    @Test
    void elEvaluadorAgregaMicrosegundosPorLlamada() {
        var politica = new Politica(Map.of(
                "consultarSaldo", new PoliticaHerramienta("consultarSaldo", Sensibilidad.BAJA, Autoridad.ASISTENTE,
                        List.of(), Map.of("cuenta", new ReglaParametro(
                                List.of(FuenteArgumento.USUARIO, FuenteArgumento.HERRAMIENTA_CONFIABLE), Set.of())), true),
                "enviarComprobante", new PoliticaHerramienta("enviarComprobante", Sensibilidad.MEDIA,
                        Autoridad.ASISTENTE, List.of("comprobante", "constancia"),
                        Map.of("destino", new ReglaParametro(List.of(FuenteArgumento.USUARIO), Set.of())), false),
                "reembolsar", new PoliticaHerramienta("reembolsar", Sensibilidad.ALTA, Autoridad.REQUIERE_HUMANO,
                        List.of(), Map.of(), false)),
                Map.of(Sensibilidad.ALTA, 3, Sensibilidad.MEDIA, 2));
        var evaluador = new EvaluadorAcciones(politica);

        // Contexto realista: 4 fragmentos de ~1.500 caracteres, como el RAG del experimento.
        String relleno = "Los reembolsos se procesan en un plazo de cinco días hábiles tras verificar la identidad. ".repeat(18);
        List<Fragmento> fragmentos = List.of(
                new Fragmento("a.txt", Procedencia.CONFIABLE), new Fragmento("b.txt", Procedencia.CONFIABLE),
                new Fragmento("c.txt", Procedencia.EXTERNO), new Fragmento("d.txt", Procedencia.CONFIABLE));
        var corto = new ContextoSolicitud("c", List.of("¿Cuál es el saldo de mi cuenta 123-456?"), fragmentos);
        var largo = new ContextoSolicitud("l", List.of(relleno, relleno, relleno,
                "Enviame el comprobante de la transferencia al correo ana@cliente.example"), fragmentos);

        medir("C1 derivar (reembolsar)", () -> evaluador.decidir(
                new LlamadaHerramienta("reembolsar", Map.of("cuenta", "123-456", "monto", 50000)), corto));
        medir("C3 permitir (consultarSaldo, contexto corto)", () -> evaluador.decidir(
                new LlamadaHerramienta("consultarSaldo", Map.of("cuenta", "123-456")), corto));
        long p95 = medir("C2+C3+C4 (enviarComprobante, 3 mensajes de ~1.600 caracteres)", () -> evaluador.decidir(
                new LlamadaHerramienta("enviarComprobante", Map.of("destino", "ana@cliente.example")), largo));

        assertThat(p95).as("p95 en nanosegundos").isLessThan(10_000_000L);
    }

    private static long medir(String nombre, Supplier<Decision> operacion) {
        for (int i = 0; i < CALENTAMIENTO; i++) {
            operacion.get();
        }
        long[] muestras = new long[MEDICIONES];
        for (int i = 0; i < MEDICIONES; i++) {
            long inicio = System.nanoTime();
            operacion.get();
            muestras[i] = System.nanoTime() - inicio;
        }
        Arrays.sort(muestras);
        long p50 = muestras[MEDICIONES / 2];
        long p95 = muestras[(int) (MEDICIONES * 0.95)];
        long p99 = muestras[(int) (MEDICIONES * 0.99)];
        System.out.printf("LATENCIA %-62s p50=%6.1f us  p95=%6.1f us  p99=%6.1f us  max=%7.1f us%n", nombre,
                p50 / 1000.0, p95 / 1000.0, p99 / 1000.0, muestras[MEDICIONES - 1] / 1000.0);
        return p95;
    }
}
