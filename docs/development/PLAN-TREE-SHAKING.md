# PLAN-TREE-SHAKING.md — stdlib por alcançabilidade: o compilador inclui só o que o programa usa

**Dono:** lane PLATAFORMA (frente designada pela mantenedora 11/09; execução na lane development) · **Status:** EM CURSO — **S-1 (T0) ✅ FEITA 12/09** (`ArtifactSize` parser ELF64 puro-Java + `ArtifactSizeTest` com gate anti-inchaço 5% travado nos números medidos + `kof build --print-sizes`; movido de `future/` p/ cá pela regra dos três estados — S-7 manda, "quando S-1 começar") · **Criado:** 12/09 · **Issue:** #97

> **Regra fundamental:** o desenvolvedor declara o que pretende utilizar; o
> compilador inclui **somente** o que for realmente necessário para executar
> o programa. Nada de microgerenciamento de dependências, nada de
> `--include=json.parser`, nada de lista manual para evitar bloat.

Este plano é **análise medida** (código + binários desta sessão, toolchain
cross ativa), não memória. Cada afirmação abaixo tem evidência reproduzível.
**Aceite dado pela mantenedora via issue #97 (12/09)** — o briefing "planejar
ANTES de implementar" foi cumprido (este doc + números medidos) e a issue
aberta com o plano no escopo É o aceite; degraus T0–T1b não mudam contrato de
linguagem (aditivos). **S-1 (T0) ✅ FEITA 12/09 (S-2 em diante, fila abaixo).**

---

## 1. O problema, medido hoje (12/09, `beta-0.4.0` @ `c14c1808`)

Programa mínimo — `main() { println("hello") }` — compilado para cada target:

| Target | Artefato | Tamanho | Símbolos `kof_*` no binário |
|---|---|---|---|
| Native x86_64 | ELF estático | **138.776 B** (`size`: text 75845 / data 19437 / bss 71224) | **605** (strings únicas) |
| Native riscv64 | ELF | **143.832 B** | **254** (`nm`), `.bss` ≈ 260 KB |
| JS | `kof-runtime.mjs` | **173.366 B** + 287 B do programa | o arquivo inteiro |
| JVM | `Main.class` | **425 B** | n/a (ver §2.4) |

O binário "hello" x86_64 contém, verificado por `strings`: `kof_jwt_*`,
`kof_sec_*` (AES-GCM, PBKDF2, BCrypt…), `kof_vk_*` (stubs Vulkan), `kof_web*`
(servidor HTTP), `kof_mq_*`, `kof_channel_*`, `kof_cache_*`, DB, URI,
math-double, random, uuid, observabilidade — **nada disso é alcançável pelo
programa**. É exatamente o cenário que o briefing da mantenedora quer
eliminar, e a tabela de riscos do `PLAN-UNIVERSAL-PLATFORM.md:1307` já o
prevê: *"Stdlib inchada → capability/link por uso"*.

Os números de 11/09 nos E2E cross (riscv 34/0, aarch 34/0) valem para
*correção*; para *tamanho*, nada mudou desde então.

## 2. Estado real da arquitetura (verificado no código, não na doc)

### 2.1 Imports (`CompilerImports.expandKofImports`)

`import kof.json` **não** é um namespace builtin: é resolução por
**diretório** no filesystem do módulo (`moduleRoot/kof/json/*.kf`), com fecho
transitivo por BFS e wildcards (`.java` de usuário importável, PKG006 para o
que não resolve, `--classpath` para dependências externas — §134). Para a
**stdlib embutida** (`kof.math`, `kof.json` como builtin, `kof.security`…), o
import é decorativo: os construtos já resolvem por nome no typer/lowerer
(`KofStd`/`KofMath`/`KofSecurity`…), sem gate por import. Medido: o mesmo
programa `json.decode/encode` compila **com e sem** `import kof.json`
(JVM/Native/JS) — o import não habilita nem inclui nada.

