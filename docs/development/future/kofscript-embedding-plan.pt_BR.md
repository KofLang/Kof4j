[English](kofscript-embedding-plan.md) | [Português](kofscript-embedding-plan.pt_BR.md)

last: none
doing: none-planned
next: spike-0-inventory
location: docs/development/future
state: planned

intent: kofscript-embedding-api

# API de embedding do KofScript — execução controlada em hosts Java

**Gate da regra 6:** este plano propõe um **contrato público** na superfície do
runtime (uma API de embedding) mais um **modelo de capacidade/isolamento** —
ambos são superfícies vizinhas da semântica congelada. Este documento é **só
plano, zero código**. A promoção exige decisão da mantenedora (`D-KOF-EMBED` ou
extensão de um `D-*` existente) e o fluxo de promoção de future
(`docs/development/future/README.pt_BR.md`, `AGENTS.md` §"Future promotion"):
reescrever com `UNDER DEVELOPMENT`, estado real + como terminar, enfileirar no
`roadmap.md` §23, apontar `docs/status.pt_BR.md` para ele, reivindicar no
`DOING.md`.

## Motivação (issue #842)

Uma aplicação host (p.ex. um mod de Minecraft/NeoForge no espírito do
ComputerCraft) quer rodar **Kof escrito pelo jogador** dentro do próprio
processo. O `KofScript` já executa Kof pelo interpretador da IR sem gerar
bytecode JVM por script — essa parte funciona. O gap **não** é "rodar Kof a
partir do Java"; é a ausência de um **contrato público de embedding** que deixe o
host:

1. **controlar a execução** (cooperativa/incremental — nenhum `while (true) {}`
   pode bloquear a thread do host), e
2. **limitar capacidades** (o script não pode alcançar filesystem, rede,
   processos, reflection ou internals do host por conta própria), e
3. **expor APIs próprias** como bindings do host (`computer.print`,
   `redstone.set`, `robot.forward`, …) implementadas pelo host, não pela stdlib
   do Kof.

O caso de uso não é específico de Minecraft: qualquer aplicação Java que embuta
Kof como linguagem de script precisa das mesmas três coisas.

## Inventário — o que existe hoje (medido, não suposto)

| Superfície | Estado hoje |
|---|---|
| API pública do `KofScript` | **só batch**: `eval(String)`, `runFile(Path)` → `RunResult(exitCode, stdout, stderr, success)` (`kof-script/.../KofScript.java`) |
| Executor | `KofScriptExecutor` roda uma unidade compilada **até completar**; sem orçamento por ciclo |
| Execução incremental | **ausente** — nenhum `step`/`pause`/`resume`/orçamento de trabalho/fuel no interpretador |
| Modelo de capacidade/permissão | **ausente** — sem sandbox, sem conjunto de capacidades, sem deny-list |
| Bindings do host | só o `ScriptGlobals` — um `ConcurrentHashMap` **global e estático** (sem escopo de sessão, sem isolamento por execução) |
| Interop Java | total — que é justamente por que o isolamento é necessário para código não confiável |

Ou seja, os três requisitos acima são exatamente o que falta; nada na árvore
atual os fornece.

## Forma proposta (a ser decidida pela mantenedora — não fixada aqui)

> Os nomes abaixo são **ilustrativos**, não uma API congelada. A decisão da regra
> 6 escolhe a superfície final.

### 1. Controle de execução (stepping cooperativo)

Um ponto de entrada incremental que avança o interpretador por uma quantidade
limitada de trabalho e devolve um estado, em vez de rodar até o fim:

- um **orçamento de trabalho** por step (instruções / ops de IR) para que um step
  não faminta a thread do host;
- estados explícitos: `Running` / `Yielded` / `Done` / `Failed`, com o host
  dirigindo o laço (`while (machine.step(budget) == Running) { … }`);
- sem threads Java reais para a concorrência do script — `spawn`/timers dentro de
  um script embutido devem passar pelo **scheduler do host**, não por `Thread`.

### 2. Modelo de capacidades (menor privilégio por padrão)

- um **conjunto de capacidades** concedido pelo host na criação da sessão; o
  script só vê o que foi concedido (filesystem, rede, processo, reflection todos
  **desligados** por padrão);
- builtins que tocam uma capacidade **não concedida** produzem um diagnóstico
  explícito (sem no-op silencioso, sem fallback) — alinhado à regra da plataforma
  "todo domínio não suportado tem um gap code; sem fallback silencioso";
- as superfícies JVM existentes `kof.auth`/`kof.net`/`kof.io` são as fronteiras
  naturais de capacidade a serem gateadas.

### 3. Bindings do host (com escopo, não globais)

- o host registra **funções/objetos para uma sessão**, visíveis só aos scripts
  daquela sessão (substituindo o mapa global `ScriptGlobals` como caminho de
  embedding);
- bindar um objeto do host não pode expor classes Java arbitrárias — só a
  superfície registrada explicitamente (sem "escape para `Class.forName`").

## Como terminar (os itens de trabalho, uma vez promovido)

1. **Spike de inventário** — fixar os pontos de entrada exatos do interpretador
   que precisam virar steppable (laço principal do `KofInterpreter`) e os usos
   atuais do `ScriptGlobals`; escrever o estado medido no plano promovido.
2. **Stepping** — adicionar a entrada de step limitado + máquina de estados ao
   interpretador; teste RED-first: um script `while (true) {}` deve devolver
   `Running` e ser retomável, nunca bloquear o chamador.
3. **Capacidades** — definir o enum de capacidade + o caminho "não concedido →
   diagnóstico"; teste RED-first: um script negado de filesystem recebe um
   diagnóstico nomeado.
4. **Bindings de sessão** — tabela de bindings por sessão + API de registro
   visível ao host; teste RED-first: duas sessões não veem os bindings uma da
   outra.
5. **Docs + exemplo funcional** — um guia de embedding sob `docs/` (quando
   pousar) e uma amostra de host pequena (sem dependência de Minecraft — um host
   Java mínimo).

## Não-objetivos

- Nenhuma API específica de Minecraft/NeoForge no Kof.
- Nenhuma **sintaxe** nova de Kof para isto — é um contrato de runtime voltado ao
  host, não uma superfície de linguagem.
- Nenhum modelo de concorrência com threads reais para scripts embutidos.
- Nenhum escape hatch de macro aberta / reflection.

## Referências

- Issue #842 (`[Feature]: API de embedding do KofScript`).
- `kof-script/src/main/java/dev/kof/script/KofScript.java`,
  `KofScriptExecutor.java`, `ScriptGlobals.java`.
- `docs/philosophy.md` ("What Kof Is NOT"), `AGENTS.md` §"Future promotion".
