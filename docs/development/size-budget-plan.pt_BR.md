[English](size-budget-plan.md) | [Português](size-budget-plan.pt_BR.md)

# Size Budget — plano

last: #704 aberta (motivação, opções, escopo)
doing: nenhum — a frente abre quando a mantenedora mergear a PR que adiciona este plano
next: fatia 1.1 — `scripts/size/measure-toolchain.sh` + selftest
location: docs/development/size-budget-plan.pt_BR.md
state: EM DESENVOLVIMENTO (no merge)

decision: `D-SIZE-BUDGET` (`DECISIONS.pt_BR.md`) · tracker: #704

constraint:

* fase-1-só-mede: nenhuma mudança de dependência, packaging, target ou comportamento
* sem-limite-absoluto-antes-da-baseline
* scripts read-only, offline, saída determinística
* unknown-em-vez-de-chute: face que não pode ser medida imprime `UNKNOWN` + motivo, nunca 0

---

## 1. Fatias da Fase 1

| # | Entrega | Prova |
|---|---|---|
| 1.1 | `scripts/size/measure-toolchain.sh` — bytes de cada jar de módulo (`kof-cli`, `kof-compiler`, `kof-runtime`, `kof-script`, `kof-c-compiler`) + arquivo de distribuição + árvore instalada | `--selftest` com arquivos plantados; saída TSV estável em duas execuções |
| 1.2 | `scripts/size/measure-dependencies.sh` — contagem e bytes diretos/transitivos por artefato, `kof-cli` shaded dividido por origem | `--selftest`; soma dos bytes atribuídos == entradas do jar shaded (não atribuído aparece, nunca some) |
| 1.3 | `scripts/size/measure-generated.sh` — `hello-world` por target (JVM, Native x86-64/riscv64/aarch64, JS, Script) | `--selftest`; toolchain ausente (as/ld/qemu) = linha `UNKNOWN`, não falha |
| 1.4 | `scripts/size/compare-size.sh A B` — size diff entre duas medições, agrupado core / opcional / gerado | `--selftest` com dois TSVs plantados (cresceu, encolheu, novo, removido) |
| 1.5 | `docs/audits/size-baseline.md` (+PT) — primeira baseline: commit, ambiente, as três tabelas, top 20 consumidores | produzida por 1.1–1.3 num SHA nomeado; nova execução reproduz os números |

## 2. Depois da Fase 1

Cada fase seguinte exige decisão própria da mantenedora, escolhida a partir da baseline:
atribuição → reduções de baixo risco (antes/depois/delta + suíte) → spikes de packaging → gate de CI `scripts/check_size_budget.sh`.

## 3. Pronto (Fase 1)

* 1.1–1.5 pousadas, cada uma com selftest ligado em `scripts/tests/run-agent-tests.sh`
* baseline publicada e reproduzível
* zero mudança fora de `scripts/size/` e `docs/audits/`