**Consequência para o plano:** "importado ≠ utilizado" já é verdade hoje no
frontend — o import não é a fonte de inclusão. A inclusão acontece **no
backend**, e é unconditional. É lá que o trabalho está.

### 2.2 Native x86_64 — emissão monolítica unconditional

`NativeBackend` (linha 342) emite `NativeRuntime.generateRuntimeAssembly()`
**inteiro** em todo programa: ~90 chamadas `RuntimeXxx.emit(sb)` em sequência
fixa (print, string-ops, list/map/set, json, alloc/GC, concurrency/channel/
scheduler/mq, net, db, security×12, validation, uri, math, strings, encoding,
uuid, random, observability, vk, ui, web, cache, config, log…). Tudo num único
`.section .text`.

### 2.3 Native riscv64/aarch64 — idem, em fatias concatenadas

`NativeArchEmitter:119/248`: `RISCV_RUNTIME_ASM` + `RISCV_STRN002_ASM` +
`RISCV_RUNTIME_ASM_B` (B0…B38) + `RISCV_MAPSET_ASM` — **sempre tudo**. O
aarch64 é o texto riscv traduzido instrução a instrução.

**O precedente que torna o plano realizável:** `NativeRiscvSpawn.usesSpawn
(module)` — scan da IR procurando `KofCall` com `kof_spawn/kof_spawn_result/
kof_await` — **já condiciona** a emissão da fatia spawn
(`if (usesSpawn) nb.emitRiscvSpawn(sb)`). O mecanismo de *análise de
alcançabilidade por varredura da IR + emissão condicional por fatia* existe e
está verde nos testes. Falta generalizar de 1 fatia para todas.

### 2.4 JVM — não inchado por construção

`JvmRuntime.generate` cria um `KofRuntime.java` com **métodos estáticos por
família**; a classe é compilada com o programa e a JVM carrega/resolve
métodos sob demanda (bytecode é lazy por método — `Main.class` do "hello" tem
425 B). `hasRuntimeFn` (29 famílias prefixadas) decide o **dispatch** no
emissor, não a inclusão. O único custo é o `.class` do `KofRuntime`
(métodos não chamados continuam no jar). Prioridade baixa; ver §6.4.

### 2.5 JS — cópia integral do módulo

`JsArtifactWriter:26-62`: escreve `kof-runtime.mjs` concatenando **todas** as
fontes `JsRuntimeUi*.JS` (core, components, widgets, forms, layout, web,
support, security, crypto, validation, stdlib, random, math-double, net, uuid,
**ws** (indent/dedent — S3.3), events). 173 KB por programa. ESM com `export
function` por função — **um bundler/rollup faria tree-shaking de graça**; a
seleção por uso no writer é o caminho sem dependência nova.

### 2.6 Linker — DCE impossível por construção

`NativeAssembler:36-45` chama `ld` sem `--gc-sections`, e o asm é emitido com
`.section .text` pelado (sem `"funcName"` por seção) — logo, mesmo com o flag,
nada seria podável: **1 seção para tudo**. Os `.space` globais (pools) ficam
em `.bss`/`.data` do mesmo jeito. O `-lc` e `-dynamic-linker` são hardcoded
no path dinâmico — o que hoje **impede** o alvo embedded/bare-metal (§6.5).

### 2.7 GC conservador — o acoplamento que a poda precisa respeitar

`RuntimeGc` varre **`kof_heap_root_start .. _end`** como raízes estáticas
(x86: `:50-52`; o marcador de início é emitido no topo de
`generateRuntimeAssembly`). Pool em `.bss`/`.data` de módulo **não incluído**
não tem ponteiros vivos (o módulo nunca roda) — a varredura continua segura
se a poda for por **fatia inteira** (a área podada some do intervalo). Mas
isto precisa de teste próprio (T0), porque um `.data` de runtime **com
inicializadores** (ex.: tabelas de crypto com ponteiros p/ strings) pode se
tornar raiz órfã se a poda separar dados de código de forma errada. A regra
segura: **código e dados de uma fatia entram/saem juntos**.

