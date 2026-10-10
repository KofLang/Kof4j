[English](README.md) | [Português](README.pt_BR.md)

# Paridade Total — a regra ABSOLUTA da release 0.5.0

> **Mantenedora, 24/09: A REGRA ABSOLUTA PARA QUALQUER PLANO É A PARIDADE
> TOTAL.** Toda superfície do Kof funciona em TODO alvo — JVM/Script, Native
> x86-64, Native riscv64/aarch64, JS — com paridade byte/golden contra o
> oráculo JVM. Um código de gap honesto é o rastreador, nunca o estado final.
> A release 0.5.0 não corta enquanto o ledger tiver linhas abertas.

## Arquivos

- [`PARITY-GAPS.md`](PARITY-GAPS.pt_BR.md) — **o ledger impeditivo**: cada
  linha de paridade parcial medida (superfície × alvo × código de gap × lane
  dona). O gate de máquina da 0.5.0 `check_release_050_gate.sh` foi
  **aposentado** (`D-RELEASE-0.5.0-CLOSED`, 28/09); a autoridade viva do
  conjunto solto é agora `scripts/check_live_records.sh`, e a promoção de
  release é regida por `docs/development/quality-pipeline.md`
  (`D-QUALITY-PIPELINE-2609`) — qualquer linha aberta ainda significa que a
  próxima promoção NÃO está verde.

## Como uma linha fecha

Veja o "Definition of done" no ledger: compilar + prova golden/E2E byte a
byte + tabelas de docs atualizadas no mesmo commit + linha removida no mesmo
commit.
