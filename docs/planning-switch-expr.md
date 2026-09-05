# Plano — `SwitchExpr`: switch como expressão

**Criado:** 03/09/2026
**Gap:** `SYN001`
**Dono:** agente-switch-expr
**Base:** `main` (após `b77249c`)

> Proposta: pattern matching através de **expressões** (switch expression),
> no espírito de Java 14 switch expression / Rust `match` / Ruby `case...in`.
> Motivação: switch *statement* tem pass-through, escopo de case esquisito e
> verbosidade de `break`; a forma expressão elimina os três.

---

## 0. Estado real do `switch` no Kof (verificado no compilador)

Antes da mudança, o que o compilador **já** faz (importante para o escopo):

- **Não existe pass-through.** Cada case é baixado como cadeia if/else e
  termina com `KofJump(end)` incondicional (`CompilerDriver.java`, case
  `SwitchStmt`, linhas ~2682 e ~2727).
- **`break` dentro de case é no-op.** O lowering do `SwitchStmt` nunca empilha
  label de `break` (`breakLabels` vazio), então `BreakStmt` emite nada
  (`CompilerDriver.java:2153-2155`). O `break` nos exemplos de
  `training/anti-patterns/fake-idioms.md` é decorativo.
- **Exaustividade de enum já é erro** (`SEM031`): switch sobre enum sem
  `default` e sem cobrir todas as constantes não compila.
- **Pattern matching já existe** na forma statement: `case String s:` e
  `case Point(var x, var y):` (`Parser.parseSwitchStatement`).
- **Precedente de expressão já existe:** `if` já é expressão (`IfExpr`,
  `var s = if (c) "a" else "b"`).

Ou seja: o "perigo" do switch statement (fall-through) **já foi removido** no
Kof. O valor real da proposta é **switch como expressão** — usável como valor
(`var x = switch (obj) { ... }`, `return switch ... { }`, aninhado).

## 1. Forma proposta (aditiva)

```kof
// SWITCH-EXPRESSION — novo, cases com `->`, corpo = única expressão
var label = switch (obj) {
    case String s -> s
    case Point(var x, var y) -> x + "," + y
    default -> "outro"
}

// SWITCH-STATEMENT — existente, continua 100% válido (retrocompatibilidade)
switch (op) {
    case "GET":     doGet()
    case "POST":    doPost()
    default:        throw "op desconhecida: " + op
}
```

Regras:

1. **Aditivo.** `case ... :` (statement) e `case ... ->` (expression)
   coexistem; a escolha é por token. Código antigo compila inalterado.
2. **`default` obrigatório** na forma expressão (ou exaustividade de enum,
   como no `SEM031` do statement). Sem default nem exaustão → erro
   `SEM032` (diagnóstico claro, nunca cair silenciosamente).
