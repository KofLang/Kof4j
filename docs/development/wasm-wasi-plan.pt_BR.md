[English](wasm-wasi-plan.md) | [Português](wasm-wasi-plan.pt_BR.md)

# WebAssembly (WASM) + WASI — especificação de implementação futura

> **Estado (19/09): FUTURE — apenas documentação, zero código.** Registrado
> por pedido explícito da mantenedora ("documente a implementação futura de
> WASM/WASI; NÃO implemente"). Não é fila de execução (regra três-estados +
> R12). Esta spec **não muda nada**: nenhum `Target`, backend, runtime ou
> semântica de linguagem.

> **Atualização de decisão (28/09/2026):** as questões técnicas que esta spec
> deixou TBD/DECISION REQUIRED foram **resolvidas** pela mantenedora em
> `DECISIONS.md` §`D-WASM-GO` (`D-WASM-01..09`: backend direto, `Int`=i64,
> unwinding por string lançada, handles+tabela de handles, GC mark-sweep do
> design nativo, env de closure explícito, WASI preview1, wasmtime primeiro,
> concorrência cooperativa). A spec continua **future/zero-código**; a promoção
> é uma-por-vez por `D-FUTURE-PROMOTION`. Não leia os marcadores TBD inline como
> decisões ainda abertas.
>
## Como ler este documento

Toda afirmação carrega exatamente uma marca:

| Marca | Significado |
|---|---|
| **CURRENT** | estado real do código hoje (arquivo citado) |
| **PLANNED** | feature futura, explicitamente *não implementada* |
| **TBD** | decisão aberta — ninguém decidiu; não trate como certa |
| **DECISION REQUIRED** | portão regra 6 — a mantenedora decide antes de implementar |

Nunca escreva "Kof suporta WASM": não suporta (CURRENT: `--target wasm` é
**rejeitado** com o gap honesto `WASM001`).

---

## 1. Objetivo da feature (PLANNED)

Adicionar **WASM** e **WASI** como targets oficiais do Kof:

```
Kof source → compilador Kof → IR Kof → backend WASM → module.wasm
```

O desenvolvedor escreve Kof e escolhe WebAssembly sem escrever JavaScript,
C ou linguagem intermediária. Kof **não** vira transpiler: WASM é
especificado como **backend de primeira classe**, par de JVM / Native / JS
/ Script (CURRENT enum: `Target.java` — `JVM, NATIVE, NATIVE_RISCV64,
NATIVE_AARCH64, JS, ANDROID, SCRIPT`; sem `WASM`).

## 2. WASM × WASI — camadas diferentes

```
WASM = o formato de módulo; execução controlada por um host (browser, runtime)
WASI = WASM + interface de sistema (fs, stdio, clocks, random, …) conforme a
       especificação WASI — camada SOBRE o módulo, não "WASM de servidor"
```

```
Kof → módulo WASM → Host                      (browser: glue JS + Web APIs)
Kof → módulo WASM → interface WASI → host/runtime WASI
```

## 3. Arquitetura futura

```
                          Kof
                            │
                   Frontend + IR (CURRENT: parser → SemanticAnalyzer →
                   lowerers; IR compartilhada já provada pelo Target.SCRIPT,
                   que interpreta a IR via CompilerPipeline.interpret)
                            │
     ┌──────────┬───────────┼────────────┬───────────┬──────────┐
     │          │           │            │           │          │
    JVM      Native         JS          WASM      Script     Android
 (ASM bytecode)(asm x86-64)(dev.kof.   ┌────┴────┐  (IR      (ASM→APK,
                       compiler/js)  Browser    WASI         §330)
                                     host      module
```

**Pontos de extensão identificados no código real** (investigar antes de
implementar — não assumir mais deste diagrama do que está escrito aqui):

- `Target.java` — enum + `isNative()`/`nativeArch()` (PLANNED: `WASM`,
  `WASI` — **TBD: uma entrada por host ou `WASM` + flag de host**;
  DECISION REQUIRED).
