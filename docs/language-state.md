# Estado Atual da Linguagem Kof

**Data:** 2 de setembro de 2026
**Versão:** 0.2.6-beta
**Testes:** 810 JUnit (793 kof-compiler +8 kof-script +5 kof-c-compiler +4 kof-cli, 0 falhas) +1 skip condicional; `NativeE2ETest` 50/50, `JvmE2ETest` 29/29, `KofJsE2ETest` 35/35, `KofCCompilerTest` 5/5, `KofHttpE2ETest` 4/4, `KofCacheE2ETest` 5/5 (x3 targets), `KofWebWsE2ETest` 11/11, `KofWebSseE2ETest` 7/7; inclui JSON (completo nos 3 targets, 31/08), exceptions, web (ws/sse 30/08), db/orm, UI, security G9, generics `Box<T>` fix, pattern matching e null safety (fix JVM 02/09)
**Status:** Compilador funcional com backends JVM, Native (x86_64 stable + `native.risc`/`native.arm` placeholder via qemu), KofJS (alpha, GraalJS), KofScript, KofC e Android (Fase 1); web server (ws/sse), distribuição e tooling oficiais (0.2.6-beta, 31/08)

---

## Novidades 0.1.0 → 0.2.6-beta (31/08)

### 0.2.6-beta — plataforma (30-31/08)

- **spawn/await no Native** (CONC001 fechado): `pthread_create` + trampoline +
  `pthread_join` + allocator thread-safe (futex) — join implícito
- **FP real no Native** (FLT001): aritmética em XMM (`vcvtsi2sd`/`mulsd`),
  dtoa via `snprintf`, parse completo (fração+expoente)
- **JSON completo no Native** (JSN001/JSN002/JSN003): objetos/records por
  composição em compile-time + arrays `Int/Long/Bool/String/Double`
- **SQLite nativo** via link direto da `.so`; MySQL wire protocol em progresso
  (auth scramble SHA-1 + parse `user:pass@`)
- **JVM**: WebSocket (`app.ws`, handshake RFC 6455 + frame codec com máscara)
  e SSE (`sse.send/event/close`) via `kof.web`; `kof.http` retry/circuit
  breaker (`KOF_HTTP_RETRIES`/`TRIPS`/`FAILURES`/`OPEN_UNTIL`, janela 30s,
  fail-fast); `kof.cache` corrigido (clobber de registradores);
  `KofRuntime.close` + descritores ws
- **JVM+JS**: `kof.http` retry/circuit em paridade (30/08)
- **JS**: scheduler `kof.time` via `setInterval`; retry/circuit de `kof.http`
- **UI Fase 7**: Router (`go/replace/back/forward/param/current/depth`) — real
  no JS, no-op no JVM
- **CLI**: `kof fmt` (parser real, idempotente) e `kof config gen` implementados
- **Android Fase 1**: `kof build --target android` → projeto Maven + APK com
  host Activity em Kof
- **Pipeline de release**: 2 jobs (`test-and-bump` exporta `bump_sha` →
  `package-and-release` checkeia o commit de bump + sanity de versão) ×
  3 plataformas (linux-x86_64/macos-arm64/windows-x86_64)

### 0.2.6-beta — linguagem e plataforma (27/08)

- **Pattern matching** `switch (x) { case String s: ... }` + record destructuring `Point(x,y)` em JVM/Native/JS (`Parser.java:1`, `SemanticAnalyzer.java:1`, `CompilerDriver.java:1`)
- **Null safety** `String?` básica (`Type?` nullable, `?`-check em compile-time)
- **List `map/filter/reduce`** + `Box<T>` generics estáveis (erasure, `substituteTypeVariable` `CompilerDriver.java:3972`)
- **KofScript** top-level `let` → `KofScriptGlobals` (REPL, `--watch`, Windows SIGPIPE fix)
- **KofCcompiler** (`kof c`) C subset native-only: `while`/`if`/deref `&`/`*(int*)` → ELF x86_64 via `kof_c`
- **Native** free-list (`kof_free_head`) + `kof_gc_collect`; MySQL handshake `kof_db_mysql_scramble`; target separation `native.riscv64`/`native.aarch64` (`Target.java:1`, `NativeBackend.java:1`, riscv64 via `riscv64-linux-gnu-as`, `.option arch,rv64g`, `li a7 214/64/93`)
- **kof.http** JVM+JS (JS via `Java HttpClient` interop no `KofJsRunner`)
- **Bugs**: large-project `import a.b.C` file handling (`CompilerDriver.java:243` `import a.b.C` + `a.b` dir, `largeproj` `a/b/C.kf` OK), `List.get`/`listOf`, `release.yml` single job + JDK 21, `kof_free_head` reuso