---

## 3. Estratégia — três camadas que se somam (não se escolhe uma)

```
fonte Kof ──frontend──▶ IR ──alcançabilidade──▶ plano de inclusão ──emissor──▶ .s por fatia
                              (T1a)                                              │
                                                                       ld ── --gc-sections ──▶ binário
                                                                              (T1b, rede de segurança)
```

- **T1a — seleção na origem (fatia).** O compilador varre a IR, coleta os
  nomes `kof_*` chamados (e os `KofCall` sintéticos já nomeados: `kof_json_*`,
  `kof_sec_*`, …), fecha o **fecho transitivo** sobre um *mapa de dependências
  por fatia* e emite só as fatias vivas. É o `usesSpawn` generalizado: o
  mapa de dependências é **dados declarados pelo emissor de cada fatia**
  (ex.: `RuntimeSecurity1` declara `provides: kof_sha256…`, `needs:
  kof_alloc, kof_string_concat…`), nunca string-sniffing no asm.
- **T1b — rede de segurança no linkador.** Cada função emitida ganha
  `.section .text.kof_jwt_create, "ax"` (padrão já usado em asm de
  compiler-generated hoje não existe — é mudança no emissor, não no asm
  handwritten) e `ld --gc-sections`. Pegou o que a análise do T1a errou
  (inclusão a mais), poda no link. Nunca poda o que é necessário:
  `--gc-sections` parte dos símbolos `globl` alcançáveis do `_start`/entry.
- **T1b não substitui T1a:** o `.bss` dos pools é reservado por `.space` em
  corpo de fatia — seção própria resolve, mas o tamanho útil vem de saber
  *quais* pools existem (mq de 64 handles não alocar se não há mq).

## 4. Import ≠ incluído ≠ alcançável — como fica a semântica

O pedido conceitual da mantenedora, traduzido para a arquitetura real:

| Camada | Hoje | Alvo |
|---|---|---|
| `import kof` / `import kof.crypto` (stdlib embutida) | decorativo (builtins resolvem sem import) | **invariante**: import nunca amplia o binário; só habilita nomes |
| import de pacote `.kf` em diretório (`CompilerImports`) | traz o **arquivo inteiro** que importa (BFS fecha o grafo, mas por *arquivo*, não por símbolo) | grão grosso aceitável no T1; o por-símbolo é T2 (mover decls não usadas) |
| IR → backend | `usesSpawn` para 1 fatia; tudo incondicional no resto | fecho transitivo por fatia (T1a) |
| link | monossessão, sem gc | seções por função + `--gc-sections` (T1b) |

## 5. Imports dinâmicos — os três casos, com política

1. **Estático** (99%): chamada nominal — resolvido no typer → `KofCall`
   nomeado → alcançabilidade T1a. Nada muda para o usuário.
2. **Conhecível na compilação** (set finito): `match`/dispatch sobre um
   construto do próprio programa (ex.: supervisor OTP chamando `fabrica.novo()`
   via vtable) — o alvo é o **método da classe**, já no grafo de vtable do
   emissor nativo (`findVirtualMethodIndex`). Vtable de classe do usuário já
   é emitida por classe alcançável; stdlib não tem dispatch por string hoje.
3. **Verdadeiramente dinâmico**: em Kof **só existe via FFI** — `extern` com
   nome de símbolo em string (`ExpressionMethodCallLowerer`, helpers fixos
   `kof_ffi_i`/`kof_ffi_si`/`kof_ffi_dd`). Não é stdlib: o símbolo resolve no
   `-lc`/`.so` do sistema em runtime. Política: os 3 stubs FFI sempre
   incluídos (custo ≈ 0), e nada mais.

**Se um dia** a linguagem ganhar reflexão por string (`sec.getAlgorithm(name)`)
— que hoje **não tem** — a regra do plano é R6: ou o conjunto de alvos é
estático-declarável (register por capability, o compilador inclui o registro
mínimo), ou **diagnóstico de compile-time** no alvo que não pode resolver
dinamicamente (Native/embedded), nunca "inclui tudo" silencioso. Não há
mechanism a preservar: não há mecanismo.

