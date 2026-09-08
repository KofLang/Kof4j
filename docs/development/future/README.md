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

## O que fica aqui (só plano, sem código)

| Doc | Tema | Por que fica em `future/` |
|-----|------|---------------------------|
| `APPLICATION_MODEL.md` | Kof Application Model (monólito ↔ distribuído, `kof.toml`, packaging, System) | RFC auditada — implementa nos incrementos I1–I4 (§23); move para `docs/` no I1 |
| `PLAN-UNIVERSAL-PLATFORM.md` | visão de longo prazo (Kof como plataforma universal) | 100% visão/estratégia — não é ordem de implementação |
| `DECOMPILER.md` | Kof Decompiler (bytecode/asm → Kof) | não há código de decompiler |
| `DIFFERENTIAL_TESTING.md` | teste diferencial de migrações | não há código |
| `LEGACY_IR.md` | Legacy Semantic IR | não há código (o "legacy" no `CompilerDriver` é genérico, não este) |
| `LEGACY_MIGRATION.md` | plataforma de migração de software legado | fora do escopo 0.0.x, sem código |
| `TRANSLATOR.md` | Kof Translator (Kof → outra linguagem) | não há código |

## Quando mover de `future/` para `docs/`

Quando o item deixar de ser "só plano" e **houver código em desenvolvimento**,
mesmo parcial:

1. Mover/reescrever o doc em `docs/` com **status `EM DESENVOLVIMENTO`**;
2. Documentar **o que já está feito** (arquivos/linhas reais) vs **o que falta**;
3. Incluir seção **"como finalizar"** (passo a passo com dependências);
4. Atualizar `docs/backend-parity.md` / `docs/status.md` para apontar o novo
   caminho;
5. Manter o gap-code (ex.: `NATIVE002`) até o item fechar.
