# DEBUGGING.md — Depuração Kof (visão de uso)

**Status:** MVP funcional no target JVM (`kof debug app.kf`)
**Data:** 27 de agosto de 2026
**Versão:** 0.2.6-beta (810 testes; 7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Experiência

Depurar Kof é depurar Kof — em qualquer target:

```text
  40 | User find(Int id) {
  41 |     var user = repository.find(id)
● 42 |     return user
  43 | }
```

Ao parar:

```text
CALL STACK

UserService.find       UserService.kf:42
UserController.get     UserController.kf:18
main                    Application.kf:7
```

```text
VARIABLES

id      Int        42
user    User
  name             "Mel"
  active           true
```

O usuário nunca precisa saber JVM bytecode, assembly ou JavaScript.

## 2. Comandos

```bash
kof debug app.kf                 # ✅ JVM (servidor DAP sobre stdio)
kof debug --target native app.kf # futuro (DWARF)
kof debug --target js app.kf     # futuro (source maps + Inspector)
kof debug --attach <pid>         # futuro
kof build app.kf --debug         # metadata extra (padrão: debug info ligado)
kof build app.kf --release
```

A sessão compila com metadata de debug, lança o JVM com
`-agentlib:jdwp` (suspend=y) e responde ao protocolo DAP.

## 3. Capacidades

**MVP implementado (target JVM — Fase 3):**

- launch (compila com metadata de debug + lança o JVM com JDWP)
- breakpoints por linha Kof (`UserService.kf:42`)
- evento `stopped` ao atingir breakpoint
- stack traces com nomes e linhas Kof (via LineNumberTable)
- `continue` e `disconnect`

**Planejadas (Fases 4-7 — ver `debug-adapter.md`):**

- step over/into/out, pause, restart
- scopes/locals por frame (`StackFrame.GetValues`)
- exceções (break on throw / uncaught) com stack Kof
- avaliação de expressões (com respeito ao type system)
- Native (DWARF — Fase 5) e JS (source maps — Fase 6)

## 4. Integração

```text
Kof Editor
    ├── LSP ────► Kof Language Server (diagnostics, symbols, hover)
    └── DAP ────► kof-debug
                      ├── JVM (JDWP)
                      ├── Native (DWARF)
                      └── JS (Node Inspector)
```

LSP e DAP não se misturam: LSP = código; DAP = execução.

## 5. Estado

- Fase 1 (DebugInfo na IR) — ✅
- Fase 2 (JVM: SourceFile, LineNumberTable, LocalVariableTable) — ✅
- Fase 3 (`kof-debug` MVP: DAP + JDWP cru) — ✅
  - requests DAP: `initialize`, `launch`, `setBreakpoints`,
    `configurationDone`, `continue`, `threads`, `stackTrace`, `disconnect`
  - evento `stopped` quando um breakpoint Kof é atingido
  - call stack com funções Kof, arquivo e linha (via LineNumberTable)
- Fases 4-7 — planejadas; ver `debugger-architecture.md`