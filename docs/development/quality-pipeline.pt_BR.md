# Esteira de qualidade — política de branches executável

last: 14.3-promotion-tooling (gate de suite + contagens scripted, #657)
doing: automation-enforcement
next: 14.4-rulesets+dispatch-on-main (mantenedora)
location: docs/development
state: active

intent: executable-branch-quality-policy

constraint:

* lab é o único ponto de entrada de desenvolvimento
* toda promoção é explícita, unidirecional e auditável
* nenhum push direto para um estágio posterior
* qualquer falha retorna para lab
* falha fechado quando o estado não pode ser determinado
* automação determinística e idempotente

## Esteira

`lab → testing → prerelease → stable → release/x.y.z → tag`

| Estágio | Papel | Gate para sair | Protegida |
|---|---|---|---|
| `lab` | desenvolvimento + correções | todos os checks obrigatórios PASS | não |
| `testing` | integração/QA | todos os checks PASS, 0 issue bloqueante | sim |
| `prerelease` | candidato público | janela de 7 dias, 0 issue relacionada, checks PASS | sim |
| `stable` | contrato fechado | todos os checks obrigatórios PASS | sim |
| `release/x.y.z` | apenas empacotamento | todos os checks PASS, versão ainda não tagueada | sim |
| tag `kof-*` | contrato público | — | imutável |

O denominador `≥80%` foi **dropado** (`D-QUALITY-PIPELINE-2609`, mantenedora
28/09/2026): toda promoção é 100%.

## Estados

`LAB → TESTING → PRERELEASE → PRERELEASE_OBSERVATION → STABLE → RELEASE → TAGGED`

Falha nunca abre fluxo paralelo: `TESTING|PRERELEASE|STABLE|RELEASE → BLOCKED → LAB`.

A máquina é código, não convenção: `scripts/pipeline/pipeline_state.py`. Transição
fora de `ALLOWED`, branch desconhecida ou no-op é **recusada** (fail closed).
`lab→prerelease`, `lab→stable`, `testing→stable`, `prerelease→release`, `lab→release`
e `stable→prerelease` são bloqueadas por teste.

## Promotion Gate

`scripts/pipeline/promotion_gate.py` avalia um passo: reusa a máquina de estados e
exige 100% dos checks obrigatórios fixos (Build + Tests, Native cross, kof.io
multiplatform, Structural quality gates, CodeQL Gate, bots), a janela de observação
completa com 0 issues relacionadas (`prerelease→stable`) e criação de tag idempotente.
É função pura dos inputs — determinística, sem writes, sem rede — portanto segura
para reexecutar.

Campos do relatório: estágio/estado de/para, status, resultado por check, commit,
versão, timestamp, issues bloqueantes/relacionadas, motivos e a ação
(`return_to: lab`) em caso de falha.

## Enforcement

* Rulesets (servidor): `pipeline-stages` bloqueia push direto, force-push e remoção
  em `main`/`testing`/`prerelease`/`stable`/`release/*`, e exige PR + status checks;
  `release-tags` torna as tags `kof-*` imutáveis. `lab` é aberta.
* Workflow `.github/workflows/promote.yml` (manual, idempotente): valida a transição,
  roda a SUITE COMPLETA no tip exato despachado como primeiro gate formal (o `lab`
  não tem CI por push por design — a suite é a medição, nunca um check-run velho),
  mescla esse veredito com os check-runs do tip e as contagens MEDIDAS de issues
  (`promotion_evidence.py`: bloqueantes = issues `bug` abertas; relacionadas =
  issues atualizadas desde `promoted-at`), roda o gate e abre um PR de promoção
  auditável apenas quando PASSA. Nunca faz push em estágio, e as contagens nunca
  têm default — omiti-las é recusado (opinião é proibida).
* Concorrência agrupada por estágio de destino; execuções duplicadas são no-op.

## Issue linkage

O bloqueio é determinístico: a promoção é recusada quando a contagem de issues
bloqueantes (`testing→prerelease`) ou de issues relacionadas na janela
(`prerelease→stable`) é não-zero. A associação é por versão/commit/tag, nunca por
heurística de texto livre.

## Política de correção

> Encontrou problema? Volta para `lab`.

Correção nunca é feita em `testing`, `prerelease`, `stable` ou `release/x.y.z` para
depois seguir de lado. Nasce em `lab` e percorre toda a esteira; uma nova pre-release
inicia sua própria janela de observação.

## Contrato

`D-QUALITY-PIPELINE-2609` / `D-BRANCH-PIPELINE` (`docs/development/DECISIONS.md`).
Anúncio do cutover e instruções de migração: issue #647.
