[English](EXCEPTIONS_MODEL.md) | [Português](EXCEPTIONS_MODEL.pt_BR.md)

# EXCEPTIONS_MODEL.md — Modelo de Exceções do Kof

**Data:** 21 de agosto de 2026
**Status:** Implementado — Fase F.6

---

## 1. Visão Geral

Kof suporta `throw` e `try/catch/finally`. Exceções são tratadas de forma diferente nos dois backends:

- **JVM**: Exceções são propagadas naturalmente pela JVM via `athrow`
- **Native**: unwinding real pela cadeia de frames (`kof_throw_string`); a
  mensagem (String) é recuperada no catch. Exceção não capturada termina o
  processo com a mensagem.

---

## 2. Sintaxe

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

### Múltiplos catch

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

## 3. Semântica

### throw

1. Avalia a expressão (String)
2. No JVM: emite `athrow` com wrap em `RuntimeException` (exceção propagada pela JVM)
3. No Native: `kof_throw_string(msg)` — unwinding real pela cadeia de frames

### try/catch

1. Executa o bloco try
2. Se uma exceção é lançada e há um catch compatível, executa o bloco catch
3. No JVM: exception table nativa da JVM
4. No Native: frame de exceção registrado no início do try (handler, rsp, rbp, prev);
   o unwind restaura rsp/rbp e salta para o handler com a mensagem em `%rdi`.
   O handler do primeiro catch captura (múltiplos catches: o primeiro captura no Native)

### finally

1. Executa independentemente de exceção ser lançada ou não
2. No JVM: catch-all + rethrow
3. No Native: catch-all no frame (handler = rethrow); o finally roda e a exceção
   é relançada, propagando para o frame anterior da cadeia

### Frame de exceção (Native, 32 bytes na stack)

```
+0  handler_addr   (leaq do primeiro catch / catch-all)
+8  rsp_value      (stack restaurada no unwind)
+16 rbp_value      (frame base restaurado no unwind)
+24 prev_chain     (kof_exc_chain anterior)
```

`kof_exc_chain` é o topo da cadeia (ponteiro global). Exceção não capturada
termina o processo imprimindo a mensagem.

---

## 4. Runtime Errors

Erros de runtime são fatais em ambos os backends:

| Erro | Função Nativa | Comportamento |
|------|---------------|---------------|
| Null pointer | `kof_null_error()` | Termina com mensagem |
| Array bounds | `kof_bounds_error(i, len)` | Termina com mensagem |
| Runtime panic | `kof_panic(msg)` | Termina com mensagem |
| Exceção não capturada | `kof_throw_string(msg)` | Imprime a mensagem e termina |

---

## 5. Arquivos

| Arquivo | Papel |
|---------|-------|
| AstNodes.java | `ThrowStmt`, `TryStmt`, `CatchClause` |
| Parser.java | Parsing de `throw`, `try/catch/finally` |
| `KofThrow.java` | `KofThrow` (um record por op) |
| CompilerDriver.java | Lowering de `throw` e `try/catch/finally` |
| JvmBackend.java | Exception table, StackMapTable, wrap `RuntimeException` |
| NativeBackend.java | Frames de exceção, unwind, `kof_throw_string` |
| NativeRuntime.java | `kof_throw_string`, `kof_panic`, `kof_null_error`, `kof_bounds_error` |

---

> **Atualizado (0.2.6-beta, 31/08):** o `spawn` em threads (pthread) roda o
> código do programa de forma concorrente; o mecanismo de exceção (frame de
> 32 bytes + cadeia `kof_exc_chain`) é inalterado. Exceções continuam fatais
> quando não capturadas.

## 6. Limitações

1. No Native, o primeiro catch de um try captura (múltiplos catches não fazem dispatch por tipo)
2. Sem exception object model completo (exceção = String/mensagem)
3. Sem DWARF-based unwinding (cadeia de frames própria)
4. Sem checked exceptions: nos alvos JVM e Script, `catch (String e)` captura toda `Exception` Java que uma chamada lança, checked ou não, com a mensagem em `e`; um `java.lang.Error` segue propagando (§810)
5. Sem stack traces
