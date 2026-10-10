[English](scoped-resources-plan.md) | [Português](scoped-resources-plan.pt_BR.md)

# Scoped Resources — RAII leve (plano de design · TIER 2.4)

**Status:** CONCLUÍDO 28/09 (lane issues — `D-SCOPED-RESOURCES-GO`, `D-FUTURE-PROMOTION`, mais barato implementável) — movido para `docs/` (regra dos três estados).
**Dona:** lane issues (esta sessão); desugar pré-lowering = todos os alvos por construção.
**Decisão:** `D-SCOPED-RESOURCES-GO` (`DECISIONS.md`) — sintaxe `using` autorizada (RAII leve, desugar mapeado, sem ownership); superfície travada durante a implementação (o plano foi dono dela).
**Estado real (landed 28/09):** parser (`parser/UsingParser.java`, `using` contextual + `(`) + `UsingStmt` + `CompilerDesugar.desugarUsing` PRIMEIRO em `DesugarSteps.defaults()` (roda em `CompilerPipeline:320`); zero mudança de typer/lowerer/codegen em qualquer alvo.
**Superfície v1 (travada):** `using (x = init, closer) { body }` → `{ var x = init; try { body } finally { closer } }`. O closer é EXPLÍCITO — o candidato `x.close()` do §2 é FALSO p/ `db` (o handle é String fechada via `db.close(handle)`; `learn/stdlib/db.md`); `conn.close()` / `sse.close()` seguem escrevíveis como closer. Closer ausente = erro de parse (R6, nunca leak silencioso). O vínculo é escopado ao bloco (sem escape por construção); disciplina de escape-pós-close fica com as passes de memory-safety (sem ownership aqui).
**Fatias 1–6 (todas landed, `UsingDesugarE2ETest` 18/18):** fatia 1 (sintaxe + desugar + pins happy/exceção/negativos); fatia 2 (reverse-close aninhado JVM/Script/JS/Native-x86 + `db` H2 happy/exceção na JVM com silêncio MEM014, sem edição cross-lane); fatia 3 (goldens Script de `db` + isolation-by-release); fatia 4 (docs de usuário `learn/14-exceptions.md`); fatia 5 (golden JS de `db`, byte-idêntico ao JVM); fatia 6 (goldens cross riscv64/aarch64 happy+nest sob qemu — o bloqueio ambiental caiu com o qemu user-mode no host). **Nada pendente:** `db` cross fora explicitamente (backends `db` nativos são da matriz da lane db); escape-pós-close fica com memory-safety (declarado, não escondido).
**Fonte:** `architecture/UNIVERSAL-PLATFORM-VISION.md` §7 · `development/roadmap.md` §23 TIER 2.4.1 (ex-`ACTION_PLAN.md`)

## 1. Objetivo

Liberar **recursos escassos** (handle de FFI, arquivo, conexão, GPU) ao sair do
escopo, sem introduzir `ownership`/`borrowing` (non-goal permanente).

A doutrina (UNIVERSAL §7) é explícita:

> *"Resource management (RAII/scoped): Sim, **leve** — lidar com handles de
> FFI, arquivos, GPU, conexões sem vazar. **B/C** — um `auto-closed`/scope leve
> (**sem ownership**)."*

Kof **já tem** o mecanismo (`try/finally` + GC). O scoped-resource é açúcar de
**intenção** sobre o mecanismo — o mesmo padrão já usado por `test "name" {}`
e `application { onStart/onShutdown }` (desugar em compile-time, zero runtime
especial).

## 2. Proposta (sintaxe candidata)

```kof
using (conn = db.connect(url), db.close(conn)) {
    validate(conn)
    store(conn, record)
}                       // db.close(conn) roda mesmo se `store` lançar
```

Desugar (compile-time, `CompilerDesugar.desugarUsing`, primeiro em
`DesugarSteps.defaults()`):

```kof
{
    var conn = db.connect(url)
    try {
        validate(conn)
        store(conn, record)
    } finally {
        db.close(conn)
    }
}
```

### Variações consideradas

