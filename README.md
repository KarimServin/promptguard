# PromptGuard (Spring Boot Starter)

Capa de control de agencia para agentes Spring AI. Se ubica **entre la decisión del modelo y la ejecución de una herramienta**
y decide, de forma determinista (sin un segundo LLM), si la llamada puede ejecutarse.

Trabajo **JIT2026-T112**: *Framework de Seguridad en Tiempo de Ejecución para la Mitigación de Prompt Injection y Excessive
Agency en Agentes de IA Empresariales basados en Java*.

Se evalúa contra la aplicación vulnerable
[vulnerable-agent-demo](https://github.com/KarimServin/vulnerable-agent-demo), que es el baseline sin defensas. Ese repositorio
contiene el diseño (`docs/DISENO_PROMPTGUARD.md`), el informe y los resultados (`docs/RESULTADOS_PROMPTGUARD.md`).

> **Estado:** núcleo y starter implementados, con pruebas unitarias y de contexto Spring. C0 a C4 se evaluaron contra el
> baseline con modelos abiertos (resumen en [Resultados](#resultados)). **C5 (autorización por usuario) está verificado con
> pruebas y con el demo, pero no se midió con modelos.**

## Requisitos y ejecución rápida

- **JDK 21**. No hace falta nada más para compilar y probar: el wrapper de Gradle baja Gradle y las dependencias.
- Es una **librería** (starter), no una aplicación ejecutable. Se prueba y se publica; la ejecución real es en una aplicación
  que la use, como [vulnerable-agent-demo](https://github.com/KarimServin/vulnerable-agent-demo).

```bash
git clone https://github.com/KarimServin/promptguard.git
cd promptguard
./gradlew test                 # en Windows: gradlew.bat test (tarda varios minutos: incluye la medición de latencia)
./gradlew publishToMavenLocal  # deja el starter en ~/.m2 para que lo use otra aplicación
```

Para verlo funcionando, seguí el README de `vulnerable-agent-demo`: allí se instala este starter con el comando anterior y se
levanta la aplicación con y sin PromptGuard.

## Qué decide

Para cada llamada que propone el modelo, `EvaluadorAcciones` aplica los controles en este orden: **C0, C1, C5, C2, C3, C4**.

| Chequeo | Pregunta | Si falla |
|---|---|---|
| **C0** | ¿La herramienta está registrada en la política? | Bloquear (denegar por defecto) |
| **C1** | ¿Quién puede ejecutarla? `PROHIBIDA` / `REQUIERE_HUMANO` / `ASISTENTE` | Bloquear / derivar a una persona |
| **C2** | ¿El cliente pidió la acción? (términos de intención en sus mensajes) | Confirmar (sensibilidad media/alta) o bloquear |
| **C3** | ¿Cada parámetro configurado viene del cliente, de un resultado de herramienta confiable o de una lista blanca? | Bloquear |
| **C4** | ¿El contexto trae fragmentos de procedencia baja para una herramienta sensible? | Pedir confirmación |
| **C5** | ¿El usuario autenticado tiene el rol y el derecho sobre los recursos que la herramienta toca? | Bloquear |

C1 a C4 controlan **de dónde viene** la instrucción; C5 controla **si quien la pide tiene derecho** a hacerla, aunque la haya
escrito él mismo (por ejemplo, pedir el saldo de la cuenta de otro cliente).

Los fragmentos recuperados **nunca** son fuente de fundamento para un argumento. Un dato que solo aparece en un documento no
puede llegar a una herramienta sensible.

Resultados posibles: `PERMITIR`, `BLOQUEAR`, `CONFIRMAR` (una persona confirma los parámetros reales, fuera del modelo) y
`DERIVAR` (la operación corresponde a soporte). Toda decisión se publica como `EventoAuditoria`.

## Integración

En la aplicación, la integración son dos cosas: **una dependencia** y **la política en YAML**. Nada de código.

### 1. Dependencia

Publicar el starter una vez en el repositorio Maven local (o en el repositorio de la organización):

```bash
gradlew publishToMavenLocal
```

Y en la aplicación:

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'ar.edu.utn.frsf:promptguard-spring-boot-starter:0.1.0-SNAPSHOT'
}
```

Con eso el starter registra en el `ChatClient` todas las herramientas de la aplicación: cualquier bean con métodos
anotados con `@Tool`, cualquier `ToolCallback` y cualquier `ToolCallbackProvider`. No hace falta declararlas.

### 2. Política (`application.yaml`)

Cada herramienta que el modelo pueda invocar debe tener una entrada. Una herramienta sin entrada se **bloquea**
(denegar por defecto). Ejemplo:

```yaml
promptguard:
  # enabled: false   -> las herramientas siguen registradas, pero sin aplicar la política (baseline)
  modo: enforce      # shadow: evalúa y registra, pero ejecuta igual (ver "Modo sombra")
  clave-procedencia: origen   # metadato del documento que indica su procedencia
  procedencia:       # valor del metadato -> nivel (CONFIABLE, INTERNO_EDITABLE, EXTERNO, DESCONOCIDO)
    CONFIABLE: CONFIABLE
    NO_CONFIABLE: EXTERNO
  confianza-minima-para:       # procedencia mínima de los fragmentos para ejecutar sin confirmar
    ALTA: 3
    MEDIA: 2
  confirmacion-ttl: 5m
  herramientas:
    consultarSaldo:
      sensibilidad: BAJA
      autoridad: ASISTENTE
      resultado-confiable: true          # su salida sirve de fundamento para otros argumentos
      parametros:
        cuenta:
          fuentes: [USUARIO, HERRAMIENTA_CONFIABLE]
    enviarComprobante:
      sensibilidad: MEDIA
      autoridad: ASISTENTE
      intencion: [comprobante, constancia]   # el cliente debe haberlos usado
      parametros:
        destino:
          fuentes: [USUARIO]                 # el correo lo escribió el cliente
    enviarEmail:
      sensibilidad: MEDIA
      autoridad: PROHIBIDA                   # texto libre hacia afuera: no se expone
    reembolsar:
      sensibilidad: ALTA
      autoridad: REQUIERE_HUMANO
```

Por omisión, una herramienta sin `autoridad` es `REQUIERE_HUMANO` y sin `sensibilidad` es `ALTA`: la configuración
falla cerrada.

La política vive en configuración, no en los documentos que recupera el RAG. Un documento no puede ampliar la
autoridad del asistente.

Lo único que el desarrollador de la aplicación agrega además, si quiere, es un listener de `EventoAuditoria` para
persistir o mostrar las decisiones.

### 3. Autorización por usuario (C5, opcional)

C5 solo se exige en las herramientas que declaran `roles` o `recursos`. La aplicación aporta dos piezas pequeñas; PromptGuard
no autentica ni conoce cuentas, tarjetas o pedidos.

```yaml
promptguard:
  herramientas:
    bloquearTarjeta:
      roles: [cliente]          # roles que pueden pedirla (vacío = no se exige rol)
      recursos:
        tarjeta: tarjeta        # parámetro -> tipo de recurso sobre el que el usuario debe tener derecho
```

```java
@Bean ProveedorIdentidad proveedorIdentidad() {          // quién hace la solicitud (p. ej. del token o de Spring Security)
    return request -> Optional.of(new Identidad("ana", Set.of("cliente")));
}

@Bean AutorizacionRecursos autorizacionRecursos() {      // qué recursos le pertenecen
    return (usuario, tipo, valor) -> repositorio.esDe(usuario, tipo, valor);
}
```

Falla cerrado: sin identidad, sin `AutorizacionRecursos`, o sin el argumento a verificar, la herramienta se bloquea.

## Uso

### Confirmaciones pendientes

Cuando la decisión es `CONFIRMAR`, la herramienta no se ejecuta y el modelo recibe un mensaje que le pide no darla por hecha.
La aplicación debe mostrar a la persona **los argumentos reales** y confirmar por su canal autenticado:

```java
AccionPendiente accion = acciones.consultar(id).orElseThrow();   // mostrar accion.herramienta() y accion.argumentos()
String resultado = acciones.confirmar(accion.id());              // se ejecuta una sola vez, dentro del plazo
// acciones.cancelar(id) si la persona la rechaza
```

El starter no expone endpoints HTTP: la confirmación debe pasar por la autenticación de la aplicación.

### Auditoría

Cada decisión se escribe en el log y se publica como `EventoAuditoria` (evento de Spring). Los campos listados en
`promptguard.campos-sensibles` (por defecto correo, destino, cuerpo, etc.) se enmascaran. La aplicación decide si lo persiste.

## Modo sombra

En `modo: shadow` el guard **evalúa y registra, pero ejecuta la herramienta igual**. Sirve para medir cuántas llamadas
legítimas bloquearía antes de activar. **Con herramientas de efecto real (reembolsos, transferencias) el modo sombra ejecuta
la acción que bloquearía.** Usarlo solo contra un entorno de prueba o con herramientas de bajo efecto.

## Estructura

```
src/main/java/ar/edu/utn/frsf/promptguard/
  core/      núcleo puro (sin Spring): política, contexto, identidad, EvaluadorAcciones (C0 a C5)
  spring/    autoconfiguración, propiedades, advisors, envoltorio de ToolCallback, confirmaciones, auditoría
src/test/    pruebas del núcleo (casos del catálogo del baseline, atacante que conoce la política, C5) y del starter
docs/        guion de la exposición y diapositivas de la defensa
```

## Pruebas

```bash
./gradlew test        # Windows: gradlew.bat test
```

La suite incluye pruebas del núcleo (incluido C5), del starter con `ApplicationContextRunner` y una medición de latencia del
evaluador, por lo que tarda varios minutos.

## Resultados

Medidos sobre el baseline `vulnerable-agent-demo`, con tres modelos abiertos y prompt endurecido (detalle, límites y
metodología en `docs/RESULTADOS_PROMPTGUARD.md` de ese repositorio):

| Medida | Sin PromptGuard | Con PromptGuard (`enforce`, C4 estricta) |
|---|---|---|
| Ataques ejecutados (17 ataques × 3 modelos, 1 repetición por caso) | 17 de 51 | 0 de 51 |
| Consultas legítimas bien resueltas (8 × 3 modelos) | 19 de 24 | 22 de 24 |
| Acciones indebidas sin atacante (10 pedidos ambiguos × 3 modelos) | 7 de 30 | 0 de 30 |

- Un atacante que conoce la política rompe la variante `refinada` de C4 (9 de 9 en tres ataques con 3 repeticiones cada uno) y no
  la `estricta` (0 de 9). Por eso `estricta` es el valor por defecto.
- El evaluador tarda menos de 1 milisegundo por decisión.
- Son pocas repeticiones, un dominio y un solo corpus: es una prueba de mecanismo, no una tasa de riesgo en producción.

## Límites

- **Fuera de alcance de esta versión (fases 3 a 5 del diseño):** procedencia en la ingesta (`origen` y `estado` como metadato
  del pipeline), heurística de entrada, capa semántica opcional, control de la respuesta (que el modelo no repita el veneno),
  persistencia de la auditoría y métricas Micrometer.
- **Solo llamadas síncronas.** El advisor implementa `CallAdvisor`; con streaming las herramientas no pasan por el contexto de
  solicitud y se bloquean las que requieren fundamento. Pendiente.
- **C2 es léxica.** Depende de la lista de términos; puede dar falsos negativos y positivos. Por eso ante la duda pide confirmación.
- **C3 compara valores completos.** Un argumento se funda si aparece entero en lo que escribió el cliente (en un correo los
  puntos cuentan; en montos e identificadores no). Un valor que es parte de otro, como un sufijo, no se acepta.
- **C3 no distingue lo que el cliente escribió de lo que pegó**, y C2 no distingue pedir una acción de preguntar cómo se hace.
- **La procedencia es un supuesto.** Si un documento externo se copia a una fuente marcada como confiable, el guard lo respeta.
- **Un atacante que conoce la política** puede explotar los términos de intención o los parámetros permitidos. La evaluación debe incluirlo.
- **Lo que no cubre:** autenticación del cliente (lo hace la aplicación), mentiras sin acción, documentos confiables comprometidos.

## Pendiente

1. Medir C5 con modelos. Hoy se verifica con pruebas unitarias, de integración y con el demo, sin llamar a un LLM.
2. Más modelos, más repeticiones por caso y otros dominios.
3. Control de la respuesta: el guard decide sobre acciones, no sobre lo que el asistente le dice al cliente.
4. Streaming (ver Límites).

## Evaluación sin llamar al modelo

- **`promptguard.c4: estricta | refinada`.** Con `estricta` (por defecto) cualquier fragmento de baja confianza en el contexto
  exige confirmación para las herramientas sensibles. Con `refinada`, C4 se omite cuando la política de la herramienta exige
  intención del cliente (C2) y fundamento de sus argumentos (C3) y ambos pasaron, como propone el diseño. La refinada se
  diseñó mirando datos propios: validarla en una corrida independiente antes de adoptarla.
- **`gradlew replay`.** Re-evalúa con el mismo código las llamadas registradas en corridas en modo `shadow` bajo distintas
  configuraciones (cada regla sola, acumuladas, una menos, C4 estricta o refinada):

```text
gradlew.bat replay --args="<application-promptguard.yaml> <corrida.replay.json>..."
```

  Los `.replay.json` salen de `experimentos/exportar_replay.py` en `vulnerable-agent-demo`. Resultados en
  `vulnerable-agent-demo/docs/RESULTADOS_PROMPTGUARD.md`, sección 9.
