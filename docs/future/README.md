# docs/future/ — só plano futuro (zero código)

**Regra desta pasta:** aqui vive **apenas** o que é **plano para o futuro** —
documento de arquitetura/visão **sem código implementado** (ou com código que
é explicitamente não-entregável e fora do escopo atual).

> Se uma ideia **já está sendo implementada** (mesmo parcialmente), o doc
> correspondente **não fica aqui** — ele vive em `docs/` e documenta o **estado
> real** (o que já existe) + **como finalizar**. Assim quem lê sabe exatamente
> onde a coisa está e o que falta.

## Exemplo recente (01/09)

- `kof-native-risc-arm.md` **saiu daqui** para `docs/native-multiarch.md`: o
  plumbing (enum `Target.NATIVE_RISCV64/AARCH64`, CLI `native.risc/arm`,
  dispatch, cross-as/ld) já está no código, então o item é **em desenvolvimento**
  e passou a ser documentado com estado real + plano de finalização.

## Movidos para `docs/` (05/09 — implementação existe)

A plataforma de migração legado saiu de "plano" para "em desenvolvimento" e
foi movida para `docs/`:

| Doc (agora em `docs/`) | Implementação |
|-----|------|
| `DECOMPILER.md` → `docs/decompiler.md` | `kof decompile` (estrutural + `Confidence`) |
| `DIFFERENTIAL_TESTING.md` → `docs/differential-testing.md` | `kof compare` |
| `LEGACY_IR.md` → `docs/legacy-ir.md` | Fases B/C/D (`ClassFileParser`, CFG, Type Recovery) |
| `LEGACY_MIGRATION.md` → `docs/legacy-migration.md` | Fases A–H (`inspect/decompile/translate/compare/migrate`) |
| `TRANSLATOR.md` → `docs/translator.md` | `kof translate` (subset Java) |

## O que fica aqui (só plano, sem código)

| Doc | Tema | Por que fica em `future/` |
|-----|------|---------------------------|
| `PLAN-UNIVERSAL-PLATFORM.md` | visão de longo prazo (Kof como plataforma universal) | 100% visão/estratégia — não é ordem de implementação |
| `ACTION_PLAN.md` | ordem de implementação dos tiers | plano de ordens (mistura feito/pendente) |
| `IMPLEMENTATION_PLAN.md` | roadmap consolidado com status | rastreia o progresso (vivo) |
| `scoped-resources-plan.md` | RAII leve (`using`) | design pronto; gated por semântica congelada |
| `planning-future-reconcile.md` | notas de reconciliação planning↔beta | memória de trabalho |

## Quando mover de `future/` para `docs/`

Quando o item deixar de ser "só plano" e **houver código em desenvolvimento**,
mesmo parcial:

1. Mover/reescrever o doc em `docs/` com **status `EM DESENVOLVIMENTO`**;
2. Documentar **o que já está feito** (arquivos/linhas reais) vs **o que falta**;
3. Incluir seção **"como finalizar"** (passo a passo com dependências);
4. Atualizar `docs/backend-parity.md` / `docs/status.md` para apontar o novo
   caminho;
5. Manter o gap-code (ex.: `NATIVE002`) até o item fechar.
