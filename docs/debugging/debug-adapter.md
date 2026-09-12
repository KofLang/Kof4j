# DEBUG-ADAPTER.md — kof-debug (Debug Adapter DAP)

**Status:** MVP implementado e validado (JVM; JDWP cru, sem jdk.jdi)
**Data:** 27 de agosto de 2026
**Versão:** 0.2.6-beta (810 testes; 7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Objetivo

Componente `kof-debug` que expõe a execução Kof via **DAP** (Debug Adapter
Protocol) — o mesmo protocolo dos editores modernos (VS Code, Neovim,
IntelliJ, Kof Editor).

Não criar protocolo proprietário.

## 2. Responsabilidades

- iniciar programas (`launch`);
- anexar a processos (`attach` — futuro);
- controle de execução: continue, pause, step over/into/out, restart, terminate;
- breakpoints (source; depois conditional, hit count, exception);
- stack traces, scopes, locals, arguments, campos;
- eventos de exceção;
- inspeção de variáveis com tipos Kof;
- avaliação de expressões (futuro — com type system, nunca Java/JS cru).

## 3. Interface

```text
kof-debug (DAP over stdio — Content-Length framing)
    ↓
JVM: launch java -agentlib:jdwp + JDWP client (raw wire protocol)
Native: launch binary + DWARF/frame info   (futuro)
JS: launch node --inspect + Inspector protocol  (futuro)
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
configurationDone     → VM.Resume
[evento] stopped      → breakpoint atingido (thread + motivo)
stackTrace            → frames Kof: nome da função, arquivo, linha
continue              → VM.Resume
disconnect/terminate  → VM.Dispose + kill do processo + limpeza
```

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
- `ThreadReference.Frames` é o command set **11** (o 10 é StackFrame) e
  o HotSpot rejeita `length > 5` com `INVALID_LENGTH` (504);
- o handler de eventos roda fora do event loop (dispatch em thread) —
  comandos JDWP emitidos pelo handler precisam do loop para receber
  replies (sem isso: deadlock de timeout);
- eventos `Composite` têm `suspendPolicy + eventCount` antes dos kinds.

## 3.3 Limitações do MVP

- `stackTrace` retorna até 5 frames (limite do JDK 25) e o frame atual
  mostra a função/linha Kof;
- `scopes`/`variables` são placeholders (locals por frame ficam na
  Fase 7, via `StackFrame.GetValues`);
- breakpoints são reportados como `verified: false` (a verificação
  efetiva via LineTable fica na Fase 7);
- sem stepping, pause, attach, exception breakpoints nem avaliação.

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
- Depois: attach, Native (DWARF), JS (source maps)