| Nome | Síntaxe | Veredito |
|------|---------|----------|
| `using (x = init, closer) { }` | closer explícito | ✅ v1 travada (vale p/ todo idioma de close: `db.close(conn)`, `conn.close()`, `sse.close()`) |
| `using (x = expr) { }` + convenção `x.close()` | implícito | ❌ FALSO p/ `db` (handle String, sem membro `close` — `learn/stdlib/db.md`) |
| `scoped { }` | implícito (qualquer recurso no escopo) | ❌ mágica — exige análise de "recurse" |
| `with` | colide com semântica de `switch`/pattern | ❌ |

## 3. Semântica

- `using (x = init, closer) { body }` declara um vínculo `x` escopado ao bloco.
- O cleanup é o **closer explícito** (qualquer idioma que o tipo realmente tem:
  `db.close(conn)`, `conn.close()`, `sse.close()`); closer ausente é erro de
  parse (nunca fallback silencioso — R6).
- O `finally` garante o closer em **ambos** os caminhos (sucesso/exceção).
- Múltiplos recursos: aninhar `using` (fecha em ordem reversa pelo aninhamento),
  como `try-with-resources`.
- **Sem** transferência de ownership; `x` não escapa do bloco por construção
  (declarado dentro do bloco gerado).

## 4. Non-goals (não é isto)

- Não é `ownership`/`borrowing` (E, UNIVERSAL §7).
- Não é effect system completo (D, pesquisa).
- Não é annotation `@AutoClose`.
- Não adiciona tipo `Resource`/interface na stdlib *antes* de decidir a forma
  da fronteira FFI/GPU (2.1.6).

## 5. Como pluga no codegen existente

O desugar entra no pipeline `DesugarStep` (2.2.3, `DesugarSteps.defaults()`,
roda em `CompilerPipeline:320`), PRIMEIRO — as fases seguintes veem
try/finally puro:

```text
unit → desugarUsing → desugarTests → desugarApplication → lowering
```

## 6. Gate e ordem

| Item | Estado |
|------|--------|
| Mecanismo (`try/finally` + GC) | ✅ já existe |
| Hook de desugar (registro `DesugarStep`) | ✅ 2.2.3 (`DesugarSteps.defaults()`, `CompilerPipeline:320`) |
| Sintaxe `using` | ✅ AUTORIZADA 28/09 (`D-SCOPED-RESOURCES-GO`, lote `D-FUTURE-BATCH-2809`) |
| Closer explícito + diagnóstico de closer ausente | ✅ fatia 1 (`UsingDesugarE2ETest` 7/7) |
| Aninhamento (reverse close) + integração `db` + silêncio MEM014 | ✅ fatia 2 (`UsingDesugarE2ETest` 11/11; H2-hermético, sem fixture) |
| Goldens Script de `db` + isolation-by-release | ✅ fatia 3 (`UsingDesugarE2ETest` 13/13; nomes mem divididos, sem `DB_CLOSE_DELAY`) |
| Docs de usuário (seção `using` em `learn/14-exceptions.md`) | ✅ fatia 4 (sample espelha o `DB_HAPPY` verde, escopo JVM-hermético anotado) |
| Golden JS de `db` | ✅ fatia 5 (`{"v":"a"}/closed` byte-idêntico ao JVM; nome mem próprio `usingjs` — host Graal divide o registry H2 da JVM; `UsingDesugarE2ETest` 14/14; exceção no JS segue pinada COMP002) |
| Goldens cross riscv/aarch | ✅ fatia 6 (`UsingDesugarE2ETest` 18/18; qemu user-mode no host, as/ld do sysroot + `QEMU_LD_PREFIX`) |
| `db` cross | ➖ fora explicitamente — backends `db` nativos são da matriz da lane db |
| Análise de escape-pós-close | ⏳ lane memory-safety (sem ownership neste plano) |

> O estágio SYSTEMS (Tier 1) já fechou (03/09 — DOING.md), então o TIER 2 é
> aberto. O gate de semântica congelada caiu 28/09: `D-SCOPED-RESOURCES-GO`
> (lote `D-FUTURE-BATCH-2809`) autoriza explicitamente a sintaxe `using` —
> a fatia 1 entrega o design + o desugar, sem espera de 0.3.0.