### 0.1.0 final (P1 — linguagem, 25/08)

- **Enums** com switch exaustivo (`SEM031`), `values/valueOf/name`,
  comparação por conteúdo e mapeamento String nos descritores
- **Map<K,V> / Set<T>** completos nos 3 targets (Native em asm próprio)
- **spawn/await** com handle tipado `Handle<T>` e unboxing de primitivos;
  concorrência real nos 3 targets (CONC001 Native + CONC003 JS fechados);
  gap `AND001` (Android) explícito; lambda não-void de expressão
  única vira return (fix de VerifyError)

- **Interop Android/JVM**: `super.metodo()` com INVOKESPECIAL (owner é a
  superclasse direta; assinaturas externas resolvidas via classpath
  `.jar`/`.aar` — `CompilerDriver.setExternalClasspath`) e annotations
  `@Name`/`@Name(valor | key = valor, ...)` emitidas no bytecode
  (RuntimeVisible/Invisible) em classes, campos, métodos e parâmetros.
  `super.metodo()` no Native reporta `SUP001`.
- `entity Name { field: Type constraint }` — schema declarativo (compile-time)
  para o `kof.orm` (`generated`, `unique`, PK não-numérica).
- Namespaces da stdlib: `kof.db`, `kof.orm`, `kof.process`, `kof.ui`
  (Window/Label/Button/Input/Column/Row/View/Style), além de `kof.web`,
  `kof.io`, `kof.time`, `kof.config`, `kof.log`, `kof.security`, `kof.validation`, `kof.observability`, `kof.http`, `kof.mq`.
- Conversões `String.toInt()/toLong()/toDouble()/toFloat()` (runtime).
- ARITH001: divisão/resto por zero **constante** rejeitada em compile-time
  (apenas inteiros — float/double produzem Infinity/NaN).
- Lexer tolera UTF-8 BOM inicial.
- Lambdas com capturas em todos os targets (box `BoxN`); múltiplas janelas no kof.ui.
- **25/08:** generics `Box<T>` com `T` primitivo (`Box<Int>`) fix — `substituteTypeVariable` + `kof_int_to_string` nativo; `SEM025` sem falso-positivo em `hashCode/equals/toString`.

---

## Sintaxe

### Estrutura básica

```kof
package com.example

import java.util.List

class Animal {
    String name
    public constructor(String name) {
        this.name = name
    }
    public speak(): String {
        return name
    }
}

main() {
    var a = new Animal("Rex")
    println(a.speak())
}
```

### O que a linguagem suporta atualmente

| Constructo | Sintaxe | Exemplo |
|-----------|---------|---------|
| Package | `package a.b.c` | `package com.example` |
| Import | `import a.b.c` | `import java.util.List` |
| Função | `name(args): RetType` | `add(Int a, Int b): Int` |
| Classe | `class Name extends Super implements Iface` | `class Dog extends Animal` |
| Record | `record Name(Type field, ...)` | `record Point(Int x, Int y)` |
| Interface | `interface Name extends Iface` | `interface Speaker` |
| Constructor | `constructor(args)` | `constructor(String name)` |
| Campo | `Type name = value` | `String name = "default"` |
| Método | `name(args): RetType` | `speak(): String` |
| Variável | `var name = value` ou `Type name = value` | `var x = 10` |
| Se | `if (cond) { } else { }` | `if (x > 0) { ... }` |
| Enquanto | `while (cond) { }` | `while (i < 10) { ... }` |
| Do-while | `do { } while (cond)` | `do { ... } while (i < 10)` |
| Para | `for (init; cond; update) { }` | `for (var i = 0; i < 10; i++) { ... }` |
| Try/catch | `try { } catch (Type e) { }` | `try { ... } catch (String e) { ... }` |
| Finally | `finally { }` | `finally { ... }` |
| Throw | `throw expr` | `throw "error"` |
| Return | `return expr` | `return x + 1` |
| New | `new Type(args)` ou `new Type[size]` | `new Dog("Rex")`, `new Int[10]` |
| Array access | `arr[index]` | `a[0]` |
| Array length | `arr.length` | `a.length` |
| String length | `str.length` | `s.length` |
| String concat | `str1 + str2` | `"Hello" + " World"` |
| Herança | `class Sub extends Super` | `class Dog extends Animal` |
| Implementação | `class Name implements Iface` | `class Dog implements Speaker` |
| Super | `super(args)` | `super(name)` |
| Override | implícito (mesmo nome) | `speak()` sobrescreve |

