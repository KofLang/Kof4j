# Specification Gaps e Divergências

**Versão:** 0.3.0-beta · **Data:** 06/09/2026 · **Fonte:** auditoria completa do
`kof-compiler` + probes de execução + revisão de `docs/`, `training/`, `AGENTS.md`

Este é o relatório de inconsistências encontradas na auditoria. Cada item
distingue **o que o código faz**, **o que a documentação diz** e **o que os
testes provam**. **Nenhum item aqui foi "corrigido" na linguagem** — são
recomendações futuras (regra 14 da tarefa: não alterar comportamento).

---

## Categoria A — Documentação contradiz código

### SG-001 — `fun`/`fn`/`func` existiam no compilador mas o corpus diz que não ✅ RESOLVIDO (06/09)

- **Implementação (antes)**: `fn` era prefixo opcional aceito. `fun`/`func`
  compilavam porque o parser lia a palavra como *tipo de retorno* e o nome da
  função vinha depois (`fun main()` → função `main`, retorno implícito `void`).
  `JsonE2ETest.java:223` usava `fun main()` e passava.
- **Documentação**: `AGENTS.md` ("não existe `fun` nem `func`"),
  `training/anti-patterns/fake-idioms.md` (lista `fun`/`func`/`let` como fake).
- **Problema (antes)**: a regra "não existe" era falsa para o compilador real —
  um agente que escrevesse `fun` não recebia erro.
- **Resolução (06/09)**: o parser Kof agora **rejeita** `fn`/`fun`/`func` como
  prefixo de declaração com `PARSE085` (diagnóstico claro, nunca silencioso —
  R6). Alinhado ao corpus (regra 4: bug = alinhar ao previsto). `fn`/`fun`/
  `func` continuam válidos como *nome* de função (identificadores). KofScript
  (`.ks`) mantém `fn` como sintaxe própria e traduz na fronteira
  (`KofScript.toKofSyntax`). Testes: `FunctionSyntaxTest` (5 novos: fun/fn/func
  rejeitados, `fn calc(): Int` rejeitado, `fn()` como nome ainda funciona).
  `let` continua inexistente em `.kf` (só `.ks`).

### SG-002 — Tokens e keywords que a gramática não usa

- **Implementação**: o lexer produz `TILDE` (`~`), `COLON_COLON` (`::`),
  `ELLIPSIS` (`...`), `DOUBLE_ARROW` (`=>`), `PIPE_LINE` (`|>`),
  `UNDERSCORE` (`_` isolado) e as keywords `sealed`/`permits`, mas **nenhum**
  aparece em produção do parser (grep: 0 usos em `Parser.java`).
- **Consequência observável**: `~5`→`PARSE041`, `a => b`→`PARSE041`,
  `sealed class X{}`→`PARSE007` (todos *probe*).
- **Problema**: tokens mortos dão a impressão de feature planejada que não
  existe. `sealed`/`permits` sugerem hierarquia selada (Java 17) que **não é
  implementada**.
- **Recomendação**: remover os tokens do lexer **ou** implementar as features
  **ou** documentar explicitamente como "reservado, não implementado". Hoje é
  **Unspecified**.

### SG-003 — Termos de marketing vs definição técnica

- **Documentação**: `README.md:60` "Kof é uma linguagem **fortemente tipada e
  estaticamente tipada**"; `docs/architecture.md` "fortemente tipada".
- **Implementação**: o type checker **não** garante subtipagem (§SG-009),
  **não** checa elemento de coleção, **não** impõe `private`/`abstract` em
  compile-time, **não** impede reatribuição de `val`.
- **Problema**: "strongly typed" é vago e, lido como "o compilador impede
  operações mal tipadas", é **falso** para Kof hoje.
- **Recomendação**: substituir por propriedades concretas (já feitas em
  [language-reference/type-system.md](language-reference/type-system.md)).
  Manter "estaticamente tipada" (verdadeiro: tipos resolvidos em compile-time).

---

## Categoria B — Comportamento não especificado (Unspecified)

### SG-004 — (resolvido na auditoria) `bool→numérico`

- **Implementação**: `bool` é armazenado como `int` 1/0; `var i: Int = true`
  → `1` (*probe*). `isAssignable` aceita por `primitiveWidth(bool)=0`.
- **Problema**: a coerção funciona por **acidente de representação**, não por
  regra. Não há teste dedicado.
- **Recomendação**: decidir se é regra da linguagem (documentar + testar) ou
  deve ser rejeitada (SEM002 já pega aritmética, mas não atribuição).

### SG-005 — Deref de `T?` sem narrowing não é erro

- **Implementação**: `var s: String? = "x"; s.length` **compila e roda**
  (*probe* → 1). O lowering desembrulha o receiver (`ExpressionTyper.java:143`).
- **Problema**: null-safety é **advisory**: o compilador não impede NPE. Se
  `s` fosse `null`, NPE em runtime.
