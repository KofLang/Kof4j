# Scoped Resources — RAII leve (plano de design · TIER 2.4)

**Status:** Plano (design) — implementação gated por bump de versão (semântica congelada)
**Fonte:** `PLAN-UNIVERSAL-PLATFORM.md` §7 · `ACTION_PLAN.md` TIER 2.4

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
using (conn = db.connect(url)) {
    validate(conn)
    store(conn, record)
}                       // conn.close() roda mesmo se `store` lançar
```

Desugar (compile-time, idêntico ao `CodegenStep` já formalizado):

```kof
{
    var conn = db.connect(url)
    try {
        validate(conn)
        store(conn, record)
    } finally {
        conn.close()
    }
}
```

### Variações consideradas

| Nome | Síntaxe | Veredito |
|------|---------|----------|
| `using (x = expr) { }` | explícito, `close()` convenção | ✅ candidata (familiar, sem ownership) |
| `scoped { }` | implícito (qualquer recurso no escopo) | ❌ mágica — exige análise de "recurse" |
| `with` | colide com semântica de `switch`/pattern | ❌ |

## 3. Semântica

- `using (x = e) { body }` declara um vincolet `x` escopado ao bloco.
- O cleanup é **uma função de convenção** `close()` no tipo do recurso
  (compilada como `x.close()`); se o tipo não expõe `close()`, diagnóstico
  compile-time (nunca fallback silencioso — R6).
- O `finally` garante o fechamento em **ambos** os caminhos (sucesso/exceção).
- Multiple resources: `using (a = f(); b = g()) { }` fecha em ordem reversa
  (`b.close()` → `a.close()`), como `try-with-resources`.
- **Sem** transfer of ownership; `x` não escapa do bloco (retorno/atributo
  externo é erro — o compilador não tenta "mover").

## 4. Non-goals (não é isto)

- Não é `ownership`/`borrowing` (E, UNIVERSAL §7).
- Não é effect system completo (D, pesquisa).
- Não é annotation `@AutoClose`.
- Não adiciona tipo `Resource`/interface na stdlib *antes* de decidir a forma
  da fronteira FFI/GPU (2.1.6).

## 5. Como pluga no codegen existente

O desugar entra no pipeline `CodegenStep` (TIER 2.2.2, já implementado em
`CompilerDriver.runCodegen`), ladeado por `desugarTests`/`desugarApplication`:

```text
unit → desugarUsing → desugarTests → desugarApplication → lowering
```

## 6. Gate e ordem

| Item | Estado |
|------|--------|
| Mecanismo (`try/finally` + GC) | ✅ já existe |
| Hook de desugar (`CodegenStep`) | ✅ TIER 2.2.2 |
| Sintaxe `using` | ⏳ **gated por R12** (SYSTEMS) + bump 0.3.0 (semântica congelada) |
| Convenção `close()` + diagnóstico | ⏳ mesmo gate |

> Implementar a sintaxe agora violaria "semântica congelada" (AGENTS.md) —
> mudança de linguagem exige **bump de versão + discussão**, nunca adição
> silenciosa.