3. **Corpo de case = UMA expressão** (sem escopo de bloco → sem o "escopo
   esquisito" apontado na proposta). `break`/`continue` não têm lugar; a
   expressão não pode conter `return` (o switch é o valor).
4. **Pattern binding** (`case String s ->`, `case Point(var x, var y) ->`)
   segue a semântica do statement: o binding é visível só na expressão do
   case.
5. **Tipo do resultado:** tipo comum dos braços (como `IfExpr`): se todos
   iguais, esse; senão, o do `default`. Sem coerção mágica.

## 2. Onde o código mora (arquitetura)

| Camada | Arquivo | Δ linhas | Nota |
|---|---|---|---|
| AST | `AstNodes.java` | +12 | `SwitchExpr` + `SwitchExprCase` (expression node) |
| Parser | `Parser.java` | +55 | `parseSwitchExpression` (arrow); dispatch no `parseExpression` |
| Semântica | `SemanticAnalyzer.java` | +35 | `inferType(SwitchExpr)` + escopo de pattern via helper compartilhado |
| KIR (JVM+Native) | `CompilerDriver.java` | +90 | `emitSwitchExpr` — cadeia `CJump`/`Label`/expr, como `IfExpr`; **cobre JVM e Native de uma vez** (KIR comum) |
| JS | `JsBackend.java` | +20 | generalização de `tryParseIfExpr` para tolerar prologue de binding (`StoreLocal`) dentro do braço |
| Formatter | `KofFormatter.java` | +10 | debug/print de `SwitchExpr` |
| Teste | `KofSwitchExprE2ETest.java` (novo) | ~180 | E2E JVM/Native/JS + backward-compat do statement |
| Docs | `training/` + `docs/status.md` + `docs/backend-parity.md` | — | idiom novo + gap SYN001 fechado |

> Por que não extrair o lowering num arquivo novo? O `emitSwitchExpr`
> depende de estado privado do `CompilerDriver` (`currentUnit`,
> `enumConstantsOf`, `emitExpression`, `inferExprType`, debug positions) —
> uma extração limpa exigiria uma interface de contexto desproporcional a
> ~90 linhas. Crescimento por arquivo fica pequeno e coeso; extração maior é
> refactor futuro (mesma nota que o repo já carrega para `NativeRuntime`).

## 3. Lowering (KIR) — desenho

`emitSwitchExpr` reusa a estratégia de comparação do `SwitchStmt`:

```
load subject → #switchExpr
[sem pattern]  por case:  load #switchExpr; <caseValue>; SUB/EQ|kof_string_equals/NE; CJump(body_i | next)
[pattern]      por case:  load #switchExpr; KofInstanceOf T; 0; CJump EQ(body_i | next)
Label body_i:
    [pattern simples]      load #switchExpr; KofCheckCast T; store s
    [pattern destructura]  load #switchExpr; KofCheckCast T; store #t;
                           load #t; KofLoadField x; store x;  (por campo)
    <ops da expressão do corpo>     (deixa 1 valor na pilha)
    Jump end
Label default: <ops da expressão do default>
Label end:     (1 valor na pilha = resultado)
```

Diferença crucial do statement: **cada braço deixa exatamente 1 valor na
pilha** (a expressão) em vez de emitir statements. O `KofPop` que o
`ExpressionStmt` faria é omitido — o valor é o resultado do switch.

Backend **JS**: não precisa de nova estrutura. `parseExpressionFragment`
já recursa em `CJump+Label` aninhado (comentário "if-expression or a
nested if-statement inside a branch", `JsBackend.java:1835-1847`); o
`SwitchExpr` aparece como cadeia de `IfExpr`/`JsConditional`. A única
mudança: `tryParseIfExpr` tolera o **prologue de binding** do caso pattern
(`LoadLocal; CheckCast; StoreLocal` antes da expressão do braço) — sem isso,
o `break` do fragment em `KofStoreLocal` impede o reconhecimento.

## 4. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| Regressão no `switch` statement (o mais usado) | statement **não é tocado**; teste de backward-compat roda o corpus atual (`KofPatternMatchingTest`, `KofEnumSwitchTest`) como gate |
| Backend JS (re-parse do KIR op a op) | reuse do caminho `IfExpr` já testado; novo teste `KofSwitchExprE2ETest` com `Js` por forma (value, pattern, destructuring, default) |
| `Optimizer`/`IRStatistics` não conhecem a nova op | **não** há nova `KofOperation` — reuso total de ops existentes (zero mudança nesses arquivos) |
| `collectCaptures` (lambda) não enxerga o binding | novo ramo em `collectCapturesStmts`/`Expr` espelhando o do `SwitchStmt` |
| Suíte (gate 840) | roda completa antes do commit; golden E2E por target já cobre o statement |

## 5. DoD (Definition of Done)

- [ ] `var x = switch (obj) { case ... -> ...; default -> ... }` compila e
      roda em **JVM, Native (x86_64, riscv64, aarch64) e JS**
- [ ] Pattern simples (`case String s ->`) e destructuring
      (`case Point(var x, var y) ->`) nos 3 targets
- [ ] `default` ausente em switch expr não-exaustivo → `SEM032` (erro claro)
- [ ] Switch statement existente: suíte atual 100% verde (retrocompat)
- [ ] Suíte completa `mvn test -o -pl kof-compiler,kof-script,kof-c-compiler,kof-cli -am` verde
- [ ] `training/idioms/` + `fake-idioms.md` atualizados (idiom novo)
- [ ] `docs/status.md` + `docs/backend-parity.md` + `DOING.md` → `FEITO`
