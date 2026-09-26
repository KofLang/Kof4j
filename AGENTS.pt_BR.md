[English](AGENTS.md) | [Português](AGENTS.pt_BR.md)

# AGENTS.md — Escrevendo Kof (guia para agentes de IA)

Este é o guia **obrigatório** para qualquer agente de IA (ou humano) que
escreva código Kof neste repositório. Leia antes de gerar qualquer `.kf`.

**Versão:** 0.5.0-beta · Última atualização: 18/09/2026 (modo autônomo + condição de ESTABILIDADE com recusa de re-disparo + **Portão de qualidade: nenhum bug sobe** + regra 8 **Kof não é Java** como ABSOLUTA (18/09) + regra 9 **portão docs-first** para issues fora da filosofia (#449) (18/09) + **push mecânico via `scripts/sync-push.sh` + política de conflito "preserve os dois lados, refaça o seu em cima" (19/09) + **armadilha do autostash: marcadores podem sobreviver a um rebase bem-sucedido — grep + gates na árvore PÓS-REBASE, §NNN reconferido contra o tip remoto (26/09)** + gate de máquina da fronteira stdlib R1 (17/09) + regra de claim compartilhada §NNN para ledgers multi-agente (18/09) + regra 10 **KOF-primeiro, externo-depois** (`D-KOF-FIRST`, DECIDED 19/09) + regra 11 **Lei da Simplicidade — tudo que chega à superfície da linguagem** como ABSOLUTO (20/09) + `D-MAKEALIVE`/`D-KOF-AS-CLOUD`/`D-BOOTSTRAP`/`D-DB-GAPS` (20/09); branch ativa = **`beta-0.5.0`** (`D-BRANCH-0.5.0`, 20/09 — `beta-0.4.0` só para pousos em voo + preparo de release); veja a regra 9)

> **PRIORIDADE Nº 1: QUALIDADE.** Antes de qualquer feature, leia o
> **Portão de qualidade — "nenhum bug sobe"** (§ abaixo), **universal para
> TODAS as branches**. A ordem é **reproduzir → consertar a causa raiz →
> provar com teste → rodar a suíte → só então commitar**. O teste **prova**;
> ele nunca substitui a correção. Entrega sem prova não é entrega: é bug
> adiado. A pressa de entregar é o maior risco do repo.

---

## Modo autônomo (definição — o padrão de operação desta sessão)

> **Entrar em modo autônomo = trabalhar sem interromper o humano, por dias a
> fio, até a próxima interferência humana.** O humano não está disponível para
> perguntas; o repo é a única fonte de verdade. Tudo de que você precisa já
> está nos documentos — se não está, é porque precisa ser escrito (e você
> escreve).

**O loop (nunca pare no meio):**

```
1. LEIA o estado (DOING.md, docs/status.md, git log, suíte) — nunca pergunte.
2. ESCOLHA a próxima tarefa: maior valor, sem dono `EM CURSO`, na sua lane.
3. REIVINDIQUE no DOING.md (mesmo commit do primeiro passo).
4. QUEBRE em escopos realizáveis numa sessão (ver "partes pequenas" abaixo).
5. EXECUTE um escopo → teste → commit → atualize DOING.md + todowrite.
6. VOLTE ao passo 1. Não anuncie "fim"; só pare por condição de parada.
```

**O loop dentro de UM turno (regra que impede o "parar e resumir"):**

> **O agente não se re-dispara sozinho.** Quando um turno termina, a execução
> para. Então: **terminar um turno com resumo é a única falha de autonomia
> imperdoável** — é o que transformou "dias a fio" em "um turno".

1. **Encadeie tool calls dentro do turno** até: (a) uma condição de parada,
   (b) o contexto quase esgotar, ou (c) o turno ficar sem trabalho novo
   (suíte verde + DOING.md sem item sem dono na sua lane).
2. **Proibido terminar o turno com resumo/status para o humano**
   ("pushed", "resumo da sessão", "o que falta agora é..."). Se o turno
   vai acabar, a ÚLTIMA coisa escrita no turno é:
   - commit final do estado atual,
   - `DOING.md` atualizado com a linha **"PRÓXIMO PASSO: <tarefa exata +
     arquivo + prova esperada>"** (o re-dispacho lê isso e continua),
   - `todowrite` espelhando isso.
   Depois disso, silêncio — ou a próxima tool call.
3. **Todo commit do turno exige atualização da linha no `DOING.md` no MESMO
   commit** (regra da seção multi-agente vale dobrado aqui: sem DOING.md
   atualizado, o próximo agente/sessão não sabe o que já existe).
4. **`todowrite` a cada mudança de etapa** — exatamente um `in_progress`;
   item só vai para `completed` com prova (teste verde/suíte).
5. **Re-dispacho é do humano ou de cron** (o agente não acorda a si mesmo).
    Ao entrar em modo autônomo, o agente **lança o cron** (ver "Heartbeat
    de cron" abaixo). Por isso o item 2b é contrato: quem volta — humano ou
    outra instância — deve conseguir retomar em ≤1 leitura do `DOING.md`,
    sem perguntar.
6. **Re-dispacho NÃO é conversa.** Quando o humano manda "continue", "vai",
   "e agora?" ou qualquer re-disparo: **não responda com reconhecimento ou
   status** ("Entendido", "ok", "pushed", "vou continuar..."). A PRIMEIRA
   ação do turno é a tool call que lê o `PRÓXIMO PASSO` e executa. Um turno
   que termina em frase de confirmação sem tool call é a MESMA falha de um
   turno que termina em resumo — o loop parou e o humano teve que empurrar
   de novo.
7. **Unidade em progresso = turno em progresso.** Se o turno vai acabar e
   existe uma unidade MEIO-EXECUTADA (edição aplicada sem teste rodado,
   teste verde sem commit, commit sem `DOING.md`), **acabe a unidade antes
   de encerrar**: rode o teste, commite, atualize o `DOING.md` — na mesma
   resposta, encadeando as tool calls. "Parei no meio de um edit" é o loop
   morrendo no ponto mais caro: o próximo agente herda working tree sujo
   sem saber o estado. Regra prática: **depois de todo tool call, a
   pergunta é "a unidade está commitada? não → próxima tool call agora"**,
   nunca "chega de tool calls nesta resposta?".

**Falhas reais que motivaram estas regras (05/09, três ocorrências):**
(a) o agente fez 5 commits corretos (fixes riscv64) e terminou o turno com
um "resumo da sessão" em vez de continuar o loop; `DOING.md` ficou sem
atualização desde o início do trabalho. (b) no MESMO dia, após o humano
dizer "continue", o agente respondeu "Entendido. Vou prosseguir." — um
turno inteiro gasto em frase de confirmação, sem tool call, sem trabalho.
(c) ainda no MESMO dia, com o loop rodando e um port (time.sleep) a 2
edições do commit, o turno TERMINOU logo após o último tool call de edit —
sem rodar o teste, sem commitar, sem atualizar o DOING.md; o humano teve
que empurrar de novo. Autonomia que termina em resumo, em "ok" ou **no
meio de uma unidade** não é autonomia — é polidez ou desatenção.

**O que fazer em vez de perguntar:**

| Dúvida | Fonte de resposta (nesta ordem) |
|---|---|
| "Existe dono nisso?" | `DOING.md` |
| "Qual a sintaxe/idiom real?" | `training/`, `learn/`, **compile e confirme** |
| "O que já funciona?" | suíte + E2E rodando (a prova, não a memória) |
| "Qual a próxima prioridade?" | `docs/status.md`, `docs/backend-parity.md`, `docs/development/` (fila P0→P5: `roadmap-audit.md`/`roadmap.md`/`specification-gaps.md` + `known-bugs.md`), `planning-*` |
| "Isso é decisão de design?" | **NÃO é sua** — registre gap/plano e siga (regra 6) |

**Escopo realizável numa sessão** = uma unidade coesa com prova ao fim
(teste verde, qemu rodando, suíte passando). Se a tarefa inteira não cabe,
faça o primeiro degrau, commite, e o próximo agente/sessão continua. Nunca
deixe trabalho grande não-commitado — é assim que se perde uma sessão.

**Condições de parada (as ÚNICAS que justificam parar e chamar o humano):**

1. **Semântica congelada em jogo** — mudança de contrato/operador/ordem de
   avaliação (regra 6): vira gap/plano em `planning-*`, nunca edição.
2. **Colisão de lane inevitável** — o único caminho toca um arquivo `EM CURSO`
   de outro agente e não dá para adiar: pare, registre no DOING.md, aguarde.
3. **Gate quebrado sem causa na sua mudança** — suíte vermelha que você não
   introduziu e não consegue diagnosticar: registre em `docs/bugs-and-gaps/known-bugs.md`
   com reproduções, não "conserte" o teste para passar.
4. **Requisito genuinamente ausente do corpus** — nem `training/`, nem
   `learn/`, nem o compilador respondem: escreva a pergunta no DOING.md na
   linha do item e siga para outra tarefa (não trave o loop).
5. **Desenvolvimento estável (condição de ESTABILIDADE — para o re-disparo)**
   — ver a seção seguinte: o loop só tem trabalho se houver trabalho real.

### Estabilidade: quando parar o loop e como avaliar cada re-disparo (obrigatório)

> **O desenvolvimento está estável quando as três condições abaixo valem ao
> mesmo tempo:**
>
> 1. **Todos os bugs resolvidos** — `docs/bugs-and-gaps/known-bugs.md` sem item
>    aberto (tudo `CORRIGIDO`/`FECHADO` com prova).
> 2. **Todo `docs/development/` concluído** — nenhuma doc com desenvolvimento
>    pendente (as concluídas já foram movidas para `docs/`, as não-iniciadas
>    movidas para `docs/development/future/`).
> 3. **Todo `docs/development/future/` desenvolvido** — os planos de futuro
>    implementados e validados (ou promovidos/promovidos a `docs/` conforme a
>    regra dos três estados).
>
> Sob essas três condições, **a suíte completa verde + a matriz de conformidade
> 5/5 são a prova da estabilidade** — ela é um ESTADO a verificar, não uma
> opinião a declarar.
>
> **A cada mensagem do modo autônomo (heartbeat/re-disparo), o agente DEVE
> avaliar antes de agir:**
>
> - **Surgiu regressão** (suíte vermelha, teste novo falhando, bug reintroduzido,
>   documento novo adicionado a `docs/development/` com trabalho pendente)?
>   → **assuma a tarefa**: reivindique no DOING.md e execute o loop normal.
> - **Não surgiu nada** (as três condições de estabilidade continuam valendo e
>   a suíte está verde)? → **RECUSE o pedido do modo autônomo**: não invente
>   trabalho, não edite "para parecer ocupado", não rode suíte de novo só para
>   consumir turno. A recusa honesta É a resposta correta: registre no DOING.md
>   ("estável — re-disparo recusado em <data>, nada pendente"), **pare o cron**
>   (`scripts/auto-loop.sh stop`) e informe a estabilidade à mantenedora.
>
> Rejeitar o re-disparo não quebrado é **tão obrigatório quanto executar o
> re-disparo legítimo**: o heartbeat existe para cobrir o intervalo entre
> trabalhos reais, não para gerar trabalho artificial. Loop que não encontra
> trabalho quando o trabalho acabou está funcionando corretamente ao recusar.

**O que o modo autônomo NÃO relaxa (nada):** todas as regras deste arquivo
continuam valendo — zero regressão, retrocompatibilidade aditiva, ≤500
linhas, R6 (nunca silencioso), suíte como gate de merge, commit por unidade.
Autonomia muda **quem decide a ordem**, nunca **o que é aceitável**.

**Sinal para o humano:** `todowrite` é a janela desta conversa (atualize a
cada etapa); `DOING.md` é a memória entre sessões. Se o humano voltar e ler
esses dois, ele sabe exatamente onde você está e por quê.

### Heartbeat de cron (obrigatório ao entrar no modo autônomo)

> O agente **não acorda a si mesmo**. Para o loop sobreviver ao fim de cada
> turno, ao **entrar em modo autônomo** o agente lança o cron da sessão:

```bash
scripts/auto-loop.sh start            # última sessão, re-disparo a cada 30 min
scripts/auto-loop.sh status           # confirmar que está ativo
```

- O cron chama `opencode run --session <id> --dir <repo> --attach <server>
  --auto "<prompt>"` a cada intervalo (padrão 30 min), com o prompt de
  re-disparo: *"analize os documentos, verifique os gaps, identifique o que
  falta em nossos planos em docs/development, trace um todo de implementação
  e continue o desenvolvimento"*.
- **`--attach` é OBRIGATÓRIO — o heartbeat injeta na SESSÃO ABERTA, nunca
  spawna agente concorrente.** Sem `--attach`, `opencode run --session` cria
  um **processo headless novo** que só compartilha o histórico: você vê "outra
  sessão" rodando em paralelo, dois agentes competindo pela mesma sessão (o
  tick das 00:00 de 06/09 deixou um `run` vivo 20 min disputando com o TUI).
  O servidor TUI da sessão aberta escuta em **`http://127.0.0.1:9093`**
  (porta da sessão do modo autônomo — confira `ss -tlnp | grep opencode` e a
  sessão viva; sobrescreva com `OPENCODE_SERVER_URL`). O `tick`
  faz health-check na porta antes de disparar: servidor fora do ar → tick
  pulado e logado (não adianta injetar numa sessão que não existe).
- `flock` no `tick` impede run sobreposto: se o turno anterior ainda está
  ativo, o tick é pulado e logado (`~/.local/state/kof-auto-loop/loop.log`).
- **Ao sair do modo autônomo** (humano retorna, condição de parada, ou
  trabalho concluído): `scripts/auto-loop.sh stop`. Deixar o cron rodando
  depois do fim é ruído — o heartbeat existe só enquanto o loop vive.
- Se o cron já está ATIVO (`status`), não lance outro — a sessão atual é a
  continuada do heartbeat.
- O re-disparo chega como turno normal: vale a regra 6 (responder com tool
  call, não com "ok") e o contrato do `PRÓXIMO PASSO` no `DOING.md`.
- **Gate de despacho (20/09, Onda 1 do Agent Worker):** o polling pode continuar
  frequente (`*/5`) porque custa **zero chamadas de modelo**; o modelo só é chamado
  quando um gate determinístico (`scripts/agent-dispatch-gate.sh`) encontra mudança.
  O tick do heartbeat compara um fingerprint do estado (HEAD, `DOING.md`,
  `known-bugs.md`, listagem de `docs/development/`, árvore de trabalho, estado da CI
  do HEAD) com o tirado **antes do último despacho** — assim um run produtivo (que
  muda HEAD/`DOING.md`) continua no tick seguinte e um run ocioso deixa os próximos
  ticks de graça. Falhas do agente têm backoff (1ª retenta no tick seguinte, 2ª espera
  15 min, 3ª+ 30 min). `auto-loop.sh tick --dry-run` mostra a decisão; todo
  dispatch/skip é registrado em `~/.local/state/kof-agent/dispatch.jsonl`;
  `auto-loop.sh stats` / `issue-watcher.sh stats` reportam ticks × chamadas de modelo
  evitadas e o **custo real medido pelo `opencode stats`** (dólares e tokens das
  sessões da máquina; janela em dias inteiros; "indisponível" quando o OpenCode
  falta/falha — nunca estimado). **Rollout:** cron iniciado antes
  desta mudança não tem `gate_mode` e roda em `shadow` (comportamento legado + registro
  do que o gate faria); vire com `set-mode active`. `flock`, watchdog e o `--attach`
  obrigatório não mudam.
- **Watchers de issue (12/09, multi-issue 13/09, por eventos 20/09):**
  `scripts/issue-watcher.sh start <issue|all> <min> <sessão>` vigia issues a cada N
  minutos e injeta um turno na sessão viva (mesmo `--attach` obrigatório do heartbeat;
  o snapshot só avança após injeção bem sucedida; `server=` gravado no state fixa a
  porta p/ o tick do cron). Em uso: **todas a cada 5min → sessão
  `ses_f69c2cb03ffe2zDYCqW7fesphi` (porta 9094)** — o tick `all` **injeta SÓ com
  evento externo** detectado pelo gate: **issue nova (mesmo sem comentários)**,
  **título/corpo editado** ou **comentário externo novo**. Comentário do próprio
  `kof-agent-worker[bot]` nunca retriga; comentário humano (mantenedora inclusive)
  sempre conta; falha de GitHub/API é retry, nunca "estável". O prompt é **focalizado
  nos eventos listados** (lê-los primeiro, depois o `DOING.md`; ampliar só com relação
  técnica comprovada) — a auditoria global é ação explícita e separada, não mais a
  cada tick. Depois **triar** (corrigir o que for da lane / registrar gap-plano
  regra 6 / declarar não-procedente); frente de outra lane = pedir review do dono,
  nunca tocar. Interagir com issue que impacta o trabalho EM CURSO é parte do loop,
  não distração.
- **Fechar issue como corrigida (20/09):** o caminho oficial do worker é
  `scripts/agent-close-issue.sh <issue> --run-id <id>`, que recusa a menos que o
  manifesto de evidência (`scripts/agent-evidence.sh`: comandos reais, exit codes, SHA
  testado; `NOT_RUN` nunca é `PASS`) seja válido para o SHA já pushado e o verifier
  determinístico (`scripts/agent-verify.sh`) passe. O risco é classificado por
  `scripts/agent-risk.sh`; **HIGH** (FFI/ABI, backends Native, nullability,
  generics/erasure, concorrência, GC, cross-target, segurança, `DECISIONS.md`,
  `AGENTS*.md`) exige além disso um veredito de verifier **independente**, em outra
  sessão (sem ele o veredito é `NEEDS_MAINTAINER` — independência nunca é fingida).
  LOW/MEDIUM não pagam um segundo modelo. Pedido de design / ambiguidade de contrato
  nunca é fechado como "fixed" (regra 6).
- **Duas sessões, dois crons (13/09, pedido da mantenedora):** 9093 =
  `ses_f69e2a3f7ffe9J10aWcHEUOfW8` (heartbeat auto-loop, `*/5`) e 9094 =
  `ses_f69c2cb03ffe2zDYCqW7fesphi` (watcher all, `*/5`). **Nunca cruzar:**
  cada tick injeta SÓ na sua sessão (`--attach` + porta gravada/resolvida).
  O heartbeat da 9093 tinha sido parado; foi **reativado** (`auto-loop.sh start
  ses_f69e2a3f7ffe9J10aWcHEUOfW8 5`, dry-run prova attach 9093).


---

## Fonte da verdade e modelo de colaboração (obrigatório)

O ecossistema Kof (Koflang, Kof4J, Kof Native, Kof Editor) é **open source
(GPLv3)** e **centralizado** em torno da mantenedora oficial, **Mel Santos**
([@aminadojava](https://pt.linkedin.com/in/aminadojava)) — a **única fonte da
verdade** e quem detém o controle da engenharia de baixo nível (compilador,
injeção de Assembly direto na JDK, arquitetura CISC x86).

O desenvolvimento é **ativamente conduzido com agentes de IA documentados
publicamente** — mas **o Kof não foi feito por IA**. A IA é uma **ferramenta**
sob as rédeas da mantenedora: acelera e otimiza, mas não substitui a engenharia
conceitual nem decide arquitetura/rumo. Consequências práticas para o agente:

1. **Ceticismo técnico.** IA é tratada com ceticismo — nunca com fé. Automação
   sem critérios mascara falta de qualidade e imediatismo. Vale a "programação
   raiz": rigor na compilação, vivência prática, código robusto.
2. **Compile antes de entregar.** Alucinação é proibida. "Achar" que compila
   não compila. O loop de verificação (§ abaixo) é inegociável.
3. **Transparência cirúrgica de erros.** Erros vão para `docs/bugs-and-gaps/known-bugs.md`
   com **causa raiz** + **menor repro**, inclusive regressões que a mantenedora
   introduziu. Nunca "documentar em volta" do bug.
4. **Discussão técnica antes de código.** Quando a dúvida é conceitual (semântica,
   estouro de ponto flutuante, ABI), a contribuição é por **debate técnico** —
   propostas/documentos de design comentados — não PR desordenado que muda
   semântica congelada.
5. **Blindagem contra poluição.** Nunca misturar a linguagem Kof com termos
   alheios ao domínio (jogos, etc.) em docs/código. Disclaimers e nomenclatura
   são lei; violou, reverte.
6. **Toda PR vem acompanhada de uma issue relacionada.** PR "solta" não entra.
   Toda mudança proposta referencia uma issue aberta que a justifica —
   rastreabilidade é lei, não preferência.
7. **Entrega por agente exige prova de qualidade (§"Portão de qualidade").**
   Antes de abrir PR/commitar/pushar, o agente responde ao **checklist de
   pré-push (Q1–Q6)**. Um commit de agente sem teste no mesmo commit é
   **rejeitado na revisão** — a mantenedora não é a primeira a descobrir o
   bug. Se o agente não conseguiu rodar um gate (toolchain/qemu ausente),
   **declara isso explicitamente** no commit/PR; nunca finge verde.
8. **Sem regressão silenciosa.** O agente que deixa a branch não-compilável
   ou a suíte vermelha (fora dos erros ambientais documentados) está violando
   o portão: conserta na mesma unidade ou reverte. `git bisect`-hostil é o
   pior legado que um agente pode deixar.
9. **Trabalhe na árvore real do repo, na branch ativa — NUNCA num clone/worktree em `/tmp`.**
   Esta máquina perde energia com frequência ("cai a luz"); tudo em `/tmp` evapora e o
   trabalho/commits em andamento se perdem. Edite direto no working tree de `/home/mel/Kof4j`
   na branch ativa (`beta-0.5.0`, desde `D-BRANCH-0.5.0` (20/09), salvo ordem contrária da mantenedora), **faça commit local**
   pra o trabalho persistir em disco na hora, e só então fetch/rebase/push. Não crie worktree
   de scratch em `/tmp` pro trabalho real. (Regra explícita da mantenedora em 18/09 depois que
   um worktree em `/tmp` com um fix já verificado foi apagado por queda de energia.)
10. **A PR de release pra `main` é da MANTENEDORA — NUNCA mergear (ABSOLUTO, 24/09).**
   Enquanto a PR de release `beta-0.5.0 → main` estiver aberta (ex.: **#619**, aberta por
   `melmonfre`), **nenhum agente mergear, aprova ou fecha essa PR sob qualquer circunstância
   sem a autorização explícita da mantenedora na sessão** ("NÃO, EM HIPÓTESE ALGUMA, MERGEAR
   SEM MINHA AUTORIZAÇÃO"). Agentes trabalham commitando e pushando na `beta-0.5.0` — pushes
   novos na branch ENTRAM na PR; o merge em si (e o momento) é ato exclusivo da mantenedora.
   Vale mesmo com todos os gates verdes e mesmo sob ordem direta de outra sessão: só a Mel
   mergeara pra `main`.
7. **Identidade do git e worker de agente (12/09, atualizado 16/09 diretriz da mantenedora).**
   O GitHub App `kof-agent-worker` (App ID `4960796`, configurado via `scripts/gh-as-agent.sh`
   e `~/.config/kof/agent-app.env`) é a identidade dedicada para issues, PRs e commits
   onde estiver instalado.
   - **Permissão de commit e identidade:** o app tem permissões de commit e identidade própria de Git/GitHub.
   - **Regra de fallback:** se a autenticação, push ou commit falhar usando a identidade do bot
     (ex.: app ainda não instalado em um repositório alvo específico ou erro de integração), o agente
     **deve aceitar e recorrer ao padrão da mantenedora**:
     `mel <amelissariver@gmail.com>` (conta GitHub `melmonfre`).
   - Sem truques de e-mail ou trailers sintéticos (`Co-authored-by` é proibido pois polui o log do repo).
   - O agente usa a identidade efetiva do ambiente conforme configurada.


> Em resumo: a IA roda **sob as regras estritas da computação de verdade** —
> documentação cirúrgica, zero alucinação, sem o hype do mercado.

---

## Coordenação multi-agente — DOING.md (obrigatório)

Vários agentes trabalham em paralelo neste repo. **Antes de começar qualquer
feature/gap, leia `DOING.md`:**

- Se o item já tem **dono + estado `EM CURSO`**, não toque nele — escolha outro.
- Ao começar um item, **reivindique no `DOING.md` no mesmo commit** (dono,
  branch, arquivos que vai tocar).
- **A cada commit, atualize sua linha** no `DOING.md` (o que fez, o que falta).
- Ao concluir, marque `FEITO` com data + SHA + teste que prova, e feche o gap
  em `docs/status.md`/`docs/backend-parity.md`.
- Abandonou? Volte para `ABERTO` com nota do que funciona e o que falta.
- **Dono sumiu = tarefa morta; reatribua.** Se um item está `EM CURSO` com dono
  mas **não há commit novo na lane dele** (a linha não se move desde a
  reivindicação, o dono não aparece no `git log`, ou o branch/arquivo citado não
  existe), assuma que o agente **morreu no meio do turno** (crash, contexto
  esgotado, sessão fechada sem fechar a unidade). O item não tem dono real:
  qualquer agente pode **reivindicá-lo de novo** (troca o dono no `DOING.md`, no
  mesmo commit do primeiro passo), reaproveitando o que o morto deixou (working
  tree/branch) e seguindo. Antes de tocar, **verifique o estado real no código**
  (o que compila, o que a suíte prova — nunca a memória do `DOING.md`) e note na
  reivindicação o que o dono anterior deixou. Não espere o fantasma voltar nem
  peça permissão — `EM CURSO` órfão é `ABERTO` disfarçado, e gap órfão é
  trabalho perdido.

Regra de ouro: **nunca dois agentes no mesmo gap ou no mesmo arquivo gigante**
(`NativeRuntime.java`, `CompilerDriver.java`) ao mesmo tempo. Se for
inevitável, combine no chat antes.

**Números §NNN também são claims compartilhados.** Antes de criar uma seção nova
em `known-bugs.md` (ou qualquer ledger que use `§NNN`), `git fetch` e pegue o
próximo número livre a partir do **tip remoto**
(`git show origin/<branch>:docs/bugs-and-gaps/known-bugs.md | grep -oE '^#{2,3} §[0-9]+' | tail -3`) —
números escolhidos "em voo" (escrever local → push → rebase) já forçaram uma lane
paralela a renumerar duas vezes (§281/§282, 18/09). Se alguém pegou primeiro,
renumere do SEU lado ANTES do push: sempre barato, ao contrário da colisão.

**Sincronização obrigatória (pull antes, push depois):** antes de **todo
commit** — `git fetch` + `git pull --rebase` (com working tree sujo, use
`git stash push` antes e `git stash pop` depois, ou `--autostash`) e
**verifique se há conflito** (rebase parado / `<<<<<<<`): conflito é resolvido
na hora, nunca commitado por cima. Depois do commit, **`git push`** — o DOING.md
só coordena quem *vê* o remoto; commit local não reivindicado é tarefa fantasma
para os outros agentes. Depois do pull, **releia o DOING.md**: o que era seu
"próximo passo" pode ter sido feito ou reivindicado por outro agente no
intervalo.

> **Push mecânico (mantenedora 19/09): todo push passa por
> `scripts/sync-push.sh`** — fetch → `pull --rebase --autostash` → push →
> verifica `ahead=0 behind=0` contra o remoto antes de retornar sucesso. Se a
> checagem final não for 0/0, o push **não aconteceu** — nunca reporte o
> trabalho como "pushado" sem essa linha.
>
> **Política de conflito — preserve os dois lados, refaça o seu em cima
> (mantenedora 19/09).** Conflito de rebase/merge se resolve mantendo a
> **intenção dos dois hunks**; nunca `checkout --ours`/`--theirs` para
> "resolver" (a truncagem do `d7dba433` destruiu assim ~4.586 linhas do
> DOING.md de outras lanes). Um trecho que *parece* velho mas chegou de
> rebase/merge de outro agente é **conteúdo atualizado, não lixo**: **refaça o
> seu edit em cima da versão nova** (re-aplique sua mudança contra ela), nunca
> reverta o edit do outro "porque o meu foi escrito depois".
>
> **A armadilha do autostash (26/09, violação própria).** O
> `pull --rebase --autostash` pode deixar **marcadores de conflito na árvore
> suja SEM falhar o rebase** (ele completa, imprime "Successfully rebased" e
> guarda o resíduo no stash) — um `git add -A` logo depois commita os
> marcadores (meu `c87dcfa32` subiu marcadores em 4 arquivos + uma colisão de
> §NNN que o rebase havia surfaced). Obrigatório após QUALQUER rebase, antes
> de QUALQUER commit: `grep -rn '^<<<<<<<' <arquivos tocados>` e rodar a
> bateria de gates **na árvore pós-rebase** (gate rodado ANTES do rebase não
> prova nada). E re-conferir o §NNN contra o tip REMOTO depois do pull —
> números reivindicados em voo colidem exatamente como arquivos.

### Lição aprendida (04/09) — trabalhe SEMPRE em partes pequenas

> **Nunca tente gravar/produzir um artefato grande de uma vez.** O plano de
> refactoring `docs/architecture/PLAN-SOLID-500.md` (FECHADO 13/09; 120 classes, 8 fases) foi
> perdido uma vez porque o agente tentou escrever o documento inteiro num único
> `write`. A lição:

- **Um passo por vez.** Cada ação (write/edit/commit) resolve UMA unidade
  coesa e pequena. Se a resposta precisa de >1 ação grande, divida em várias
  respostas com commit entre elas.
- **Commite cedo e sempre.** Toda unidade concluída vira commit isolado
  (`git add -A && git commit`), mesmo que "pareça incompleta" — o próximo
  passo continua de onde parou.
- **Arquivos grandes são editados em pedaços.** Ler/editar um arquivo de 17k
  linhas aos poucos (nunca `read` de 2000+ linhas de uma vez se não precisar).
- **Se a tarefa parece maior que a janela**, crie o esqueleto/documento-enxuto
  primeiro, commite, e preencha incrementalmente.
- **A regra ≤500 linhas/classe existe exatamente porque** "fazer tudo de uma
  vez" vira código impossível de carregar/manter. O agente é parte do sistema:
  agir pequeno é seguir a própria regra que aplicamos ao código.
- **Faixas do gate `check_500` (decisão da mantenedora, 13/09):** ≤500 é alvo;
  **500–599 é TOLERADO** (dívida viva — o gate avisa, não quebra o CI; split
  continua sendo o caminho); **≥600 é CRÍTICO** (falha o CI, refactor/split
  obrigatório antes do merge). Dívida já travada no baseline nunca cresce
  (aproximar-se de 600 = split agora). Ao splitar, tire a linha do baseline com
  `./scripts/check_500.sh --update-baseline`.

Isso vale para código, docs, planos e testes: **pequeno é sustentável.**

### Status visível — `todowrite` (obrigatório, a cada etapa)

`DOING.md` é a memória **persistente** do repo (sobrevive entre sessões e
agentes). O **`todowrite`** é o status **visível ao humano nesta sessão** —
uma lista de tarefas que a CLI renderiza em tempo real. Os dois são
**complementares**, nunca substitutos:

- **A cada etapa de pensamento entre implementações**, atualize o `todowrite`:
  marque `completed` o que terminou, `in_progress` exatamente **um** item
  (o que você está atacando agora), `pending` o que falta.
- Não espere o fim do turno nem o commit: a pessoa acompanhando precisa ver
  o progresso **enquanto** você trabalha (ex.: ao trocar de módulo — JSON →
  http → spawn — mova o item anterior para `completed` e abra o próximo).
- Um item só vai para `completed` quando a prova existe (teste verde, qemu
  rodando, suíte passando) — nunca por intenção.
- Se uma etapa destrava trabalho novo que não estava previsto, **adicione**
  ao `todowrite` na hora.
- Ao fim da sessão, o `DOING.md` continua sendo a fonte da verdade para o
  **próximo** agente; o `todowrite` é só a janela desta conversa.

---

## Organização de documentação (obrigatório — 09/09)

A estrutura de documentação tem **três estados**, e a classificação reflete o
estado do **SOFTWARE**, não o do texto:

| Pasta | Conteúdo | Significado |
|---|---|---|
| `docs/` | documentação consolidada e válida | **somente** o que já foi implementado, validado ou decidido |
| `docs/development/` | trabalho atualmente em desenvolvimento | **somente** itens com implementação, validação, testes ou integração **pendentes** |
| `docs/development/future/` | planejado para depois | ideias/funcionalidades **não** em desenvolvimento atual |
| `docs/development/DECISIONS.md` | **registro de decisões da mantenedora** (a pasta `decision-pending/` foi EXTINTA 13/09 — os 6 planos viraram este doc único) | decisão tomada no chat **mora aqui** (data + opção + evidência), nunca só no chat; item decidido vira fila no `roadmap.md` §23/DOING no mesmo commit — nunca atacar frente sem decisão travada aqui (regra 6) |
| `docs/bugs-and-gaps/` | **registros vivos** de bugs, gaps de spec e matrizes de conformidade/paridade | fila por alvo (regra 3 do congelamento); não é "plano" — atualiza no MESMO commit que fecha o item |
| `docs/audits/` | **auditorias** — foto de estado (planejado × realizado) e registros datados de comparação | apontam trabalho, nunca são fila: o que uma auditoria marca pendente tem casa própria (bug → `bugs-and-gaps/`, código → `development/`, decisão → `decision-pending/`); ao fechar o apontado, atualiza a linha da auditoria no MESMO commit |

> **Refactor de clareza (13/09, decisão da mantenedora):** bugs/gaps/matrizes
> (`known-bugs.md`, `conformance-matrix.md`, `ecosystem-coverage.md`,
> `KOFUI-AUDIT.md`, `specification-gaps.md`) **não são backlog de
> desenvolvimento** — moram em `docs/bugs-and-gaps/`. Documentos 100% parados
> por decisão **não têm mais pasta própria**: os 6 que viviam em
> `decision-pending/` (`PLATFORM-PLAN.md`, `APPLICATION_MODEL.md`,
> `security-plan.md`, `plan-platform-completion.md`,
> `plan-spring-independence.md`, `planning-stdlib-time-design.md`) foram
> inventariados, auditados contra o código e **ratificados 13/09** num doc
> único — `docs/development/DECISIONS.md` (a pasta foi apagada). Auditorias
> (`roadmap-audit.md`,
> `complexity-audit.md`, `PLANNING-FUTURE-AUDIT.md`,
> `planning-future-reconcile.md`) moram em `docs/audits/` — não são fila de
> desenvolvimento nem registro de gap, são fotos de estado.
> `docs/development/` fica **só** com
> trabalho que anda (planos com código em andamento e refactors). Um agente da lane **docs** não mexe em bug/gap (são de
> outra lane) — só mantém esses registros sincronizados com o código.

> **`docs/development/` NÃO é arquivo morto, histórico nem depósito de
> documentação.** A presença de um documento lá significa explicitamente:
> *"existe trabalho técnico pendente para este item."*

### Regra fundamental — auditar antes de iniciar

**Antes de iniciar qualquer nova implementação**, o agente DEVE vasculhar
`docs/development/` e comparar cada documento com o estado REAL do código,
testes, build e commits. Para cada item:

1. **Já implementado e validado** → atualizar a doc se necessário, **mover
   para `docs/`**, remover referências antigas que indiquem desenvolvimento.
2. **Parcialmente implementado** → manter em `docs/development/`, identificar
   exatamente o que falta, **implementar o que falta**, rodar os testes;
   somente após a conclusão mover para `docs/`.
3. **Apenas planejado** (sem implementação em andamento) → mover para
   `docs/development/future/`.
 4. **Obsoleto, duplicado ou contradizendo o estado atual** → corrigir ou
    consolidar; nunca manter documentação falsa/desatualizada em
    `docs/development/`.

### Regra de prioridade — `.md` soltos primeiro (13/09, orientação da mantenedora)

> **Implemente PRIMEIRO os `.md` soltos em `docs/development/`** — eles são o
> trabalho **sem impedimento**: plano ratified, escopo definido, nada parado.
> Documentos em pastas dentro de `docs/development/` (`refactoring/`,
> `future/`) **têm impedimento** e não são fila enquanto o
> impedimento existir.

- **Decisão no chat vira DECISIONS.md no mesmo commit:** quando a mantenedora
  decide algo no chat, o agente **trava a resposta em
  `docs/development/DECISIONS.md`** (data + opção + evidência) **e abre a
  fila** no `roadmap.md` §23/DOING — decidir sem registrar = decisão
  invisível; registrar sem abrir fila = decisão morta. Frente sem linha em
  DECISIONS.md **não é atacada** (regra 6).
- **Ordem de seleção de tarefa:** (1) `.md` solto em `development/` com
  implementação pendente e sem dono `EM CURSO`; (2) fila recém-aberta de
  `DECISIONS.md`; (3) só então o resto da fila.
- **`future/`** permanece intocável sem promoção explícita (regra dos três
  estados); um item de `future/` **não** vira prioridade só porque está
  "planejado".

### Regra de conclusão

**NADA que esteja concluído pode permanecer em `docs/development/`.** A ordem
obrigatória ao concluir uma tarefa é:

```
implementar → testar → validar → atualizar documentação → mover de development/ para docs/
```

A movimentação do documento **não é opcional nem tarefa administrativa
secundária** — faz parte da definição de "concluído".

### Regra de retomada

Ao retomar o trabalho no repositório:

1. Ler `DOING.md`, `AGENTS.md` e `docs/status.md`.
2. Vasculhar `docs/development/`.
3. Para cada documento, verificar o estado real da implementação no código e
   nos testes.
4. Corrigir a classificação dos documentos.
5. **Finalizar primeiro o trabalho que já está em desenvolvimento** antes de
   iniciar novas funcionalidades.
6. Após cada conclusão, mover imediatamente a documentação para `docs/`.
7. Somente depois de esgotar o trabalho em desenvolvimento, selecionar novos
   itens.
8. Itens em `future/` **não** são trabalho atual sem decisão explícita de
   promovê-los para desenvolvimento.

### Proibição de cascata documental

- Não criar documentos de planejamento, auditoria, roadmap ou TODO **apenas
  para evitar implementar** uma tarefa já iniciada.
- Não transformar uma tarefa em desenvolvimento em outra tarefa de planejamento.
- Se o código já começou a ser implementado, o objetivo é **terminar a
  implementação**, testar e consolidar a documentação.

### Critério objetivo e prioridade

```
development/ + implementação concluída  = doc MAL classificada (mover p/ docs/)
development/ + implementação pendente   = correto
future/      + implementação não iniciada = correto
docs/        + funcional implementada/validada = correto
```

Ordem de prioridade do agente: (1) concluir o que já está em
`docs/development/`; (2) validar e consolidar; (3) mover doc concluída para
`docs/`; (4) só então escolher novo trabalho; (5) `future/` só entra em
execução sem trabalho atual pendente ou por decisão explícita da mantenedora.

---

## Diretriz primária

> **Kof deve ser mais simples que qualquer alternativa.**

O propósito da linguagem é reduzir verbosidade. Se o código que você está
gerando em Kof parece Java, C# ou Go traduzido, **ele está errado** — mesmo
que compile. O teste do litmo, antes de emitir qualquer código:

> *"Um humano escreceria isso em Kof, ou eu traduzi outra linguagem?"*
> *"Se um reviewer do Kof ver isso num PR, ele fica constrangido?"*

Se a resposta for "traduzi" ou "sim", reescreva.

**Caso canônico (nunca se repete):**

```kof
// ❌ NUNCA — 50 || seguidos é Java disfarçado de Kof
Bool isQuery(String op) {
    return op == "GetSession" || op == "GetAccess" || op == "GetDashboard"
        || op == "GetToday" || op == "GetTodayBoard" || op == "ListIntakes"
        || op == "GetIntake" || op == "ListCompanies" || op == "GetCompany"
        || op == "ListCompanyMembers"
}
```

```kof
// ✅ IDIOMÁTICO — a linguagem tem a feature; use-a
Bool isQuery(String op) {
    val known = setOf(
        "GetSession", "GetAccess", "GetDashboard", "GetToday",
        "GetTodayBoard", "ListIntakes", "GetIntake", "ListCompanies",
        "GetCompany", "ListCompanyMembers"
    )
    return known.contains(op)
}
```

> *"Por que a Kof deixou você escrever 50 `||`?"* — a resposta nunca é
> "aprenda a escrever melhor". É "use a abstração da linguagem".

---

## Regras de ferro (negociáveis com o compilador, não com o estilo)

1. **Intenção, não mecanismo.** `spawn` (não `Thread`), `setOf().contains()`
   (não `||`), `==` (não `.equals()`), `json.encode` (não parser manual).
2. **Complexidade pertence à plataforma.** JSON, DB, HTTP, cache, crypto,
   UI já existem na stdlib (`kof.*`). Reimplementar = anti-pattern.
3. **Represente o domínio, não a implementação acidental.** `List<T>`/`Map<K,V>`/
   `Set<T>`, não linked-list manual.
4. **Zero cerimônia.** Sem getters/setters, sem builders, sem utility classes,
   sem camadas Service/Repository/Controller.
5. **Nunca alucine sintaxe.** Se não está em `training/`, **compile e confirme**
   antes de usar. Sintaxe que não compila é pior que sintaxe verbosa.
6. **Multi-target honesto.** Código que só roda em um target precisa de
   diagnóstico claro (gap `XXX00x`), nunca fallback silencioso.
7. **Nome descreve responsabilidade, nunca posição.** `JsRuntimeUiMathDouble`,
   `RuntimeStringsWords` — sim; `...Math2`, `...V2`, `...New`, `...Bak`,
   `stdmath2` — não. Sufixo numérico é lixo de co-processador (só existe para
   não colidir com um nome que ninguém entendeu). Ao splitar por gate ≤500, o
   arquivo novo ganha nome pelo que **contém** (a responsabilidade que
    saiu), não por quantos irmãos já existem. Legibilidade vem antes de
    qualquer economia de digitação.
8. **Kof não é Java/Kotlin/C# — pedido de feature de outra língua NÃO é bug do
   Kof (ABSOLUTO, mantenedora 18/09).** Quando uma issue pede um construto que
   não existe no Kof porque é **Java ou Kotlin traduzido** (`StringBuilder`,
   `.equals()`, `new`, `val`/`var`/`let` top-level, keyword `fun`/`val`,
   `v is Car`, `"""três aspas"""`, `Pair`, `it` implícito de lambda,
   `mutableListOf`, Elvis `?:`, `?.`, `0..n`, `!!`, argumento nomeado `f(p=v)`,
   `class Box(size: Int)` primário COM corpo, `catch (e: Type)`, `object`,
   `open`/`override`), a rejeição do compilador é **correta e esperada** — a
   issue é **NÃO-PROCEDENTE: responda uma vez com o idiom do Kof que substitui
   (a tabela de idioms deste arquivo) e FECHE-a**; NÃO implemente a feature
   estrangeira nem "conserte o diagnóstico". Vira trabalho real apenas se o Kof
   *promete* o construto em `training/`/`learn/`/docs e o compilador *discorda
   da própria doc* (aí sim é bug) — ou se a mantenedora decidir (regra 6:
   decisão de design, não edição de agente). Todo fechamento desses carrega o
   motivo "Kof não é Java" e aponta `training/anti-patterns/fake-idioms.pt_BR.md`.
   Cruzamento: se o reproducer compilaria em **Kotlin/Java** por ser
   *traduzido*, é esta regra.
9. **A checagem de filosofia precede a issue — mande o autor LER a
   documentação PRIMEIRO (ABSOLUTA, mantenedora 18/09, caso #449/RawView).** A
   regra 8 cobre *linguagens* traduzidas; esta generaliza para qualquer
   **pilha estrangeira**: um pedido que importa tags HTML, seletores CSS,
   estilo inline ou `innerHTML` para o `kof.ui` (ex.: uma via de escape
   `RawView(tag, cssText, html)`), camadas de framework (`@Controller`,
   Service/Repository), engines de template — seja qual for o nome dado a
   ele. Tal pedido NÃO é feature faltante: é o autor contornando uma filosofia
   que não leu. A PRIMEIRA resposta — antes de qualquer "gap confirmado",
   antes de tocar no código — DEVE mandar o autor para a leitura
   (`docs/philosophy.pt_BR.md` "O que Kof NÃO é", o doc de idiom da área, ex.
   `training/idioms/ui.pt_BR.md`, e `training/anti-patterns/`), declarar em
   uma linha POR QUE viola a filosofia (código Kof declara **intenção**; a
   plataforma renderiza — complexidade pertence ao compilador/backends, nunca
   a markup embutido no código do usuário), e apontar o idiom Kof que resolve
   a necessidade real. A issue só fica aberta se, depois da leitura, a
   necessidade for real E destampada por abstração Kof — e a resolução é uma
   abstração Kof decidida pela mantenedora (regra 6), nunca a sintaxe
   importada. Os checkboxes dos templates fazem o humano assinar o mesmo
   portão (`feature_request.yml`, `bug_report.yml`).
10. **KOF-primeiro, externo-depois — nenhum comportamento externo é oráculo
    (ABSOLUTA — `D-KOF-FIRST`).** Nenhum comportamento de Java, Kotlin, C#,
    Rust, Swift, JavaScript, Python, SQL, nem de qualquer outra língua,
    framework, runtime, especificação, fórum, paper ou benchmark é, **por si
    só**, expectativa de comportamento do Kof. Antes de abrir issue ou sugerir
    fix: **(1)** prove que o reproducer é **Kof válido** (docs de
    gramática/sintaxe, `training/`, `learn/` — o
    `training/anti-patterns/fake-idioms.md` nomeia os suspeitos estrangeiros
    de sempre); **(2)** identifique o **contrato Kof que governa**
    (`DECISIONS.md` → docs normativos → testes de conformidade/golden → matriz
    de paridade → implementação; nunca outra língua); **(3)** procure o
    **idiom ou abstração Kof** que já expressa a intenção; **(4)** **meça** o
    comportamento real nos alvos relevantes. Só existe **bug** quando o Kof
    diverge do **próprio contrato**; só existe **gap** quando uma necessidade
    legítima permanece sem solução Kof adequada. **A pesquisa externa começa
    só depois dessa prova interna** — e contribui com princípios, invariantes,
    trade-offs e bugs conhecidos, nunca com sintaxe, API ou semântica para
    copiar automaticamente; toda importação é reexpressa pela filosofia do Kof
    primeiro. Qualquer proposta que mude gramática, semântica, operadores,
    modelo de tipos ou API congelada é **decisão de projeto da mantenedora**
    (regra 6), não bugfix de agente — um diff pequeno no parser que aceita uma
    forma nova é **feature nova de linguagem**, não conserto de parser.
    Pipeline completo (Gates 0–9) e o bloco de evidência: `DECISIONS.md`
    §`D-KOF-FIRST`.
11. **A Lei da Simplicidade — tudo que chega à superfície da linguagem
    (ABSOLUTO, mantenedora 20/09).** Todo novo código que chega ao
    frontend/sintaxe/semântica do Kof (nova sintaxe, nova semântica, nova
    superfície da linguagem — incluindo a superfície de stdlib que o código
    do usuário chama) deve ser **extremamente simples, curto, idiomático e
    representar intenção**, de acordo com a filosofia do Kof ("Kof deve ser
    mais simples que qualquer alternativa"). **Nenhum boilerplate ou
    complexidade acidental pode entrar na superfície da linguagem.** O portão
    antes de landar qualquer superfície (ele antecede os Q0–Q7, não os
    substitui): um humano escreveria exatamente isto em Kof? O construto
    declara *intenção*, não *mecanismo*? Existe uma forma mais curta que diz
    o mesmo? Há cerimônia (repetição explícita, wiring manual, nome por nome
    mesmo) que a plataforma deveria absorver? Se alguma resposta for "não" →
    a superfície está errada mesmo que compile e os testes estejam verdes. A
    lei amarra todas as frentes: Makealive (`D-MAKEALIVE`), a frente DB/ORM
    (`D-DB-GAPS`) e, por fim, o `D-BOOTSTRAP` — o compilador escrito em Kof é
    o teste: se a linguagem não consegue expressar o próprio compilador com
    simplicidade, a linguagem falhou.

---

## Portão de qualidade — "nenhum bug sobe" (obrigatório, 13/09 — **UNIVERSAL**)

> **Prioridade nº 1 do projeto é QUALIDADE, não volume de entrega.** Um commit
> que adiciona feature sem prova é pior que um commit que não existe: ele
> transfere o custo do bug para a próxima sessão e para a mantenedora.
>
> **Esta regra é universal: vale para TODAS as branches** (`beta-0.4.0`,
> `wip-*`, `fix/*`, `issue-lane`, `docs/*`, feature branches, forks), **para
> todo agente, toda lane e toda unidade** — não só a branch de release. Um
> push que quebra o build em QUALQUER branch é uma violação do portão. Não é
> negociável com "o teste já passava antes", "é só um WIP" ou "outro agente
> vê depois".

### Q0. O bug se CONSERTA; o teste só PROVA

> **Consertar o bug é o trabalho. O teste é a prova de que ele morreu — nunca
> um substituto para a correção.** É proibido entregar "o teste que reproduz o
> bug" e deixar o código quebrado; é proibido também "documentar em volta" ou
> rebaixar a asserção para o teste passar. A ordem é sempre:
>
> **reproduzir (menor repro) → consertar a causa raiz → provar com o teste
> que falharia antes → rodar a suíte completa.**

- **Bug fix entrega o código corrigido + o teste de regressão no mesmo commit.**
  Faltando qualquer um dos dois, o commit não existe.
- **Corrigir a causa raiz, não o sintoma.** Um `if` que esconde a exceção, um
  `try/catch` que engole, ou um valor default que mascara o erro **não é fix** —
  é bug adiado. Se a correção exige mudança de contrato/operador/ordem de
  avaliação, é **regra 6**: vira plano, nunca edição silenciosa.
- **Build quebrado é o bug mais grave.** Se o seu commit deixa a branch
  não-compilável (ex.: `7f174a6f`, `usesPow` não declarado), a correção é
  **prioridade zero** — conserta na mesma unidade e pusha, não espera o próximo.

### Q1. Toda mudança de código vem com teste que a PROVA

- **Feature nova → teste novo.** Sem exceção. "Implementei X" sem teste que
  execute X é entrega inválida (o `pow` de 13/09 subiu sem teste e quebrou o
  build da release — não se repete).
- **Bug fix → teste de regressão** que falharia no código velho e passa no
  novo. O teste é a prova de que o bug morreu; sem ele, o bug volta.
- **Refactor → mesma suíte + golden E2E por target** (regra 3 do Congelamento).
- **O teste entra no MESMO commit da mudança.** Teste depois = teste que nunca
  vem. Se o commit não tem a prova, o commit não existe.

### Q2. Prova de compilação ANTES de qualquer push

```bash
mvn -o -pl kof-compiler -am compile -q     # falha aqui = NÃO PUSHA
```

O caso `7f174a6f` (pow com `usesPow` não declarado) deixou a branch de release
**não-compilável** para todos os agentes. **Regra dura: agente que não roda o
`compile` do módulo antes do push está quebrando o repo.** Se o gate completo
não foi rodado, o commit/mensagem diz isso explicitamente — nunca finge verde.

### Q3. Além do caminho feliz — a matriz mínima de cenários

Todo teste novo cobre, no mínimo, **o caminho feliz + as bordas relevantes**.
O agente escolhe as que se aplicam e **registra no commit** o que cobriu:

| Cenário | Exemplo |
|---|---|
| **Borda numérica** | `0`, negativo, overflow, `NaN`/`Infinity`, `-0.0`, expoente negativo/fracionário |
| **Vazio/nulo** | coleção vazia, String `""`, `null`/`Nullable`, ausência de chave |
| **Limite/índice** | primeiro/último elemento, fora-de-faixa, um-past-the-end |
| **Erro esperado** | entrada inválida → diagnóstico/gap `XXX00x` (nunca silêncio, R6) |
| **Cross-target** | JVM × Native × Script × JS com a MESMA saída (ou gap diagnosticado) |
| **Idempotência/repetição** | rodar 2× dá o mesmo resultado; estado não vaza |
| **Concorrência** | `spawn`/`await`, corrida, cancelamento, isolamento entre workers |
| **Interop/limite de recurso** | arquivo inexistente, rede fora, lib ausente, memória |

- **Proibido entregar só o happy path** (self-check 7). Se o caso só tem o
  caminho feliz testável, **diga por quê no commit** (ex.: "só o determinístico
  é observável; o resto é ambiente").
- **Golden = medição real, nunca memória.** O valor esperado sai do oracle JVM
  executado (ou harness C isolado), nunca de "acho que dá isso".
- **`assertEquals` com mensagem** que identifica o caso e o target — um vermelho
  precisa ser diagnosticável sem re-rodar.

### Q4. Cace o bug ANTES de subir (postura de caça, não de entrega)

Antes de cada commit, o agente **tenta quebrar a própria mudança**:

1. **Casos extremos:** o que acontece com entrada vazia/nula/negativa/gigante?
2. **Cross-target:** os 3+ targets concordam? Onde divergem, é gap ou bug?
3. **Fronteira de contrato:** a mudança toca operador/precedência/ordem de
   avaliação/null-safety/`==`/exceções/`spawn`/coleções? Então é **regra 6** —
   vira plano, não edição.
4. **Regressão vizinha:** rode a suíte completa, não só a classe nova.
5. **O que o teste NÃO cobre?** Escreva-o — é exatamente aí que o bug mora.

> Um bug achado por um agente antes do push custa minutos. O mesmo bug subindo
> custa uma sessão inteira de outro agente + a confiança da mantenedora. **Achar
> o bug é parte do trabalho, não uma fase opcional.**

### Q5. Nada de "verde falso"

- Teste que passa por acidente (assert fraco, `success=true` sem executar
  output, mensagem de erro aceita como saída esperada) é **bug disfarçado**.
  Proibido "consertar" teste relaxando a asserção (JavaFX, §149 — precedentes).
- Skip é **honesto e explícito** (`assumeTrue` de toolchain ausente), nunca
  para esconder falha.
- Se a suíte fica vermelha por causa da sua mudança, **o commit não entra** —
  nem "com nota", nem "depois eu volto". Corrige ou reverte.

### Q6. A suíte é o chão, não o teto

Passar a suíte é o **mínimo**. A pergunta de aceite é: *"que cenário quebra
isso e eu ainda não testei?"*. Enquanto houver resposta, a unidade não está
pronta.

### Q7. Proibido stub — a implementação é COMPLETA ou não sobe (13/09)

> **"Funciona o suficiente para o teste passar" não é entrega.** Um stub,
> placeholder, `return null`/`return 0` de fachada, corpo vazio, `throw
> "not implemented"`, `TODO`/`FIXME`, ramo `default` que engole caso não
> tratado, ou qualquer caminho que **finge** fazer o trabalho é **bug
> pré-instalado** — ele passa o teste de hoje e falha o usuário de amanhã.
> **É proibido commitar stub.** A unidade entrega a **implementação
> completa** do escopo declarado, com a matriz Q3 coberta.

- **Implementação completa = o comportamento previsto, inteiro.** Se o escopo
  é "suporte a `++` em long", entrega os 4 targets, prefixo/pós-fixo, borda
  numérica e array/field — não "só o caso do teste". Escopo menor é aceitável
  **se declarado**; escopo menor disfarçado de completo, nunca.
- **Stub não é "trabalho incremental"** — trabalho incremental é entregar um
  **degrau inteiro** (uma capacidade completa), commitá-lo e seguir. Deixar
  metade de uma capacidade no código fingindo completude é o que a regra
  proíbe. A distinção: *corte vertical completo* (ok) × *fachada de fachada*
  (proibido).
- **Gap honesto ≠ stub.** Um caminho **não suportado** deve falhar com
  diagnóstico `XXX00x` (R6, regra 6 do congelamento), nunca retornar valor
  falso silenciosamente. "Não suportado com diagnóstico" é entrega válida;
  "não suportado fingindo que sim" é stub.
- **Todo stub EXISTENTE é dívida catalogada.** Encontrar um stub/placeholder
  no software (código, stdlib, backend, docs) **obriga** a:
  1. **catalogá-lo como gap de implementação** em
     `docs/bugs-and-gaps/known-bugs.md` (bug) ou
     `docs/bugs-and-gaps/specification-gaps.md` (gap de spec), com
     **localização** (`arquivo:linha`), **o que falta** e **menor repro**;
  2. **anotá-lo no ponto do código** com o código do gap (`XXX00x`/`§NNN`),
     para que o próximo agente o veja sem arqueologia;
  3. **planejá-lo** na fila (regra dos três estados) para que seja
     **formalmente desenvolvido da forma correta** — não corrigido às pressas
     nem escondido atrás de um teste fraco.
  Stub achado e não catalogado = **omissão de agente**, tão grave quanto o
  próprio stub. Catalogar não fecha o item: ele só fecha com implementação
  completa + prova (Q0–Q6).
- **Aceite:** a pergunta final não é "o teste passa?", é **"o que aqui ainda
  é fachada?"**. Enquanto houver resposta, a unidade não está pronta.

---

## Congelamento de comportamento (obrigatório)

> **O comportamento previsto é lei.** "Comportamento previsto" = o que o corpus
> (`training/`, `learn/`, `docs/`) documenta e os testes (golden + E2E + suíte
> completa) provam. **Nenhum agente pode quebrar comportamento que já funciona.**

1. **Zero regressão.** Nenhum commit pode fazer um teste existente passar a
   falhar. A suíte completa (`mvn test`, hoje **2687** nos 4 módulos — ver
   §"Loop de verificação" para o comando com o flag de failure.ignore) é **gate de merge** —
   mudança que não mantém tudo verde não entra. Exceção única: mudança de
   contrato **deliberada**, com bump de versão + docs atualizados + migração.
2. **Retrocompatibilidade obrigatória.** Toda feature/API nova é **aditiva**:
   código Kof que compila e roda hoje continua compilando e rodando. Mudança de
   semântica existente nunca é silenciosa — só com bump + doc + migração.
3. **Refactor preserva semântica.** O refactor para a regra **≤500 linhas/
   classe** (e qualquer outro refactor) mexe em **estrutura**, nunca em
   **comportamento**. Prova: mesma suíte + golden E2E por target. Se o refactor
   muda output observável, é **bug do refactor** — corrige ou reverte.
4. **Bug = alinhar ao previsto, nunca o contrário.** Tudo em
   `docs/bugs-and-gaps/known-bugs.md` é desvio do comportamento previsto e **deve ser
   corrigido no código** para atingir o comportamento documentado. Proibido
   "documentar em volta do bug" (mudar o corpus para aceitar o comportamento
   errado como se fosse o certo). Se o comportamento documentado está errado,
   é decisão de design → bump de versão + discussão, nunca correção silenciosa.
5. **Paridade cross-target.** JVM/Native/JS divergindo no mesmo programa é bug
   de paridade. O comportamento previsto vale nos 3 targets, ou gap `XXX00x`
   diagnosticado — nunca divergência silenciosa.
6. **Semântica congelada (0.2.6-beta).** Operadores, precedência, ordem de
   avaliação, null-safety, `==` de conteúdo, exceções como String,
   `spawn`/`await`, coleções `List/Map/Set` são **congelados**. Proposta de
   mudança vira gap/plano em `planning-*`, nunca edição direta da semântica
   atual.

---

## Invariantes da plataforma (plano universal — `docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.md`)

Estas regras **sempre** se aplicam, mesmo quando não há código de domínio novo
em jogo. São o mecanismo anti-"god language":

1. **Fronteira core → stdlib base → plataforma → pacotes oficiais → interop**
   (R1). Domínio pesado (`ml`, `bio`, `hpc`, `infra-<cloud>`) vai para
   **pacote oficial**, nunca para a stdlib base. Só entra na stdlib o que é
   "essencial à plataforma e pequeno". **Machine-gated** desde 17/09:
   `scripts/check_stdlib_boundary.sh` + ledger `scripts/stdlib_boundary.txt`
   (CI, morde sob `--selftest`) — namespace novo sem linha no ledger com a sua
   camada **quebra o build**; domínios pesados são hard-deny. Registre a
   camada primeiro (ordem de decisão §3.4), nunca em silêncio.
2. **Interop-first** (R9). Para qualquer capacidade, a primeira pergunta é
   "já existe por fora e é melhor?" → FFI/interop (`kof.process`, `.so`, JVM,
   GraalJS). Nunca reimplementar Arrow/Parquet/BLAS/LAPACK/CUDA/NumPy/
   alinhadores/frameworks de ML. **Exceção nomeada — `D-GRAPHICS-GAMING`
   adendo 4 (20/09):** o motor de gráficos/mídia de jogos é **próprio da
   Kof** (código da plataforma, idioma zero-boilerplate, aceite por paridade
   cross-target total); os bindings FFI ficam restritos à camada não-engine
   (janela/GPU/dispositivo de áudio).
3. **Escopo honesto por target** (R7): capacidades pesadas chegam **JVM-first**
   (interop), **Native** para sistemas/deploy, **JS** só web/edge. Nunca
   prometer paridade JS para domínios pesados.
4. **Nunca silencioso por domínio** (R6): todo gap de domínio tem código
   (`INFRA00x`, `DATA00x`, `SCI00x`, `BIO00x`, `SECPQ`, ...) + entrada na
   matriz de paridade. Nunca stub silencioso, nunca fallback fraco.
   **R6 é sobre SILÊNCIO, não sobre ESCOPO (mantenedora 21/09, ABSOLUTO):** uma
   entrega **incremental** — uma fatia vertical completa para o seu escopo
   *declarado*, com os caminhos ainda não suportados falhando por
   **diagnóstico honesto** (`FFI001`/`FFI002`/`XXX00x`, que é o próprio R6) —
   **NÃO fere o R6**. O R6 é violado só quando o gap é **escondido**: stub
   silencioso, fallback fraco, divergência que o usuário não enxerga. Entregar
   JVM-first e deixar Native/JS como gaps *declarados e diagnosticados* **é** o
   caminho incremental sancionado (R7); bloquear "por causa do R6" é o
   anti-padrão.
5. **Tiers de estabilidade** (R5): namespace/pacote é `stable` ou
   `experimental`. Camada de pacotes oficiais nasce `experimental` e só
   promove a `stable` com DoD completo (3 targets ou gap diagnosticado, E2E
   por target, benchmark quando plausível, docs+training sincronizadas).
6. **Core pequeno e estável** (R12): nenhum item de plano futuro é **ação**
   sobre o trabalho atual. Frentes novas (infra/data/sci/bio, plataforma de
   migração legado) **não** abrem antes do estágio SYSTEMS (gaps de paridade,
   GC mark-sweep, package manager) fechar.
7. **Segurança: defesa primeiro** (R11). Cripto nunca caseira — toda primitiva
   nova é FFI a lib auditada (JCA/liboqs/libsodium/SubtleCrypto). Default
   seguro, constante de tempo, formato versionado, gaps `SECN00x`/`SECPQ`.
8. **Correto e determinístico por padrão** (R10): em ciência/ML, correção
   numérica e determinismo são requisito de aceite (property-based + golden).

**Non-goals permanentes:** sem macros abertas, type-classes, annotations como
fundação, ownership/borrowing, effect system completo; sem "Kali em Kof"; sem
target por domínio; sem motor SQL/Arrow/ML próprio.

---

## Antes de escrever código (obrigatório)

1. Leia `training/idioms/<area>.md` da área do problema
   (collections, functions, strings, errors, records, classes, concurrency, control-flow, interop).
2. Leia `training/anti-patterns/` — em especial `java-like-code.md`,
   `chained-or-membership.md`, `fake-idioms.md`.
3. Se a dúvida persistir: **escreva um snippet e compile** (loop abaixo).

---

## Sintaxe real (verificada no compilador — 0.5.0-beta)

### Funções (não existe `fun` nem `func`)

```kof
main() { println("entry point") }            // única sem tipo explícito

String saudacao() { return "oi" }            // tipo antes do nome
despedida(): String { return "tchau" }       // tipo depois dos parênteses
void fazIsso() { println("x") }              // void explícito
Bool positivo(Int x) = x > 0                 // expression body
Int dobro(Int x) { return x * 2 }

Int g(Int x) { return x }                    // sobrecarga top-level (0.4.0,
Int g(Int x, Int y) { return x + y }         // oracle JVM): assinatura difere
// ❌ duplicata EXATA → SEM047; só trocar o RETORNO NÃO é sobrecarga (SEM047)
// chamada ambígua → SEM057 (dê tipo ao argumento p/ escolher)
// sobrecarga de MÉTODO de classe ✅ existe (0.4.0, §131 13/09): mesmo nome,
// assinaturas diferentes (aridade/tipos) na mesma classe coexistem nos 4
// backends; o typer seleciona por aridade+compatibilidade
```

### Variáveis (só dentro de funções/corpos — **não existe top-level `val`/`var`/`let`**)

```kof
var x = 10              // mutável
val y = 20              // imutável
String nome = "Mel"
String? nome2 = find(key)  // nullability: forma TIPO-PRIMEIRO (idiomática no corpus)
var idade: Int? = findAge() // nullability: forma ANOTADA (também válida)
// ⚠️ literal `= null` é REJEITADO desde 10/09 (SEM048, decisão da mantenedora
// por trás da SG-008/D-NULL-INTENT): `null` só chega a um `T?` via API
// (map.get, readLine, função que retorna `T?` com `return null`) —
// aí `if (x != null)` faz o narrowing.
```

### Classes (mutable → campos + `constructor(...)`) e o caso `class X(...)` = record

```kof
// ✅ ESTADO MUTÁVEL — campos explícitos + construtor (campos públicos, diretos)
class User {
    String name
    Int age
    public constructor(String name, Int age) {
        this.name = name
        this.age = age
    }
    String greeting() { return "Hello " + name }
}
var u = User("Mel", 26)     // sem `new`
u.age = 27                  // escrita direta — mutável

// ⚠️ ATENÇÃO (verificado 02/09): `class User(String name, Int age) { }` NÃO é
// classe mutável — o parser o trata como RECORD (imutável, accessors p.x()).
// Leitura `u.name` funciona (vira accessor); escrita `u.name = "x"` NÃO.
// Para dados imutáveis, use `record` (a forma canônica).
```

### Records (dados imutáveis, zero cerimônia)

```kof
record Point(Int x, Int y)
var p = Point(10, 20)
println(p.x())                               // accessors
println(p)                                   // JVM: Point[x=10, y=20]
```

### Controle de fluxo

```kof
var status = if (ativo) "online" else "offline"   // if-EXPRESSION
for (var item in items) { println(item) }          // for-in (com `var`)
while (cond) { ... }
switch (obj) {
    case String s:            println(s); break
    case Point(var x, var y): println(x + "," + y); break
    default:                  println("outro")
}
// switch-EXPRESSION (SYN001) — quando o switch produz valor:
var desc = switch (obj) {
    case String s -> "str:" + s
    case Point(var x, var y) -> x + "," + y
    default -> "outro"
}
```

### Strings

```kof
var s = "Hello"
s.length          // propriedade
s.charAt(1)
s.substring(6)
s.contains("lo")
s.startsWith("He")
s.split(" ")      // String[]
a == b            // compara CONTEÚDO (não referência) — nunca .equals()
a + "!"           // concatenação — nunca StringBuilder
```

### Coleções (API real)

```kof
var l = listOf(1, 2, 3)
l.add(4)
l.get(0)
l.set(0, 9)
l.size            // propriedade (não método)
l.contains(3)
l.isEmpty()
l.remove(1)
l.clear()

var m = mapOf("a", 1)
m.put("b", 2)
m.get("a")

var s = setOf("a", "b", "c")   // variádico
s.contains("a")

// Higher-order (3 targets)
var nomes = users.map((u: User) -> u.name)
var adultos = users.filter((u: User) -> u.age >= 18)
var total = nums.reduce((a: Int, b: Int) -> a + b, 0)
```

### Erros (exceções são Strings)

```kof
try {
    throw "not found: " + key
} catch (String e) {
    println("falhou: " + e)
} finally {
    println("cleanup")
}
```

### Concorrência (não existe `Thread`/`Executor`)

```kof
spawn trabalho()              // fire-and-forget
spawn { println("bg") }
val r = spawn compute()       // Handle<T>
var v = await r               // bloqueia; unboxing de primitivos
var id = time.interval(1000, () -> println("tick"))
scheduler.every(100) { ... }
```

### Null safety

```kof
var nome: String? = find(key)   // forma anotada (não `String? nome = ...`)
if (nome != null) {
    println(nome)               // narrowing
}
```

---

## Tabela de idioms (BAD → GOOD) — a referência rápida

| ❌ BAD (Java/outra linguagem) | ✅ GOOD (Kof) | Por quê |
|---|---|---|
| `x == "A" \|\| x == "B" \|\| ...` (3+ valores) | `setOf("A","B",...).contains(x)` | intenção de pertencimento, O(1), sem esquecer entrada |
| `a.equals(b)` | `a == b` | `==` compara conteúdo em Kof |
| `StringBuilder` em loop | `+` / `+=` | `+` já é eficiente |
| getters/setters | campo direto (`u.name`, `u.age = 3`) | Kof não tem JavaBeans/reflection ceremony |
| `new User(...)` com construtor explícito | `User(...)` sem `new` (ambos válidos) | `new` é retrocompatível |
| utility class com `static` | função top-level | Kof tem funções fora de classes |
| Service/Repository/Controller | função top-level ou classe direta | sem camadas de injeção |
| `class Node { Node next ... }` | `List<T>` | coleção da linguagem |
| loop manual para map/filter | `list.map/filter/reduce` | higher-order expressa intenção |
| `return ""` como "não encontrado" | `throw "not found: " + key` ou `String?` | sentinela esconde erro |
| `var s = ""; if (c) { s = "a" } else { s = "b" }` | `var s = if (c) "a" else "b"` | if-expression |
| parser JSON / DB / HTTP manual | `json.encode/decode`, `db.connect`, `http.get` | plataforma |
| `new Thread(...)`, `Executor` | `spawn` / `await` | intenção, não mecanismo |
| DTO + mapper + `@Data` | `record User(String name, Int age)` | dados imutáveis |
| `Optional<T>` | `String?` + `if (x != null)` | nullability nativa |
| `instanceof` + cast | `case String s:` / `as` | pattern matching |
| `import java.util.*` | `listOf`/`mapOf`/`setOf` + `import a.b.C` | stdlib própria |

---

## Fake idioms — NÃO EXISTE em Kof (nunca use)

Se você está prestes a escrever algo desta lista, **pare**:

| ❌ Não existe | ✅ Use |
|---|---|
| `fun` / `func` / `fn` | `String nome(...) { }` (palavras reservadas — não existem) |
| `val x = ...` / `var x = ...` no **top-level** | dentro de função; ou campo de `class` |
| `let x = ...` / `const x = ...` / `async fn` | `var`/`val` em função; `spawn`/`await` (KofScript **não** é JavaScript — roda Kof puro) |
| `x in [...]` (operador de expressão) | `setOf(...).contains(x)` |
| `Int.MAX_VALUE` / `Long.MIN_VALUE` / `<primitivo>.<campo>` | literal (`2147483647`) ou `as` — primitivos não têm estáticos (SEM050) |
| `{"a", "b"}` (literal de conjunto) | `setOf("a", "b")` |
| `[1, 2, 3]` (literal de array) | `listOf(1, 2, 3)` ou `new Int[n]` |
| `Option<T>` / `Result<T>` | `String?` + narrowing; `throw` para erro |
| `async`/`await` JS-style | `spawn`/`await` (Kof; `spawn f()` fire-and-forget é válido sozinho) |
| `for (x in coll)` **sem `var`** | `for (var x in coll)` |
| `Thread` / `Executor` / `Runnable` | `spawn` |
| `match x { A, B => ... }` (multi-case OR) | `switch (x) { case "A": ... case "B": ... }` ou `setOf` |
| `x instanceof String ? (String) x : null` | `if (x instanceof String) { var s = x as String ... }` ou `case String s:` |
| primary constructor `class X(val a, val b)` (Kotlin) | `record X(String a, Int b)` (imutável) ou classe mutável com `constructor(...)` |

> Regra: **toda feature nova que você quiser usar, compile antes.**
> Se não compila, é fake idiom — mesmo que exista em outra linguagem.

---

## Self-check obrigatório antes de considerar o código "pronto"

Responda SIM a todas antes de terminar:

1. **Compilei?** (loop de verificação abaixo) — `mvn -o -pl kof-compiler -am compile -q` verde.
2. **Traduzi alguma linguagem?** Se sim, reescreva com a abstração do Kof.
3. **Há repetição 3+ vezes de um padrão?** (comparação, branch, construção)
   → existe feature da linguagem para isso (Set/Map/switch/higher-order/record).
4. **Crio infraestrutura que a stdlib já tem?** (`kof.json`, `kof.db`,
   `kof.http`, `kof.cache`, `kof.security`, `kof.ui`) → use a stdlib.
5. **Código parece gerado ou escrito por humano?** Se gerado, reescreva.
6. **Novo idiom/anti-pattern descoberto?** → atualize `training/` (obrigatório).
7. **Testei apenas o "caminho feliz"?** Se sim, testar comportamentos
   inesperados (confiabilidade do codegen, bordas de erro, tipos nullable,
   concorrência, alocação de memória, cross-target paridade). Nunca delivery
   com testes que cobrem apenas o caso de sucesso esperado.
8. **A feature/bug tem teste no MESMO commit?** (Q1) — sem prova, o commit não existe.
9. **Cobri as bordas relevantes da matriz Q3** (numérica, vazio/nulo, limite,
   erro esperado, cross-target, idempotência, concorrência, recurso) e
   **declarei no commit** o que cobri?
10. **Tentei quebrar a mudança antes do push?** (Q4) — casos extremos,
    cross-target, fronteira de contrato, regressão vizinha, o que o teste não cobre.
11. **O golden veio de medição real** (oracle JVM/harness C) e não de memória?
12. **Nenhum teste passou por acidente** (assert fraco, `success=true` sem
    executar, erro aceito como saída)? (Q5)
13. **A entrega é implementação COMPLETA, sem stub?** (Q7) — nenhum
    placeholder/`TODO`/`return` de fachada/ramo que engole caso não tratado.
    Stub encontrado no caminho foi catalogado como gap + anotado no código?

> Se alguma resposta for NÃO, a unidade **não está pronta** — volte para a
> implementação. O portão de qualidade (§"nenhum bug sobe") é pré-requisito
> de commit, não uma revisão posterior.

---

## Loop de verificação (obrigatório)

Sempre que escrever/alterar código Kof:

```bash
# 0. PRE-PUSH GATE (Q2) — sem isto o push é proibido
mvn -o -pl kof-compiler -am compile -q

# 1. Rodar os testes da área alterada (rápido)
mvn test -o -pl kof-compiler -am -Dtest='KofAreaTest' -Dsurefire.failIfNoSpecifiedTests=false

# 2. Suíte completa antes de commit
mvn test -o -pl kof-compiler,kof-script,kof-c-compiler,kof-cli -am \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dmaven.test.failure.ignore=true

# 3. Conferir os reports POR MÓDULO (não confie no resumo do reactor)
grep -rl "FAILURE" */target/surefire-reports/*.txt
```

> **Hosts Windows / validação Native:** o backend Native x86-64 invoca
> ferramentas externas reais de assembler/linker (`as`/`ld`) e dependências de
> runtime ELF do Linux. Um resultado `ToolchainMissing` / `as not available` /
> `ld not available` é falha de pré-condição do ambiente, não por si prova de
> regressão do Kof. Ao validar Native a partir do Windows, rode o gate
> relevante em ambiente Linux/WSL com a toolchain disponível. Para scripts de
> build aninhados que usam explicitamente `bash -lc`, prefira `wsl.exe -e`/
> `--exec`; isto é orientação de scripting do Kof para interpretação previsível
> de argumentos, não uma alegação de bug do WSL. Veja
> `docs/debugging/debugging-native.pt_BR.md`.

### Checklist de pré-push (Q0–Q7 — responda antes de `git push`, em QUALQUER branch)

0. O bug foi **consertado na causa raiz** (não mascarado) e o teste que prova
   falharia no código velho? **(Q0)**
1. `mvn -o -pl kof-compiler -am compile -q` verde? **(Q2)**
2. A mudança tem teste no MESMO commit que a prova? **(Q1)**
3. O teste cobre o happy path **e** as bordas Q3 que se aplicam? **(Q3)**
4. Tentei quebrar a mudança (casos extremos, cross-target, regressão vizinha)? **(Q4)**
5. A suíte completa está verde (0 falhas fora dos erros ambientais de `node`)? **(Q5)**
6. `grep -rl FAILURE */target/surefire-reports/*.txt` não aponta nada seu? **(Q5)**
7. A entrega é implementação COMPLETA (sem stub/fachada/TODO)? Stub achado foi
   catalogado como gap + anotado no código? **(Q7)**

> **Nenhum push sem os 8 itens, em nenhuma branch.** Se algum falhar, corrija
> ou reverta — não suba "com nota" nem "para o próximo agente ver".

> **`-Dmaven.test.failure.ignore=true` é OBRIGATÓRIO na suíte completa.** Sem
> ele, o Maven é fail-fast por módulo: qualquer falha em **kof-compiler aborta
> o reactor** e **kof-script, kof-c-compiler e kof-cli nunca rodam** — você
> acha que validou tudo mas só viu o primeiro módulo. O total real com o flag
> é **2687 testes** (compiler 2296 + script 48 + kof-c 7 + cli 336, medição
> 18/09 ~23:10 no job CI Build+Tests do tip `33363a3f` — cresce com cada commit): **0 regressões / 0 erros**
> (ATUALIZAÇÃO 18/09: o trio histórico de nativos vermelhos está FECHADO no código — §252
> corrigido `20495e48` (o ret-addr do usleep clobberava a slot de tamanho cacheada; size
> agora em `%r14` callee-saved), resíduo §181 cross corrigido `c56c74a7` (o `NEG` cross
> rodava `neg` inteiro no bit pattern de float — -inf virava NaN; XOR do bit de sinal),
> face (b) do §256 corrigida `3a593734` (golden sem HB — `await b` + acquire
> `fence r,rw`/`dmb ish` nos consumidores cross). Primeira suíte kof-compiler sem
> vermelho desde que o resíduo foi aberto em 14/09.)
> (node agora presente
> no host da medição — o antigo "13 erros = node ausente" não se aplica mais). O §149 JS (`KofRandomTest.randomStringJs`/`randomShapeJs`,
> regressão do fix §147 no `JsIfThrowElse`) foi **CORRIGIDO 13/09** — a raiz era
> o parser consumir o label de início do `while` seguinte a um assert/if-throw
> como fim do else (ver `known-bugs.md §149`).
> As 59 falhas históricas do bug 59 (Native riscv/aarch,
> `kof_static_java_lang_System_out` no link) foram **CORRIDAS 09/09** — com
> qemu os cross agora PASSAM (`NativeRiscv64E2ETest`/`NativeAarch64E2ETest`
> 42/42 cada; prova no `known-bugs.md §59` + gate 12/09). Qualquer falha que
> não seja de uma guarda ambiental documentada é SUA. Antes de commitar, confira os
> reports POR MÓDULO
> (`grep -rl FAILURE */target/surefire-reports/*.txt`).
> (Lição registrada 08/09: sessões inteiras citaram "suíte 1085/59" sem os
> módulos finais terem rodado.)
>
> **Os números mudam com qemu no ambiente:** sem qemu (host da medição de
> 16/09 — sem toolchain cruzada), os 84 cross
> (2×42, `NativeRiscv64/Aarch64E2ETest`) são **skipados** pelo guard
> (`4408eb6`) + os outros guards de toolchain/BD externo + o guard de sysroot do §255 (`06e77e94`) → `2411/0/196-skip` (o flake §252 disparou 16/09 09:44, depois calou às 11:38, 15:09, 15:54 e 17/09 15:49 — ~1/4 das corridas completas)
> (MEDIDO 17/09 ~15:49, run limpo no tip `f276e966`). Com qemu, **tudo executa** — os 84 cross rodam
> verdes e o total fica igual com a contagem de skip caindo para o
> resíduo externo de BD/ambiente `node`. Estado correto HOJE (19/09 ~07:20, suíte reactor local +
> re-run limpo do kof-compiler no tip `972086f3`, `.class` fantasma removido): **2786 = 2376+48+7+355, 0F / 0E / 209 skip (CI ubuntu executa o android APK; hosts sem SDK = 1 skip honest a mais)** (medido 19/09 ~09:20 no tip `ae0f6ab9` + o run reactor do 1.5.3-S2 neste commit — suíte completa 35:34 BUILD SUCCESS + docs: suíte reactor completa 32:36 + re-run limpo do kof-compiler após deletar o `.class` órfão de `DeclaredReturnLawE2ETest` (minha árvore pré-rebase vazou teste fantasma em `target/test-classes`; mvn sem `clean` executa cadáveres; rm do órfão + re-run = prova 2354/0F; lição: classes órfãs no target/ são fantasmas de testes deletados — o tally da suíte DEVE vir de um reactor cujo target/ casa com a fonte do tip) (compiler 2362→2376 pelo §336+§270 (`AsCastPrecedenceE2ETest` 6, `GenericArgAssignmentE2ETest` 8, 19/09); compiler 2292→2296 pelos testes fmt §305; compiler 2354→2362 pelo §306 (`NullableBoolTruthinessE2ETest` 8, `4abc68f0`); cli 349→355 por `DepsRegistryTest` (pull 1.5.3-S2); cli 333→336 pelas fatias X10 5–7 `LspServerTest`; cli 308→313 por `CmdBuildClasspathTest` da #441 em `d14275f0`, 313→322 por `CmdDeployTest` das fatias 1–3 do X9 (`154ea1a4`/`bfdd452a`/`84c82139`); compiler 2216→2230 por rng (`KofRngTest` 8) + testes de corrida do §286; CI Build+Tests de `0f3c42d6` medido) — o flake
> §252, o resíduo cross §181 e o flake de poll §256(b) estão TODOS fechados no
> código; os skips restantes são o gate opcional de asm e as guardas de toolchain.
> **0 regressões / 0 erros** (2411 na época = 2058+38+7+308, 196 skip) — a corrida completa das 09:44 teve o flake INTERMITENTE
> conhecido do §252 nativo (`spawnWorkerThrowPropagatesThroughSelectAnyNative`, dona lane
> nativa `.18`/nat; às 11:38, 15:09 e 15:54 ele ficou calado — frequência ~1/4, ver §252), que
> deve ser lido como um vermelho de TESTE, não regressão. **HISTÓRICO (superado 18/09):**
> o §252 foi depois provado NÃO ser race — ver `known-bugs.md §252` (raiz +
> fix `20495e48`). O que importa continua sendo nenhum FAILURE fora das guardas
> documentadas.

Para validar um snippet isolado (ex.: confirmar se um idiom compila),
use o harness do projeto ou crie um teste E2E mínimo no pacote da área.

**Nunca** entregue código Kof que você não compilou.

> **Regra do JavaFX (12/09, pedido da mantenedora): a mensagem `Erro: os
> componentes de runtime do JavaFX não foram encontrados. Eles são obrigatórios
> para executar este aplicativo` NUNCA é benigna — sempre representa uma
> regressão ou bug oculto e exige causa raiz + correção.** No JVM o launcher
> do `java -cp <dir> Default.Main` engole o `VerifyError`/`ExceptionInInitializer`
> real atrás dessa mensagem (medido: era `VerifyError: Bad type on operand
> stack`). Para ver o erro de verdade, rode por reflection (contorna o launcher
> JavaFX): grave um `Run.java` que faz `Class.forName("Default.Main")
> .getMethod("main", String[].class).invoke(null, (Object) new String[0])`,
> compile e `java -cp "out:run-dir" Run Default.Main`. A stack trace que sai é
> o bug; trate-a como qualquer falha (regra 1 do Congelamento). **Proibido**
> "consertar" o teste aceitando essa mensagem como saída esperada.

---

## Corpus (onde aprofundar)

| Arquivo | Conteúdo |
|---|---|
| `training/idioms/` | FORMA IDIOMÁTICA de cada problema (BAD/GOOD/WHY) |
| `training/anti-patterns/` | Catálogo de o que NÃO fazer |
| `training/anti-patterns/fake-idioms.md` | Tabela de features que NÃO existem |
| `training/anti-patterns/chained-or-membership.md` | Cadeia de `\|\|` → `setOf().contains()` |
| `training/anti-patterns/java-like-code.md` | Java traduzido → Kof |
| `learn/` | Tutorials passo a passo (00-introduction → 39-stdlib) |
| `docs/architecture/architecture.md`, `docs/architecture/compiler-architecture.md` etc. | Domínios específicos (estáveis) |
| `docs/development/` | **Backlog vivo — tudo que NÃO está concluído** (planos, roadmaps, audits, gaps, refactors). Ver `docs/development/README.md` para índice completo. |
| `docs/development/future/` (plans) | **só plano sem código**: RAII TIER 2.4 (DD-STDLIB-01 FECHADO 13/09 → `docs/stdlib/`). A migração legado (decompiler/translator/IR/differential) foi p/ `docs/development/` 12/09, **voltou p/ `future/` 15/09 — DESPRIORIZADA pela mantenedora** (código fica em kof-cli; promoção exige decisão explícita dela) |
| `docs/development/roadmap.md`, `docs/audits/roadmap-audit.md`, `docs/bugs-and-gaps/ecosystem-coverage.md` | Roadmaps & auditoria de cobertura (fila P0→P5) |
| `docs/bugs-and-gaps/specification-gaps.md`, `docs/bugs-and-gaps/known-bugs.md` | Gaps de spec (SG-00x — fila do maintainer completa, virou referência) + bugs abertos |
| `docs/native-multiarch.md`, `docs/stdlib/DATABASE_VISION.md`, `docs/audits/complexity-audit.md` | Native multiarch (NATIVE002) + DB vision (realizada → stdlib) + audit ≤500 (snapshot → architecture) |
| `docs/development/DECISIONS.md` | **Decisões da mantenedora** (time/segurança/app-model/Spring — pasta `decision-pending/` extinta 13/09) |
| `docs/development/roadmap.md` §23 | **Plano de implementação consolidado** (Tiers 0–12) — único plano ordenado; migração A–H ✅, universal **EM DESENVOLVIMENTO** 17/09 (`IMPLEMENTATION-UNIVERSAL-PLATFORM.md`, R12 sobreposto — §D-UNIVERSAL) |

---

## Atualizando o corpus (obrigatório)

Se durante o trabalho você descobrir:

- Um **idiom novo** que a linguagem suporta (ex.: `setOf` variádico) →
  adicione em `training/idioms/<area>.md` com BAD/GOOD/WHY.
- Um **anti-pattern novo** (ex.: cadeia de `\|\|`) → crie
  `training/anti-patterns/<nome>.md` com Name/Problem/Bad/Preferred/Why.
- Uma **feature que não existe** que uma IA quase alucinou → adicione na
  tabela de `training/anti-patterns/fake-idioms.md`.

O corpus é a memória de longo prazo dos agentes. Se você aprendeu algo,
ensine-o para o próximo.

---

## Resumão (cola na tela)

```
Kof = intenção + simplicidade.

- Função:  String nome(Int x) { ... }     (sem fun/func)
- Classe:  class X { campos; constructor(...) }  (mutável) / class X(...) = record
- Dados:   record Point(Int x, Int y)
- String:  a == b  (não .equals)   a + "!"  (não StringBuilder)
- Coleção: listOf / mapOf / setOf  +  .map/.filter/.reduce
- Memb.:   setOf("A","B").contains(x)   (NUNCA x=="A" || x=="B" || ...)
- Erro:    throw "msg"  /  catch (String e)
- Null:    String?  +  if (x != null)
- Cast:    x as Char / big as Int  (conversões numéricas reais)
- Concorr: spawn / await   (sem Thread)
- Loops:   for (var x in coll)  /  if-expr  /  switch-expr (case ->)
- Top-level: SÓ class e função (sem val/var/let)

Se parece Java, está errado. Compile antes de entregar.
```
