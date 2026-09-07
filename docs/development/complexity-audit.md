# Auditoria de Complexidade

**Última atualização:** 2 de setembro de 2026
**Versão:** 0.2.6-beta (810 testes; 7 targets: jvm, native x86_64, native.risc/arm, js, kofc, android; free-list GC; `VERSION` 0.2.6-beta)

---

## Princípio

> Kof deve ser pequeno por design.

Não queremos acumular features até virar outro Java.

---

## O que Existe Hoje

### Código do Compilador

| Arquivo | Linhas (31/08) | Função |
|---------|--------|--------|
| Lexer.java | ~480 | Análise léxica (pattern matching `case` + `String?`) |
| Parser.java | ~1.720 | Análise sintática (pattern matching, `String?`, `let` KofScript, lambda trailing) |
| SemanticAnalyzer.java | ~1.870 | Análise semântica (pattern matching, `String?`, `CompilerDriver.java:243` import fix) |
| CompilerDriver.java | ~7.520 | Lowering IR (free-list alloc, `substituteTypeVariable` `Box<T>`, `KofScriptGlobals`, stdlib dispatch) |
| JvmBackend.java | ~1.320 | Backend JVM (V21, LineNumberTable, web ws/sse, `Java HttpClient` for JS) |
| NativeBackend.java | ~1.800 | Backend Native (x86_64; toolchain riscv64/aarch64) |
| NativeRuntime.java | ~15.340 | Runtime nativo — maior parte é assembly embutido (free-list `kof_free_head`, spawn pthread 31/08, FP XMM, JSON objetos/arrays, config/log/security/cache asm, `kof_db_mysql_scramble`) |
| JsBackend.java | ~5.280 | Backend JS (GraalJS, CORE_RUNTIME DOM/UI, `kof.http` interop) |
| KofCCompiler + Lexer/Parser/AST/Emitter | ~710 | C subset (`kof c`) → ELF x86_64 |
| KofScript.java | ~610 | KofScript (`let` → `KofScriptGlobals`, REPL) |
| IRNodes.java | ~250 | Representação intermediária (+ KofDebugInfo) |
| Type.java | ~140 | Sistema de tipos (`Type?` nullable) |
| SymbolTable.java | ~210 | Tabela de símbolos |
| ClassLayout.java | ~110 | Layout de memória |
| Optimizer.java | ~610 | Otimizador de IR (sempre ativo) |
| Outros | ~10.000 | Geração de runtime JVM (`JvmRuntime`/`JvmWebRuntime`...), descritores stdlib (`KofWeb`, `KofSecurity`, `KofUi`, `KofDb`...), utilidades |

**Total:** ~46.600 linhas no `kof-compiler` (main; ~51.800 somando `kof-cli`/`kof-script`/`kof-c-compiler`) (0.2.6-beta, 31/08) — o peso vem de assembly nativa embutida e do runtime JVM gerado, não de lógica Java.

### O que é necessariamente complexo

- Lexer: precisa reconhecer toda a sintaxe
- Parser: precisa lidar com ambiguidades
- SemanticAnalyzer: precisa resolver tipos e membros
- NativeRuntime: precisa implementar funções de baixo nível

### O que pode ser simplificado

1. **NativeTypeMapper** — não é usado pelo NativeBackend (código morto)
2. **JvmTypeMapper.BUILTIN_TYPES** — mapa definido mas não lido
3. **FieldLayout.naturalSize()** — método nunca chamado
4. **ClassLayout.clearCache()** — no-op
5. **JvmBackend.computeLocals/computeStack** — valores descartados pelo ASM

---

## Abstrações Necessárias vs Desnecessárias

### Necessárias

- **IR** — fronteira entre frontend e backends
- **ClassLayout** — cálculo centralizado de offsets
- **BuiltinTypes** — referências centralizadas de tipos
- **NativeRuntime** — funções de runtime nativo

### Potencialmente Desnecessárias

- **NativeTypeMapper** — não é usado
- **JvmTypeMapper.BUILTIN_TYPES** — não é usado
- **FieldLayout.naturalSize()** — não é usado

---

## Complexidade Acidental

### No Compilador

