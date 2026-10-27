[English](debug-adapter.md) | [Português](debug-adapter.pt_BR.md)

# DEBUG-ADAPTER.md — kof-debug (Debug Adapter DAP)

**Status:** JVM (JDWP cru, sem jdk.jdi) + NATIVE (console gdb + DAP↔GDB/MI, X7-3/X7-4) implementados e validados — `kof debug --dap --target native` faz a ponte DAP↔gdb/MI2 real (`KofGdbMi` + `KofDebugNativeDap`); `stackTrace`/breakpoints sempre mostram o `.kf`; sem gdb = erro DAP honesto nomeando a ferramenta (R6); JS = recusa honesta (o alvo roda no engine EMBUTIDO — não há node/inspector para anexar). **20/09 (X7-5):** o attach é REAL no JVM (`--dap --attach <pid>`, JDWP cru numa VM viva) e no Native (`--dap --attach <pid>`, gdb `-p`); o cliente JVM foi reconstruído contra o wire do JDK 25 (`known-bugs.md §376`) e as conversas completas de launch/attach agora têm testes E2E (`KofDebugJvmTest`, `KofDebugAttachTest`). **20/09 (Fase 7):** JVM `next`/`stepIn`/`stepOut` + `evaluate` (`KofDebugJvmStepTest`) e `pause` + `setExceptionBreakpoints` (`KofDebugJvmExceptionTest`); Native `pause` + `setExceptionBreakpoints` (`KofDebugNativeDapTest`)
**Data:** 27 de agosto de 2026 (atualizada 20/09 com as faces Native)
**Versão:** 0.6.7 (7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Objetivo

Componente `kof-debug` que expõe a execução Kof via **DAP** (Debug Adapter
Protocol) — o mesmo protocolo dos editores modernos (VS Code, Neovim,
IntelliJ, Kof Editor).

Não criar protocolo proprietário.

## 2. Responsabilidades

- iniciar programas (`launch`);
- anexar a processos (`attach` — ✅ 20/09: JVM `--dap --attach <pid>` e Native `--dap --attach <pid>`; JS = gap honesto);
- controle de execução: continue, pause, step over/into/out, terminate (`restart` **não** é implementado — ver §3.3);
- breakpoints (source; depois conditional, hit count, exception);
- stack traces, scopes, locals, arguments, campos;
- eventos de exceção;
- inspeção de variáveis com tipos Kof;
- avaliação de expressões (Native ✅ X7-4; JVM pendente — com type system, nunca Java/JS cru).

## 3. Interface

```text
kof-debug (DAP over stdio — Content-Length framing)
    ↓
JVM: launch java -agentlib:jdwp + JDWP client (raw wire protocol)
Native: build do ELF com DWARF + gdb (console: `--target native` com `--break`/`--output`;
        editor: `--dap --target native` traduz DAP -> GDB/MI 2, `KofGdbMi`/`KofDebugNativeDap`)  [✅ 20/09]
JS: recusa honesta — o alvo JS roda no engine Graal embutido; não há
    node/inspector para lançar ou anexar (roadmap §19.5 face 7 segue aberta para um futuro
    inspector do engine, nunca uma ponte falsa)
```

O CLI (`kof debug`) é apenas uma interface — a lógica vive no adaptador.
O cliente JDWP é implementado sobre o **wire protocol cru** (sem
dependência do módulo `jdk.jdi`) para manter o tooling autocontido.

## 3.1 Fluxo DAP implementado (JVM)

```text
initialize            → capabilities (configurationDone, terminate)
launch                → compila (JVM + debug info), porta livre,
                        java -agentlib:jdwp=transport=dt_socket,server=y,
                        suspend=y,address=<porta>, conecta e registra o
                        ClassPrepare de Default.Main (suspend ALL)
setBreakpoints        → registra as linhas Kof (aplicadas no ClassPrepare)
setExceptionBreakpoints → EventRequest.Set kind 4 + modificador ExceptionOnly 8
                        (refType 0 = todas; faces caught/uncaught)
configurationDone     → VM.Resume
pause                 → ThreadReference.Suspend (11,2) em TODA thread de
                        USUÁRIO — as threads do próprio agente JDWP (nome começa
                        com "JDWP") são puladas: suspender a thread de transporte
                        congela o protocolo (medido). `stopped` com reason `pause`
[evento] stopped      → breakpoint atingido (thread + motivo)
stackTrace            → frames Kof: nome da função, arquivo, linha
continue              → VM.Resume
disconnect/terminate  → VM.Dispose + kill do processo + limpeza
```

## 3.1b Fluxo DAP implementado (Native, via GDB/MI — `--dap --target native`)

```text
initialize            → capacidades (configurationDone, terminate)
launch                → compila NATIVE com debug info (DWARF: line table +
                        DIEs — X7-1/X7-2), sobe `gdb -q --interp mi2` com
                        `directory <dir da fonte>`; gdb ausente = success:false
                        nomeando a ferramenta (nunca stack no meio do stream)
setBreakpoints        → -break-insert -f -- <file.kf>:<line> (verified a partir
                        da linha real que o DWARF resolveu)
setExceptionBreakpoints → -break-insert -f -- kof_throw_string (a cadeia de throw
                        do próprio runtime Kof, NÃO exceções C++, então o
                        catch-throw do gdb não se aplica); o refinamento
                        caught/uncaught é só do JVM → `verified:false` honesto (R6)
configurationDone     → -exec-run --all
pause                 → -exec-interrupt --all → `stopped` com reason `pause`
[evento] *stopped     → DAP stopped (breakpoint-hit/entry/end-stepping mapeados);
                        exit-code = exited + terminated
stackTrace            → -stack-list-frames; source.path é SEMPRE o .kf — o
                        ponto inteiro da frente (o editor nunca vê asm)
variables             → -stack-list-variables --simple-values sobre os DIEs
                        DW_TAG_variable reais (nome + DW_OP_fbreg + DW_AT_type)
                        que o compilador já emite nas 3 arquiteturas nativas —
                        medido 20/09 com objdump no ELF/.s; temporários do
                        lowering (tmp/cap/lambda$) ficam FORA das DIEs, o
                        editor só vê nome Kof
evaluate              → -data-evaluate-expression; símbolo inexistente = o erro
                        do gdb repassado, nunca valor inventado (R6)
disconnect/terminate  → -gdb-exit + kill + limpeza do build dir
```

Detalhe de transporte MI que custou uma sessão de debug (20/09): os registros
vêm com TOKEN como PREFIXO (`2^done,...`) — o TIPO do registro é o primeiro
caractere NÃO-dígito; classificar pelo char 0 descarta silenciosamente toda
resposta síncrona (elas só aparecem como timeouts de 5s).

## 3.2 Particularidades do JDWP (JDK 25) descobertas na implementação

- event kinds do JDK 25: `VMStart=90`, `VMDeath=99`, `ClassPrepare=8`
  (os valores clássicos do spec — 0, 15, 6 — não são usados pelo HotSpot);
- `ClassMatch` é o modifier **5** (o modifier 1 é `Count` — um erro aqui
  faz o request ser aceito mas o evento nunca disparar);
- `LocationOnly` é o modifier **7**, com o location `tag(1) + typeID +
  methodID + codeIndex` (o tag é obrigatório — sem ele o JVM responde
  `INVALID_OBJECT`);
- `Method.LineTable` retorna `[codeIndex(long), lineCode(int)]` por
  entrada (ordem long/line, não line/codeIndex);
- `ReferenceType.Methods` retorna `methodID + name + signature +
  modifiers` (4 campos);
- `ThreadReference` é o command set **11** (`Frames`=6, `FrameCount`=7);
  `StackFrame` é o **16** (`GetValues`=1); `StringReference` é o **10**
  (`Value`=1) (medido contra `jdk.jdi/.../JDWP.java` do `src.zip` da própria JDK);
- o HotSpot rejeita `maxFrames` MAIOR QUE O TAMANHO REAL do stack em
  `ThreadReference.Frames` com `INVALID_LENGTH` (504) — pergunte o
  `FrameCount` primeiro e corte (não existe limite de "5 frames");
- o handler de eventos roda fora do event loop (dispatch em thread) —
  comandos JDWP emitidos pelo handler precisam do loop para receber
  replies (sem isso: deadlock de timeout);
- eventos `Composite` têm `suspendPolicy + eventCount` antes dos kinds, e
  cada evento é **`[kind (byte)][requestID (int)]`** — kind PRIMEIRO
  (JDWP.java 7827; ler o requestID antes desloca o corpo inteiro);
- `IDSizes` responde **5 tamanhos, não 6** no JDK 25+ (`argIDSize` saiu);
  ler o 6º int rouba 4 bytes do próximo pacote e dessincroniza o stream
  inteiro desde o handshake (medido byte a byte, §376);
- `VM.ClassesBySignature (1,2)` está QUEBRADO no JDK 25.0.4 (responde
  `count=0` + lixo e trava o comando seguinte; o `jdb` nunca o usa) —
  resolva classes via `VM.Classes (1,3)` ([tag][ref][signature][status]);
- `Method.VariableTable (6,2)` responde `{argWords, slotCount,
  slots[start(long), name, sig, length, slot]}` — NÃO há lista de
  argumentos; chamá-lo num frame nativo devolve `NATIVE_METHOD` (511) —
  trate isso, nunca engula os outros.
- `Method.LineTable (6,1)` / `Method.VariableTable (6,2)` num frame nativo ou
  abstrato devolve `NATIVE_METHOD` (511, medido no frame do topo `Thread.sleep`
  de uma thread pausada). Um stack trace deve reportar ESSE frame como
  desconhecido (nome `?`, linha `-1`) e manter os frames Kof — abortar o
  `stackTrace` inteiro porque um frame da JDK não tem debug info matava a sessão.

## 3.3 Limites atuais (pós-X7-5)

- `stackTrace` retorna a profundidade pedida (cortada pelo `FrameCount`)
  com nome do método e linha Kof reais por frame;
- `scopes`/`variables` retornam **locals reais por frame** (VariableTable +
  `StackFrame.GetValues`, formatados pelo tipo Kof);
- `verified: false` só até a classe carregar — no `ClassPrepare` o
  breakpoint é posicionado via LineTable e os hits disparam `stopped`;
- `next`/`stepIn`/`stepOut` — ✅ JVM 20/09 (pedido JDWP `SingleStep`, evento
  kind 1 + modificador Step kind 10: size LINE, depth over/into/out; o pouso
  dispara `stopped` com reason `step`) e ✅ Native desde X7-4;
- `evaluate` — ✅ JVM 20/09 (o **nome** de um local do frame, pela mesma rota
  VariableTable/GetValues; o JDWP não tem avaliador de expressão, então
  qualquer outra coisa é `success:false` honesto nomeando a limitação) e ✅
  Native desde X7-4 (o gdb avalia expressões completas);
- `pause` — ✅ JVM 20/09 (ThreadReference.Suspend 11,2 em toda thread de usuário,
  nunca nas do próprio agente JDWP) e ✅ Native 20/09 (`-exec-interrupt --all`);
- `setExceptionBreakpoints` — ✅ JVM 20/09 (evento Exception kind 4 + modificador
  ExceptionOnly 8; faces `caught`/`uncaught`) e ✅ Native 20/09 (breakpoint em
  `kof_throw_string`, a cadeia de throw do próprio runtime; o refinamento
  caught/uncaught é só do JVM e vira `verified:false` honesto, nunca um filtro
  que estoura silenciosamente — R6);
- `restart` (e qualquer outra request não implementada) — ✅ 21/09 (§428): a
  sessão responde **`success:false` honesto** nomeando o comando
  (`unsupported request: <command>`), nunca `success:true` com corpo vazio (Q7).
  JVM (`KofDebugJvmSession`) e Native (`KofDebugNativeDap`). `restart` é limite
  declarado; `exceptionInfo`/`setVariable`/`completions`/`disassemble`/
  `readMemory` caem no mesmo balde honesto enquanto não implementados;
- o que falta: JS continua gap honesto (engine embutido, sem inspector).

> Uma verruga do `LocalVariableTable` afeta a leitura de locals numa linha que
> declara variável: o backend JVM emite todo local com `Start=0`/tamanho do
> método, então um local recém-declarado fica "visível" mas sem valor e o JDWP
> responde `INVALID_SLOT` (35) no lote inteiro — o adaptador refaz por slot e
> omite só o ilegível (nunca inventa valor); a raiz está catalogada no §385
> (lane do backend JVM).

## 4. Tipos de runtime

O adaptador traduz representações de backend para tipos Kof:

| Kof | JVM | Native | JS |
|-----|-----|--------|-----|
| `List<User>` | ArrayList | kof list | Array |
| `User` | User.class | struct | object |
| `String` | java.lang.String | KofString | string |

O usuário sempre vê o tipo Kof.

## 5. Fases

- Fase 3 (MVP): launch JVM + breakpoints por linha Kof + stack — ✅
- Fase 7: locals por frame (`StackFrame.GetValues`), stepping, breakpoints
  verificados, exception breakpoints, avaliação com o type system
  (Native: locals/scopes/stepping/evaluate pousaram X7-4/X7-5; JVM: locals +
  breakpoints verificados pousaram X7-5, **stepping (`next`/`stepIn`/`stepOut`)
  + `evaluate` pousaram 20/09**, e **`pause` + `setExceptionBreakpoints`
  pousaram 20/09** — as faces JVM e Native da Fase 7 estão fechadas; JS
  permanece gap honesto)
- ✅ 20/09: Native (DWARF) — console + DAP<->GDB/MI (X7-3/X7-4)
- ✅ 20/09 (X7-5): attach no JVM + Native; locals por frame e stack
  multi-frame vieram junto (antes da Fase 7); JS fica gap honesto
  (engine embutido, sem inspector)
- ✅ locals no DWARF: DW_TAG_variable + DW_OP_fbreg + DW_AT_type nos 3
  arquétipos nativos (já existiam desde o trabalho da fatia-2; RE-MEDIDO
  20/09 com objdump depois que esta doc afirmou o contrário por um turno —
  shapes measured, never assumed, vale até contra a própria lane)