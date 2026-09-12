# 19 — Packages e Módulos

> **Status: implementado — imports `a.b.C` corrigidos para projetos grandes (0.3.22-beta)**
>
> `package` + `import` funcionam end-to-end. Em 27/08 o `CompilerDriver` passou a tratar `import a.b.C` como import de arquivo **mais** import de diretório `a.b`, corrigindo a perda de imports em projetos com `a/b/C.kf`.

## Package

Cada arquivo Kof pode declarar um package:

```kf
package com.exemplo.users

record User(String name, String email)
```

Isso gera a classe no package `com.exemplo.users` (JVM: internal name `com/exemplo/users/User`).

## Imports

```kf
import a.b.C          // 0.2.0: resolve tanto o arquivo C.kf quanto o diretório a/b/
import a.b.*          // diretório inteiro
import kof.http       // stdlib
```

> **`java.util.*` é interop, não o idiomático.** Para coleções Kof, `List<T>`/
> `Map<K,V>`/`Set<T>` + `listOf`/`mapOf`/`setOf` vêm da stdlib **sem import**.
> `import java.util.ArrayList` etc. só é necessário ao chamar APIs Java
> diretamente (ver cap. 21).

`kof build` agora compila `largeproj` corretamente:

```text
src/
├── Main.kf          // import a.b.C
└── a/b/C.kf         // package a.b; class C { ... }
→ Main.class + a/b/C.class  (decls=2, ambos emitidos)
```

Antes de 27/08, `import a.b.C` em projetos grandes podia perder a segunda declaração — o driver só registrava o arquivo, não o diretório. Agora (`CompilerDriver.java:243`) registra `file import` + `dir import`, e a cadeia `intention->Kof->frontend->IR->backend->runtime` preserva todos os `CompilationUnit`s até o backend.

### Exemplo runnable (multi-arquivo)

```kf
// a/b/C.kf
package a.b
class C {
    String msg() { return "de C" }
}
```

```kf
// Main.kf
import a.b.C

main() {
    var c = C()
    println(c.msg())  // de C
}
```

```bash
kof build src --target=jvm     # gera Main.class + a/b/C.class
kof build src --target=js      # Default.mjs com import C
```

> ⚠️ **Native (x86_64) quebra com classe importada de outro pacote** — o
> mangling do construtor usa o nome simples (`C_init_0`) em vez do internal
> name (`a_b_C_init_0`) → `undefined reference`. Bug 22 em
> `docs/development/known-bugs.md`. Use `--target=jvm`/`js` enquanto isso, ou corrija o
> `NativeBackend.java:1725`.

> ⚠️ **Nomes iguais em pacotes diferentes são rejeitados** (PKG005) — `pkgA.Data`
> + `pkgB.Data` não compilam juntos. Bug 21 em `docs/development/known-bugs.md`.

### Import estático (planejado)

```kf
import static java.lang.Math.PI
import static java.lang.Math.sqrt
```

### Import de módulo (planejado)

```kf
import module java.base
```

## Visibilidade

| Modificador | Mesmo package | Subclasses | Qualquer lugar |
|-------------|:---:|:---:|:---:|
| `public` | ✅ | ✅ | ✅ |
| `protected` | ✅ | ✅ | ❌ |
| (padrão) | ✅ | ❌ | ❌ |
| `private` | ❌ | ❌ | ❌ |

## Módulos JPMS (planejado)

```kf
module com.exemplo.app {
    requires java.sql
    requires spring.boot
    exports com.exemplo.api
}
```

## Próximo passo

[Annotations →](20-annotations.md)
