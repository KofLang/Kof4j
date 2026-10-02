[English](PROPOSAL-VERSIONING-RELEASE.md) | [Português](PROPOSAL-VERSIONING-RELEASE.pt_BR.md)

# KOF — Proposta de consolidação da política de versionamento e corte de releases

**Status:** `RATIFICADO — aprovado pela mantenedora (PR #582 mergeada 22/09/2026); MATERIALIZADO como D-VERSIONING-RELEASE (22/09/2026). **24/09: promovido para fora de `docs/development/` — a REGRA operante é o [`VERSIONING.pt_BR.md`](VERSIONING.pt_BR.md) (+EN) neste diretório; este documento é o registro da decisão/justificativa ratificada.**

**Status:** `RATIFICADA — aprovada pela mantenedora (PR #582 mergeado em 22/09/2026); MATERIALIZADA como D-VERSIONING-RELEASE (22/09/2026)`
**Branch-base:** `beta-0.5.0`
**Tip revalidado na elaboração:** `6c9aeb847f167e97ed48c21a9e4453028198da63`
**VERSION medido no tip:** `0.5.0-beta`
**Última release publicada medida:** `kof-0.4.9-beta-*` (família de tags Linux/Windows/macOS)
**DOING.md — colisão de lane:** nenhuma reivindicação ativa sobre `VERSIONING.md`, `release-naming.md`, `D-RELEASE`, `D-RELEASE-0.5.0-GATE`, `D-RELEASE-1.0`, `D-1.0-EDGES` ou "versionamento"/"versioning" encontrada na medição desta unidade.
**Decisão proposta:** `D-VERSIONING-RELEASE`
**Autoridade de aprovação:** **Mel / `melmonfre`**
**Regra de merge:** **PROIBIDO MERGEAR SEM APROVAÇÃO EXPLÍCITA DA MEL SOBRE O DIFF FINAL**

---

## 0. Objetivo

Consolidar em uma única política normativa os critérios do KOF para:

1. classificar uma mudança como `PATCH`, `MINOR` ou `MAJOR`;
2. decidir **quando avaliar** uma nova release;
3. decidir **quando um candidato está apto ao corte**;
4. preservar o `D-RELEASE-1.0` como gate integral da primeira release estável;
5. remover a ambiguidade atual entre `VERSIONING.md`, `D-RELEASE`, gates de release e a prática recente do projeto;
6. impedir que uma nova superfície pública seja publicada como simples patch por julgamento subjetivo de que a mudança seria "pequena" ou "materialmente pequena".

Este trabalho **não autoriza por si só nenhuma release, bump de versão, tag ou mudança de contrato**.

---

# 1. KOF-first — evidência obrigatória

Revalidado no tip `6c9aeb847f167e97ed48c21a9e4453028198da63` (branch `beta-0.5.0`, sincronizada com `origin`) na elaboração desta proposta:

1. `AGENTS.md` e `AGENTS.pt_BR.md` — lidos; rule 6 ("a decision made in chat lives here [DECISIONS.md]... never attack a front without a decision locked here") é a base normativa que esta proposta invoca sobre si mesma.
2. `docs/development/DECISIONS.md` e `.pt_BR.md` — lidos; `D-RELEASE` (§, 2026-09-14), `D-BRANCH-0.5.0`, `D-VERSION-BUMP-0.5.0`, `D-RELEASE-0.5.0-GATE` (+ condition 2 + `D-RELEASE-0.5.0-SCOPE`), `D-RELEASE-1.0`, `D-1.0-EDGES` confirmados presentes e `DECIDED`/`RATIFIED` conforme citado abaixo.
3. `docs/distribution/VERSIONING.md` e `.pt_BR.md` — lidos; confirma o drift descrito na §2.1: a seção "Current stage" ainda afirma `0.0.x` / Alpha e "every commit on main generates the next Alpha version (PATCH increment)", que não descreve o estado medido (`VERSION` = `0.5.0-beta`, branch dedicada `beta-0.5.0`).
4. `docs/distribution/release-naming.md` e `.pt_BR.md` — lidos; já usa `MAJOR.MINOR.PATCH-<stage>` e a tabela de codenames; nenhuma contradição normativa com a proposta.
5. `docs/distribution/release-beta-0.5.0.md` e `.pt_BR.md` — registro de aceitação do corte 0.5.0 (FECHADO 28/09); ortogonal a esta proposta.
6. `docs/PROPOSAL-1.0-EXIT-GATE.md` e `.pt_BR.md` — lido; `D-RELEASE-1.0`/EXIT GATE confirmado `RATIFIED`, tratado como intocável por esta proposta (§6).
7. `DOING.md` — nenhuma claim ativa (`EM CURSO`) sobre os arquivos desta frente na medição feita.
8. `VERSION` — `0.5.0-beta` no tip acima.
9. Branch ativa `beta-0.5.0`; última release publicada medida por tag: `kof-0.4.9-beta-linux-x86_64` (e pares windows/macos).

Este snapshot não substitui uma nova revalidação se o tip avançar antes da revisão da Mel.

### Bloco de evidência

```text
KOF VALIDITY:
N/A — governança/versionamento.

CONTRACT SOURCE:
VERSIONING.md
release-naming.md
D-RELEASE
D-BRANCH-0.5.0
D-VERSION-BUMP-0.5.0
D-RELEASE-0.5.0-GATE
D-RELEASE-0.5.0-SCOPE
D-RELEASE-1.0
D-1.0-EDGES

CURRENT KOF IDIOM:
MAJOR.MINOR.PATCH-<stage>

MEASUREMENT:
tip = 6c9aeb847f167e97ed48c21a9e4453028198da63 (origin/beta-0.5.0)
VERSION = 0.5.0-beta
última release = kof-0.4.9-beta-* (linux/windows/macos)
DOING.md = sem colisão de lane nesta frente

CLASSIFICATION:
CONTRACT AMBIGUITY / DESIGN REQUEST de governança.

DUPLICATE / PRECEDENT CHECK:
D-RELEASE já diferencia patch de minor.
D-RELEASE-1.0 já governa o primeiro major estável.
Não há hoje uma fonte única que una classificação + trigger + gate + corte.

ACTION:
propor D-VERSIONING-RELEASE; aguardar aprovação da Mel antes de torná-la normativa.
```

---

# 2. Problema observado no repositório

Hoje o KOF possui as peças, mas elas estão espalhadas.

## 2.1 `VERSIONING.md`

Já define a hierarquia conceitual:

```text
MAJOR.MINOR.PATCH
```

com:

- `PATCH`: bugfix, regressão, pequenos ajustes;
- `MINOR`: evolução significativa;
- `MAJOR`: grande release / mudança arquitetural ou de compatibilidade.

Porém o arquivo ainda possui texto histórico dizendo que:

```text
Current stage = 0.0.x / Alpha
```

e que cada commit em `main` gera um próximo Alpha/PATCH.

Isso não descreve mais o estado atual do projeto, hoje em `0.5.0-beta` e com branch ativa própria — confirmado nesta revalidação (§1.3).

## 2.2 `D-RELEASE`

Já decidiu (confirmado no tip, §1.2):

- entre `100–150` commits de avanço, uma nova release **deve ser avaliada**;
- isso não congela features;
- só depois da avaliação a versão é atualizada;
- se houver capability nova material que altere contrato, operador ou superfície de API, a release deixa de ser patch e passa a minor.

A ideia é boa, mas possui duas limitações:

1. o comando normativo está hardcoded em `beta-0.4.0`;
2. o termo **"material capability"** ainda exige julgamento subjetivo.

## 2.3 `D-RELEASE-0.5.0-GATE`

Criou um modelo forte de elegibilidade ao corte:

1. paridade;
2. decisões relevantes resolvidas/revisadas;
3. hygiene de docs de desenvolvimento;
4. estabilidade total;
5. zero bugs abertos;
6. edges/blockers fechados;
7. bugs-and-gaps sem pendência aplicável.

Esse gate é específico para `0.5.0`; esta proposta **não deve silenciosamente convertê-lo em gate universal**. Ele serve como precedente e modelo.

## 2.4 `D-RELEASE-1.0`

Já define que `1.0.0` não é um contador nem uma decisão de marketing.

A primeira release estável só pode existir após:

```text
EXIT GATE integralmente GREEN
+
mesmo candidato
+
nenhuma aresta aberta
+
RC sem regressão
```

`D-1.0-EDGES` também decidiu que a linha 1.0 só abre depois do corte da `0.5.0`, fechamento de EG-1..EG-7 e declaração explícita da mantenedora.

Esta proposta **não altera nem enfraquece esse contrato**.

---

# 3. Evidência externa — função apenas fundamentadora

Referências externas **não são o contrato KOF**. Elas servem para testar se a política proposta é tecnicamente razoável.

## Peso 5 — pesquisa empírica

### Maven / SemVer e consumidores

Estudo em larga escala sobre upgrades de bibliotecas Maven encontrou ampla adesão a SemVer, mas também breaking changes e impacto real em consumidores mesmo quando o versionamento sugeria compatibilidade.

Referência:

- `Semantic Versioning and Impact of Breaking Changes in the Maven Ecosystem` — arXiv:2110.07889

Implicação para KOF:

> O número declarado não é prova suficiente de compatibilidade. A classificação deve ser acompanhada de gate e evidência.

### Go / breaking changes fora de major

Estudo sobre ecossistema Go encontrou breaking changes em upgrades que não alteravam major version e impacto potencial em consumidores downstream.

Referência:

- estudo empírico sobre SemVer e breaking changes no ecossistema Go — arXiv:2309.02894

Implicação para KOF:

> `PATCH` ou `MINOR` precisa ser validado contra a superfície observável, não apenas escolhido manualmente.

### Cadência de releases

Estudo em centenas de projetos open source não encontrou evidência de que simplesmente encurtar ou alongar o ciclo de release melhore por si só o tratamento de bugs.

Referência:

- estudo sobre rapid releases em projetos open source — arXiv:2103.08648

Implicação para KOF:

> quantidade de dias ou commits deve ser gatilho de **avaliação**, não autorização de publicação.

### Security releases

Pesquisa sobre milhares de advisories mostra que security fixes frequentemente exigem ciclo de publicação muito mais curto.

Referência:

- estudo empírico sobre tempo entre security fix e release — arXiv:2112.06804

Implicação para KOF:

> security fix / regressão crítica deve poder disparar avaliação imediata sem esperar 100–150 commits.

---

## Peso 3 — projetos maduros

### Rust

Rust usa cadência própria de releases e tratou `1.0` como compromisso de estabilidade da linguagem, não como simples incremento numérico.

A evolução continua após 1.0, preservando compatibilidade e usando mecanismos explícitos para mudanças maiores.

### Go

Go vincula a linha 1.x a forte compromisso de compatibilidade, mas continua adicionando capacidades relevantes de forma compatível.

Conclusão útil:

```text
1.0 != linguagem congelada
1.x = evolução + compatibilidade
```

### Python

Python separa fase de feature development, beta/feature freeze, RC e releases de correção.

Isso reforça a separação:

```text
classificar versão != decidir o momento do corte
```

---

## Peso 2 — documentação oficial

### Semantic Versioning 2.0

SemVer distingue:

- PATCH: fix compatível;
- MINOR: funcionalidade pública compatível;
- MAJOR: incompatibilidade da API pública.

SemVer também trata `0.y.z` como desenvolvimento inicial.

Para KOF, a proposta deliberadamente adota uma disciplina **mais rígida que o mínimo de SemVer em 0.x**, porque o próprio projeto já possui contratos congelados, decisões normativas e multi-target parity antes de 1.0.

---

# 4. Proposta normativa — `D-VERSIONING-RELEASE`

> **ATENÇÃO:** o texto abaixo é uma PROPOSTA. Não registrar como `DECIDED` antes da aprovação explícita da Mel.

## 4.1 Princípio central

Versionamento e corte são duas decisões distintas:

```text
CHANGE
  ↓
VERSION CLASSIFICATION
  ↓
PATCH / MINOR / MAJOR
  ↓
RELEASE EVALUATION
  ↓
CANDIDATE
  ↓
RELEASE GATE
  ↓
CUT
```

Uma mudança poder ser classificada como PATCH não significa que uma release PATCH deve ser publicada imediatamente.

---

## 4.2 Regra pré-1.0 — PATCH

Durante `0.x`, `PATCH` é reservado a alterações que **não adicionam nem alteram a superfície pública contratada**.

Exemplos permitidos:

- bugfix;
- security fix;
- regression fix;
- correção de target parity para cumprir contrato existente;
- performance sem mudança de comportamento contratado;
- refactor interno;
- CI/tooling;
- release engineering;
- packaging;
- documentação;
- melhoria de diagnóstico que não altera contrato;
- implementação interna de uma decisão já aprovada, quando não cria nova superfície pública.

Regra proposta:

```text
PUBLIC_CONTRACT_SURFACE_DIFF = 0
→ PATCH admissível
```

---

## 4.3 Regra pré-1.0 — MINOR

Durante `0.x`, qualquer **nova superfície pública contratada** ou alteração deliberada dessa superfície exige `MINOR`.

Inclui, quando aplicável:

- nova sintaxe;
- novo operador;
- nova semântica observável;
- nova API pública relevante;
- novo namespace público;
- novo comando/flag público que faça parte do contrato;
- nova capability pública de stdlib;
- novo contrato de package/registry/interop;
- promoção de novo target para superfície suportada/Stable;
- breaking change pré-1.0 deliberada e aprovada.

Regra proposta:

```text
PUBLIC_CONTRACT_SURFACE_DIFF > 0
→ PATCH proibido
→ MINOR mínimo
→ Decision ID obrigatório
```

### Breaking change pré-1.0

Pode acontecer somente com:

```text
MINOR
+
DECISION registrada
+
impact/migration note
+
prova correspondente
```

Nenhuma breaking change deliberada deve ser escondida em patch por o projeto ainda estar abaixo de 1.0.

---

# 5. Regra pós-1.0

Após a primeira Stable:

```text
1.2.3 → 1.2.4
PATCH = correções backward-compatible

1.2.3 → 1.3.0
MINOR = nova funcionalidade pública backward-compatible

1.2.3 → 2.0.0
MAJOR = mudança incompatível do contrato público
```

No KOF, compatibilidade deve ser avaliada em quatro dimensões:

1. **source compatibility** — código KOF anterior continua válido onde a promessa se aplica;
2. **artifact/binary compatibility** — package/interop/artifacts mantêm o contrato declarado;
3. **behavioral compatibility** — comportamento observável contratual não muda indevidamente;
4. **cross-target compatibility/parity** — todos os targets da superfície seguem o contrato ou o gap documentado.

---

# 6. Regra do primeiro `1.0.0`

Esta proposta deve **incorporar por referência, sem reescrever nem relaxar**, `D-RELEASE-1.0` e `D-1.0-EDGES`.

`1.0.0` não é escolhido por:

- quantidade de commits;
- quantidade de features;
- idade do projeto;
- chegar numericamente a `0.9.9`;
- percepção subjetiva de maturidade.

O primeiro `1.0.0` só existe quando o EXIT GATE ratificado autoriza.

Resumo não normativo:

```text
0.5.0 cortada
+
EG-1..EG-7 fechadas
+
declaração da mantenedora
+
primeiro RC
+
Stable Surface fechada
+
EXIT GATE integralmente GREEN no mesmo candidato
+
RC → Stable sem regressão
→ 1.0.0
```

---

# 7. Gatilhos de avaliação de release

A política deve diferenciar **trigger** de **cut**.

## 7.1 Trigger ordinário

Generalizar o atual `D-RELEASE` de:

```text
git rev-list --count origin/main..origin/beta-0.4.0
```

para um conceito independente de branch histórica:

```text
LAST_RELEASE..ACTIVE_BRANCH
```

Faixa atual proposta a preservar:

```text
100–150 commits
→ abrir RELEASE EVALUATION
```

Isso **não autoriza publicação**.

## 7.2 Trigger extraordinário

Avaliação imediata pode ser aberta por:

- security fix relevante;
- regressão crítica;
- correção urgente de distribuição/package;
- decisão explícita da mantenedora.

Novamente:

```text
trigger != cut
```

---

# 8. Gate comum de elegibilidade ao corte

A política consolidada deve definir uma base comum, sem transformar silenciosamente o gate específico de `0.5.0` em regra universal.

Base proposta:

```text
[ ] candidate SHA identificado
[ ] VERSION / pom / version resource consistentes
[ ] CHANGELOG/release metadata coerentes
[ ] suite requerida GREEN no candidato
[ ] classification PATCH/MINOR/MAJOR comprovada
[ ] decisões necessárias registradas
[ ] blockers aplicáveis resolvidos
[ ] package real validado quando aplicável
[ ] artifact trust/provenance conforme contrato vigente
[ ] documentação EN/PT sincronizada onde exigida
```

Gates específicos de uma linha continuam prevalecendo:

- `D-RELEASE-0.5.0-GATE` para 0.5.0;
- `D-RELEASE-1.0` para RC/Stable 1.0;
- futuros gates específicos quando decididos.

---

# 9. Futuro gate mecânico de superfície

A decisão pode autorizar como backlog, **não como requisito para aprovar este PR**, a criação futura de:

```text
release-surface-gate
```

Objetivo:

comparar a última release com o candidato em superfícies contratuais como:

- grammar;
- language-reference;
- operadores;
- regras de tipos;
- Stable stdlib catalog;
- CLI commands/flags contratuais;
- package/registry contract;
- Stable Target Surface;
- outros manifests gerados pelo projeto.

Resultado desejado:

```text
PUBLIC_SURFACE_DIFF = 0
→ PATCH pode continuar em avaliação

PUBLIC_SURFACE_DIFF > 0
→ PATCH recusado
→ exige MINOR/MAJOR + Decision ID
```

Não implementar esse gate dentro deste PR de contrato, salvo decisão explícita posterior.

---

# 10. Drift documental a corrigir se a proposta for aprovada

Após aprovação da Mel, atualizar EN+PT de forma coordenada.

## 10.1 `VERSIONING.md`

Remover/substituir como estado atual as afirmações obsoletas:

```text
Current stage = 0.0.x
Alpha current
every commit on main generates next PATCH
```

Preservar histórico onde necessário, mas fazer o documento descrever o estado vigente e apontar para `D-VERSIONING-RELEASE`.

## 10.2 `D-RELEASE`

Não apagar a decisão histórica.

Adicionar relação/supersession parcial esclarecendo:

- `100–150 commits` = trigger de avaliação ordinário;
- branch deve ser resolvida como `LAST_RELEASE..ACTIVE_BRANCH`, não hardcoded em `beta-0.4.0`;
- a classificação passa a seguir `D-VERSIONING-RELEASE`.

## 10.3 `release-naming.md`

Manter `MAJOR.MINOR.PATCH-stage` e estágio Beta/RC/Stable; apenas alinhar qualquer frase que contradiga a nova decisão.

## 10.4 `D-RELEASE-0.5.0-GATE` e `D-RELEASE-1.0`

Não reescrever os gates.

Somente adicionar relacionamento com a nova decisão caso necessário.

---

# 11. Fluxo obrigatório do PR — aprovação da Mel

## ETAPA A — PR de proposta

Criar branch a partir do tip remoto atualizado de `beta-0.5.0`.

Nome usado nesta unidade:

```text
docs/versioning-release-contract
```

Título sugerido:

```text
[Design/Contract]: consolidate KOF versioning and release-cut policy
```

Base:

```text
beta-0.5.0
```

Na primeira versão do PR:

- adicionar o dossiê/proposta em `docs/development/` EN+PT (este documento);
- **NÃO marcar `D-VERSIONING-RELEASE` como DECIDED**;
- **NÃO alterar VERSION**;
- **NÃO cortar release**;
- **NÃO criar tag**;
- **NÃO alterar production code**;
- **NÃO implementar release-surface-gate**;
- pedir revisão da `melmonfre`.

### Pergunta de decisão para a Mel

O PR deve pedir decisão explícita sobre:

> Aprova consolidar o versionamento do KOF sob `D-VERSIONING-RELEASE`, com PATCH pré-1.0 reservado a mudanças sem alteração de superfície pública contratada; MINOR obrigatório quando houver nova/alterada superfície pública contratada; 100–150 commits funcionando apenas como gatilho de avaliação; security/critical regressions podendo disparar avaliação imediata; SemVer estrito após 1.0; e `D-RELEASE-1.0` permanecendo integral e soberano para o primeiro Stable?

## HARD STOP A

Sem resposta explícita da Mel:

```text
STOP
NO DECISIONS.md normative edit
NO MERGE
```

Uma reação, ausência de objeção, passagem de CI, comentário de bot ou aprovação de outro contributor **não substitui** aprovação da mantenedora.

---

# 12. ETAPA B — materialização normativa após aprovação

Somente se Mel aprovar o contrato:

1. atualizar `docs/development/DECISIONS.md` + `.pt_BR.md`;
2. registrar `D-VERSIONING-RELEASE` com a redação aprovada;
3. atualizar `docs/distribution/VERSIONING.md` + `.pt_BR.md`;
4. alinhar `release-naming.md` + `.pt_BR.md` se necessário;
5. adicionar relação de supersession/refinement no `D-RELEASE` sem apagar história;
6. atualizar roadmap/tracker apenas se a decisão criar backlog mecânico;
7. manter `D-RELEASE-0.5.0-GATE` e `D-RELEASE-1.0` intactos salvo links/relationships necessários;
8. rodar os gates de docs exigidos pelo repo.

### Estado sugerido da decisão

Se Mel aprovar apenas a política documental:

```text
State: DECIDED
```

Se também forem implementadas guardas mecânicas no mesmo ciclo — o que **não é recomendado neste PR** — só então avaliar `PARTIAL`/`IMPLEMENTED` conforme evidência.

---

# 13. HARD STOP B — aprovação final do diff

Como a ETAPA B modifica o diff depois da primeira decisão da Mel, **a aprovação inicial não basta para merge**.

Após materializar o texto normativo:

1. push do diff final;
2. solicitar novamente review da Mel;
3. resumir exatamente o que mudou após a primeira aprovação;
4. exigir aprovação explícita do **diff final**.

Regra:

```text
MEL_FINAL_APPROVAL != TRUE
→ PR MUST NOT MERGE
```

Aceitável como aprovação final:

- GitHub Review `APPROVED` da `melmonfre`; ou
- comentário inequívoco da mantenedora autorizando o merge do diff final, caso o workflow de review do repo não permita `APPROVED` formal.

Preferência: `APPROVED` formal no GitHub.

Não aceitável:

- self-approval;
- aprovação do autor;
- aprovação de bot;
- "LGTM" de outro contributor;
- CI verde sem review;
- aprovação anterior a commits normativos subsequentes;
- inferência de silêncio.

---

# 14. Checklist do PR

## Baseline

```text
[x] fetch/rebase da beta-0.5.0
[x] tip registrado no PR (6c9aeb847f167e97ed48c21a9e4453028198da63)
[x] VERSION medido (0.5.0-beta)
[x] última release medida (kof-0.4.9-beta-*)
[x] DOING/lane collision verificada (nenhuma)
[x] DECISIONS atual lido
[x] VERSIONING EN/PT lido
```

## Proposta

```text
[x] KOF-first evidence block incluído
[x] problema de drift documentado
[x] PATCH proposto por ausência de public-surface diff
[x] MINOR proposto por presença de public-surface diff
[x] MAJOR pós-1.0 alinhado a incompatibilidade
[x] trigger separado de cut
[x] 100–150 commits = avaliação, nunca auto-release
[x] security/critical = trigger extraordinário
[x] D-RELEASE-1.0 preservado integralmente
[x] research references incluídas como fundamentação, não contrato
```

## Aprovação da Mel — etapa A

```text
[x] review solicitado para melmonfre
[x] decisão explícita registrada (PR #582 mergeado em 22/09/2026)
[x] nenhuma mudança normativa antes da decisão
```

## Materialização

```text
[x] DECISIONS EN/PT atualizado após aprovação (D-VERSIONING-RELEASE, 22/09/2026)
[x] VERSIONING EN/PT atualizado (estado atual + ponteiro da política)
[x] release-naming EN/PT alinhado se necessário (já consistente — sem mudança)
[x] D-RELEASE refinado sem apagar história
[x] nenhuma mudança de VERSION/tag/release
[x] nenhum production code
```

## Qualidade

Comandos executados nesta unidade e resultado real (registrar sempre o comando + saída real, nunca alegar execução que não ocorreu):

```text
scripts/docs-lang.sh check   -> ver resultado registrado no commit/PR desta unidade
```

mais qualquer gate de docs/links/live-records requerido pelo `AGENTS.md` atual.

## Aprovação da Mel — diff final

```text
[ ] novo review solicitado após o último commit normativo
[ ] Mel aprovou explicitamente o diff final
[ ] nenhum commit posterior invalidou a aprovação
[ ] CI/gates aplicáveis verdes ou falhas pré-existentes claramente demonstradas
```

## Merge

```text
[ ] MEL_FINAL_APPROVAL == TRUE
[ ] branch atualizada/rebase conforme política do repo
[ ] sem conflito de lane
[ ] merge permitido pela governança atual
```

Se qualquer item crítico acima falhar:

```text
DO NOT MERGE
```

---

# 15. Texto sugerido para descrição do PR

```markdown
## Objetivo

Consolidar a política de versionamento e corte de releases do KOF sem alterar a linguagem ou publicar uma release.

Hoje o contrato está distribuído entre `VERSIONING.md`, `D-RELEASE`, o gate específico de `0.5.0` e o EXIT GATE de `1.0`.

A proposta separa duas decisões:

1. **classificação da mudança** — PATCH / MINOR / MAJOR;
2. **elegibilidade ao corte** — trigger → candidate → gate → release.

### Proposta pré-1.0

- PATCH: nenhuma nova/alterada superfície pública contratada;
- MINOR: nova/alterada superfície pública contratada ou breaking change pré-1.0 aprovada;
- 100–150 commits: gatilho de avaliação, não autorização de release;
- security/critical regression: gatilho extraordinário de avaliação.

### Pós-1.0

SemVer estrito, com compatibilidade analisada em source, artifact/binary, behavioral e cross-target parity.

### 1.0

`D-RELEASE-1.0` e `D-1.0-EDGES` permanecem integralmente vigentes e não são relaxados por este PR.

## Decisão solicitada

@melmonfre — aprova esta consolidação como `D-VERSIONING-RELEASE`?

**Este PR não deve ser mergeado sem sua aprovação explícita.**
Após eventual aprovação da proposta e materialização do texto normativo, será solicitado novo review sobre o diff final antes do merge.
```

---

# 16. Critérios de aceite

O trabalho só é considerado concluído quando:

```text
A. a ambiguidade atual está explicitamente documentada;
B. a política proposta está fundamentada em contrato KOF + evidência externa;
C. Mel tomou decisão explícita;
D. se aprovada, a decisão foi registrada EN+PT sem apagar história;
E. VERSIONING deixou de descrever 0.0.x/Alpha como estado atual;
F. trigger de release ficou separado de classificação e de cut;
G. D-RELEASE-1.0 permaneceu intacto;
H. o diff final recebeu aprovação explícita da Mel;
I. somente então o PR pode ser mergeado.
```

---

# 17. Não fazer

```text
NÃO alterar production code.
NÃO implementar parser/typer/runtime.
NÃO mudar VERSION.
NÃO criar tag.
NÃO publicar release.
NÃO self-ratificar D-VERSIONING-RELEASE.
NÃO marcar como DECIDED antes da Mel.
NÃO transformar 100–150 commits em auto-release.
NÃO enfraquecer o EXIT GATE 1.0.
NÃO usar SemVer externo como autoridade acima do contrato KOF.
NÃO mergear com aprovação somente de outro contributor/bot.
NÃO considerar aprovação antiga válida após alteração normativa posterior sem re-review.
```

---

# 18. Resultado esperado

Ao final, se aprovado:

```text
CHANGE
   ↓
PUBLIC CONTRACT SURFACE DIFF?
   ├── NO  → PATCH candidate
   └── YES → MINOR candidate (+ Decision ID)

AFTER 1.0:
   compatible fix        → PATCH
   compatible capability → MINOR
   incompatible contract → MAJOR

VERSION CLASSIFICATION
   ↓
RELEASE TRIGGER
   ↓
RELEASE EVALUATION
   ↓
IMMUTABLE CANDIDATE
   ↓
LINE-SPECIFIC GATE
   ↓
MAINTAINER CUT
```

E para o primeiro Stable:

```text
D-RELEASE-1.0 EXIT GATE
        ↓
100% GREEN
        ↓
1.0.0
```

---

## Referências externas de fundamentação

- Semantic Versioning 2.0.0 — https://semver.org/
- Rust stability / release trains — https://blog.rust-lang.org/2014/10/30/Stability/
- Rust Editions — https://doc.rust-lang.org/edition-guide/editions/
- Go 1 Compatibility Promise — https://go.dev/doc/go1compat
- Python release cycle / annual release model — https://peps.python.org/pep-0602/
- Maven/SemVer empirical study — https://arxiv.org/abs/2110.07889
- Go/SemVer empirical study — https://arxiv.org/abs/2309.02894
- Rapid release empirical study — https://arxiv.org/abs/2103.08648
- Security fix/release empirical study — https://arxiv.org/abs/2112.06804

**Regra final:** referências externas fundamentam; `DECISIONS.md` governa.