- **Recomendação**: ou documentar que null-safety é parcial (só `if (x!=null)`
  estreita, deref direto é permitido), ou tornar deref de `T?` sem narrowing um
  erro (breaking change).

### SG-006 — Short-circuit de `&&`/`||` desligado no JS

- **Implementação**: `ExpressionLowerer.java:147-148` — o short-circuit por
  labels é emitido só quando `target != JS`. No JS, ambos os lados são
  avaliados.
- **Problema**: `if (x != null && x.length > 0)` pode NPE no JS mas não no
  JVM/Native. **Divergência de paridade** (regra 5 de congelamento).
- **Recomendação**: documentar como Target-specific (feito em
  [expressions.md](language-reference/expressions.md) §5) **e** abrir gap de
  paridade para corrigir o JS.

### SG-007 — Wildcard de genérico (`? extends T`) compila mas quebra

- **Implementação**: `List<? extends Int>` é parseado (o `?` vira sufixo
  nullable, `extends Int` entra no nome) e **roda com
  `NoClassDefFoundError: ?extendsInt`** (*probe*).
- **Problema**: sintaxe aceita sem significado — pior que erro claro (viola R6
  "nunca silencioso").
- **Recomendação**: rejeitar `?` dentro de type-args com diagnóstico (PARSE ou
  SEM). Não é feature da linguagem.

### SG-008 — Comparação de nullable de primitivo com `null`

- **Implementação**: `Int? a = null; a == null` → **NPE em runtime** (*probe*:
  o unbox do `Integer` null lança). `String? s = null; s == null` → `true`
  corretamente.
- **Problema**: inconsistência entre nullable de primitivo e de referência.
- **Recomendação**: tratar `T? == null` para `T` primitivo como comparação de
  referência (sem unbox). Bug — registrar em `known-bugs.md`.

### SG-009 — Subtipagem não é checada pelo type checker

- **Implementação**: `isAssignable` retorna `true` para **qualquer** par
  `ClassType→ClassType` (`TypeChecker.isAssignable`). A segurança vem
  do `checkcast` do lowering/runtime.
- **Problema**: `A a = <objeto de classe não-relacionada>` passa na checagem de
  tipos; falha só em runtime. `implements` sem cobrir métodos compila (SG-015).
  Abstract pode ser instanciado (SG-017).
- **Recomendação**: implementar checagem de subtipagem nominal em
  `isAssignable` (caminhando `superClass`/`interfaces` via
  `resolveInHierarchy`). É a maior lacuna de segurança de tipos. **Não
  implementado aqui** (mudança de comportamento — exige suíte + possibly bump).

### SG-010 — `val` não impede reatribuição

- **Implementação**: `val x = 1; x = 2` **compila e roda** (imprime 2, *probe*
  confirmado isoladamente). Não há flag de imutabilidade no `VarDeclStmt`
  (só `type`/`name`/`initializer` — `AstNodes.java:351`).
- **Documentação**: `AGENTS.md` "val y = 20 // imutável".
- **Problema**: `val` é decorativo. A distinção `val`/`var` não tem efeito
  observável.
- **Recomendação**: ou implementar rejeição de atribuição a `val` (SEM novo), ou
  documentar que `val` é convenção (não-garantido). Decisão de design.

### SG-011 — Função aninhada e sobrecarga top-level

- **Implementação**: função dentro de função não é parseada como declaração
  (SG-011); duas funções top-level homônimas colidem sem diagnóstico claro
  (o `define` sobrescreve).
- **Recomendação**: especificar (erro? último-vence?) e testar.

### SG-012 — Inferência de tipo de parâmetro de lambda

- **Implementação**: `(x) -> x + 1` → `x` é `Object` → `SEM001`. A tabela de
  `map` sabe que o elemento é `Int`, mas não propaga ao corpo.
- **Problema**: força anotação mesmo quando o tipo é óbvio do contexto.
- **Recomendação**: é uma **limitação** conhecida; documentar (feito em
  [closures.md](language-reference/closures.md) §2). Inferência contextual é
  feature futura.

### SG-013 — `private`/`protected` não são checados em compile-time

- **Implementação**: viram flags JVM; acesso indevido → `IllegalAccessError` em
  **runtime** (*probe*).
- **Recomendação**: adicionar checagem de visibilidade no analyzer (SEM novo).

### SG-014 — Pattern matching sem guardas/aninhamento

- **Implementação**: só `case Type var` e `case Type(a,b)` (top-level).
- **Recomendação**: documentar como limite (feito). Guardas/aninhados são
  planned.

### SG-015 — `implements` não exige cobrir métodos abstratos

- **Implementação**: `class C implements I {}` (com `I.f()` abstrato) compila
  (*probe*); falha runtime (`AbstractMethodError`).
- **Recomendação**: checagem de implementação completa no analyzer (ligado a
  SG-009).

