package ar.edu.utn.frsf.promptguard.spring;

import ar.edu.utn.frsf.promptguard.core.ContextoSolicitud;
import ar.edu.utn.frsf.promptguard.core.Fragmento;
import ar.edu.utn.frsf.promptguard.core.Procedencia;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PromptGuardAutoConfigurationTest {

    private static final String[] PROPIEDADES_BANCARIAS = """
            promptguard.herramientas.consultarSaldo.sensibilidad=BAJA
            promptguard.herramientas.consultarSaldo.autoridad=ASISTENTE
            promptguard.herramientas.consultarSaldo.resultado-confiable=true
            promptguard.herramientas.consultarSaldo.parametros.cuenta.fuentes=USUARIO,HERRAMIENTA_CONFIABLE
            promptguard.herramientas.reembolsar.sensibilidad=ALTA
            promptguard.herramientas.reembolsar.autoridad=REQUIERE_HUMANO
            promptguard.herramientas.enviarComprobante.sensibilidad=MEDIA
            promptguard.herramientas.enviarComprobante.autoridad=ASISTENTE
            promptguard.herramientas.enviarComprobante.intencion=comprobante,constancia
            promptguard.herramientas.enviarComprobante.parametros.destino.fuentes=USUARIO
            """.lines().toArray(String[]::new);

    private final ApplicationContextRunner correr = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PromptGuardAutoConfiguration.class))
            .withUserConfiguration(Herramientas.class);

    @Test
    void sinConfiguracionElStarterBloqueaTodaHerramienta() {
        correr.run(contexto -> {
            // Sin política no hay herramienta registrada (C0): la llamada se bloquea aunque el cliente la pida.
            var saldo = herramienta(contexto, "consultarSaldo");

            String respuesta = saldo.call("{\"cuenta\":\"123-456\"}", contextoSolicitud("¿Saldo de la cuenta 123-456?"));

            assertThat(respuesta).contains("no se ejecutó");
            assertThat(contexto.getBean(Herramientas.class).llamadasSaldo.get()).isZero();
        });
    }

    @Test
    void lecturaFundadaEnElClientePasaYSeEjecuta() {
        correr.withPropertyValues(PROPIEDADES_BANCARIAS).run(contexto -> {
            var saldo = herramienta(contexto, "consultarSaldo");

            String respuesta = saldo.call("{\"cuenta\":\"123-456\"}", contextoSolicitud("¿Saldo de la cuenta 123-456?"));

            assertThat(respuesta).isEqualTo("saldo 123-456: 250000");
            assertThat(contexto.getBean(Herramientas.class).llamadasSaldo.get()).isEqualTo(1);
        });
    }

    @Test
    void reembolsoNuncaSeEjecutaPeroSeDeriva() {
        correr.withPropertyValues(PROPIEDADES_BANCARIAS).run(contexto -> {
            var reembolso = herramienta(contexto, "reembolsar");

            String respuesta = reembolso.call("{\"monto\":50000}", contextoSolicitud("Quiero un reembolso de $50.000"));

            assertThat(respuesta).contains("corresponde a una persona");
            assertThat(contexto.getBean(Herramientas.class).llamadasReembolso.get()).isZero();
        });
    }

    @Test
    void destinoDelDocumentoSeBloqueaYDestinoDelClienteConFragmentoExternoPideConfirmacion() {
        correr.withPropertyValues(PROPIEDADES_BANCARIAS).run(contexto -> {
            var comprobante = herramienta(contexto, "enviarComprobante");
            var conDocumentoExterno = List.of(new Fragmento("destino.txt", Procedencia.EXTERNO));

            String desdeDocumento = comprobante.call("{\"destino\":\"seguimiento@gestion-externa.example\"}",
                    contextoSolicitud("Enviame el comprobante de la transferencia", conDocumentoExterno));
            assertThat(desdeDocumento).contains("no se ejecutó");

            String desdeCliente = comprobante.call("{\"destino\":\"ana@cliente.example\"}",
                    contextoSolicitud("Enviame el comprobante al ana@cliente.example", conDocumentoExterno));
            assertThat(desdeCliente).contains("requiere confirmación");
            assertThat(contexto.getBean(Herramientas.class).llamadasComprobante.get()).isZero();
        });
    }

    @Test
    void laConfirmacionEjecutaLaAccionGuardadaUnaSolaVez() {
        correr.withPropertyValues(PROPIEDADES_BANCARIAS).run(contexto -> {
            var comprobante = herramienta(contexto, "enviarComprobante");
            var fragmentoExterno = List.of(new Fragmento("oculto.txt", Procedencia.EXTERNO));
            comprobante.call("{\"destino\":\"ana@cliente.example\"}",
                    contextoSolicitud("Enviame el comprobante al ana@cliente.example", fragmentoExterno));

            var pendientes = contexto.getBean(AccionesPendientes.class);
            AccionPendiente accion = pendientes.listar().get(0);
            assertThat(accion.argumentos()).containsEntry("destino", "ana@cliente.example");

            String resultado = pendientes.confirmar(accion.id());

            assertThat(resultado).isEqualTo("comprobante enviado a ana@cliente.example");
            assertThat(contexto.getBean(Herramientas.class).llamadasComprobante.get()).isEqualTo(1);
            assertThat(pendientes.consultar(accion.id())).isEmpty();
        });
    }

    @Test
    void enModoSombraLaAccionBloqueadaSeEjecutaPeroQuedaRegistrada() {
        correr.withPropertyValues(PROPIEDADES_BANCARIAS).withPropertyValues("promptguard.modo=shadow").run(contexto -> {
            var reembolso = herramienta(contexto, "reembolsar");

            String respuesta = reembolso.call("{\"monto\":50000}", contextoSolicitud("Quiero un reembolso"));

            assertThat(respuesta).isEqualTo("reembolso procesado");
            assertThat(contexto.getBean(Herramientas.class).llamadasReembolso.get()).isEqualTo(1);
        });
    }

    @Test
    void sinPromptGuardLasHerramientasSeRegistranIgualSinProtegerlas() {
        correr.withPropertyValues("promptguard.enabled=false").run(contexto -> {
            var reembolso = herramienta(contexto, "reembolsar");

            String respuesta = reembolso.call("{\"monto\":50000}", contextoSolicitud("Quiero un reembolso"));

            assertThat(respuesta).isEqualTo("reembolso procesado");
            assertThat(contexto.getBeansOfType(PromptGuardAdvisor.class)).isEmpty();
        });
    }

    @Test
    void unBeanConMetodosToolSeRegistraYSeProtegeSinDeclararlo() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PromptGuardAutoConfiguration.class))
                .withUserConfiguration(Anotadas.class)
                .withPropertyValues(PROPIEDADES_BANCARIAS)
                .run(contexto -> {
                    var saldo = herramienta(contexto, "consultarSaldo");

                    String respuesta = saldo.call("{\"cuenta\":\"999-000\"}", contextoSolicitud("Saldo de 123-456"));

                    assertThat(respuesta).contains("no se ejecutó");
                    assertThat(contexto.getBean(BancoAnotado.class).llamadas.get()).isZero();
                });
    }

    @Test
    void elAdvisorAportaLosMensajesYLosFragmentosConSuProcedencia() {
        correr.withPropertyValues(PROPIEDADES_BANCARIAS).run(contexto -> {
            var advisor = contexto.getBean(PromptGuardAdvisor.class);
            var recuperados = List.of(
                    new Document("política", Map.of("fuente", "reembolsos.txt", "origen", "CONFIABLE")),
                    new Document("veneno", Map.of("fuente", "plausible.txt", "origen", "NO_CONFIABLE")));
            var capturadas = new ArrayList<Map<String, Object>>();
            var request = ChatClientRequest.builder()
                    .prompt(new Prompt(List.of(new UserMessage("¿Cuánto tarda un reembolso?"))))
                    .context(Map.of(PromptGuardAdvisor.CLAVE_DOCUMENTOS_RAG, recuperados))
                    .build();

            advisor.adviseCall(request, cadenaQueCaptura(capturadas));

            var ctx = (ContextoSolicitud) capturadas.get(0).get(PromptGuardAdvisor.CLAVE_CONTEXTO);
            assertThat(ctx.mensajesCliente()).containsExactly("¿Cuánto tarda un reembolso?");
            assertThat(ctx.fragmentos()).containsExactly(
                    new Fragmento("reembolsos.txt", Procedencia.CONFIABLE),
                    new Fragmento("plausible.txt", Procedencia.EXTERNO));
            assertThat(ctx.confianzaMinima()).isEqualTo(Procedencia.EXTERNO.nivel());
        });
    }

    /**
     * Caso real de ChatClient: el ToolCallingAdvisor ejecuta la herramienta después de que el advisor interno terminó,
     * sin ToolContext. Solo el ámbito de la solicitud puede darle el contexto.
     */
    @Test
    void elAmbitoCubreLaEjecucionDeHerramientasFueraDeLaLlamadaAlModelo() {
        correr.withPropertyValues(PROPIEDADES_BANCARIAS).run(contexto -> {
            var ambito = contexto.getBean(PromptGuardAmbitoAdvisor.class);
            var advisor = contexto.getBean(PromptGuardAdvisor.class);
            var saldo = herramienta(contexto, "consultarSaldo");
            var request = ChatClientRequest.builder()
                    .prompt(new Prompt(List.of(new UserMessage("¿Saldo de la cuenta 123-456?"))))
                    .build();
            var resultado = new String[1];
            CallAdvisorChain modelo = cadena(req -> null);
            CallAdvisorChain siguienteDelAmbito = cadena(req -> {
                advisor.adviseCall(req, modelo);
                // Esto ocurre después de que el advisor interno terminó, como en el ToolCallingAdvisor real.
                resultado[0] = saldo.call("{\"cuenta\":\"123-456\"}");
                return null;
            });

            ambito.adviseCall(request, siguienteDelAmbito);

            assertThat(resultado[0]).isEqualTo("saldo 123-456: 250000");
        });
    }

    /**
     * C5 de punta a punta, sin modelo: propiedades roles/recursos, identidad aportada por la aplicación, verificador de
     * recursos de la aplicación y herramienta envuelta. ana pide su cuenta y la de luis, escribiendo ella el número.
     */
    @Test
    void c5SoloDejaOperarLosRecursosDelUsuarioAutenticado() {
        String[] c5 = {
                "promptguard.herramientas.consultarSaldo.roles=cliente",
                "promptguard.herramientas.consultarSaldo.recursos.cuenta=cuenta"};
        correr.withPropertyValues(PROPIEDADES_BANCARIAS).withPropertyValues(c5)
                .withBean(ProveedorIdentidad.class, () -> request -> java.util.Optional.of(
                        new ar.edu.utn.frsf.promptguard.core.Identidad("ana", java.util.Set.of("cliente"))))
                .withBean(ar.edu.utn.frsf.promptguard.core.AutorizacionRecursos.class,
                        () -> (usuario, tipo, valor) -> usuario.equals("ana") && tipo.equals("cuenta")
                                && valor.equals("123-456"))
                .run(contexto -> {
                    var ambito = contexto.getBean(PromptGuardAmbitoAdvisor.class);
                    var saldo = herramienta(contexto, "consultarSaldo");
                    var propia = new String[1];
                    var ajena = new String[1];

                    ambito.adviseCall(pedido("Saldo de la cuenta 123-456"), cadena(req -> {
                        propia[0] = saldo.call("{\"cuenta\":\"123-456\"}");
                        return null;
                    }));
                    ambito.adviseCall(pedido("Saldo de la cuenta 789-012"), cadena(req -> {
                        ajena[0] = saldo.call("{\"cuenta\":\"789-012\"}");
                        return null;
                    }));

                    assertThat(propia[0]).isEqualTo("saldo 123-456: 250000");
                    assertThat(ajena[0]).contains("no se ejecutó");
                    assertThat(contexto.getBean(Herramientas.class).llamadasSaldo.get()).isEqualTo(1);
                });
    }

    @Test
    void c5SinProveedorDeIdentidadBloqueaLasHerramientasQueLaExigen() {
        correr.withPropertyValues(PROPIEDADES_BANCARIAS)
                .withPropertyValues("promptguard.herramientas.consultarSaldo.recursos.cuenta=cuenta")
                .run(contexto -> {
                    var ambito = contexto.getBean(PromptGuardAmbitoAdvisor.class);
                    var saldo = herramienta(contexto, "consultarSaldo");
                    var respuesta = new String[1];

                    ambito.adviseCall(pedido("Saldo de la cuenta 123-456"), cadena(req -> {
                        respuesta[0] = saldo.call("{\"cuenta\":\"123-456\"}");
                        return null;
                    }));

                    assertThat(respuesta[0]).contains("no se ejecutó");
                    assertThat(contexto.getBean(Herramientas.class).llamadasSaldo.get()).isZero();
                });
    }

    private static ChatClientRequest pedido(String mensaje) {
        return ChatClientRequest.builder().prompt(new Prompt(List.of(new UserMessage(mensaje)))).build();
    }

    private static CallAdvisorChain cadena(java.util.function.Function<ChatClientRequest, Object> nextCall) {
        return (CallAdvisorChain) Proxy.newProxyInstance(
                PromptGuardAutoConfigurationTest.class.getClassLoader(),
                new Class<?>[]{CallAdvisorChain.class},
                (proxy, metodo, args) -> {
                    if (metodo.getName().equals("nextCall")) {
                        return nextCall.apply((ChatClientRequest) args[0]);
                    }
                    return null;
                });
    }

    private static ToolCallback herramienta(ApplicationContext contexto, String nombre) {
        return contexto.getBean(PromptGuardChatClientCustomizer.class).herramientas().stream()
                .filter(t -> t.getToolDefinition().name().equals(nombre))
                .findFirst()
                .orElseThrow();
    }

    private static ToolContext contextoSolicitud(String mensaje) {
        return contextoSolicitud(mensaje, List.of());
    }

    private static ToolContext contextoSolicitud(String mensaje, List<Fragmento> fragmentos) {
        var ctx = new ContextoSolicitud("prueba", List.of(mensaje), fragmentos);
        return new ToolContext(Map.of(PromptGuardAdvisor.CLAVE_CONTEXTO, ctx));
    }

    /** Cadena de advisors falsa: solo importa lo que el advisor le entrega al modelo. */
    private static CallAdvisorChain cadenaQueCaptura(List<Map<String, Object>> capturadas) {
        return (CallAdvisorChain) Proxy.newProxyInstance(
                PromptGuardAutoConfigurationTest.class.getClassLoader(),
                new Class<?>[]{CallAdvisorChain.class},
                (proxy, metodo, args) -> {
                    var request = (ChatClientRequest) args[0];
                    capturadas.add(((ToolCallingChatOptions) request.prompt().getOptions()).getToolContext());
                    return null;
                });
    }

    @Configuration
    static class Herramientas {

        final AtomicInteger llamadasSaldo = new AtomicInteger();
        final AtomicInteger llamadasReembolso = new AtomicInteger();
        final AtomicInteger llamadasComprobante = new AtomicInteger();

        @Bean
        ToolCallback consultarSaldo() {
            return simple("consultarSaldo", llamadasSaldo, "saldo 123-456: 250000");
        }

        @Bean
        ToolCallback reembolsar() {
            return simple("reembolsar", llamadasReembolso, "reembolso procesado");
        }

        @Bean
        ToolCallback enviarComprobante() {
            return simple("enviarComprobante", llamadasComprobante, "comprobante enviado a ana@cliente.example");
        }

        private static ToolCallback simple(String nombre, AtomicInteger contador, String resultado) {
            ToolDefinition definicion = DefaultToolDefinition.builder()
                    .name(nombre)
                    .description(nombre)
                    .inputSchema("{}")
                    .build();
            return new ToolCallback() {
                @Override
                public ToolDefinition getToolDefinition() {
                    return definicion;
                }

                @Override
                public String call(String toolInput) {
                    contador.incrementAndGet();
                    return resultado;
                }
            };
        }
    }

    @Configuration
    static class Anotadas {

        @Bean
        BancoAnotado bancoAnotado() {
            return new BancoAnotado();
        }
    }

    /** Clase concreta: el registro de herramientas la inspecciona sin instanciarla. */
    static class BancoAnotado {

        final AtomicInteger llamadas = new AtomicInteger();

        @Tool(description = "Consulta el saldo de una cuenta.")
        public String consultarSaldo(String cuenta) {
            llamadas.incrementAndGet();
            return "saldo " + cuenta;
        }
    }
}