1. **Parser**: 1.720 linhas — cresceu com pattern matching, lambda trailing, `let` KofScript
2. **SemanticAnalyzer**: 1.874 linhas — razoável para análise semântica (inclui `supportedOn`/diagnostics de gap)
3. **CompilerDriver**: 7.521 linhas — lowering + dispatch da stdlib inteira; candidato prioritário a extração de helpers
4. **NativeBackend**: 1.803 linhas — complexo mas necessário (x86_64 + toolchains)

### No Runtime

1. **NativeRuntime**: 15.341 linhas — assembly embutido é verboso mas necessário (free-list, spawn pthread, FP XMM, JSON, config/log/security/cache/db em asm)
2. **Funções de runtime** — subconjunto amplo mas cada função cobre um módulo da stdlib

---

## O que Poderia Ser Simplificado

1. **Remover NativeTypeMapper** — não é usado
2. **Remover JvmTypeMapper.BUILTIN_TYPES** — não é usado
3. **Remover FieldLayout.naturalSize()** — não é usado
4. **Remover ClassLayout.clearCache()** — no-op
5. **Simplificar CompilerDriver** — extrair helpers

---

## Regra de arquitetura — limite de 500 linhas por classe (futura)

> **Registrada 02/09/2026 — refactor geral obrigatório no futuro.**

**Regra:** nenhuma classe pode ter mais de **500 linhas**. Classes grandes
são um cheiro de arquitetura: múltiplas responsabilidades, acoplamento, diffs
dolorosos e barreira para agentes/humanos entenderem.

**Estado atual (violações):**

| Arquivo | Linhas | O que é |
|---------|--------|---------|
| `NativeRuntime.java` | **~17.300** | Assembly x86-64 embutido (free-list, spawn pthread, FP XMM, JSON, config/log/security/cache/db) + runtime C |
| `CompilerDriver.java` | **~8.200** | Lowering IR + dispatch da stdlib inteira |
| `JsBackend.java` | **~5.700** | Backend JS (GraalJS) + runtime DOM/UI |
| `Parser.java` | **~1.800** | Análise sintática |
| `SemanticAnalyzer.java` | **~2.000** | Análise semântica |
| `JvmBackend.java` | **~1.400** | Backend JVM (ASM) |

**Como chegar lá (refactor futuro):**

1. **`NativeRuntime.java`** — o assembly embutido (strings Java gigantes) deve
   virar **módulos separados por domínio** (ex.: `native/asm/*.s` incluídos em
   build, ou classes `NativeRuntimeMemory`/`NativeRuntimeJson`/…) com um
   concatenador. É o maior esforço (é a fonte das "dezenas de milhares de
   linhas de assembly").
2. **`CompilerDriver.java`** — extrair helpers por área (lowering de
   expressões, stdlib dispatch, collections, json, web) em classes dedicadas.
3. **`JsBackend.java`** — separar emitter do runtime embutido.
4. `Parser`/`SemanticAnalyzer`/`JvmBackend` — extrair sub-parsers/validators.

**Critério de aceite:** `find src -name '*.java' | xargs wc -l | sort -n |
tail` não deve mostrar nenhuma classe acima de 500 linhas.

**Nota:** o `git` não divide por classes — usar `grep -n '^class '`/ide para
contar por declaração, ou ferramenta de métricas (ex.: `cloc` por classe) no
PR de refactor.

---

## Conclusão

O Kof atual tem ~46.600 linhas no `kof-compiler` (31/08), das quais ~15.300 são
assembly nativa embutida em `NativeRuntime.java` e ~10.000 são templates de
runtime JVM gerado — a lógica Java "pura" do frontend/backends permanece na
escala de dezenas de milhares, comparável a:
- Lua: ~20.000 linhas
- Zig: ~150.000 linhas
- Go: ~1.500.000 linhas

Kof é pequeno. O objetivo é manter assim — e o crescimento recente veio da
implementação nativa (asm), não de camadas Java extras.

**Regra:** antes de adicionar uma feature, perguntar:
1. "Isso resolve um problema real?"
2. "Existe uma forma mais simples?"
3. "Quanto código isso adiciona?"
4. "Isso pode ser resolvido pelo runtime em vez do compilador?"