### Modificadores suportados

`public`, `private`, `protected`, `static`, `final`, `abstract`, `override`

> `override` é **aceito** como modificador (retrocompatível) mas **não exigido**:
> override é implícito — mesmo nome de método sobrescreve. `training/idioms/classes.md`.

### Tipos primitivos

`bool`, `byte`, `short`, `int`, `long`, `float`, `double`, `char`, `string`, `void`

### Literais

- Inteiro: `42`, `0xFF`
- Long: `42l`
- Float: `3.14f`
- Double: `3.14`
- String: `"texto"`
- Char: `'c'`
- Boolean: `true`, `false`
- Null: `null`

### Operadores

Aritméticos: `+`, `-`, `*`, `/`, `%`
Comparação: `==`, `!=`, `<`, `>`, `<=`, `>=`
Lógicos: `&&`, `||`, `!`
Atribuição: `=`, `+=`, `-=`, `*=`, `/=`
Bitwise: `&`, `|`, `^`, `~`, `<<`, `>>`, `>>>`

---

## Tipos

### Tipos primitivos

| Tipo | Tamanho | Descrição |
|------|---------|-----------|
| `bool` | 4 bytes | Booleano |
| `byte` | 1 byte | Byte sinalizado |
| `short` | 2 bytes | Short sinalizado |
| `int` | 4 bytes | Inteiro sinalizado |
| `long` | 8 bytes | Long sinalizado |
| `float` | 4 bytes | Ponto flutuante IEEE 754 |
| `double` | 8 bytes | Ponto flutuante IEEE 754 |
| `char` | 4 bytes | Codepoint UTF-32 |
| `string` | referência | String Kof (UTF-8) |
| `void` | — | Sem retorno |

### Tipos de referência

| Tipo | Descrição |
|------|-----------|
| `ClassType` | Classe ou record |
| `ArrayType` | Array de tipo |
| `InterfaceType` | Interface |

### Tipos compound

- **Records**: `record Point(Int x, Int y)` — imutáveis, campos definidos pelo usuário
- **Classes**: `class User { ... }` — mutáveis, campos + métodos
- **Interfaces**: `interface Speaker { ... }` — contratos

---

## Orientação a Objetos

### Classes

```kof
class User {
    String name
    Int age
    public constructor(String name, Int age) {
        this.name = name
        this.age = age
    }
    public getName(): String {
        return name
    }
}
```

### Herança

```kof
class Animal {
    String name
    public constructor(String name) {
        this.name = name
    }
}
class Dog extends Animal {
    public constructor(String name) {
        super(name)
    }
}
```

### Interfaces

```kof
interface Speaker {
    speak(): String
}
class Dog implements Speaker {
    public speak(): String {
        return "woof"
    }
}
```

### Virtual Dispatch

- Métodos são resolvidos pelo tipo real do objeto em runtime
- `Animal a = new Dog()` → `a.speak()` chama `Dog.speak()`
- Implementado via vtable no Native backend
- JVM usa `INVOKEVIRTUAL` nativo

### Records

```kof
record Point(Int x, Int y)
// Gera: classe, construtor, accessors x(), y(), toString()
```

---

## Runtime

### JVM

- Delega para facilities da JVM
- GC: usa GC da JVM
- Memória: gerenciada pela JVM
- Strings: `java.lang.String`
- Arrays: arrays nativos da JVM

### Native

