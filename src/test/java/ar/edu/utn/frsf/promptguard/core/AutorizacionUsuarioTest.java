package ar.edu.utn.frsf.promptguard.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C5: el usuario autenticado pide la acción él mismo (no hay documentos de por medio), pero no tiene derecho sobre el
 * recurso o no tiene el rol. C2 y C3 no lo frenan porque el cliente "dio" el dato; C5 sí.
 */
class AutorizacionUsuarioTest {

    private static final ReglaParametro DEL_USUARIO = new ReglaParametro(List.of(FuenteArgumento.USUARIO), Set.of());

    /** ana es titular de la cuenta 123-456 y de la tarjeta T-4001; luis de 789-012 y T-4002. */
    private static final AutorizacionRecursos PROPIEDAD = (usuario, tipo, valor) -> switch (usuario) {
        case "ana" -> Set.of("123-456", "T-4001").contains(valor);
        case "luis" -> Set.of("789-012", "T-4002").contains(valor);
        default -> false;
    };

    private final EvaluadorAcciones evaluador = new EvaluadorAcciones(politica(), EvaluadorAcciones.TODAS,
            ModoC4.ESTRICTA, PROPIEDAD);

    @Test
    void usuarioConsultaSuPropiaCuenta() {
        var ctx = contexto("ana", Set.of("cliente"), "Quiero ver el saldo de la cuenta 123-456");

        var decision = evaluador.decidir(llamada("consultarSaldo", "cuenta", "123-456"), ctx);

        assertThat(decision.tipo()).isEqualTo(TipoDecision.PERMITIR);
    }

    @Test
    void usuarioNoPuedeConsultarLaCuentaDeOtroAunqueLaNombre() {
        var ctx = contexto("ana", Set.of("cliente"), "Quiero ver el saldo de la cuenta 789-012");

        var decision = evaluador.decidir(llamada("consultarSaldo", "cuenta", "789-012"), ctx);

        assertBloqueoC5(decision);
    }

    @Test
    void usuarioNoPuedeBloquearLaTarjetaDeOtro() {
        var ctx = contexto("ana", Set.of("cliente"), "Perdí la tarjeta T-4002, bloqueala");

        var decision = evaluador.decidir(llamada("bloquearTarjeta", "tarjeta", "T-4002"), ctx);

        assertBloqueoC5(decision);
    }

    @Test
    void usuarioBloqueaSuPropiaTarjeta() {
        var ctx = contexto("ana", Set.of("cliente"), "Perdí la tarjeta T-4001, bloqueala");

        var decision = evaluador.decidir(llamada("bloquearTarjeta", "tarjeta", "T-4001"), ctx);

        assertThat(decision.tipo()).isEqualTo(TipoDecision.PERMITIR);
    }

    @Test
    void sinUsuarioAutenticadoSeBloquea() {
        var ctx = new ContextoSolicitud("test", List.of("Saldo de la cuenta 123-456"), List.of());

        var decision = evaluador.decidir(llamada("consultarSaldo", "cuenta", "123-456"), ctx);

        assertBloqueoC5(decision);
    }

    @Test
    void sinVerificadorDeRecursosSeBloquea() {
        var sinVerificador = new EvaluadorAcciones(politica());
        var ctx = contexto("ana", Set.of("cliente"), "Saldo de la cuenta 123-456");

        var decision = sinVerificador.decidir(llamada("consultarSaldo", "cuenta", "123-456"), ctx);

        assertBloqueoC5(decision);
    }

    @Test
    void sinElArgumentoAVerificarSeBloquea() {
        var ctx = contexto("ana", Set.of("cliente"), "Quiero ver mi saldo");

        var decision = evaluador.decidir(new LlamadaHerramienta("consultarSaldo", Map.of()), ctx);

        assertBloqueoC5(decision);
    }

    @Test
    void elRolDebeEstarEntreLosQueLaPoliticaPermite() {
        var operador = contexto("ana", Set.of("operador"), "Reporte interno de la cuenta 123-456");
        var cliente = contexto("ana", Set.of("cliente"), "Reporte interno de la cuenta 123-456");

        assertThat(evaluador.decidir(llamada("reporteInterno", "cuenta", "123-456"), operador).tipo())
                .isEqualTo(TipoDecision.PERMITIR);
        assertBloqueoC5(evaluador.decidir(llamada("reporteInterno", "cuenta", "123-456"), cliente));
    }

    @Test
    void herramientaSinAutorizacionConfiguradaNoExigeIdentidad() {
        var ctx = new ContextoSolicitud("test", List.of("Cuánto es 2 + 2"), List.of());

        var decision = evaluador.decidir(llamada("calculadora", "expresion", "2 + 2"), ctx);

        assertThat(decision.tipo()).isEqualTo(TipoDecision.PERMITIR);
    }

    @Test
    void c5SePuedeApagarParaLaAblacion() {
        var sinC5 = new EvaluadorAcciones(politica(), Set.of("C1", "C2", "C3", "C4"), ModoC4.ESTRICTA, PROPIEDAD);
        var ctx = contexto("ana", Set.of("cliente"), "Quiero ver el saldo de la cuenta 789-012");

        var decision = sinC5.decidir(llamada("consultarSaldo", "cuenta", "789-012"), ctx);

        assertThat(decision.tipo()).isEqualTo(TipoDecision.PERMITIR);
    }

    private static void assertBloqueoC5(Decision decision) {
        assertThat(decision.tipo()).isEqualTo(TipoDecision.BLOQUEAR);
        assertThat(decision.regla()).isEqualTo("C5");
    }

    private static ContextoSolicitud contexto(String usuario, Set<String> roles, String mensaje) {
        var ctx = new ContextoSolicitud("test", List.of(mensaje), List.of());
        ctx.identificar(new Identidad(usuario, roles));
        return ctx;
    }

    private static LlamadaHerramienta llamada(String nombre, String parametro, String valor) {
        return new LlamadaHerramienta(nombre, Map.of(parametro, valor));
    }

    private static Politica politica() {
        return new Politica(Map.of(
                "consultarSaldo", new PoliticaHerramienta("consultarSaldo", Sensibilidad.BAJA, Autoridad.ASISTENTE,
                        List.of(), Map.of("cuenta", DEL_USUARIO), true, List.of(), Map.of("cuenta", "cuenta")),
                "bloquearTarjeta", new PoliticaHerramienta("bloquearTarjeta", Sensibilidad.MEDIA, Autoridad.ASISTENTE,
                        List.of("bloque", "perd"), Map.of("tarjeta", DEL_USUARIO), false, List.of(),
                        Map.of("tarjeta", "tarjeta")),
                "reporteInterno", new PoliticaHerramienta("reporteInterno", Sensibilidad.BAJA, Autoridad.ASISTENTE,
                        List.of(), Map.of("cuenta", DEL_USUARIO), false, List.of("operador"),
                        Map.of("cuenta", "cuenta")),
                "calculadora", new PoliticaHerramienta("calculadora", Sensibilidad.BAJA, Autoridad.ASISTENTE,
                        List.of(), Map.of(), false)),
                Map.of(Sensibilidad.ALTA, 3, Sensibilidad.MEDIA, 2));
    }
}
