[English](EXCEPTIONS_MODEL.md) | [Português](EXCEPTIONS_MODEL.pt_BR.md)

# EXCEPTIONS_MODEL.md — Kof Exception Model

**Date:** August 21, 2026
**Status:** Implemented — Phase F.6

---

## 1. Overview

Kof supports `throw` and `try/catch/finally`. Exceptions are handled differently in the two backends:

- **JVM**: Exceptions are propagated naturally by the JVM via `athrow`
- **Native**: real unwinding through the frame chain (`kof_throw_string`); the
  message (String) is recovered in the catch. An uncaught exception terminates the
  process with the message.

---

## 2. Syntax

### throw

```kof
throw "error message"
```

### try/catch

```kof
try {
    throw "error"
} catch (String e) {
    println(e)
}
```

### try/finally

```kof
try {
    throw "error"
} finally {
    println("cleanup")
}
```

### try/catch/finally

```kof
try {
    throw "error"
} catch (String e) {
    println(e)
} finally {
    println("cleanup")
}
```

### Multiple catch

```kof
try {
    throw "error"
} catch (String e) {
    println(e)
} catch (Int e) {
    println(e)
}
```

---

## 3. Semantics

### throw

1. Evaluates the expression (String)
2. On the JVM: emits `athrow` with a wrap in `RuntimeException` (exception propagated by the JVM)
3. On Native: `kof_throw_string(msg)` — real unwinding through the frame chain

### try/catch

1. Executes the try block
2. If an exception is thrown and there is a compatible catch, executes the catch block
3. On the JVM: JVM's native exception table
4. On Native: exception frame registered at the start of the try (handler, rsp, rbp, prev);
   the unwind restores rsp/rbp and jumps to the handler with the message in `%rdi`.
   The handler of the first catch captures (multiple catches: the first one captures on Native)

### finally

1. Executes regardless of whether an exception is thrown or not
2. On the JVM: catch-all + rethrow
3. On Native: catch-all in the frame (handler = rethrow); the finally runs and the exception
   is rethrown, propagating to the previous frame in the chain

### Exception frame (Native, 32 bytes on the stack)

```
+0  handler_addr   (leaq of the first catch / catch-all)
+8  rsp_value      (stack restored on unwind)
+16 rbp_value      (frame base restored on unwind)
+24 prev_chain     (previous kof_exc_chain)
```

`kof_exc_chain` is the top of the chain (global pointer). An uncaught exception
terminates the process by printing the message.

---

## 4. Runtime Errors

Runtime errors are fatal in both backends:

| Error | Native Function | Behavior |
|------|---------------|---------------|
| Null pointer | `kof_null_error()` | Terminates with message |
| Array bounds | `kof_bounds_error(i, len)` | Terminates with message |
| Runtime panic | `kof_panic(msg)` | Terminates with message |
| Uncaught exception | `kof_throw_string(msg)` | Prints the message and terminates |

---

## 5. Files

| File | Role |
|---------|-------|
| AstNodes.java | `ThrowStmt`, `TryStmt`, `CatchClause` |
| Parser.java | Parsing of `throw`, `try/catch/finally` |
| `KofThrow.java` | `KofThrow` (one record per op) |
| CompilerDriver.java | Lowering of `throw` and `try/catch/finally` |
| JvmBackend.java | Exception table, StackMapTable, wrap `RuntimeException` |
| NativeBackend.java | Exception frames, unwind, `kof_throw_string` |
| NativeRuntime.java | `kof_throw_string`, `kof_panic`, `kof_null_error`, `kof_bounds_error` |

---

> **Updated (0.2.6-beta, 31/08):** `spawn` on threads (pthread) runs the
> program code concurrently; the exception mechanism (32-byte frame +
> `kof_exc_chain` chain) is unchanged. Exceptions remain fatal
> when uncaught.

## 6. Limitations

1. On Native, the first catch of a try captures (multiple catches do not dispatch by type)
2. No complete exception object model (exception = String/message)
3. No DWARF-based unwinding (own frame chain)
4. No checked exceptions: on the JVM and Script targets `catch (String e)` catches every Java `Exception` a call raises, checked or not, with its message in `e`; a `java.lang.Error` still propagates (§810)
5. No stack traces