- Assembly x86-64 System V AMD64 ABI
- Sem dependência de libc
- Alocação via mmap (free-list `kof_free_head` com reuso, 27/08)
- GC: mark-sweep pendente; auto-GC desativado após hang — memória
  devolvida só no `munmap` fallback (reclaim pelo SO no exit)
- Concorrência: `spawn`/`await` via `pthread_create` + trampoline +
  `pthread_join` + allocator thread-safe (futex) — 31/08 (CONC001)
- FP real em XMM (`vcvtsi2sd`/`mulsd`), dtoa via `snprintf` — 31/08 (FLT001)
- Strings: KofString (header + UTF-8)
- Arrays: KofArray (header + elementos)
- Objetos, herança, virtual dispatch e instanceof com hierarquia:
  execução real validada por testes E2E (compile → assemble → link → run)
- String methods nativos: length, charAt, substring, contains, startsWith,
  endsWith, concat
- valueOf (int/char/bool → KofString) implementado no runtime
- JSON: objetos/records + arrays `Int/Long/Bool/String/Double` (31/08)

### Object Model

```
Header (16 bytes):
  offset 0:  type_id (4 bytes)
  offset 4:  flags (4 bytes)
  offset 8:  method_table_ptr (8 bytes)

Fields:
  offset 16: field_0
  offset 24: field_1
  ...
```

---

## Backends (0.2.6-beta, 31/08)

| Feature | JVM | Native x86_64 | native.risc (riscv64) | native.arm (aarch64) | JS (GraalJS) | KofC | Android (Fase 1) |
|---------|-----|---------------|----------------|----------------|--------------|------|-----------|
| Target | .class / .jar | ELF x86_64 | ELF riscv64 via qemu (codegen x86_64 placeholder) | ELF aarch64 via qemu (codegen x86_64 placeholder) | ES Modules (.mjs) | ELF x86_64 (C subset) | projeto Maven + APK (bytecode JVM) |
| Runtime | JVM (virtual threads, web ws/sse, cache, http retry/circuit) | Assembly x86-64 (free-list, pthread spawn, FP XMM) | toolchain + qemu | toolchain + qemu | GraalJS embedded + `Java HttpClient` interop | Native only (`kof_c`) | ART (dex via d8) |
| GC | JVM GC | free-list `kof_free_head` (mark-sweep pendente; auto-GC desativado — `munmap` fallback) | same | placeholder | GC JS | none | GC da ART |
| Strings | java.lang.String | KofString | via qemu (x86_64) | via qemu (x86_64) | JS string | C char* | KofString (dex) |
| Arrays | arrays nativos | KofArray | via qemu (x86_64) | via qemu (x86_64) | JS Array | C array | arrays nativos |
| Virtual dispatch | INVOKEVIRTUAL | vtable | via qemu (x86_64) | via qemu (x86_64) | prototype | — | INVOKEVIRTUAL |
| Interfaces | INVOKEINTERFACE | vtable | via qemu (x86_64) | via qemu (x86_64) | — | — | INVOKEINTERFACE |
| Exceptions | Exceções JVM | unwinding próprio | via qemu (x86_64) | via qemu (x86_64) | JS throw | — | Exceções JVM |
| print/println | System.out | Syscalls Linux (`write` 1) | Syscalls riscv64 (`li a7 64`) | Syscalls aarch64 | `kof_platform` | `write` | System.out |
| Pattern matching | ✅ `case String s` + `Point(x,y)` | ✅ | via qemu (x86_64) | placeholder | ✅ (`typeof`) | — | ✅ |

---

## Segurança de Tipos

- Tipagem estática e forte
- Verificação em compile-time
- Coerção implícita limitada (widening primitivo)
- String + anything → String (concatenação)
- Operações inválidas rejeitadas pelo compilador

---

## Erros

### Compile-time

- Variável inexistente
- Método inexistente
- Tipo incompatível
- Argumento incompatível
- Quantidade errada de argumentos

### Runtime

- Null pointer → `kof_null_error` (fatal)
- Array bounds → `kof_bounds_error` (fatal)
- Allocation failure → retorna null
- Panic → `kof_panic` (fatal)

---

## Performance

### Gargalos arquiteturais conhecidos