## 6. Degrus implementáveis (ordem = valor/risco)

### T0 — harness de tamanho (PRIMEIRO, sem ele nada é validável)

- `kof build --print-sizes` (CLI, aditivo) → JSON: bytes por seção + contagem
  de símbolos `kof_*` do artefato.
- Teste `ArtifactSizeTest` (kof-cli ou compiler): golden com **tolerância de
  regressão** — o `Hello` nativo tem de cair de 138 KB para ≤ 60 KB na T1a
  completa (meta), ≤ 45 KB na T1a+T1b; JS ≤ 40 KB. Gate: qualquer aumento >5%
  no *hello* ou no *json-only* quebra o build (molde: `ConformanceMatrixDocTest`
  — doc/medida travados por teste; a lição da #89: sem guard, a coisa apodrece).
- Utilidade: é o instrumento que transforma "achei que ficou menor" em prova.

### T1a — fatias vivas no Native (maior valor, zero risco de asm)

1. Parser do mapa: cada emissor de fatia (x86 `runtime/RuntimeXxx`, riscv
   `RtBxx`) passa a declarar `provides[]/needs[]` (const `String[]` no Java do
   emissor — o chain de `NativeRiscvAsm` vira lista de fatias nomeadas).
   Refactor mecânico, precedentes: `RuntimeStrings.emit` e `usesSpawn`.
2. `Reachability`: varre `IRModule` → `KofCall.methodName()` + entrypoints
   obrigatórios (`_start`, `kof_alloc`, `kof_panic`, `kof_print*` quando o
   programa usa print — o print **é** chamado, entra pelo grafo) → BFS no
   mapa → `Set<fatia>` mínima.
3. `NativeBackend`/`NativeArchEmitter`: emitir só `Set<fatia>` (a ordem atual
   dos `.append` preserva-se por subconjunto). GC roots: a varredura
   `root_start.._end` é por rótulos reais do texto emitido — se uma fatia sai,
   o rótulo de dados dela sai junto (regra: código+dados de fatia solidários).
4. Prova por fatia: teste E2E por família (crypto/json/db/mq/web/ui/vk…) com
   programa que usa X e asserção `nm` de que Y **não** está presente —
   exatamente os testes do briefing (`import kof.crypto` + só AES ⇒ sem
   `kof_jwt_*`; só string ⇒ sem crypto).

### T1b — gc-sections no link (depois da T1a verde, risco baixo)

- `.section .text.<sym>, "ax"` por função emitida (helper no emissor x86:
  as linhas `.globl`/rótulo já são conhecidas — um `emitFunctionPrologue(name)`
  centralizado nos ~90 emissores; riscv idem, tradutor repassa).
- `ld --gc-sections` (e manter `-lc` no path dinâmico). `.data`/`.bss` de
  fatia ganham `.section .data.<sym>` idem.
- Risco conhecido: GC varre `root_start.._end` — se o linker reordenar seções
  entre os dois rótulos, o intervalo pode engolir seções mortas (inofensivo:
  zeros) ou perder vivas (perigoso: ponteiro vivo não marcado → UAF). Medida
  de segurança: os dois rótulos viram `.section .gcroots,"a"` primeiro/último
  com `.globl`, e o script de link preserva a ordem — ou (fallback simples)
  o root-scan passa a usar `.koroots` dedicada só com ponteiros estáticos
  reais das fatias emitidas (a regra "código+dados solidários" já foi
  implementada na T1a).

### T2 — JS por família

- `JsArtifactWriter`: registrar o uso por família (o lowering JS já roteia
  por `KofCall` → nome `kofXxx`); emitir `kof-runtime.mjs` só com os blocos
  `export function` das famílias alcançáveis + o core mínimo (println, alloc,
  handle). Precedente: `kof-runtime-io.mjs` **já é condicional** por arquivo.
  Os 173 KB viram ~8–20 KB no hello. Sem bundler novo, sem dependência.

### T3 — embedded/MCU (documentar a rota, NÃO prometer agora)

O alvo "binário pequeno" de MCU esbarra hoje em 3 coisas medidas:
(a) `-lc` + `-dynamic-linker` hardcoded; (b) syscalls Linux diretos na face
riscv (`write`/`mmap`/`futex` — o port para `probe-rs`/semihosting é projeto
próprio); (c) heap bump-pointer + GC mark-sweep (pools `.space`). O plano
declara: **nenhum `profile minimal` sem T1a+T1b prontas**, e mesmo assim
embedded real exige RTOS/bare-metal backend — fica em `future/` sem degrau
agendado. O que T1 entrega para ele hoje: o runtime **subsetável por fatia**
(a arquitetura que impede o monólito), que é exatamente o requisito do
briefing ("a solução não pode depender de runtime monolítico").

### T4 — JVM (baixa prioridade, honestidade)

Sem custo de runtime hoje (lazy por método). O único inchaço é o `KofRuntime`
completo no jar; `jlink`/`ProGuard` resolvem por fora. O plano **não** promete
mínimo-jar; registra como fora-de-escopo com justificativa.

## 7. O que NÃO é decisão deste plano (regra 6 — é da mantenedora)

1. **Formato do mapa de fatias** (Java const vs arquivo de dados) — decisão
   de implementação da lane, livre, sem contrato.
2. **T1b mexer no GC root-scan** — toca mecanismo de GC (congelado): se o
   fallback `.koroots` precisar mudar o invariante atual, é bump/discussão.
3. **Profiles (`--profile minimal/embedded`)**: só *complementam* a análise
   automática (restrição extra de capacidades), nunca substituem. Promover a
   decisão de superfície CLI.
4. **Import de diretório por símbolo** (mover só o usado, não o arquivo):
   muda semântica de side-effect de top-level de pacote `.kf` — decisão.

## 8. DoD (o que fecha este plano) e fila por sessão

**Unidade de entrega** = degrau com teste verde + suíte completa (com o
flag `failure.ignore` da regra de verificação). Ordem de execução da fila
após o aceite dos §T:

1. **S-1 (T0)** ✅ **FEITA 12/09** — `dev.kof.compiler.ArtifactSize` (parser ELF64 puro-Java: mapa seção→bytes + contagem de símbolos `kof_*` DEFINIDOS no `.symtab`; `jsBytes` soma os `.mjs`), teste `ArtifactSizeTest` (3 gates: hello x86 138.928B/627 syms; runtime JS 177.412B; hello riscv 144.000B/258 syms `assumeToolchain`; tolerância +5% UNILATERAL p/ inchaço — encolher é a meta, sabotagem do baseline → FAIL provado) e `kof build --print-sizes` (JSON estável, aditivo — sem flag, build inalterado). Números deste doc reproduzidos por teste automatizado ✔ (651 vs 627: o issue contou `nm` com imports; o harness define "DEFINIDOS no symtab", o que T1a vai derrubar — gate travado na medida do harness).
 2. **S-2 (T1a.1)** ✅ **FEITA 12/09** — mapa de fatias por **REFLEXÃO derivada do fonte de produção** (implementação da lane livre, §7.1: o mapa não é `const` transcrito à mão — `dev.kof.compiler.nat.RuntimeSlices` lê o corpo de `NativeRuntime.generateRuntimeAssembly` e extrai a ordem das 113 chamadas `RuntimeXxx.emitYyy(sb)`; reordenar/inserir/remover no fonte sem atualizar NADA → o teste de paridade quebra. `provides` = `.globl`/labels `kof_*` (comentários `#` riscados), `needs` = referências externas; símbolos não-fatia modelados: préâmbulo GC + `programSideSymbols()` (`kof_super_table`, emitido pelo Main.s). Prova: `NativeRuntimeSliceRegistryTest` 5/5 — concatenação **byte-idêntica** ao `generateRuntimeAssembly()` de produção (mais forte que "bins idênticos": zero mudança no `.s`), 1 dona por símbolo, needs fechados no mapa, e o número que abre S-3: **fechamento do hello (kof-only) = 6 fatias / 14 de 611 símbolos; o precursor `.L`-aware (12/09) unifica as 119 arestas locais e leva o piso real a 10 fatias / 18 símbolos — provando que o kof-only é inseguro** — os ~600 restantes (crypto/web/mq/vk/security…) são exatamente o que a poda tem de alcançar.
 3. **S-3 (T1a.2)** ✅ **FEITA 12/09** — poda x86 por alcançabilidade, **seed por TEXTO do programa** (a correção da cautela medida: `instanceof`/`checkcast`/array emitem `call kof_*` como texto raw em `NativeMethodEmitter:303`, NÃO via `KofCall` — varrer só a IR perderia seeds reais e quebraria o link). `RuntimeSlices.textKofSeeds/textLocalSeeds/keepForProgramText` + `renderSubset(keep)`; `NativeBackend.pruneRuntime` (marca `rtStart/rtEnd` na região da concatenação; no write do `.s` reconstrói: head + subset na MESMA ordem da S-2 + `.section .text` + tail). keep = `mandatoryRoots()` (piso unificado 10/18, da S-2.5) ∪ fecho `.L`-aware. **Propriedades de segurança:** (a) keep-all → texto ORIGINAL byte-idêntico (fallback = pré-S-3); (b) exceção no mapa → runtime completo + aviso (nunca link quebrado silencioso); (c) seed por texto erra só p/ MAIS (literal de usuário contendo `kof_mq_...` super-inclui — binário maior, link válido); falso-negativo de call site real é impossível; (d) tail (init/DB/HTTP/Web/métodos/start) NUNCA é podado — é o programa, não o runtime. **Números medidos:** hello **13/113 fatias, 627→37 syms, 138.928B→32.520B (−77% binário, −94% símbolos)**; só-crypto 15/113 (traz `kof_sec_sha256*`, ZERO json/mq/vk/random); só-json 20/113 (13 `kof_json_*`, ZERO `kof_sec_sha256`); `coll` 23/113. **Prova:** `NativeE2ETest` 64/64 byte-idêntico COM a poda ligada (rodar > medir: os MESMOS ouros x86 saem dos binários podados) + `ArtifactSizeTest` 4/4 com baseline NOVO travado (unilateral; volta a proteger contra regressão a partir de 32.520B/37; floor virou `<100`) + `nativeFamilyAbsenceAfterPrune` (T1a.4: nomes REAIS do mapa — anti-vácuo: sabotado com poda off FAILA, provado) + riscv/aarch 39/39 intactos (o `emit()` deles retorna antes do sítio x86 — S-4 cuida do cross). Suíte 4-módulos **1569/0** (+2 testes). check_500 sem violador novo (NativeBackend já era violador de base, 621→664: extrair `pruneRuntime` p/ classe da poda fica na fila ≤500).
   - **⚠️ DESCOBERTA 12/09 (medida — muda o desenho da BFS):** o runtime tem **119 referências cruzadas a rótulos LOCAIS `.L*`** entre fatias que só funcionam hoje porque tudo é concatenado num único `.s` (ex.: `emit_alloc`/`emit_gc` — fatias 20/22 — referenciam `.Lkof_alloc_count`/`.Lkof_free_*` DEFINIDOS na fatia `memstats` (62); `string_to_long/double` (5/6) + `json_encode` (14) usam `.Lfmt_float/.Lfmt_double` da fatia 3; `string_base` (39) usa `.Lkof_null_str` da 34; as fatias json-decode 16/17 compartilham `.Ljad_f64_*`). O `needs[]` da S-2 rastreia só `kof_*` (globl) → **uma BFS puramente-kof pode podar a fatia-dona de um `.L` lido por fatia viva → `as` quebra (undefined label). SOLUÇÃO (implementada no próprio S-3, precursor já codado em `RuntimeSlices`): a BFS de alcançabilidade opera no grafo UNIDO kof-needs ∪ local-needs** (`localNeeds()` = `.L` referenciados, `localProvides()` = `.L` definidos por linha; `closureFrom(seeds, includeLocal=true)`) — uma fatia que lê um `.L` doutro depende dele mesmo sem chamar um `kof_` seu. O teste-pilar (S-3) EXIGE: para todo conjunto de sementes IR, o fecho .L-aware é sempre um SUPERCONJUNTO do fecho kof — e um caso real (alloc sem memstats) prova que o kof-only é INSUFICIENTE (ver `NativeRuntimeSliceRegistryTest.localLabelEdgesExistAndKofOnlyClosureIsUnsafe`). Agrupar em super-fatias seria a alternativa, mas perde granularidade — a BFS .L-aware é o caminho.
4. **S-4 (T1a.3)** idem riscv64 (aarch64 herda pelo tradutor) + golden dos
   E2E cross existentes (riscv/aarch 34/34) intacto.
5. **S-5 (T1b)** seções por função + `--gc-sections` + proteção do
   root-scan do GC; gate: suíte cross sob qemu + `ArtifactSizeTest` com
   metas novas (hello x86 ≤ 45 KB — **JÁ BATEU na S-3: 32.520B**; o degrau
   x86 do S-5 agora é opcional/extra, o valor real é o cross).
6. **S-6 (T2)** JS por família (writer registra famílias usadas no lowering);
   hello ≤ ~20 KB; `KofJsBrowserE2ETest`/`KofHttpE2ETest`/`SpawnE2ETest` js*
   verdes.
7. **S-7** docs consolidadas (`docs/stdlib-loading.md` ou seção em
   `docs/architecture.md`) + mover este doc para `docs/development/` no
   início da S-1 (regra dos três estados: com código em desenvolvimento,
   não é mais `future/`).

Cada sessão: 1 degrau, commit, DOING.md atualizado no MESMO commit.

## 9. Resultado esperado do briefing → onde este plano responde

| Pedido | Resposta (seção) |
|---|---|
| 1. Como os imports são resolvidos hoje | §2.1 (medido: import de stdlib embutida é decorativo; diretório `.kf` fecha por arquivo; `--classpath` externo §134) |
| 2. Pontos de inclusão excessiva | §1 (números) + §2.2/2.3/2.5/2.6 (emissão unconditional; `ld` sem gc-sections; JS copia tudo) |
| 3. Estratégia de alcançabilidade | §3+§2.3-precedente (generalizar `usesSpawn`: IR→fecho por fatia; mapa declarativo por emissor) |
| 4. Implementação efetiva deste ciclo | este doc + issue #97; **zero código sem aceite §7** — o briefing manda planejar ANTES de implementar; os degraus T0–T1a estão prontos para execução na 1ª sessão com aceite |
| 5. Imports dinâmicos | §5 (os 3 casos; em Kof só FFI é dinâmico; política R6: nunca incluir tudo silencioso) |
| 6. Testes automatizados | §6-T0 (gate de tamanho com tolerância, molde `ConformanceMatrixDocTest`) + T1a.4 (por família: `import kof.crypto`+AES ⇒ ausência de `kof_jwt_*`) |
| 7. Evidência de que amplo não incha | §1 (estado atual, honesto: INCHA) — a meta com as metas por degrau; prova final será o harness T0 verde com os números novos |
| 8. Docs consolidadas | §8-S-7 |
| 9. Depende de trabalho futuro | §6-T3 (embedded real: sem `-lc`, RTOS backend, GC previsível — fora de escopo, rota declarada), §7.3–7.4 (profiles e import por símbolo: decisões da mantenedora), §6-T4 (jar mínimo JVM: fora-de-escopo com justificativa) |

**Não-cascata:** este doc é UM plano, com números medidos e fila executável —
não há outro doc de "auditoria do plano" nem de "meta-plano". O próximo
artefato é código (S-1).
