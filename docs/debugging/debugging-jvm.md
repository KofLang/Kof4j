[English](debugging-jvm.md) | [Português](debugging-jvm.pt_BR.md)

# DEBUGGING_JVM.md — Debugging on the JVM target

**Status:** Implemented (debugger Phases 1-3: metadata + JDWP via `kof-debug`)
**Date:** August 27, 2026
**Version:** 0.6.7 (7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Flow

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

## 2. Generated metadata (in debug mode)

- `SourceFile` — the .kf file (via `IRModule.sourceName`);
- `LineNumberTable` — maps bytecode → Kof line: each IR op carries
  the position (KofDebugInfo); the JvmBackend emits `visitLineNumber` when the
  line changes;
- `LocalVariableTable` — Kof names of the locals, method span;
- enabled by `debugInfoEnabled` (default true).

## 3. Mapping

```text
UserService.kf:42
    ↓
corresponding JVM method + bytecode offset
```

The translation happens in the backend; the user never sees the bytecode.

## 4. JDWP

The program is launched with
`-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=<port>`
and the adapter talks to JDWP over the raw wire protocol
(`JdwpClient`, without `jdk.jdi`): breakpoints by Kof line (via
LineNumberTable), stack frames, continue, dispose.

JDK 25 particularities: see `debug-adapter.md` §3.2.

The user sees only the Kof abstraction — bytecode, offsets and line tables
are internal.