1. **kof_alloc** usa mmap (lento para alocações pequenas; free-list com reuso `mmap` mitigou — 27/08)
2. **kof_string_concat** copia byte a byte
3. **kof_memcpy** copia byte a byte
4. **kof_print_int** usa divisão em loop
5. **GC mark-sweep pendente** — free-list reusa memória; devolução ao SO só no `munmap` fallback (auto-GC desativado após hang)
6. **Otimizador de IR ativo** (constant folding, branch simplification, DCE, dead stack effects) — mas sem escape analysis/loop optimization

---

## O que NÃO existe (residual 0.2.6-beta, 31/08)

- Reflection, Macros; annotations de enum/Classe em valores (`ANNOT001`) — planned
- `kof init` (P5); LSP rename + Debugger Native DWARF/JS source maps (P5)
- Database nível 3 (query DSL tipada `User.query { where ... }`) — `kof.db` nível 0 e `kof.orm` nível 2/4 já DONE; MySQL wire protocol WIP (scramble SHA-1 + parse `user:pass@`, 31/08)
- Native riscv64/aarch64 codegen completo (toolchain + qemu prontos; codegen ainda x86_64 placeholder)
- GC mark-sweep completo (free-list `kof_free_head` done; auto-GC desativado após hang; memória devolvida só no `munmap` fallback)
- Scheduler no Native (SCHED001); `kof.http` no Native (HTTP002); web no Native/JS (WEB002/WEB001)

## O que existe desde 0.0.5 → 0.2.6-beta

- Generics (erasure) — 25/08 `Box<T>` `T` primitivo fixo (`Box<Int>` + `println` nativo `kof_int_to_string` `CompilerDriver.java:2257`)
- `List<T>` (JVM + Native + JS), `listOf` + `map/filter/reduce` (0.2.0), for-in
- Pattern matching `switch case String s` + record destructuring `Point(x,y)` (JVM/Native/JS, 27/08)
- Null safety `String?` básica (`Type?` nullable, 27/08)
- Lambdas `(x: Int) -> expr` + if-expr + capturas (box `BoxN`) em 3 targets
- JSON encode/decode completo (JVM + Native + JS; objetos/records + arrays `Int/Long/Bool/String/Double` nos 3 targets — 31/08)
- Exceptions reais (JVM table + Native unwinding)
- `assert` + `kof test` estruturado (`test "nome" {}`) + `process.exit`
- `spawn` (concorrência real nos 3 targets — JVM virtual threads, Native pthread 31/08, JS async/await/Promise 03/09)
- kof.io (File/Path/Directory, readFile/writeFile), kof.time (`now()`, `sleep`; `interval`/`every` JVM+JS)
- HTTP (`kof serve` — web stack nativa com WebSocket/SSE JVM 30/08), `kof.http` client (JVM+JS via `Java HttpClient`, retry/circuit 30/08), `kof.cache` (3 targets, 30/08), `kof.mq`
- `kof.validation`, `kof.observability` (health/metrics), `kof.security` (PBKDF2/SHA/JWT/AES-GCM + G9 rateLimit/session/apiKey em 3 targets), `kof.db` (JVM + SQLite nativo `.so` + MySQL WIP) / `kof.orm` + `kof.config`/`kof.log` (asm Native)
- KofJS (target `js` — GraalJS embutido, `kof.http` JS), TLS `web.listenSecure` (JVM), `KofScript` (`let` → `KofScriptGlobals`), `KofCcompiler` (`kof c`)
- `native.risc`/`native.arm` targets (`Target.NATIVE_RISCV64/AARCH64` — toolchain + qemu; codegen x86_64 placeholder)
- Android Fase 1 (`Target.ANDROID` — `kof build --target android` → projeto Maven + APK, host Activity em Kof)
- Language Server (`kof lsp` — frontend real do compilador, hover/completion)
- `kof check`, `kof info`, `kof install`, `kof bench`/`profile`/`inspect`/`debug`, `kof script`/`repl`/`c`, `kof fmt` (31/08), `kof config gen` (31/08)
- Distribuição oficial com JDK 21 embutido, versionamento `VERSION` 0.2.6-beta e releases por 2 jobs (`test-and-bump` → `package-and-release`) × 3 plataformas (`release.yml`) — `scripts/package.sh` PASS
