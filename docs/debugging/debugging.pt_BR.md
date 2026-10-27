[English](debugging.md) | [Português](debugging.pt_BR.md)

# DEBUGGING.md — Depuração Kof (visão de uso)

**Status:** Debug DAP nos targets **JVM e Native** (`kof debug`,
`kof debug --dap --target native`), batch `--break` no Native, `--attach` nos
dois; JS = gap honesto (motor embutido — ver `debugging-js.pt_BR.md`)
**Data:** 20 de setembro de 2026
**Versão:** 0.6.7 (7 targets; free-list + pthread spawn + FP XMM)

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
kof debug --dap app.kf           # DAP JVM explicito (o padrao e o mesmo servidor)
kof debug --dap --target native app.kf # ✅ X7-4 (`bda631a7`): DAP em ponte com o gdb/MI2 real
kof debug --target native app.kf # ✅ X7-3 (`cfa67238`): builda o ELF com DWARF Kof e
                                 #    delega ao gdb do alvo (`-x` arquivo de comandos, `-iex set
                                 #    directories` ate a fonte Kof) — breakpoints em
                                 #    `Main.kf:2`, nunca no mangle
kof debug --target native --break 4 --output out app.kf # ✅ X7-3 fatia 2 (`64114449`): sessão
                                 #    batch scriptável (gdb `-batch`, `break Main.kf:N`, `run`, `bt`);
                                 #    `--output <dir>` preserva o ELF; ambos recusam no DAP JVM
kof debug --attach <pid>         # ✅ X7-5 (`bda631a7`): JVM = JDWP cru numa VM viva (o debuggee
                                 #    sobrevive ao disconnect); Native = gdb `-p`
kof debug --target js app.kf     # gap honesto: o alvo JS roda no motor EMBUTIDO (sem
                                 #    protocolo devtools ainda) — diagnostico, nao silencio
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

**Implementado além do MVP JVM (medido nos handlers DAP 20/09):**

- `next` / `stepIn` / `stepOut`, `scopes` / `variables` e `evaluate` — ✅ no
  Native (DAP↔gdb/MI) e, desde 20/09, também no DAP **JVM** (JDWP `SingleStep`;
  `evaluate` resolve o **nome** de um local — o JDWP não tem avaliador de
  expressão, então qualquer outra coisa é recusa honesta)
- `pause` e `setExceptionBreakpoints` — ✅ JVM + Native 20/09 (JVM: JDWP
  `ThreadReference.Suspend` sobre as threads de usuário — nunca nas do agente — e
  o evento Exception; Native: `-exec-interrupt --all` e um breakpoint em
  `kof_throw_string`, com o refinamento caught/uncaught sendo `verified:false`
  honesto)
- attach (`--attach <pid>`) no JVM e no Native — ✅ X7-5
- ~~Native (DWARF — Fase 5)~~ ✅ **X7-3 pousou 20/09** (`cfa67238`, `KofDebugNativeTest`);
  JS (source maps — Fase 6) = diagnostico honesto hoje (motor embutido)

**Ainda planejadas (Fase 4 — ver `debugger-architecture.md`):**

- Fase 4: UI do Kof Editor (o servidor DAP — JVM + Native — está pronto)

## 4. Integração

```text
Kof Editor
    ├── LSP ────► Kof Language Server (diagnostics, symbols, hover)
    └── DAP ────► kof-debug
                      ├── JVM (JDWP)
                      ├── Native (DWARF ↔ gdb/MI)
                      └── JS (gap honesto — motor embutido)
```

LSP e DAP não se misturam: LSP = código; DAP = execução.

## 5. Estado

- Fase 1 (DebugInfo na IR) — ✅
- Fase 2 (JVM: SourceFile, LineNumberTable, LocalVariableTable) — ✅
- Fase 3 (`kof-debug` MVP: DAP + JDWP cru) — ✅
  - requests DAP: `initialize`, `launch`, `attach`, `setBreakpoints`,
    `setExceptionBreakpoints`, `configurationDone`, `continue`, `pause`,
    `next`, `stepIn`, `stepOut`, `threads`,
    `stackTrace`, `scopes`, `variables`, `evaluate`, `disconnect`
  - evento `stopped` quando um breakpoint Kof é atingido
  - call stack com funções Kof, arquivo e linha (via LineNumberTable)
- Fase 5 (Native) — ✅ entregue como a ponte DAP↔gdb/MI2
  (`kof debug --dap --target native`, `bda631a7`; garantias medidas em
  `debug-adapter.md`), incluindo `next`/`stepIn`/`stepOut`, `scopes`,
  `variables` e `evaluate`. Fase 6 (JS) — recusa honesta (engine embutido,
  sem node/inspector — nunca uma ponte falsa; ver
  `debugging-js.pt_BR.md`). Fase 4 (UI do Kof Editor) e os refinamentos da
  Fase 7 — ver `debugger-architecture.md`.

## 6. Medindo um fix pousado — a armadilha do jar obsoleto (lição 20/09)

Ao verificar um fix do compilador pelo jar da CLI, o jar precisa ser
**provavelmente atual**: um `mvn package -pl kof-cli -am` incremental pode
deixar as entradas sombreadas `dev/kof/compiler/*` apontando para um build
antigo, e você mede o **compilador velho e acredita que o fix não existe**
(aconteceu com o §368 — o `FieldAssignabilityPhantomE2ETest` 8/8 estava
certo, o jar era fantasma). Regra: reconstrua com
`mvn clean package -DskipTests` e, na dúvida, compare a classe dentro do jar
com o output do módulo (`unzip -p <cli.jar> dev/kof/compiler/Foo.class |
md5sum` vs `md5sum kof-compiler/target/classes/.../Foo.class`) — **bytes
idênticos ou você não está medindo o tip.**