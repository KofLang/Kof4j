# Planning — `finally` no caminho `return` do `try` (DD-01)

> **Status:** `PROPOSED` (aguarda decisão da mantenedora) · **Gap:** bug 45 ·
> **Lane:** lowerers · **Criado:** 08/09/2026 · **Bump proposto:** 0.3.0 → 0.3.1

## O conflito

`training/idioms/errors.md:107` documenta o comportamento **previsto**:

> `finally` roda no caminho normal, no caminho capturado e na propagação.

`return` dentro de `try` **é** caminho normal. Esperado (Java/Kotlin e o corpus
do Kof): o `finally` roda **e** o valor do `return` é preservado.

O código atual, no entanto:

| Target | `Int f(){ try { return 1 } finally { println("fin") } }` | Roda `fin`? | Valor |
|---|---|---|---|
| JVM | `1` | **não** | ok |
| Native | `1` | **não** | ok |
| Interpretador | `1` | **não** | ok |
| JS | `fin` + `undefined` | sim | **perdido** |

Os 3 primeiros **concordam no errado** (descartam o efeito colateral do
`finally`); o JS roda o `finally` mas **perde o valor**. Nenhum dos 4 atinge o
previsto. O agente anterior (07/09) rotulou os 3 de "congelado por construção"
(regra 6), mas isso **contradiz o corpus** → pela regra 4, o previsto é lei:
isto é **bug de código**, não decisão de design congelada.

**Por que ainda é decisão de design *como implementá-lo*:** consertar muda a
ordem de avaliação (regra 6 — "semântica congelada 0.2.6-beta": ordem de
avaliação). A *direção* está no corpus (rodar `finally`); o *mecanismo* e o
impacto nos 4 backends + no reconstructor JS exigem vivência → este DD pede
bump + assinatura antes de editar o lowering.

## Proposta de correção (lowering único, propaga via IR)

Hoje o retorno-do-try faz `return` direto, pulando o bloco `finally`. Proposta
(espelho do que o JVM bytecode real faz):

1. lowering de `TryStmt` com `finally` abre um **frame** na pilha do driver
   (`Deque<FinallyFrame>` — análogo ao `breakLabels`):
   `{ rethrowLabel, finallyLabel, slotValor (se a função retorna valor), returnType }`.
2. lowering de `ReturnStmt` com frame ativo: em vez de `return` direto, faz
   `KofStoreLocal(#retVal, slot)` → `KofJump(finallyLabel)`; o epílogo
   `finally` termina com `KofLoadLocal(#retVal)` + `KofReturn`. (Void:
   só `KofJump(finallyLabel)`, o epílogo cai em `doneLabel`.)
3. `CompilerLambdaClass` (corpo de lambda lowered no mesmo driver) **salva e
   zera** a pilha ao entrar, restaura ao sair — senão um `finally` do método
   externo vazaria para dentro do lambda (mesmo padrão de `savedMutated`).
4. `JsControlFlowParser.parseTryStatement`: reconhecer a nova forma de IR
   (store→jump-finally→load+return) preservando o valor — o JS **reconstrói**
   try/finally da IR, não emite raw; sem este ponto o JS regridiria.

Alternativa (menor risco de IR, maior risco de parser): o backend JVM já tem
`finally` nativo; mas Native/interp/JS não têm um "finally real" — todos
consomem a IR — então o fix **precisa** ser na IR (opção acima), não por
target.

## Gate de aceite (regra 3: refactor preserva semântica; aqui é *correção*)

- Casos novos na `ConformanceMatrixTest` (4 targets): `fin`+`1` no
  return-no-try; `fin`+`1` no catch-return; `fin`+`2` no try-normal;
  propagação mantém o throw **depois** do finally.
- `finally` com `return` **dentro** do próprio finally (sombra o do try) —
  edge case a decidir (Java: finally return vence).
- Golden/E2E por target; `BackendParityTest.finally-return` sai da exclusão.
- Bump 0.3.1 no `KofVersion` + nota em `docs/` (mudança de ordem de avaliação).

## Riscos

- Ordem de avaliação é congelada (0.2.6-beta): qualquer mudança precisa de
  bump + doc + migração. Este DD pede essa autorização; **não** editar o
  lowering antes dela (regra 6 + modo autônomo condição de parada 1).
- O `Optimizer` pode encolher/reordenar os ops do epílogo — validar que o
  store/load do `#retVal` sobrevive à otimização (slot novo, sem outros
  leitores → risco de DCE). Mitigação: `locals.add` nomeado `#retVal` + teste
  com `-Xverify:all` (kitchen-sink já roda o verifier estrito).
