[English](PROPOSAL-1.0-EXIT-GATE.md) | [Português](PROPOSAL-1.0-EXIT-GATE.pt_BR.md)

# KOF 1.0 — Exit Gate Contract Proposal

**Status:** RATIFICADA pela mantenedora em 20/09/2026 — registrada como `D-RELEASE-1.0 — KOF 1.0 EXIT GATE` em `docs/development/DECISIONS.md` (chat: "decisão [...] ratificada. concordo com o planejamento [...] kof RC 1.0.0 e kof release 1.0.0 só existem QUANDO todos os pontos estiverem correspondentes e não houver nenhuma aresta aberta")  
**Issue de acompanhamento:** [#560](https://github.com/KofLang/Kof4j/issues/560) (DESIGN REQUEST à mantenedora)  
**Local:** `docs/development/` — promovida de `future/` pela ratificação: plano normativo com a fila da §23 agora como meta de desenvolvimento vinculante.  
**Revisão:** v3.1 — v3 corrigida após revalidação de 20/09/2026 (seção 24); alinhada ao estado do repositório, às decisões/publicações da Mel e a benchmark externo ponderado (não normativo)  
**Repositório:** `KofLang/Kof4j`  
**Branch ativa atual:** `lab` (`D-BRANCH-PIPELINE`; `beta-0.5.0`/`D-BRANCH-0.5.0` `SUPERSEDED` pelo cutover de 28/09)  
**Branch anterior:** `beta-0.4.0` — somente pousos já em voo + preparo de release, conforme decisão da mantenedora  
**Revisora / autoridade de decisão requerida:** **Mel (`melmonfre`)**  
**Natureza:** contrato de saída para um futuro KOF 1.0; NÃO é autorização para cortar 1.0 agora.

> ## REVISÃO E APROVAÇÃO DA MEL — RATIFICADAS EM 20/09/2026
>
> A mantenedora aprovou o contrato e o planejamento como escritos (ver §22 para
> a evidência verbatim e §23 para a fila que isso abre). A partir deste registro,
> o EXIT GATE (§8) é a meta de desenvolvimento vinculante: **não existe Kof RC
> 1.0 nem Kof release 1.0 enquanto qualquer item obrigatório estiver em falta ou
> qualquer aresta estiver aberta.**
>
> A salvaguarda original permanece para o futuro: nenhum agente ratifica nada em
> nome da Mel; só ela preenche blocos de aprovação e responde perguntas em
> aberto. A superfície KofC/Android, a declaração que abre o primeiro RC e os
> candidatos de reforço foram todos respondidos pela Mel em 20/09/2026
> (`D-1.0-EDGES`); não são mais arestas abertas.
>
> Após a aprovação explícita da Mel este contrato:
>
> 1. virou decisão normativa em `docs/development/DECISIONS.md` (`D-RELEASE-1.0`);
> 2. deve ser sincronizado com os documentos de release/versionamento (fila);
> 3. deve ser transformado em gates automáticos obrigatórios (fila — REDs antes);
> 4. orienta o corte do primeiro Release Candidate de 1.0 (último passo da fila).

> ## O que mudou da v3 para a v3.1 (revalidação da seção 24, 20/09/2026)
>
> Só correções factuais; **nenhuma decisão foi tomada e nenhum requisito foi acrescentado ou removido.**
>
> 1. **Seção 6** — o tip citado (`47a7b8f9`) já é ancestral do atual; a condição "nenhum pouso residual só em `beta-0.4.0`" foi **medida e hoje NÃO está satisfeita** (3 commits, todos da própria Mel).
> 2. **Seções 4/5** — a API de code-scanning devolve **38 alertas abertos** em `refs/heads/beta-0.5.0`, número diferente dos **21** do texto original da #555.
> 3. **Seção 13** — a afirmação de que os docs de release/versionamento citam `kofc` **não foi confirmada** (0 menções em `docs/distribution/`); `kofc` aparece no README e nos docs de arquitetura.
> 4. **Seções 13/16/21** — o site público (`koflang.github.io`) diverge da lista de targets do checklist e da `VERSION`: v0.4.1-beta, KofC "Disponível", KofJS "Em desenvolvimento".
> 5. **Seção 28.3** — os números da tese do Rahman **não puderam ser verificados** e foram retirados; o princípio fica, marcado como não confirmado.
>
> Registro completo do que foi/não foi verificado: **seção 37**.

---

## 0. Leia primeiro

Este é o **contrato ratificado do exit gate 1.0** (`D-RELEASE-1.0`). Leia:

- **§25 Resumo executivo** — a versão de dois minutos.
- **§8 EXIT GATE** — o contrato; **§13** o checklist de alvos (a Stable 1.0 de 8
  alvos, KofC + Android dentro, por `D-1.0-EDGES`).
- **§21 Q1–Q8** — as decisões da mantenedora (todas resolvidas; só **EG-8** — a
  declaração da própria mantenedora — segue aberta).
- **§23 Após a aprovação da Mel** — a **ordem de execução** (a fila), rastreada
  no `roadmap.md` §24.
- **§35** — o bloco candidato do EXIT GATE v3; **§37** o registro de
  revalidação de 20/09 (v3.1).
- **§24** — a regra de atualização deste documento.
- **`D-1.0-STABILITY-100`** (`DECISIONS.md`, 20/09) — o refinamento do
  fechamento: nenhum 1.0.0 enquanto QUALQUER item estiver aberto em
  `docs/development/`, `docs/development/future/` ou `docs/bugs-and-gaps/`,
  com a paridade cross-target MEDIDA (provada, nunca alegada).

O estado vivo é medido mecanicamente por `scripts/check_release_050_gate.sh`
(as condições do 0.5.0) e `scripts/check_release_blockers.sh` (a fila EG /
`--rc-gate`), nunca a olho.

---

# 1. Estado real que governa esta revisão

Esta revisão substitui a leitura anterior que ainda tratava `beta-0.4.0` como linha ativa.

A decisão vigente é:

```text
D-BRANCH-0.5.0
```

Registrada em `docs/development/DECISIONS.md` em 20/09/2026.

A ordem da mantenedora, registrada no próprio documento, foi:

> "avise os outros agentes, vamos mover todo trabalho pra branch beta-0.5.0 e começar a preparar a nova release"

Consequências normativas já publicadas:

```text
beta-0.5.0 = branch ativa
beta-0.4.0 = somente pousos já em voo + release prep
```

Todo novo trabalho de código e documentação deve ir para `beta-0.5.0`.

Quando ainda pousar algo já iniciado em `beta-0.4.0`, a docs lane deve absorver esse pouso em `beta-0.5.0`.

O número final da nova release, o bump de versão, o CHANGELOG e a tag são **decisão da mantenedora no momento do corte**.

Portanto:

> **Este documento NÃO presume que a próxima release seja 1.0.**

O projeto está preparando a linha `beta-0.5.0`. O contrato abaixo define o que deverá ser verdade quando a Mel decidir iniciar efetivamente a preparação do KOF 1.0.

---

# 2. O que já existe no contrato de release do KOF

A documentação vigente já define:

```text
Beta → Release Candidate → Stable
```

E estabelece, em essência:

- **Beta:** desenvolvimento ainda aberto, com evolução de features;
- **RC:** paridade de targets e fase de estabilização;
- **Stable:** 1.0 com garantia de compatibilidade.

Isso significa que o `1.0 EXIT GATE` não cria do zero a ideia de RC ou Stable.

Ele tenta tornar **mensurável** a passagem entre esses estágios.

---

# 3. Correção importante em relação à proposta anterior

A versão anterior podia ser lida como se o projeto devesse congelar features agora.

Isso não está correto.

A redação correta é:

```text
BETA
│
├── features podem continuar entrando
├── bugs são corrigidos
├── gaps são fechadas ou explicitadas
├── targets evoluem
├── contratos são decididos
└── surface 1.0 é preparado
        │
        │  somente quando Mel autorizar o corte de RC
        ▼
   PRIMEIRO RC 1.0
        │
        └── congelamento da superfície pública 1.0
```

Portanto:

> **Não há feature freeze automático na `beta-0.5.0`.**

O congelamento proposto começa **somente após o corte do primeiro RC de 1.0**, e somente se a Mel aprovar essa regra como parte do contrato.

---

# 4. KOF-first evidence block

```text
KOF VALIDITY:
N/A — este documento trata de release/governança, não de sintaxe KOF.

CONTRACT SOURCE:
- docs/development/DECISIONS.md — especialmente D-BRANCH-0.5.0;
- docs/distribution/release-naming.md;
- docs/distribution/VERSIONING.md;
- AGENTS.md — Quality Gate / no bug ships / zero regression / suite as gate;
- docs/distribution/release-beta-0.5.0.md (registro de aceitação, FECHADO 28/09).

CURRENT KOF IDIOM:
Beta → RC → Stable já é a progressão oficial.
A branch ativa agora é beta-0.5.0 por decisão explícita da Mel.
O bump/tag/release number permanecem decisão da mantenedora.

MEASUREMENT:
Snapshot consultado em 20/09/2026:
- beta-0.5.0 tip observado na v3: 47a7b8f9af12cc10f471b811f461d7e778053c02;
- beta-0.5.0 tip na revalidação da v3.1 (20/09/2026): 9ee038f7 — o tip muda a cada pouso, revalidar sempre;
- beta-0.4.0 = 4ee3a5c9 e continua com 3 commits que a beta-0.5.0 ainda não absorveu (seção 6);
- alertas CodeQL abertos na API para refs/heads/beta-0.5.0: 38 (o texto original da #555 falava em 21);
- #550 CLOSED;
- #553 CLOSED;
- #554 CLOSED;
- #555 OPEN;
- Code Quality Analysis da beta-0.5.0 estava GREEN;
- CodeQL Gate (alerts API) estava RED;
- kof-security-bot estava GREEN;
- VERSION em beta-0.5.0 ainda era 0.4.7-beta;
- D-BRANCH-0.5.0 proíbe bump unilateral.

CLASSIFICATION:
DESIGN REQUEST — contrato de release/governança para 1.0.

DUPLICATE / PRECEDENT CHECK:
- D-RELEASE trata avaliação de patch/release;
- D-BRANCH-0.5.0 governa a linha ativa atual;
- release-beta-0.5.0-prep.md governa o preparo da próxima beta;
- não foi encontrado um D-RELEASE-1.0 já ratificado.

ACTION:
Submeter este contrato à Mel.
Somente após aprovação explícita:
- registrar decisão normativa;
- sincronizar docs;
- automatizar gates;
- definir o primeiro candidato 1.0 RC.
```

---

# 5. Situação atual da preparação da nova release

A fila oficial de `beta-0.5.0` foi criada pela própria decisão `D-BRANCH-0.5.0`.

Desde a primeira versão deste documento:

```text
#550 — CLOSED
#553 — CLOSED
#554 — CLOSED
#555 — OPEN
```

Os três bugs/ferramentas que estavam no caminho da preparação da nova beta já foram encerrados com prova registrada.

O maior gate ainda explicitamente aberto é:

```text
#555 — Quality Gate / CodeQL debt + confiabilidade do portão
```

A última execução concluída inspecionada antes do novo tip mostrou:

```text
Code Quality Analysis  = GREEN
CodeQL Gate            = RED
Security bot           = GREEN
```

No tip `47a7b8f9...`, os workflows ainda estavam em fila/execução durante esta revisão. A issue `#555` permanecia OPEN. Portanto não se deve promover o estado anterior para o novo SHA sem nova medição.

**Revalidação da v3.1 (20/09/2026):** no último `kof-quality-bot` concluído (SHA `7b2dd963`) o job `Code Quality Analysis` estava `success` e o job `CodeQL Gate (alerts API)` estava `failure`; `kof-security-bot` estava `success`. A API de code-scanning devolvia **38 alertas abertos** em `refs/heads/beta-0.5.0`, contra os **21** citados no texto da #555 (medidos, na época, no tip `817c27f2` da `beta-0.4.0`). A diferença pode ser dívida nova, contagem por ref diferente ou ambos — **medir sempre pelo próprio gate no SHA candidato, nunca pelo texto da issue.**

Logo, o requisito de 1.0:

```text
Quality/Security gates verdes e confiáveis
```

**ainda NÃO pode ser marcado como concluído.**

---

# 6. Transição de branches também precisa entrar na preparação

A decisão da Mel determina que `beta-0.4.0` receba somente pousos em voo e que esses pousos sejam absorvidos pela `beta-0.5.0`.

No snapshot da v3:

```text
beta-0.5.0 = 47a7b8f9...
beta-0.4.0 = 4ee3a5c9...
```

**Medição da revalidação da v3.1 (20/09/2026):**

```text
beta-0.5.0 = 9ee038f7   (o tip citado na v3, 47a7b8f9, já é ancestral)
beta-0.4.0 = 4ee3a5c9
beta-0.5.0 em relação à beta-0.4.0: 16 à frente / 3 atrás
```

Os 3 commits que existem **só** na `beta-0.4.0` (ainda não absorvidos pela `beta-0.5.0`) são, todos, pousos da própria Mel (MakeAlive/GAPS-DB): `4ee3a5c9`, `ce5e8e66` e `d3f79e7a`.

Portanto a condição "nenhum pouso residual exclusivo em `beta-0.4.0`", listada abaixo, **hoje NÃO está satisfeita** — e a absorção desses pousos cabe à docs lane, conforme a decisão.

Isso não viola por si só a decisão — porque a decisão explicitamente permite pousos já em voo —, mas cria uma condição objetiva que deve estar resolvida antes de qualquer futura linha RC:

```text
[ ] nenhum pouso residual exclusivo em beta-0.4.0
[ ] tudo que deve sobreviver foi absorvido pela branch ativa
[ ] beta-0.5.0 (ou sucessora indicada pela Mel) é a única fonte ativa
```

---

# 7. Proposta de decisão normativa

Identificador sugerido, **somente após aprovação da Mel**:

```text
D-RELEASE-1.0 — KOF 1.0 EXIT GATE
```

Contrato proposto:

> Um build só pode ser declarado **Release Candidate de KOF 1.0** quando todos os itens obrigatórios do EXIT GATE estiverem satisfeitos com evidência reproduzível no mesmo candidato.
>
> Um RC só pode ser promovido a **Stable 1.0** quando o EXIT GATE continuar verde e a rodada RC → Stable não introduzir regressão.
>
> A superfície pública do 1.0 fica congelada **a partir do primeiro RC aprovado pela Mel**, e não durante a Beta.

---

# 8. KOF 1.0 EXIT GATE

```text
[ ] CI principal verde
[ ] Quality/Security gates verdes e confiáveis
[ ] 0 bugs OPEN classificados como release-blocker
[ ] pacote real testado fora do repo
[ ] JVM green
[ ] x86-64 green
[ ] riscv64 green
[ ] aarch64 green
[ ] JS green
[ ] Script green
[ ] KofC green (gate próprio — `D-1.0-EDGES`)
[ ] Android green (gate próprio — `D-1.0-EDGES`)
[ ] golden byte parity onde o contrato exige
[ ] todas as gaps restantes explicitamente fora do surface 1.0
[ ] VERSION / docs / release metadata sincronizados
[ ] RC sem feature nova
[ ] apenas fixes durante RC
[ ] RC → Stable sem regressão
[ ] 100% dos itens de docs/development/, docs/development/future/ e
    docs/bugs-and-gaps/ resolvidos, com paridade cross-target total PROVADA
    (D-1.0-STABILITY-100)
```

### Complemento aderente à decisão D-BRANCH-0.5.0

Antes do primeiro RC:

```text
[ ] branch ativa definida pela Mel está convergida
[ ] nenhum pouso residual relevante ficou apenas em beta-0.4.0
[ ] release-prep anterior foi encerrado corretamente
[ ] a Mel declarou explicitamente que começou a linha/candidato 1.0
```

---

# 9. CI principal verde

Para marcar:

```text
[ ] CI principal verde
```

é necessário:

- candidato identificado por SHA;
- CI do mesmo SHA;
- build e testes executados;
- gates estruturais executados;
- jobs obrigatórios não pulados por falha anterior;
- golden/integration executados quando fazem parte do workflow;
- cross tests executados onde o toolchain oficial existe;
- nenhuma justificativa baseada apenas em teste local.

Um SHA anterior verde não valida um SHA posterior.

---

# 10. Quality/Security gates verdes E confiáveis

A exigência não é apenas visual.

Critérios mínimos:

```text
[ ] scan e veredito pertencem ao mesmo SHA
[ ] API vazia != API indisponível
[ ] análise velha não pode decidir commit novo
[ ] alerta antigo de outra lane não pode ser atribuído à mudança atual
[ ] debt inventory e merge gate têm semânticas claras
[ ] não existe uso rotineiro de CODEQL_GATE_SKIP=1
[ ] não existe falso-green conhecido
[ ] lógica do próprio gate tem regressão automatizada
```

O estado atual de `#555` mostra que esta área ainda precisa de fechamento.

**Status (EG-2, fechado 20/09/2026):** o `scripts/codeql-gate.sh` +
`scripts/codeql-baseline.txt` da lane security (`0d2a019d`) fecharam seis dos
oito critérios (API fora é INCONCLUSIVO; sem `CODEQL_GATE_SKIP` de rotina;
`state:null` tratado; baseline com dono/data/motivo; regressão automatizada). A
lane de estabilização fechou as duas restantes no mesmo arquivo (coordenado,
aprovado pela mantenedora), RED-first em `scripts/tests/codeql-gate-test.sh`
(agora 10 cenários, registrado no `run-agent-tests.sh`):
1. **vazio ≠ indisponível** — a falha de API é rastreada em `api_ok`, nunca
   inferida de lista vazia; uma lista de alertas legitimamente vazia é GREEN,
   não INCONCLUSIVO.
2. **Amarração ao SHA** — o SHA da análise mais recente (analyses API) é
   comparado com o tip do branch (branches API); análise velha ou ausente é
   INCONCLUSIVO (rc=2, não bloqueia), nunca green. Alerta novo ainda vence como
   RED. O gate real de 20/09 mediu o mecanismo funcionando (main/beta-0.4.0
   stale → rc=2; beta-0.5.0 acusou o alerta novo #945, já fixado na fonte por
   `9c88d590`, aguardando re-scan).

A solução técnica final do Quality Gate é uma frente própria e não deve ser autorratificada por este documento.

---

# 11. Zero OPEN release-blockers

O requisito é:

```text
OPEN release-blocker = 0
```

Mas a simples ausência de uma label chamada `release-blocker` não prova isso.

Antes do RC, a Mel deve aprovar o mecanismo de classificação.

Cada issue aberta deve estar explicitamente em uma destas categorias:

```text
BLOCKS 1.0
OUTSIDE 1.0 SURFACE
POST-1.0
NOT A BUG / CLOSE
TRACKING (processo de release / umbrella)
```

Nenhuma issue ambígua pode ser ignorada apenas porque não recebeu label.

**Decidido (`D-1.0-EDGES`, 20/09/2026):** as categorias são **cinco** — uma
quinta, `tracking/contract`, foi adicionada para issues de processo/umbrella
(a thread de acompanhamento #560); é válida durante a estabilização, mas ainda
precisa fechar antes do RC. O mecanismo está pousado (EG-1): labels +
`scripts/release-blockers.tsv` + `scripts/check_release_blockers.sh` (RED quando
qualquer issue OPEN tem zero ou 2+ categorias; `--rc-gate` falha enquanto
qualquer `1.0-blocks` estiver aberto). Estado atual: #561/#563/#564/#565 =
`1.0-blocks`; #560 = `tracking/contract`; #555 FECHADA.

---

# 12. Pacote real testado fora do repo

Esta condição ganhou evidência prática importante com o fechamento da #550.

A #550 mostrou exatamente por que o gate é necessário: algo podia funcionar dentro da árvore e falhar no CLI distribuído por depender de caminhos do repo.

A prova de 1.0 deve usar o pacote real:

```text
build/package
      ↓
copiar/extrair para diretório limpo
      ↓
sem acesso à árvore-fonte
      ↓
kof version
kof info
build/run
      ↓
smoke por target
```

O artefato que o usuário instala é o objeto da validação.

**Mecanismo (pousado 20/09, EG-3 — fila §23 item 9 do `D-RELEASE-1.0`):**
`scripts/test-package-outside-repo.sh` automatiza exatamente o fluxo acima —
builda a dist, extrai o **tar.gz real** para um diretório limpo em `$HOME`
(nunca `/tmp`, regra 9 do repo) com as variáveis do repo desexportadas, e roda
`kof version → kof info → kof new → run do template por alvo → resolução de
lib pura-Kof` (`kof.pdf` de `lib/kof-libs` — a classe de bug do #550), com
preflights honestos (JDK ≥ 25 / node / toolchain cross ausentes falham alto,
nunca falso-verde; alvos cross são build-only aqui — o exec mora na matriz
final, §23 item 10). PASS medido 20/09 com `kof-0.4.7-beta-linux-x86_64`
(jvm+script+js+native). Prova offline RED-first para a suíte de agentes:
`scripts/tests/test-package-outside-repo-test.sh`. O **re-run no dia do RC, no
mesmo candidato**, continua sendo o que satisfaz este item do checklist — o
mecanismo apenas torna esse re-run um comando.

---

# 13. Targets propostos no checklist

O checklist solicitado contém:

```text
JVM
Native x86-64
Native riscv64
Native aarch64
JS
Script
```

Essa lista deve ser revisada pela Mel antes da ratificação.

Há duas superfícies atuais que não devem ser silenciosamente esquecidas:

**Observação da revalidação da v3.1:** o site público (`koflang.github.io`), lido em 20/09/2026, apresenta os targets assim — JVM, Native x86_64, Native RISC-V/ARM64, KofScript e **KofC** como "Disponível", e **KofJS** como "Em desenvolvimento" — na versão **v0.4.1-beta**. Isso diverge da lista do checklist acima (que inclui JS e não inclui KofC) e da `VERSION` do repositório (`0.4.7-beta`). Qualquer que seja a decisão da Mel para Q2/Q7, o site precisa dizer a mesma coisa que o contrato.

## KofC

**Correção da v3.1:** a v3 afirmava que os documentos de release/versionamento citam `kofc`. Isso **não foi confirmado**: em 20/09/2026 não há nenhuma menção a `kofc` em `docs/distribution/` (release-naming, VERSIONING, RELEASES, PACKAGING, INSTALL…). `kofc` aparece no `README.md` e em documentos de arquitetura (`docs/architecture/`), e o site público o marca como "Disponível". A pergunta abaixo continua válida, mas o fundamento agora é o README, a arquitetura e o site — não os docs de release.

Pergunta obrigatória:

```text
KofC faz parte do Stable Surface 1.0?
```

Se sim, precisa de gate próprio.

Se não, precisa ficar explicitamente fora do surface 1.0.

**Decidido (`D-1.0-EDGES`, 20/09/2026): sim — KofC faz parte do Stable Surface
1.0 e precisa de gate próprio.** O "Disponível" do site agora fica consistente
com o contrato.

**Mecanismo do gate (pousado 20/09, EG-9):** `scripts/test-kofc-gate.sh` — faz
o preflight do toolchain nativo (`as` + `ld`/`gcc`) e de um JDK ≥ 25 (ausência =
falha alta nomeando a ferramenta, nunca skip silencioso), compila **e executa**
o corpus suportado (5 casos, ELF real rodado com stdout afirmado) e rejeita
entrada malformada sem emitir binário (a classe do #485 — R6/Q7). O veredito é
amarrado ao SHA (`KOFC-GATE: PASS sha=…`). Prova RED-first offline:
`scripts/tests/test-kofc-gate-test.sh` (5 cenários, registrado em
`run-agent-tests.sh`). PASS medido em `8fa39ff9`.

## Android

O repositório possui Android em evolução e decisões recentes dizem, em faces específicas, que "Android é JVM" e deve compartilhar comportamento onde essa paridade foi decidida (verificado: `docs/development/DECISIONS.md`, registro das faces DB; Android também aparece em `docs/distribution/INSTALL.md`).

Isso não significa automaticamente que Android inteiro já pertença ao Stable Surface 1.0.

A Mel precisa decidir:

```text
Android = Stable 1.0
ou
Android = experimental / post-1.0
```

**Decidido (`D-1.0-EDGES`, 20/09/2026): Android = Stable 1.0, com gate próprio**
(a opção cheia, não a parcial; o CI já roda o APK).

**Mecanismo do gate (entregue 20/09, EG-10):** `scripts/test-android-gate.sh` —
preflight honesto (JDK ≥ 25 + `jar`; `ANDROID_HOME` com build-tools COMPLETA
≥ 35 e uma plataforma `android-N/android.jar`); SDK ausente = SKIP honesto
(exit 3) **nomeando o que falta** — nunca verde mudo (R6) — e o CI `android.yml`
roda o MESMO gate. Com SDK ele roda `kof build --target android --apk` (o
pipeline standalone aapt2→d8→zip→zipalign→apksigner) e prova que o artefato é
um zip real com `AndroidManifest.xml` + `classes.dex`; o veredito é amarrado ao
SHA (`ANDROID-GATE: PASS sha=…`). Prova RED-first offline:
`scripts/tests/test-android-gate-test.sh` (6 cenários, registrado no
`run-agent-tests.sh`).

---

# 14. Golden byte parity

Onde o contrato do KOF exige comportamento observável idêntico:

```text
mesmo programa KOF
        ↓
JVM / Native / JS / Script
        ↓
stdout + exit + demais bytes observáveis
        ↓
byte parity
```

deve existir prova no candidato.

Onde existe diferença de target explicitamente decidida, não se inventa uma paridade artificial.

**Mecanismo (landed 20/09, EG-5 — fila §23 item 10 do `D-RELEASE-1.0`):**
`scripts/target-matrix.sh` roda o MESMO programa Kof por toda a superfície core e
prova a paridade byte-a-byte contra o oráculo JVM em um comando: `jvm`, `native`
(x86-64), `native.riscv64`, `native.aarch64`, `js`, `script` — o cross builda com
a toolchain e EXECUTA sob `qemu-<arch>` quando presente. Preflights honestos
(R6/R7): toolchain de build ausente num alvo core é `FAIL`; qemu ausente no cross
é `SKIP` honesto que resulta em `INCOMPLETE` (rc=2, nunca verde falso); `kofc`
(EG-9) e `android` (EG-10) aparecem como linhas `DELEGATED`, cada um com seu gate.
PASS medido em 20/09 nos seis alvos core (stdout idêntico, oráculo JVM). Prova
RED-first offline para a suíte de agentes: `scripts/tests/target-matrix-test.sh`
(o comparador reprova um alvo divergente e aceita igualdade; matriz coerente
PASSA; alvo core divergente FALHA nomeando-o; preflight sem JDK sai alto) —
registrado no `run-agent-tests.sh`. A **rodada do dia do RC na mesma candidata**
continua sendo o que satisfaz este item; o harness só torna essa rodada um comando.
O **gate de release 0.5.0** (`scripts/check_release_050_gate.sh`, condição 1)
auto-executa este harness e lê sua linha `PARITY: 100%`, então a paridade é
MEDIDA, nunca a olho.

---

# 15. Gaps fora do Stable Surface 1.0

O 1.0 não precisa necessariamente implementar toda ideia existente no roadmap.

Mas toda gap restante deve estar em um de dois estados:

```text
DENTRO DO 1.0
→ precisa ser fechada

FORA DO 1.0
→ precisa estar explicitamente documentada
```

Para uma gap ficar fora do 1.0 sem bloquear:

- target afetado conhecido;
- comportamento/diagnóstico conhecido;
- sem fallback silencioso;
- docs atualizadas;
- nenhum documento pode chamar a face de Stable por engano;
- decisão de escopo registrada.

A autoridade dessa decisão é a Mel.

---

# 16. VERSION / docs / metadata

**Antes do bump** o repositório mostrava em `beta-0.5.0`:

```text
VERSION = 0.4.7-beta
```

Isto era coerente com `D-BRANCH-0.5.0`, porque a própria decisão diz que o bump de versão é item de release-prep e o número é confirmado pela mantenedora no corte; nenhum agente faz bump unilateral.

**Atualizado (`D-VERSION-BUMP-0.5.0`, 20/09/2026):** a mantenedora ordenou o bump, então em `beta-0.5.0`:

```text
VERSION = 0.5.0-beta
```

`scripts/bump-version.sh` sincronizou `VERSION` + `pom.xml` + `version.properties` + os stamps de versão corrente dos docs (EN+PT); o histórico ficou intacto.

**Drift de metadados (medido em 20/09/2026):** os docs de release in-repo foram
sincronizados pelo bump (`release-naming.md` e `INSTALL.md` agora dizem "Current
version: 0.5.0-beta"). O único drift restante é o **site público**
(`koflang.github.io` ainda mostra `v0.4.1-beta`, e KofJS "Em desenvolvimento"
enquanto JS está na superfície 1.0) — **propriedade da mantenedora e adiado**:
ela vai atualizar o site em breve (chat 20/09). O lado do repositório do EG-7
está feito.

No futuro candidato 1.0, sincronizar pelo menos:

```text
VERSION
pom.xml
version.properties empacotado
CHANGELOG
AGENTS header
release docs
site público (koflang.github.io)
target/support matrix
release notes
tag
artefatos
```

---

# 17. Feature freeze — redação corrigida

O checklist mantém:

```text
[ ] RC sem feature nova
[ ] apenas fixes durante RC
```

Mas a interpretação normativa proposta é:

> **Após o corte do primeiro RC de 1.0, congela-se a superfície pública aprovada para o KOF 1.0.**

Durante RC continuam permitidos:

- bug fixes;
- security fixes;
- correções de paridade;
- testes necessários para provar fixes;
- documentação;
- correções de packaging;
- correções de CI/release engineering;
- refactors estritamente necessários à estabilidade, sem expandir o contrato.

Não entram sem nova decisão da Mel:

- nova sintaxe;
- nova semântica;
- nova API pública Stable;
- nova feature de stdlib fora do surface já aprovado;
- expansão de target/domínio;
- feature "pequena" adicionada só porque parece barata.

Se um blocker exigir mudança na superfície pública:

```text
Mel decide:
A) mudar o contrato e reiniciar a validação RC
ou
B) adiar para pós-1.0
```

---

# 18. Beta-0.5.0 continua aberta a desenvolvimento

Este ponto deve permanecer explícito para aderir à direção atual da mantenedora.

`D-BRANCH-0.5.0` diz que todo novo trabalho vai para a nova branch ativa.

Portanto a `beta-0.5.0` não deve ser tratada por este documento como uma RC disfarçada.

A sequência é:

```text
beta-0.5.0
    ↓
novas releases beta se a Mel decidir
    ↓
surface 1.0 explicitamente aprovado
    ↓
primeiro RC 1.0
    ↓
freeze de surface
    ↓
fixes / estabilização
    ↓
Stable 1.0
```

Este contrato não tenta decidir quantas Betas existirão antes do RC.

---

# 19. RC → Stable

A promoção só ocorre se:

```text
EXIT_GATE(final_rc_sha) = GREEN
AND
release_blockers = 0
AND
new_public_features_since_rc_cut = 0
AND
regressions_since_rc_cut = 0
```

E o artefato Stable deve ser validado novamente.

Nunca usar "RC anterior estava verde" como prova para um SHA diferente.

---

# 20. Situação observada versus o futuro EXIT GATE

| Item | Situação observada nesta revisão | Pode marcar? |
|---|---|---|
| CI principal verde | não há candidato 1.0 declarado | NÃO |
| Quality/Security confiáveis | Security verde; Quality Gate vermelho (`CodeQL Gate (alerts API)` = failure em 20/09); #555 aberto; API com 38 alertas abertos na beta-0.5.0 | NÃO |
| 0 release-blockers | triagem 1.0 ainda não ratificada | NÃO |
| pacote fora do repo | #550 provou o padrão e foi fechado; não é ainda prova de candidato 1.0 | NÃO |
| JVM green | sem candidato 1.0 | NÃO |
| x86-64 green | sem candidato 1.0 | NÃO |
| riscv64 green | sem candidato 1.0 | NÃO |
| aarch64 green | sem candidato 1.0 | NÃO |
| JS green | sem candidato 1.0 | NÃO |
| Script green | sem candidato 1.0 | NÃO |
| byte parity | sem rodada final do candidato 1.0 | NÃO |
| gaps fora do surface | surface 1.0 ainda não aprovado | NÃO |
| metadata sincronizada | VERSION segue 0.4.7-beta por decisão correta de release-prep; drift já existente: site público em v0.4.1-beta e `release-naming.md`/`INSTALL.md` em "Current version 0.4.0-beta" | NÃO |
| branch ativa convergida (seção 6) | 3 commits só em `beta-0.4.0` (todos da Mel) ainda não absorvidos pela `beta-0.5.0` | NÃO |
| RC sem feature nova | ainda não existe RC 1.0 | N/A |
| fixes-only durante RC | ainda não existe RC 1.0 | N/A |
| RC → Stable sem regressão | ainda não existe RC 1.0 | N/A |

Os checkboxes não devem ser pré-verdejados antes de existir um candidato 1.0 real.

---

# 21. Pontos que a Mel precisa decidir antes da ratificação

**Respondido (`D-1.0-EDGES`, 20/09/2026):** Q1, Q2 e Q7 abaixo agora estão
decididas (ver a nota em cada uma). Q3–Q6 e Q8 já foram ratificadas no
`D-RELEASE-1.0`.

## Q1 — Quando começa formalmente a linha 1.0?

A preparação atual é da `beta-0.5.0`.

```text
Qual evento/decisão encerra a sequência Beta e abre a preparação do primeiro RC 1.0?
```

**Respondido:** a linha 1.0 abre **após o release 0.5.0 ser cortado e os
EG-1..EG-7 fechados**; só então a Mel declara e a primeira candidata a RC é
cortada (EG-8).

## Q2 — Surface de targets 1.0

Confirmar explicitamente:

```text
[ ] JVM
[ ] Native x86-64
[ ] Native riscv64
[ ] Native aarch64
[ ] JS
[ ] Script
[ ] KofC ?
[ ] Android ?
```

Dado para a decisão (medido em 20/09/2026): o site público marca KofC como "Disponível" e KofJS como "Em desenvolvimento"; o checklist da v3 tem o inverso (JS dentro, KofC fora, sem decisão).

**Respondido:** a superfície são os seis acima **mais KofC e Android** — oito
alvos, cada um com gate próprio. KofC e Android são alvos Stable 1.0.

## Q3 — Release blocker

Qual mecanismo é normativo: label GitHub, milestone, ledger, release-prep ou combinação?

## Q4 — Freeze

Aprovar ou alterar:

> o freeze começa somente após o primeiro RC 1.0 e congela a superfície pública, não o trabalho de estabilização.

## Q5 — Gaps

Aprovar ou alterar:

> gap pode permanecer em 1.0 somente se estiver explicitamente fora do Stable Surface e tiver comportamento honesto/documentado.

## Q6 — Quality Gate

Definir o nível de exigência:

> verde não basta; o gate precisa provar que está analisando o SHA correto e não produz falso-green/falso-red conhecido.

## Q7 — KofC e Android

Decidir se são targets Stable 1.0 ou superfícies separadas/experimentais.

Se KofC ou Android ficarem fora do Stable Surface 1.0, o site público, o README e os docs de arquitetura precisam deixar isso explícito (hoje o site marca KofC como "Disponível").

**Respondido:** ambos são **alvos Stable 1.0 com gate próprio**. Nenhuma nota de
"fora" no site/README é necessária; o "Disponível" do KofC no site agora fica
consistente com o contrato.

## Q8 — Ratificação

Se aprovada, registrar como:

```text
D-RELEASE-1.0 — KOF 1.0 EXIT GATE
```

ou usar o identificador que a Mel preferir.

---

# 22. Approval block

```text
MAINTAINER REVIEW — MEL

[x] APPROVED AS WRITTEN
[ ] APPROVED WITH CHANGES
[ ] REQUEST CHANGES
[ ] REJECTED / SUPERSEDED

Reviewer: Mel Santos (maintainer) — via chat, recorded by the docs lane as
          instructed ("decisão ... ratificada. concordo com o planejamento")
Date: 09/20/2026
Decision reference / commit: D-RELEASE-1.0 — KOF 1.0 EXIT GATE (docs/development/DECISIONS.md)
Notes: Maintainer's words (verbatim, PT): "setar como meta de desenvolvimento a
  estabilização dos contratos seguindo o planejamento existente nessa issue.
  kof RC 1.0.0 e kof release 1.0.0 só existem QUANDO todos os pontos estiverem
  correspondentes e não houver nenhuma aresta aberta". The EXIT GATE becomes
  binding development meta; the §23 queue opens on beta-0.5.0. Ratification
  covers the contract and the plan — the still-open sub-questions of §21
  (KofC and Android inside the Stable 1.0 surface; the exact declaration that
  opens the first RC) are themselves "arestas abertas": each must be closed by
  the maintainer BEFORE the first RC candidate, per the no-open-edge rule.
```

Nenhum agente preenche este bloco em nome da mantenedora. *(O bloco acima foi
preenchido pelo agente APENAS como registro mecânico da ratificação explícita da
mantenedora no chat de 20/09/2026, citando suas palavras como evidência — não em
nome dela.)*

---

# 23. Após aprovação da Mel

Somente depois da aprovação:

1. atualizar a branch ativa indicada pela Mel;
2. reler `DECISIONS.md`, `AGENTS.md` e release-prep corrente;
3. registrar a decisão normativa;
4. sincronizar EN/PT;
5. definir `release-blocker` de forma mecânica;
6. implementar machine gate;
7. escrever REDs para o gate antes de alterar lógica;
8. validar BEFORE/AFTER;
9. testar package real;
10. executar a matriz final;
11. só então criar o primeiro candidato RC 1.0.

---

# 24. Regra de atualização deste documento

O KOF está mudando rapidamente.

Antes de qualquer ratificação:

```text
git fetch origin
git checkout beta-0.5.0
git pull --rebase origin beta-0.5.0
git log -1 --oneline
```

e revalidar:

```text
DECISIONS.md
AGENTS.md
release-prep atual
issues abertas
PRs abertas
CI
CodeQL
backend-parity
known-bugs
gaps
VERSION
CHANGELOG
```

Se a Mel mudar novamente a branch ativa, o número de release ou o surface pretendido, **a decisão mais nova da mantenedora prevalece** e este documento deve ser atualizado antes de virar norma.

---

# 25. Resumo executivo

```text
HOJE
beta-0.5.0
desenvolvimento continua
nova release está em preparação
#550/#553/#554 fechadas
#555 ainda aberta
VERSION ainda não deve ser bumpada unilateralmente
        │
        ▼
FUTURO — quando Mel decidir preparar 1.0
        │
        ├── definir surface Stable
        ├── zerar release-blockers
        ├── fechar/confinar gaps
        ├── gates confiáveis
        ├── package real
        └── matriz final
                │
                ▼
            1.0 RC
                │
                ├── freeze da superfície pública
                ├── fixes/security/parity/docs/release only
                └── zero regressão
                        │
                        ▼
                    Stable 1.0
```

> **O KOF 1.0 EXIT GATE deve respeitar a linha de release que a Mel está conduzindo agora. Ele não força 1.0, não congela a Beta e não antecipa decisões que pertencem à mantenedora. Ele apenas define a evidência que o projeto deverá exigir quando a Mel decidir que chegou a hora do 1.0.**


---

# 26. Regra de benchmark externo — atualizada pelo critério de evidência

A pesquisa externa desta revisão segue uma regra explícita:

> **O que vem de fora serve como inspiração comparativa. Não define a direção
> do KOF e nunca se sobrepõe às decisões da Mel, ao `DECISIONS.md`, aos testes,
> à documentação normativa ou à implementação medida do projeto.**

A ordem continua sendo KOF-first:

```text
KOF
DECISIONS
contrato/documentação
testes/goldens
paridade/gaps
implementação medida
        ↓
gap/problema provado
        ↓
pesquisa externa
        ↓
tradução do princípio de volta para KOF
        ↓
revisão/aprovação da Mel quando muda contrato
```

## 26.1 Pesos usados nesta revisão

A ponderação foi ajustada para que **pesquisa acadêmica empírica, teste real e
case real dominem a análise**.

| Peso | Fonte | Como entra na análise |
|---:|---|---|
| **5** | **Pesquisa acadêmica/empírica de qualidade + teste real + case real com dados observados** | evidência externa dominante; usada para procurar mecanismos que realmente funcionaram ou falharam |
| **3** | Repositório maduro, amplamente usado e com processo de release executado repetidamente | precedente operacional forte |
| **2** | Documentação/processo oficial de outras linguagens/projetos | referência de design/processo, não prova suficiente sozinha |
| **1** | Fóruns/discussões/comentários comunitários | sinal fraco; serve para descobrir problemas e experiências, nunca para decidir o contrato |

### Regra adicional

Não se somam fontes duplicadas artificialmente.

Um paper e uma página que apenas reproduz o mesmo paper contam como **uma
evidência**, não duas.

Da mesma forma:

```text
popularidade ≠ prova
documentação ≠ resultado medido
forum ≠ contrato
```

---

# 27. Evidência específica da Mel fora do Git

As publicações públicas da Mel não substituem `DECISIONS.md`, mas ajudam a
interpretar a intenção do projeto quando são coerentes com o que depois aparece
no repositório.

Dois padrões públicos são especialmente aderentes ao EXIT GATE.

## 27.1 "Números reais, ou nenhum número"

O site oficial do KOF declara:

> **"Números reais, ou nenhum número."**

E também assume publicamente que o projeto mostra o que existe, o que está
sendo construído e para onde está indo, sem transformar roadmap em promessa.

**Tradução para o EXIT GATE:**

```text
checkbox sem medição = checkbox não concluído
```

Nenhum target fica GREEN por documentação, expectativa ou memória.

## 27.2 Software real como validação

Na publicação sobre o Kof Editor, Mel descreve explicitamente a construção de
um software real em KOF como uma das melhores formas de validar a linguagem.

Isso já tem um correspondente concreto no Git:

- `KofLang/Kof-Editor`;
- CI próprio;
- histórico de restrições encontradas usando o KOF de verdade;
- bugs descobertos pelo editor e transformados em repros;
- registro histórico de suíte `669/669`;
- repros em três targets;
- paridade byte a byte restaurada em um dos ciclos documentados.

**Consequência proposta para 1.0:**

o gate não deve parar em `hello.kf`.

O candidato 1.0 deve provar pelo menos uma aplicação real de referência usando o
**artefato empacotado do candidato**, fora da árvore de `Kof4j`.

A Mel deve decidir qual aplicação/corpus será oficial para esse gate.

Candidato natural para discussão:

```text
Kof Editor
```

mas este documento NÃO o torna obrigatório sem aprovação.

Fontes KOF específicas:

- https://koflang.github.io/
- https://pt.linkedin.com/posts/aminadojava_github-koflangkof-editor-activity-7497346539044970497-R3oP
- https://github.com/KofLang/Kof-Editor

---

# 28. Pesquisa acadêmica e cases reais — peso 5

## 28.1 Ericsson — feature freeze curto e sustentado por automação

**Estudo:** Eero Laukkanen, Maria Paasivaara, Juha Itkonen, Casper Lassenius e
Teemu Arvonen, *Towards Continuous Delivery by Reducing the Feature Freeze
Period: A Case Study*, ICSE-SEIP 2017.

Case real em organização de P&D da Ericsson.

Resultados relatados:

- redução de **56%** no período de feature freeze;
- depois disso, **63% menos mudanças durante o freeze**;
- **59% menos mudanças perto da data de release**;
- automação de testes foi um habilitador central.

Fonte:
https://research.aalto.fi/en/publications/towards-continuous-delivery-by-reducing-the-feature-freeze-period/

### O que isso inspira no KOF

Não congelar a Beta cedo.

Usar um **RC curto, deliberado e altamente automatizado**, quando o surface já
estiver escolhido.

Isso reforça a correção que já fizemos no documento:

```text
beta-0.5.0 ≠ freeze
primeiro RC aprovado = início do freeze do surface
```

### O que NÃO copiar

O paper não define quantos dias o RC do KOF deve durar.

Qualquer duração fixa seria inventada.

---

## 28.2 Linux + Chrome — estabilização é uma fase própria

**Estudo:** Md Tajmilur Rahman e Peter C. Rigby,
*Release Stabilization on Linux and Chrome*, IEEE Software 2015.

O estudo empírico encontrou uma fase real de estabilização mesmo em ciclos
rápidos e mostrou que um grupo pequeno controla boa parte desse trabalho.

No caso Linux analisado, a estabilização segue por RCs até que regressões
importantes não estejam mais pendentes.

Fontes:
- https://users.encs.concordia.ca/~pcr/paper/Rahman2015IEEES-preprint.pdf
- DOI 10.1109/MS.2015.31

### Inspiração para KOF

Adicionar ao processo de RC:

```text
RELEASE OWNER / VERIFIER MATRIX
```

Cada target/gate do 1.0 deve ter:

- responsável pela evidência;
- SHA;
- comando/job;
- resultado;
- eventual verifier independente;
- pendência explícita.

Isso casa bem com o padrão já existente de lanes, ownership e verifier por
risco do KOF.

---

## 28.3 Builds com testes falhando e crashes pós-release

> **⚠ NÃO VERIFICADO (correção da v3.1).** A v3 citava medianas de crash reports
> (447 / 247 / 2) atribuídas à tese abaixo. Na revalidação de 20/09/2026 o PDF foi
> baixado, mas o texto não pôde ser extraído e uma busca independente não
> encontrou esses valores. **Os números foram retirados deste documento** até que
> alguém confirme a página exata. O que a busca confirmou é só a existência de
> trabalho correlato de Rahman e Rigby (2018) sobre o impacto de testes
> falhando/flaky/de alta falha no número de crash reports de builds do Firefox.

A linha de pesquisa de Rahman sobre release engineering associa builds com testes
falhando a mais crashes pós-release. **A direção da associação é o único ponto
aproveitado aqui, e ainda depende dessa confirmação; nenhum valor numérico dela
sustenta qualquer requisito do EXIT GATE.**

Fonte (não reverificada quanto aos números):
https://spectrum.library.concordia.ca/id/eprint/983513/1/Rahman_PhD_S2018.pdf

### Inspiração para KOF

O EXIT GATE deve distinguir:

```text
RED explicado
RED flake
RED infraestrutura
RED regressão
```

mas **não pode normalizar RED ignorado**.

Isto reforça:

```text
[ ] nenhuma falha obrigatória ignorada
[ ] nenhuma flake usada como desculpa sem evidência
[ ] exceção/reclassificação registrada
```

---

## 28.4 8,8 bilhões de execuções — flake escondida continua sendo risco

**Estudo 2026, IEEE TSE:** Leinen, Gruber, Erdogan, Stahlbauer e Pretschner,
*An Empirical Study of Detected and Undetected Flaky Test Failures in Real-World
CI Pipelines*.

Foram analisadas **8,8 bilhões de execuções de testes** em quatro projetos de
escala industrial.

Entre os resultados:

- falhas flaky não detectadas responderam por **9,8%–16,3%** dos pipelines
  falhos;
- ambiente teve impacto relevante, com variação de flakiness chegando a
  aproximadamente **3x** entre ambientes.

Fonte:
https://portal.fis.tum.de/en/publications/an-empirical-study-of-detected-and-undetected-flaky-test-failures/

### Inspiração para KOF

Um rerun verde não deve automaticamente converter uma falha de release em
"infra".

Para o candidato 1.0:

```text
falha
  ↓
reproduzir em ambiente limpo
  ↓
classificar
  ├── regressão → fix
  ├── flake conhecida → issue + owner + evidência
  └── infra → evidência do ambiente
```

Se a falha não puder ser classificada, o gate permanece inconclusivo/RED.

---

## 28.5 Release readiness não é uma única métrica

O estudo *Monitoring and Controlling Release Readiness by Learning Across
Projects* analisou atributos de readiness em dezenas de projetos.

Os gargalos recorrentes incluíram:

- taxa de CI;
- taxa de conclusão de features;
- taxa de correção de bugs.

Fonte:
https://doi.org/10.1007/978-3-319-31545-4_14

### Inspiração para KOF

O `EXIT GATE` deve permanecer uma **matriz de evidências**, não virar um único
score numérico.

Não criar:

```text
"KOF readiness = 87%"
```

como critério de release.

Melhor:

```text
cada condição é observável
cada condição tem prova
qualquer blocker obrigatório continua blocker
```

---

# 29. Repositórios maduros — peso 3

## 29.1 LLVM — RC contra baseline, regressões e pacotes reais

O processo real de release do LLVM tem elementos particularmente relevantes:

- testers oficiais por targets/ambientes;
- RCs construídos e testados como artefatos;
- comparação do RC com release/RC anterior;
- regressões são identificadas e registradas;
- regressões precisam ser fechadas antes das etapas seguintes;
- artefatos recebem verificação/attestation no pipeline atual;
- release blockers vivem em milestone/processo explícito.

Fontes:
- https://github.com/llvm/llvm-project/blob/main/llvm/docs/ReleaseProcess.md
- https://github.com/llvm/llvm-project/blob/main/llvm/docs/HowToReleaseLLVM.rst
- https://github.com/llvm/llvm-project/blob/main/llvm/RELEASE_TESTERS.TXT

### Inspiração KOF

Adicionar um **baseline explícito**:

```text
candidate
   vs
last accepted baseline
```

Não basta:

```text
candidate passou
```

Deve também responder:

```text
candidate introduziu regressão?
```

---

## 29.2 Rust — Crater como prova de compatibilidade no mundo real

O Rust roda Crater para comparar Beta contra Stable em um corpus amplo de
crates, inclusive em modo `build-and-test`.

Fonte:
https://forge.rust-lang.org/release/crater.html

### Inspiração KOF

O KOF ainda não tem o tamanho de ecossistema do Rust, então copiar Crater seria
exagero.

Mas o princípio é forte:

> compatibilidade deve ser medida contra consumidores reais, não inferida só
> pela suíte do compilador.

Proposta para Mel:

```text
KOF 1.0 COMPATIBILITY CORPUS
```

Possíveis componentes:

- exemplos oficiais;
- snippets normativos executáveis;
- training/learn que contêm programas completos;
- projetos KOF oficiais;
- Kof Editor;
- outros consumidores reais aprovados pela mantenedora.

O corpus deve usar o candidato empacotado, não classes internas do repositório.

---

## 29.3 Kubernetes — release para quando o sinal está vermelho

O handbook de release do Kubernetes determina que erros no snapshot de testes
interrompam o processo até serem:

```text
FIXED
ou
explicitamente marcados como NON-RELEASE-BLOCKING
```

Também exige que RCs posteriores sejam medidos na própria release branch.

Fonte:
https://github.com/kubernetes/sig-release/blob/master/release-engineering/handbooks/release-cuts.md

### Inspiração KOF

Adicionar uma política de waiver:

```text
release blocker só deixa de bloquear com:
- motivo;
- evidência;
- dono;
- aprovação explícita da Mel/release authority;
- destino da pendência.
```

Não existe waiver implícito.

---

# 30. Documentação oficial de outras linguagens — peso 2

## 30.1 Python — freeze tem fase clara

O PEP 602 separa claramente desenvolvimento, Beta e RC.

Na política Python, o freeze começa já na primeira Beta; isso **não deve ser
copiado automaticamente pelo KOF**, pois a direção atual do KOF mantém a Beta
aberta.

O princípio reaproveitável é outro:

> cada fase precisa ter regras de mudança explícitas.

Fonte:
https://peps.python.org/pep-0602/

### Tradução correta para KOF

```text
KOF Beta   = desenvolvimento permitido conforme contrato corrente
KOF RC     = public surface freeze + estabilização
KOF Stable = contrato aprovado + compatibilidade
```

---

## 30.2 Rust — estabilidade exige experiência antes de Stable

O rustc-dev-guide documenta que features novas não entram simplesmente no
Stable: passam por canais e experiência antes da estabilização, em razão das
garantias fortes de compatibilidade.

Fonte:
https://github.com/rust-lang/rust/blob/master/src/doc/rustc-dev-guide/src/implementing-new-features.md

### Inspiração KOF

No primeiro RC, gerar um **snapshot do Stable Surface**:

```text
grammar/semantics decididas
stdlib pública
CLI pública
targets declarados
gap codes permitidos
diagnósticos contratuais onde aplicável
```

Depois do snapshot:

```text
diff de surface != vazio
    ↓
não passa silenciosamente
    ↓
Mel decide mudança + reinício de validação
ou
adiamento pós-1.0
```

Isso transforma "sem feature nova" em uma regra verificável.

---

# 31. Fóruns — peso 1

Discussões do Python sobre RCs reais reiteram que, nessa fase, entram apenas
mudanças revisadas que sejam correções claras, com objetivo de minimizar
alterações antes da final.

Exemplo:
https://discuss.python.org/t/python-3-15-0-release-candidate-1-is-here/108395

Discussões do Rust também tratam Beta como oportunidade para detectar regressões
antes do Stable:
https://internals.rust-lang.org/t/the-case-for-a-new-relese-channel-testing/14412

### Uso no benchmark

Essas fontes só corroboram padrões já encontrados em fontes mais fortes.

Elas **não adicionam requisito ao KOF**.

---

# 32. Melhorias propostas ao KOF 1.0 EXIT GATE após o benchmark

As linhas abaixo são **candidatas**, não contrato.

Cada uma precisa ser aprovada pela Mel antes de entrar no bloco normativo.

**RATIFICADO 20/09/2026 (`D-1.0-EDGES`):** os nove candidatos de reforço viram
**gates obrigatórios** (os marcadores abaixo leem `[RATIFICADO]`). As propostas
da §32.x são agora gates normativos do contrato de saída, não candidatos.

## 32.1 Gate de aplicação real

Adicionar:

```text
[RATIFICADO] aplicação real de referência validada com o pacote candidato
```

Forma proposta:

```text
release artifact
      ↓
ambiente externo ao Kof4j
      ↓
aplicação KOF real
      ↓
build/check/run
      ↓
prova funcional
```

**Aderência KOF:** MUITO ALTA.

É diretamente coerente com a forma como a própria Mel descreve a validação do
Kof Editor.

---

## 32.2 Gate de baseline/regressão

Adicionar:

```text
[RATIFICADO] candidato comparado ao último baseline aceito, sem nova regressão
```

Isso complementa:

```text
RC → Stable sem regressão
```

porque define "sem regressão" como comparação e não sensação.

Baseline possível:

```text
último RC aceito
ou
última Beta declarada referência pela Mel
```

---

## 32.3 Política explícita de flaky tests

Adicionar ao CI/release evidence:

```text
[RATIFICADO] 0 falhas obrigatórias sem classificação
[RATIFICADO] flakes conhecidas têm issue + owner + evidência
[RATIFICADO] rerun isolado não apaga automaticamente a primeira falha
```

Para 1.0, uma flake não resolvida pode ser:

- release-blocker;
- explicitamente non-blocking;
- ou gap de infraestrutura;

mas nunca "sumir" sem classificação.

---

## 32.4 Snapshot do Stable Surface no RC1

Adicionar:

```text
[RATIFICADO] Stable Surface snapshot criado no RC1
[RATIFICADO] nenhum drift de surface sem nova decisão
```

Este snapshot torna o feature freeze mensurável.

---

## 32.5 Compatibility corpus

Adicionar:

```text
[RATIFICADO] corpus de consumidores reais compila/roda com o candidato
```

Não precisa ter escala Rust/Crater.

Começa pequeno e honesto.

---

## 32.6 Artifact identity gate

Fortalecer:

```text
[ ] pacote real testado fora do repo
```

para:

```text
[RATIFICADO] pacote testado tem o MESMO digest do artefato que será publicado
```

Registro OBRIGATÓRIO (ratificado 20/09 por `D-ARTIFACT-TRUST` — não é mais
"idealmente"):

```text
SHA256
builder/run
commit SHA
target
timestamp
```

O contrato ratificado vai além do registro: **integridade + artefato exato +
proveniência de build neutra, atestada pelo workflow oficial**; o portão de
release **BLOQUEIA** sem evidência válida, e o `kof deps resolve`
**BLOQUEIA DURAMENTE** pacotes oficiais sem ela (pacotes da comunidade =
aviso honesto, nunca silêncio). Fila de execução: (a) checagem de digest no
rc-gate + jars standalone no `SHA256SUMS`, (b) attest+verify no workflow de
release, (c) checagem de evidência no lado do resolve — `DECISIONS.md`
§D-ARTIFACT-TRUST.

O próprio roadmap/decisões do KOF já usa `SHA256SUMS` na direção do registry,
portanto este princípio tem aderência interna adicional.

---

## 32.7 Evidência de release por target

Proposta:

```text
target         SHA        proof/job        result     verifier
JVM            ...        ...              GREEN      ...
x86-64         ...        ...              GREEN      ...
riscv64        ...        ...              GREEN      ...
aarch64        ...        ...              GREEN      ...
JS             ...        ...              GREEN      ...
Script         ...        ...              GREEN      ...
```

Para gates HIGH-risk, aproveitar a direção já existente no KOF de verifier
independente.

---

## 32.8 Waiver de release formal

Se algo vermelho for considerado não bloqueante:

```text
waiver id
finding/test
causa
por que não bloqueia
risco residual
owner
release destino
aprovação Mel
```

Sem isso:

```text
RED continua RED
```

---

# 33. O que NÃO recomendo importar

Mesmo com fontes fortes, alguns mecanismos de outros projetos não são bons
candidatos para cópia direta.

## 33.1 Não copiar o calendário Python

Não há evidência interna de que KOF precise de Beta/RC com duração fixa.

## 33.2 Não copiar o Crater inteiro

KOF ainda tem outro tamanho de ecossistema.

Copiar a infraestrutura seria custo sem benefício proporcional.

Reaproveitar apenas o princípio do **consumer corpus**.

## 33.3 Não criar burocracia de Kubernetes

Kubernetes precisa de uma Release Team grande porque o projeto é enorme.

KOF pode ter o mesmo rigor com:

```text
manifesto pequeno
owner claro
evidência automática
decisão Mel
```

## 33.4 Não transformar score em release decision

Os pesos desta pesquisa ordenam **fontes externas**.

Eles NÃO produzem:

```text
score >= 80 → ship 1.0
```

A decisão continua binária por contrato:

```text
gates obrigatórios satisfeitos?
sim/não
```

---

# 34. Ranking das melhorias pelo benchmark ponderado

Os pontos abaixo são **evidence points externos**, não pontuação de produto.

| Proposta | Evidência acadêmica/case (5) | Repo maduro (3) | Docs (2) | Fórum (1) | Leitura |
|---|---:|---:|---:|---:|---|
| não normalizar RED/flake; classificar falha | 15 | 3 | 0 | 0 | **fortíssima** |
| freeze curto e somente em fase de estabilização | 10 | 6 | 2 | 1 | **fortíssima** |
| comparar candidato vs baseline anterior | 5 | 6 | 0 | 0 | **forte** |
| testar aplicação/consumidor real | 5 + evidência direta KOF | 3 | 0 | 0 | **fortíssima para KOF** |
| release owner/verifier por target | 5 | 6 | 0 | 0 | **forte** |
| compatibility corpus | 0 | 3 | 2 | 1 | **moderada externamente, alta aderência prática** |
| snapshot do Stable Surface | 0 | 3 | 4 | 1 | **moderada/forte** |
| artifact digest/provenance | 0 | 6 | 2 | 0 | **forte + aderência interna SHA256SUMS** |
| waiver formal para non-blocking | 0 | 6 | 0 | 0 | **forte operacionalmente** |

> Os números servem apenas para ordenar a pesquisa externa.
> **Nenhuma linha vence uma decisão KOF existente.**

---

# 35. EXIT GATE v3 — bloco candidato para revisão da Mel

O checklist original continua intacto:

```text
[ ] CI principal verde
[ ] Quality/Security gates verdes e confiáveis
[ ] 0 bugs OPEN classificados como release-blocker
[ ] pacote real testado fora do repo
[ ] JVM green
[ ] x86-64 green
[ ] riscv64 green
[ ] aarch64 green
[ ] JS green
[ ] Script green
[ ] KofC green (gate próprio — `D-1.0-EDGES`)
[ ] Android green (gate próprio — `D-1.0-EDGES`)
[ ] golden byte parity onde o contrato exige
[ ] todas as gaps restantes explicitamente fora do surface 1.0
[ ] VERSION / docs / release metadata sincronizados
[ ] RC sem feature nova
[ ] apenas fixes durante RC
[ ] RC → Stable sem regressão
```

### Candidatos de reforço — RATIFICADOS (os nove, `D-1.0-EDGES` 20/09/2026)

```text
[x] Stable Surface snapshot congelado no RC1
[x] 0 falhas obrigatórias sem classificação
[x] política de flaky tests aplicada ao candidato
[x] candidato comparado ao último baseline aceito
[x] compatibility corpus executado
[x] aplicação KOF real validada com o pacote candidato
[x] digest do pacote testado == digest do pacote publicado
[x] manifesto de evidência por target
[x] waiver de release somente explícito, documentado e aprovado
```

**Decidido:** os nove viram gates obrigatórios — não só os quatro recomendados.
A recomendação abaixo fica como contexto histórico e está **sobreposta**.

Minha recomendação para discussão com a Mel é **não transformar todos em novos
gates de uma vez**.

Os quatro com melhor relação entre evidência, aderência e custo são:

```text
1. aplicação real com pacote candidato
2. comparação contra baseline / zero regressão nova
3. política de falhas/flakes sem falso-green
4. snapshot do Stable Surface no RC1
```

Depois:

```text
5. artifact identity (SHA256/provenance)
6. evidence manifest por target
7. compatibility corpus crescente
8. waiver formal
```

---

# 36. Síntese da pesquisa

A pesquisa externa não muda a direção do KOF.

Ela reforça algo que já aparece tanto no repositório quanto na comunicação
pública da Mel:

```text
não provar feature por lista
provar por uso real

não chamar expectativa de métrica
medir

não esconder gap
explicitar

não confundir compilou com funciona
executar

não confundir CI verde com gate confiável
validar o próprio gate

não chamar RC enquanto o surface continua mudando
cortar RC quando começa a estabilização real
```

O objetivo do EXIT GATE não é tornar o KOF parecido com Rust, Python, LLVM,
Kubernetes, Chrome ou qualquer outro projeto.

O objetivo é aproveitar o que esses projetos e estudos aprenderam para fazer
uma pergunta melhor ao próprio KOF:

> **"Que evidência precisamos produzir para afirmar, dentro do contrato da Mel,
> que este exato artefato é realmente KOF 1.0?"**

A resposta final continua pertencendo ao KOF e à mantenedora.


---

# 37. Registro de verificação — revalidação de 20/09/2026 (v3.1)

Somente leitura: nenhum arquivo do repositório, issue, label ou decisão foi alterado.
Tips medidos: `beta-0.5.0 = 9ee038f7`, `beta-0.4.0 = 4ee3a5c9` (ambos mudam a cada pouso — revalidar).

## 37.1 Afirmações internas

| Afirmação da v3 | Resultado |
|---|---|
| `D-BRANCH-0.5.0` existe em `DECISIONS.md`, com a ordem da Mel citada | ✅ confirmado (texto idêntico) |
| `AGENTS.md` na `beta-0.5.0` declara a branch ativa | ✅ confirmado |
| `VERSION` e `pom.xml` = `0.4.7-beta` na `beta-0.5.0` | ✅ confirmado |
| `release-beta-0.5.0-prep.md`, `release-naming.md`, `VERSIONING.md` existem | ✅ confirmado |
| não existe `D-RELEASE-1.0` ratificado | ✅ confirmado (nenhuma ocorrência) |
| #550, #553, #554 CLOSED; #555 OPEN | ✅ confirmado (#555 é a única issue aberta) |
| `Code Quality Analysis` verde / `CodeQL Gate (alerts API)` vermelho / security-bot verde | ✅ confirmado (último `kof-quality-bot` concluído, SHA `7b2dd963`) |
| `beta-0.4.0 = 4ee3a5c9` | ✅ confirmado |
| tip `47a7b8f9` da `beta-0.5.0` | ⚠ existe e é ancestral do atual (`9ee038f7`) |
| "nenhum pouso residual só em `beta-0.4.0`" (condição da seção 6) | ❌ **não satisfeita hoje**: 3 commits (`4ee3a5c9`, `ce5e8e66`, `d3f79e7a`, todos da Mel) |
| #555 = 21 alertas | ⚠ a API de code-scanning devolve **38** abertos em `refs/heads/beta-0.5.0` |
| docs de release/versionamento citam `kofc` | ❌ **não confirmado** (0 menções em `docs/distribution/`; há no README e em `docs/architecture/`) |
| Android: "Android é JVM" em decisões recentes | ✅ confirmado (`DECISIONS.md`, faces DB; e `INSTALL.md` cita Android) |
| Kof Editor: CI próprio | ✅ existe (`CI (build + check)`); últimos 2 runs `action_required`, 1 `success` |
| Kof Editor: `669/669` e paridade byte a byte restaurada | ✅ confirmado em `docs/08-restricoes-kof.md` (`mvn clean test` 669/669 = 456 compiler + 8 script + 5 C-compiler; **resultado de build local**) |
| `SHA256SUMS` na direção do registry | ✅ confirmado (`DECISIONS.md`, D2 — registry MVP) |
| site: "Números reais, ou nenhum número." | ✅ confirmado (seção "Métricas") |

## 37.2 Achados novos (não estavam na v3)

- O site público (`koflang.github.io`) está em **v0.4.1-beta**, marca **KofC "Disponível"** e **KofJS "Em desenvolvimento"**. Diverge do checklist de targets e da `VERSION`.
- `docs/distribution/release-naming.md` e `INSTALL.md` ainda dizem "Current version: 0.4.0-beta".

## 37.3 Fontes externas

| Fonte | Resultado |
|---|---|
| Ericsson (Laukkanen et al., ICSE-SEIP 2017): freeze −56%, mudanças no freeze −63%, perto do release −59%, via automação de testes | ✅ confirmado no resumo |
| Leinen et al., IEEE TSE 2026: 8,8 bilhões de execuções, 4 projetos, 9,8%–16,3%, até 3× por ambiente | ✅ confirmado (portal TUM; publicação marcada como "in press") |
| Kubernetes release-cuts: parar o processo se o snapshot de testes tiver erro até corrigir ou marcar como não bloqueante; RCs seguintes medidos na release branch | ✅ confirmado |
| Rust Crater: beta × stable em corpus de crates, modo `build-and-test` | ✅ confirmado |
| Rahman (tese, Concordia): medianas 447 / 247 / 2 crash reports | ❌ **não verificado — números retirados** (seção 28.3) |
| Rahman & Rigby, *Release Stabilization on Linux and Chrome* (IEEE Software 2015) | ⚪ não reverificado |
| LLVM (`ReleaseProcess.md`, `HowToReleaseLLVM.rst`, `RELEASE_TESTERS.TXT`) | ⚪ não reverificado |
| Python PEP 602; Rust `implementing-new-features.md`; discussões de fórum (peso 1) | ⚪ não reverificado |
| Post da Mel no LinkedIn sobre o Kof Editor (seção 27.2) | ⚪ não reverificado (o conteúdo equivalente foi confirmado no repositório do Kof Editor) |

Os pesos (seção 26) e o ranking (seção 34) **não foram alterados**: as correções acima não mudam nenhuma recomendação, apenas o que é fato verificado e o que ainda não é.

**RATIFICADO EM 20/09/2026** — o bloco de aprovação (§22) foi preenchido como
registro mecânico da aprovação da mantenedora no chat (palavras dela citadas
lá); a decisão normativa é `D-RELEASE-1.0` em `docs/development/DECISIONS.md`.
Os candidatos de reforço `[? MEL]` da §35 estão **fechados** pelo `D-1.0-EDGES`
(20/09/2026): os nove viraram gates obrigatórios — os marcadores da §32 leem
`[RATIFICADO]`; não são mais arestas abertas.