- `TargetMatrix.java` — **CURRENT**: já mapeia pedido `wasm`/`kofwebasm`
  ao gap honesto `WASM001` ("planejado Fase 6 — rejeitado com gap honesto,
  nunca silencioso"). O código está reservado para este futuro (§14).
- `dev.kof.compiler.js` (`JsBackend`, `JsArtifactWriter`, …) — precedente
  estrutural para um pacote `dev.kof.compiler.wasm`.
- CLI: `dev.kof.cli` mapeia `"jvm"|"native"|"js"` para `Target` (ex.:
  `BenchDiscovery.java`); braço `wasm`/`wasi` é PLANNED (§23).
- Sistema de capabilities da stdlib — CURRENT: gates por `Target`
  (`supportedOn`) + códigos de gap `XXX00x` + matriz
  `docs/backend-parity.md` (§14).

## 4. Decisão arquitetural principal (REQUISITO)

```
Kof → WASM deve ser compilação DIRETA pela infraestrutura interna do
compilador (parser → typer → lowerer → emissor WASM), produzindo o binário
.wasm ele mesmo.
```

Rejeitado como arquitetura principal: `Kof → JavaScript → WASM` (amarra o
produto a toolchain JS e herda semântica JS) e `Kof → C → WASM`
(mediatizado por clang; perde tipos/IR do Kof, duplica a história da
toolchain C). O que viabiliza o backend próprio: o **front-half**
(lexer/parser/AST/`SemanticAnalyzer`/lowerers já target-neutrais — prova:
`SCRIPT` roda na IR compartilhada sem backend) e os **contratos de shape de
runtime** (GC, strings, dispatch) já pagos em JVM+Native+JS. **TBD**: se o
emissor consome a IR de lowering atual direto ou uma IR intermediária
orientada a WASM — decidir na Fase 0 (§31).

## 5. Target registry

```
CURRENT (real hoje):
  kof compile/build/run → jvm | native (native.risc/arm cross) | js | script | --android
  kof --target wasm|wasi → REJEITADO hoje com WASM001 (gap honesto, TargetMatrix) — nunca silencioso

PLANNED (só esta doc — não documentar como existente):
  kof build --target=wasm / --target=wasi
  kof check --target=wasm / --target=wasi
  kof run   --target=wasm   (precisa de host — §19)
  kof run   --target=wasi   (precisa de runtime WASI — §19)
```

Formato da flag (`--target=x` vs uso atual) medido no `dev.kof.cli` na
implementação — **TBD**.

## 6. Backend WASM (spec futura)

O emissor deve produzir módulo core-WASM válido cobrindo ao menos:
`Type · Function · Table · Memory · Global · Export · Import · Code · Data`.

Mapeamento `conceito Kof → IR → WASM`:

| Conceito Kof | Representação WASM | Status |
|---|---|---|
| função | função WASM (type + code) | PLANNED |
| local | local WASM | PLANNED |
| control flow (`if`/`while`/`for`/`switch`) | `block`/`loop`/`if`/`br`/`br_table` | PLANNED |
| `Int` | `i64` ou `i32`? | **TBD — segue a spec numérica do Kof, nunca arbitráio** |
| `Float`/`Double` | `f32`/`f64` | PLANNED |
| referência (objeto/record/String) | `i32` offset na linear memory vs GC `ref` | **DECISION REQUIRED (§8)** |
| chamada ao host | `import` + adaptador JS/WASI | PLANNED |

Mapeamento que dependa de decisão aberta fica **TBD** — não inventar.

## 7. Runtime WASM

Runtime mínimo avaliado contra o runtime Nativo existente (allocator,
memória, strings, arrays, objetos, erros de runtime/panic, GC). Perguntas
abertas da Fase 0 (todas **TBD**): o que é reutilizável do Native
(source-level, não binário); ABI necessária (a ABI Native atual é
SysV-shaped x86-64; WASM é nova); representação de objetos Kof (a história
do slot de tamanho de lista tem seu próprio §252); administração de
memória (§8).

## 8. Memória linear

```
objeto Kof → runtime Kof → linear memory WASM
```

Pontos de investigação (nenhuma estratégia decidida): allocator
(bump-then-GC?), alignment, object layout, representação de ponteiro
(`i32` offset vs `i64` vs `ref` gerenciado), growth (`memory.grow`), free,
integração com GC, root tracking. Nada está comprometido — afirmar hoje
seria ficção.

## 9. Garbage collector

CURRENT do GC Kof: **JVM** delega ao GC da JVM; **Native** tem decomposição
mark-sweep **em andamento** no roadmap (G-0 riscv ✅ `356f33b9`; G-1..G-5
pendentes — `docs/development/roadmap.md`); auto-GC foi desabilitado após
um hang (nota do roadmap). WASM deve se acoplar a **esse** design, não
forkar o seu.

Dependências documentadas: safe points, root maps, referências de objeto,
ciclos alocação/coleção, metadados de runtime.

> **Invariantes: o backend WASM NÃO pode criar uma segunda semântica de
> gerenciamento de memória independente do Kof.** Se o GC atual não está
> pronto, isso é dependência explícita (§30), não motivo para inventar
> outro GC.

## 10. Strings

A representação futura de `String` em WASM deve **seguir a spec Kof
existente** (o que um `String` observa — length, indexação, comparação,
concatenação — é definido pela linguagem, não pelo backend). Cobrir:
decisão UTF-8 vs UTF-16 de armazenamento (**TBD — checar a semântica de
strings em `docs/` primeiro; não escolher arbitrariamente**), meaning de
length, allocation, concatenação, comparação (conteúdo `==`, congelada),
indexing/slicing, custo de conversão no host.

## 11. Records, objetos e classes

Mapeamento futuro a investigar: records (imutáveis — layout estático),
classes (campos mutáveis), interfaces, herança, **dispatch virtual**
(vtable? switch-on-type-tag? — **TBD**; o backend Native já escolheu o
esquema dele — estudá-lo). A semântica Kof deve ser preservada; abstrações
Kof **não** se traduzem automaticamente em abstrações JavaScript
(regras 8/9: Kof não é JS).

## 12. Closures

Implementação futura cobre: capture, environment, lifetime, allocation,
referência de função (calls indiretas via `table` WASM ou convenção
closure-record — **TBD**), closures aninhadas. **Seção de riscos**: closure
é o feature mais sensível a layout de memória — depende de §8/§9/§11
decididos em conjunto; a semântica de capture por referência deve bater
com os outros quatro targets (congelada — Freeze §6).

## 13. WASI

Superfície de integração: `stdin`, `stdout`, `stderr`, `arguments`,
`environ`, `clock`, `random`, `filesystem`; demais capabilities conforme a
versão escolhida.

```
Versão WASI: TBD — re-verificar o estado da spec upstream na hora de
implementar (era preview1 vs 0.2/component-model); nenhuma versão é
assumida aqui.
```

## 14. Capabilities

Integra com o sistema **CURRENT** (`supportedOn` por target + códigos
`XXX00x` + matriz `docs/backend-parity.md`). `WASM001` já existe no
`TargetMatrix` como rejeição honesta; a implementação precisará de uma
família de códigos por API não suportada (R6). Matriz futura — valores só
onde há decisão real (`✓ ~ - ?`):

```
                     JVM    Native  JS    WASM  WASI
strings/records      ✓      ✓       ✓      ?     ?
filesystem (kof.io)  ✓      ✓       -      -     ~
browser APIs         -      -       ✓      ~     -
clock                ✓      ✓       ✓      ?     ?
random               ✓      ✓       ✓      ?     ?
process (kof.process)✓      PROC001 ✓*     ?     ?
networking           ✓      ~       ?      -     ?
```

(`-` não aplicável / `~` parcial / `?` TBD — nada inventado; linha JS
`process` reflete `PROC001` em spawn/pipeline.)

> **Regra arquitetural (existente, R6): API indisponível num target deve
> produzir diagnóstico explícito quando conhecível em compile time. Sem
> silent stubs (Q7).**

## 15. Browser WASM

```
Kof → módulo WASM → host Browser → Web APIs (glue JS)
```

Distinto de KofJS: **KofJS** = target JavaScript (emissor em
`dev.kof.compiler.js`); **KofWASM** = target WebAssembly + host JS fino.
Bridge a documentar: DOM, eventos, `fetch`, storage, canvas, Web APIs —
cada uma é host integration, e cada gap voltado ao Kof ganha código (§14).
(Interage com `qrcode-wasm-plan.md` — plano de features QR+web — e com
`D-UI-SCOPE`: mudanças de regra do kof.ui pertencem a KofJS hoje e ao Kof
WebASM quando `Target.WASM` existir, nunca JVM/Native.)

## 16. Interop JavaScript

Separar **WASM core** (sem dependência de JS para rodar lógica Kof) de
**host integration específica de browser** (glue JS exigido pelo
browser). JS é *capability/host integration* quando necessário — nunca
dependência obrigatória para todo código Kof WASM. Mecanismo (gluegen?
wasm-bindgen-like? adaptador à mão?) — **TBD / DECISION REQUIRED**.

## 17. KofUI

```
KofUI
 ├── KofJS   (target UI web CURRENT)
 └── KofWASM (segundo target UI futuro)
```

Intenção: uma API conceitual de UI, hosts diferentes. **Não** duplicar a
API. Dividir: **abstração compartilhada** (intenta declarada — layout,
widgets, eventos) vs **implementação backend/host** (chamadas DOM, bridge
wasm). Regra 9 vale: código Kof declara intent; HTML/CSS nunca vaza para
código de usuário — WASM não é exceção.

## 18. WASI para aplicações (PLANNED)

CLI, servidores, edge, sandbox, plugins, embedded — futuros **possíveis**,
não garantias iniciais. WASI permite rodar Kof compilado fora do browser
em ambientes compatíveis; o que entra na primeira leva é escopo Fase 4+.

## 19. Host e runtime

`.wasm` é um **módulo**; rodar exige sempre um **host/runtime**. Registrar
para decisão (tudo **TBD / DECISION REQUIRED**):

```
runtime oficial de desenvolvimento: TBD
WASI runtime(s) suportados:         TBD (avaliar wasmtime/wasmer-like na
                                    hora; nenhuma ferramenta externa é
                                    adotada por esta doc)
descoberta de runtime (kof run):    TBD — diagnóstico honesto se ausente (R6),
                                    nunca fallback silencioso (Freeze §6)
```

> Kof não se acopla permanentemente a um runtime externo específico sem
> decisão arquitetural (R9 vale para *bibliotecas*, não para virar refém
> de um runtime).

## 20. Concorrência

Semântica congelada do Kof: `spawn`/`await` (Freeze §6). Análise futura:

- browser WASM: event loop single-threaded do host → mapeamento de `spawn`
  (**TBD**; worker threads = host-gated);
- `wasi-threads` / SharedArrayBuffer+atomics: não são WASM baseline —
  **TBD**, capability-gated;
- channels/scheduler sobre execução cooperativa;
- o modelo pthread de Native/JVM **não** pode ser assumido portável.

## 21. Networking

Documentar em separado, sem API falsa:

```
Browser WASM → fetch/WebSocket via bridge do host (glue JS)   PLANNED/TBD
WASI         → wasi-sockets quando spec+host suportarem        TBD
Native       → estado real atual (gaps na matriz)              CURRENT
JVM          → kof.http via JVM                                CURRENT
```

Registrar gaps explicitamente (R6) em vez de criar APIs que fingem andar.

## 22. Modules / imports

`import foo` continua sendo Kof independentemente do target — **nenhum
sistema de módulos exclusivo de WASM**. Questões futuras: link de múltiplos
módulos vs módulo único + `import`/`export`; fronteiras de módulo; o que
vira export WASM; dead-code elimination; empacotamento de dependências;
implicações do component-model (**TBD**).

## 23. CLI e build system

O build futuro deve tratar `wasm`/`wasi` como qualquer target: seleção,
output (`module.wasm` + glue do host — forma TBD), resolução de runtime,
linking, configuração de host, flags de capability. Marcar todo comando
como **proposta** (§5). O registro `koffing`/`deps` (CURRENT 1.5.x) ganha
empacotamento wasm-aware quando o ecossistema precisar — **TBD**.

## 24. WAT / debugging

Ferramentas futuras (PLANNED, sem promessa):

```bash
kof build --target=wasm --emit=wat   # proposta — forma texto p/ debug do backend
```

Pipeline de debug source-level (`Kof → WASM → debug info → devtools`):
suporte a DWARF/custom-name-section em WASM é **TBD**; o precedente de
source map V3 do backend JS (landado 01/09) é o modelo a estudar.

## 25. Conformance

Ligado à suíte futura de conformance (`docs/bugs-and-gaps/conformance-matrix.md`
é a matriz living CURRENT; layout `tests/conformance/{language,semantics,
stdlib,jvm,native,js,wasm,wasi}` é proposta):

> Validar **equivalência semântica** entre targets quando as
> **capabilities são equivalentes**. Nunca exigir binários idênticos.

## 26. Estratégia de testes

```
Compiler tests    : AST → IR → WASM (goldens de .wasm/.wat)
Validation tests  : módulo gerado passa num validator wasm
Runtime tests     : executa → stdout/exit esperados (golden medido — Q3)
WASI tests        : roda sob o host WASI escolhido → pins de comportamento
Cross-target      : mesmo programa → JVM/Native/JS/WASM(/WASI) quando as
                    capabilities permitem; divergência = gap diagnosticado
                    ou bug, nunca silêncio
```

Guardas honestas (`assumeTrue` + toolchain ausente ≠ red — §"Verification
loop").

## 27. Fuzzing

Fuzz/property testing é **obrigatório** para o backend WASM — gerar um
`.wasm` que *existe* não é gerar um `.wasm` *semanticamente correto*.
Superfícies prioritárias: control flow, **stack typing** (o validator WASM
é o adversário), assinaturas de função, locals, closures, ops de memória,
strings, object layout. Alimenta o mesmo programa de correctness já usado
no Native (família `ArrayBoundsDeepStressTest`).

## 28. Performance

Benchmarks futuros (lista, **sem promessa**): startup, custo de chamada,
aritmética inteira e float, loops, arrays, strings, alocação, JSON.
Comparar depois JVM / Native / JS / WASM / WASI com a tool de bench
existente (`BenchDiscovery` é CURRENT).

## 29. Segurança

Documentar: sandbox, capabilities, fronteiras do host, isolamento de
memória, acesso a filesystem, acesso a rede — WASI em especial.

> Meta de design: código Kof compilado para WASI não obtém acesso
> arbitrário ao SO senão através das capabilities declaradas do host.
> (R11: defesa primeiro — nada security-sensitive é caseiro neste backend.)

## 30. Dependências / pré-requisitos

Só o que a análise do código justifica:

| Dependência | Por quê | Status |
|---|---|---|
| GC Native concluído (roadmap G-1..G-5) | invariante §9 — uma semântica de memória | aberto |
| Estabilidade da IR | emissor WASM é consumidor novo da IR de lowering | SCRIPT já consome — risco baixo |
| `TargetMatrix`/capabilities | estender, não reinventar | CURRENT |
| Decisão D-UI-SCOPE | UI = KofJS/KofWebASM apenas | ratificada `95002c50` |
| Suíte de conformance | portão de claims de paridade | em evolução |
| Split da abstração KofUI (§17) | compartilhado vs host impl. | **TBD** |

## 31. Fases futuras

Cada fase: objetivo · dependências · componentes · testes · critérios.

```
Fase 0  Arquitetura / ABI   — decidir forma da IR (§4), memória (§8), versão
        WASI (§13), runtime (§19); componentes: esta doc + entradas em
        DECISIONS.md; testes: nenhum (design); conclui quando cada TBD acima
        tiver decisão da mantenedora.
Fase 1  Backend WASM mínimo — código straight-line + funções + control flow;
        validation tests.
Fase 2  Runtime + memória   — allocator/layout conforme Fase 0; goldens de
        runtime num host.
Fase 3  Paridade de linguagem — records/classes/dispatch/closures/strings;
        goldens cross-target; fuzzing LIGADO.
Fase 4  WASI                — versão + host da Fase 0; linhas de capabilities
        da stdlib; gaps honestos no resto.
Fase 5  Host Browser        — glue (§16), bridge DOM/eventos (§15).
Fase 6  Integração KofUI    — segundo alvo do D-UI-SCOPE navega.
Fase 7  Conformance         — suíte §25 verde sobre as capabilities declaradas.
```

## 32. Release gates

Esta doc sai de `docs/development/future/` para `docs/` **somente** com a
implementação real. Gerar `.wasm` **não** é suficiente:

```
[ ] backend real (sem roundtrip JS/C — requisito §4)
[ ] módulos válidos (validator-clean)  [ ] runtime ok  [ ] esquema de memória
[ ] strings  [ ] paridade de linguagem (4 targets existentes ≡ WASM onde há
    capability)
[ ] WASI     [ ] linhas da matriz de capabilities honestas (sem silent stub
    — R6/Q7)
[ ] testes de execução + conformance verdes  [ ] docs (learn/, training/idioms)
    atualizadas
```

## 33. Relação com o roadmap

- A fila transversal de `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md`
  lista **WASM** como capacidade (série X) — esta doc é a spec profunda
  dela; a *ordem* obedece **R12** (estágio SYSTEMS fecha primeiro).
- `roadmap.md` §23 é o único plano ordenado — promoção de `future/` exige
  decisão da mantenedora registrada em `DECISIONS.md` (regra 6), que então
  cria a fila no §23.
- Dependências reais upstream: GC Native (G-1..G-5), maturidade da spec
  WASM/WASI (§13), host escolhido (§19) — sem lista artificial de features.

## 34. Registro de decisões

```
Decisão: backend direto (sem roundtrip JS/C)      Status: REQUISITO (esta doc,
        registrado 19/09; implementação ainda futura)
Decisão: código de gap WASM001 reservado          Status: CURRENT (TargetMatrix)
Decisão: D-UI-SCOPE (UI = KofJS/KofWebASM)        Status: DECIDIDA (95002c50)
Decisão: versão WASI                              Status: TBD
Decisão: forma do enum (WASM+WASI vs WASM+flag)   Status: TBD — DECISION REQUIRED
Decisão: representação ponteiro/GC-ref            Status: TBD — DECISION REQUIRED
Decisão: runtime oficial / hosts suportados       Status: TBD — DECISION REQUIRED
Decisão: mecanismo de glue JS                     Status: TBD
Decisão: modelo de concorrência em WASM           Status: TBD — spawn/await
        congelados (Freeze §6) o restringem (regra 6)
```

## 35. Organização dos arquivos

A tarefa de implementação propunha pasta `wasm-wasi/`; a **convenção do
repositório vence** (§35 da própria tarefa): `docs/development/future/` usa
pares planos EN + PT — este único par é o mínimo que mantém as 38 seções
endereçáveis. Se a feature for promovida, a divisão em `wasm-backend.md` /
`runtime.md` / `wasi.md` etc. acontece então, em `docs/`.

## 36. Documentos relacionados

- `qrcode-wasm-plan.md` / `.pt_BR.md` — estratégia de **features**
  QR + KofWasm (esta doc é o counterpart **backend** mais profundo).
- `DECOMPILER.md`, `TRANSLATOR.md` (nesta pasta) — a direção *reversa*
  (WASM → Kof), independente desta.
- `docs/backend-parity.md` — a matriz living que esta spec estende no
  futuro (nunca editar linhas de WASM antes de existir código — regra
  CURRENT: `WASM001` permanece a rejeição honesta).
- `docs/native-multiarch.md`, `docs/architecture/compiler-architecture.md`
  — precedentes de backend (cross-arch Native = o padrão de plumbing que
  entradas de Target seguem).

**Nenhuma implementação realizada. Apenas documentação.**
