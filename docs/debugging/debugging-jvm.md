# DEBUGGING_JVM.md — Debug no target JVM

**Status:** Implementado (Fases 1-3 do debugger: metadata + JDWP via `kof-debug`)
**Data:** 27 de agosto de 2026
**Versão:** 0.2.6-beta (810 testes; 7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Fluxo

```text
Kof Debug Info (IR)
    ↓
JVM Debug Metadata (ASM)
    ↓
class file (LineNumberTable, LocalVariableTable, SourceFile)
    ↓
JDWP (java -agentlib:jdwp)
    ↓
kof-debug (DAP)
    ↓
Editor
```

## 2. Metadata gerada (em modo debug)

- `SourceFile` — o arquivo .kf (via `IRModule.sourceName`);
- `LineNumberTable` — mapeia bytecode → linha Kof: cada op da IR carrega
  a posição (KofDebugInfo); o JvmBackend emite `visitLineNumber` quando a
  linha muda;
- `LocalVariableTable` — nomes Kof dos locals, span do método;
- habilitada por `debugInfoEnabled` (default true).

## 3. Mapeamento

```text
UserService.kf:42
    ↓
método JVM correspondente + offset de bytecode
```

A tradução acontece no backend; o usuário nunca vê o bytecode.

## 4. JDWP

O programa é lançado com
`-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=<porta>`
e o adaptador conversa com o JDWP via wire protocol cru
(`JdwpClient`, sem `jdk.jdi`): breakpoints por linha Kof (via
LineNumberTable), stack frames, continue, dispose.

Particularidades do JDK 25: ver `debug-adapter.md` §3.2.

O usuário vê apenas a abstração Kof — bytecode, offsets e line tables
são internos.