### SG-016 — Semântica de classes aninhadas

- **Implementação**: parser aceita `class` dentro de `class`
  (`parseClassMember:740`), mas não há teste que fixe o nomeamento (`A.B`?
  `B`? pacote?).
- **Recomendação**: especificar e testar.

### SG-017 — `abstract class` instanciável em compile-time

- **Implementação**: `new A()` de classe abstrata compila; `InstantiationError`
  runtime (*probe*).
- **Recomendação**: erro de compilação (SEM novo).

### SG-018 — Exit code de `Int main()`

- **Implementação**: o emit JVM gera `void main`; o `Int` retornado é ignorado.
- **Recomendação**: decidir se `Int main()` define exit code (útil para CLI) ou
  não (remover a forma). Hoje Unspecified.

### SG-019 — Cláusula `throws` é decorativa

- **Implementação**: `parseThrows` captura, mas nada valida (exceções são
  String, não há checked).
- **Recomendação**: documentar como metadado (não contrato) ou remover.

### SG-020 — Modelo de memória concorrente ausente

- **Implementação**: `spawn`/`await`/`Channel` funcionam, mas não há definição
  de happens-before/visibilidade/atomicidade.
- **Recomendação**: para uma spec de conformidade, adotar um modelo (mesmo que
  "sequentially consistent por target"). Hoje Unspecified.

---

## Categoria C — Divergências entre targets (paridade)

| # | Divergência | JVM | Native | JS | Gap |
|---|---|---|---|---|---|
| SG-C1 | Short-circuit `&&`/`||` | ✅ | ✅ | ❌ | SG-006 |
| SG-C2 | Exceção (representação) | RuntimeException | kof_panic | throw string | Stable efeito |
| SG-C3 | GC | JVM | free-list/mark-sweep (x86); bump (riscv) | engine | Target-specific |
| SG-C4 | FP extremo | IEEE | IEEE (FLT001) | IEEE | FLT001 |
| SG-C5 | Interop tipos host | ✅ | ❌ | ❌ | Target-specific |
| SG-C6 | `println(null)` | "null" | (corrigido R6) | "null" | — |
| SG-C7 | Map/Set type-arg classe | ❌ bug#33 | ❌ bug#33 | ❌ bug#33 | #33 |
| SG-C8 | `spawn{lambda}` handle | ❌ bug#29 | ❌ bug#29 | ❌ bug#29 | #29 |

---

## Categoria D — Bugs conhecidos (referência cruzada)

Não duplicados aqui — ver [known-bugs.md](known-bugs.md):
- **#29** spawn{lambda}-com-handle (todos os targets)
- **#30** decode<Bool> x86_64 (corrigido)
- **#31** process.<inexistente>
- **#32** type-arg genérico via import (corrigido — `qualifyDeep`)
- **#33** Map/Set com type-arg de classe (emit) — **aberto**

---

## Categoria E — Documentação desatualizada (docs ≠ código)

### SG-E1 — `docs/architecture.md` chama riscv64/aarch64 de "placeholder x86_64"

- **Doc** (`architecture.md:40-46,97-98`): "codegen ainda x86_64 (placeholder)".
- **Código**: `NativeBackend.emitRiscv` (`:1947`) é lowering riscv64 **real**;
  aarch64 via `translateRiscvToAarch64` (`:8200`). `docs/status.md:668,737`
  confirma "core completo".
- **Problema**: a arquitetura está **1 versão desatualizada** (0.2.6 → 0.3.0).
- **Recomendação**: atualizar `architecture.md` (feito parcialmente em
  [compiler-architecture.md](compiler-architecture.md); o arquivo antigo deve
  apontar para o novo).

### SG-E2 — `docs/language-state.md` data 02/09, versão 0.2.6-beta

- Conta 810 testes; hoje são **969**. Versão 0.2.6; hoje 0.3.0.
- **Recomendação**: regenerar ou marcar como snapshot histórico.

### SG-E3 — `docs/architecture.md` lista "KofC Backend" como backend da IR

- **Doc**: mostra `KofC Backend` no pipeline consumindo a IR.
- **Código**: `KofCCompiler` **não** implementa `Backend` nem consome
  `IRModule` — é um compilador C-subset separado (`kof-c-compiler`).
- **Recomendação**: corrigir o diagrama (o pipeline de IR tem 3 backends:
  JVM/Native/JS; Android é JVM+empacotamento).

---

## Resumo

- **20 gaps SG-00x** (A: contradições doc/código; B: comportamento não
  especificado).
- **8 divergências de target** (C).
- **3 docs desatualizados** (E).
- **5 bugs** (D, já em known-bugs).

**Nenhum foi corrigido na linguagem** — esta tarefa é de documentação. Cada
item B/C que envolve mudança de semântica é **decisão de design** (regra 6:
semântica congelada) e deve virar gap/plano em `planning-*`, nunca edição
silenciosa.
