[English](kofscript-embedding-guide.md) | [Português](kofscript-embedding-guide.pt_BR.md)

# Embutir Kof num host Java

**Status:** `DISPONÍVEL` — `KofEmbedSession` (`kof-script`), o contrato voltado ao host da issue #842. Plano: [`docs/development/kofscript-embedding-plan.md`](development/kofscript-embedding-plan.md).

Use isto quando uma aplicação Java precisa rodar **Kof autorado pelo jogador/usuário**
dentro do próprio processo: superfície de script de um mod de jogo, um host de
plugins, um motor de regras. O caminho de bytecode JVM por script não é o
problema — o problema é CONTROLE, e esta API é esse controle.

## As três garantias

| Garantia | O que significa | Prova |
|---|---|---|
| **Execução limitada** | O script avança um número limitado de operações de IR por chamada e ESTACIONA num limite de op. `while (true) {}` nunca bloqueia a sua thread. | `KofEmbedSessionTest#anInfiniteLoopNeverBlocksTheHost` |
| **Confinamento de capacidade** | A sessão é construída com um conjunto explícito de concessões (**vazio por padrão**). Uma superfície stdlib atrás de uma capacidade não concedida recusa com `EMBED001`, nomeando a capacidade — nunca um no-op silencioso. | `KofEmbedSessionTest#anUngrantedCapabilityRefusesByName`, `#theGrantOpensTheSurface` |
| **Bindings de sessão** | Valores do host registrados por sessão são visíveis só aos scripts daquela sessão — duas sessões nunca compartilham estado pelo caminho global. | `KofEmbedSessionTest#sessionBindingsAreScopedAndIsolated` |

## Host mínimo

```java
import dev.kof.compiler.KofCapability;
import dev.kof.script.KofEmbedSession;

try (KofEmbedSession session = KofEmbedSession.builder()
        .grant(KofCapability.FILESYSTEM)     // omit = no capability at all
        .bind("host", myHostApi)             // this session's values only
        .start(playerScript)) {

    KofEmbedSession.Status status;
    do {
        // Dá ao script 10.000 operações de IR e RETOMA O CONTROLE.
        status = session.run(10_000);
        // o seu laço segue rodando aqui (renderizar um frame, tratar input, ...)
    } while (status == KofEmbedSession.Status.RUNNING);

    if (status == KofEmbedSession.Status.FAILED) {
        log.warn("script falhou: {}", session.failure());
    } else {
        System.out.print(session.stdout());
    }
}
```

## Limites e não-objetivos

* **Sem sintaxe Kof nova.** É um contrato de runtime voltado ao host; os scripts
  seguem Kof comum.
* **Sem concorrência de thread real para scripts.** Um script executa numa
  thread de trabalho da própria sessão; o host a dirige em fatias.
* **Sem escape por reflexão.** Um binding é um valor que você registrou; o
  script não alcança classes Java arbitrárias por esta API.
* **Quem concede escolhe a superfície.** `KofCapability` é grosso hoje (uma
  constante por área da stdlib); um modelo mais fino é decisão posterior.
* **Não é uma sandbox contra uma JVM maliciosa.** As capacidades barram as
  superfícies da stdlib Kof que o interpretador consulta; não são um security
  manager da JVM.
