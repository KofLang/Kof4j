# AGENTS.md — Escrevendo Kof (guia para agentes de IA)

Este é o guia **obrigatório** para qualquer agente de IA (ou humano) que
escreva código Kof neste repositório. Leia antes de gerar qualquer `.kf`.

**Versão:** 0.4.0-beta · Última atualização: 11/09/2026 (modo autônomo + condição de ESTABILIDADE com recusa de re-disparo; branch ativa = `beta-0.4.0`)

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

1. **  em jogo** — mudança de contrato/operador/ordem de
   avaliação (regra 6): vira gap/plano em `planning-*`, nunca edição.
2. **Colisão de lane inevitável** — o único caminho toca um arquivo `EM CURSO`
   de outro agente e não dá para adiar: pare, registre no DOING.md, aguarde.
3. **Gate quebrado sem causa na sua mudança** — suíte vermelha que você não
   introduziu e não consegue diagnosticar: registre em `docs/development/known-bugs.md`
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
> 1. **Todos os bugs resolvidos** — `docs/development/known-bugs.md` sem item
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
  falta em nossos planos, trace um todo de implementação e continue o
  desenvolvimento"*.
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
- **Watchers de issue (12/09):** `scripts/issue-watcher.sh start <issue> <min>
  <sessão>` vigia comentários novos de uma issue a cada N minutos (aceita horas
  divisíveis de 60: 60/120) e injeta um turno na sessão viva (mesmo
  `--attach` obrigatório do heartbeat; `seen` só avança após injeção bem
  sucedida). Em uso: **#97 a cada 2h** — quando a frente tree-shaking recebe
  comentário externo (review/parceiro), o agente LÊ, responde na issue se
  procedente, ajusta o plano/DOING e segue a fila. Interagir com issue que
  impacta o trabalho EM CURSO é parte do loop, não distração.


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
3. **Transparência cirúrgica de erros.** Erros vão para `docs/development/known-bugs.md`
   com **causa raiz** + **menor repro**, inclusive regressões que a mantenedora
   introduziu. Nunca "documentar em volta" do bug.
4. **Discussão técnica antes de código.** Quando a dúvida é conceitual (semântica,
   estouro de ponto flutuante, ABI), a contribuição é por **debate técnico** —
   propostas/documentos de design comentados — não PR desordenado que muda
    .
5. **Blindagem contra poluição.** Nunca misturar a linguagem Kof com termos
   alheios ao domínio (jogos, etc.) em docs/código. Disclaimers e nomenclatura
   são lei; violou, reverte.
6. **Toda PR vem acompanhada de uma issue relacionada.** PR "solta" não entra.
   Toda mudança proposta referencia uma issue aberta que a justifica —
   rastreabilidade é lei, não preferência.
7. **Identidade em commits e issues/PRs (12/09, mecanismo DESVENDADO e
   medido).** Agente publica pela identidade da organização/agente, **nunca**
   pelo perfil pessoal visível da mantenedora. **Não é App nem PAT** — é
   atribuição do GitHub por **e-mail de autor**: commit com
   `user.email = aminadojava@gmail.com` (e-mail verificado pelo usuário
   `temmcode`, id 214545180) aparece no painel de contributors como
   **`temmcode`** — 268 commits no Kof4j provados (`git log | grep -c` ==
   `contributors API` == 268, zero ocorrências de "temm" em qualquer autor).
   Por isso nenhuma máquina tem token `temmcode`. Setup por clone (uma vez):
   `git config user.name "temmcode"` +
   `git config user.email "aminadojava@gmail.com"` — o nome `Kof-agent-worker`
   da org fica para quando a org ganhar verificação de e-mail própria; o
   truque hoje é o e-mail. Para issues/PRs via `gh`, o token ativo resolve
   (`gh api user --jq .login`): sem credencial do worker, **NÃO comentar como
   melmonfre** — salvar o corpo em `.issue<N>-reply-pending.md` (gitignored),
   registrar a pendência no `DOING.md` e seguir com o código. Verificação da
   sessão: `git config user.email` = o e-mail da identidade ANTES do primeiro
   commit. (Histórico: um comentário na #97 saiu como melmonfre 12/09 e foi
   APAGADO; re-postagem quando a identidade de issue existir.)

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

**Sincronização obrigatória (pull antes, push depois):** antes de **todo
commit** — `git fetch` + `git pull --rebase` (com working tree sujo, use
`git stash push` antes e `git stash pop` depois, ou `--autostash`) e
**verifique se há conflito** (rebase parado / `<<<<<<<`): conflito é resolvido
na hora, nunca commitado por cima. Depois do commit, **`git push`** — o DOING.md
só coordena quem *vê* o remoto; commit local não reivindicado é tarefa fantasma
para os outros agentes. Depois do pull, **releia o DOING.md**: o que era seu
"próximo passo" pode ter sido feito ou reivindicado por outro agente no
intervalo.

### Lição aprendida (04/09) — trabalhe SEMPRE em partes pequenas

> **Nunca tente gravar/produzir um artefato grande de uma vez.** O plano de
> refactoring `docs/development/refactoring/PLAN-SOLID-500.md` (120 classes, 8 fases) foi
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

---

## Congelamento de comportamento (obrigatório)

> **O comportamento previsto é lei.** "Comportamento previsto" = o que o corpus
> (`training/`, `learn/`, `docs/`) documenta e os testes (golden + E2E + suíte
> completa) provam. **Nenhum agente pode quebrar comportamento que já funciona.**

1. **Zero regressão.** Nenhum commit pode fazer um teste existente passar a
   falhar. A suíte completa (`mvn test`, hoje **1207** nos 4 módulos — ver
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
   `docs/development/known-bugs.md` é desvio do comportamento previsto e **deve ser
   corrigido no código** para atingir o comportamento documentado. Proibido
   "documentar em volta do bug" (mudar o corpus para aceitar o comportamento
   errado como se fosse o certo). Se o comportamento documentado está errado,
   é decisão de design → bump de versão + discussão, nunca correção silenciosa.
5. **Paridade cross-target.** JVM/Native/JS divergindo no mesmo programa é bug
   de paridade. O comportamento previsto vale nos 3 targets, ou gap `XXX00x`
   diagnosticado — nunca divergência silenciosa.
6. **  (0.2.6-beta).** Operadores, precedência, ordem de
   avaliação, null-safety, `==` de conteúdo, exceções como String,
   `spawn`/`await`, coleções `List/Map/Set` são **congelados**. Proposta de
   mudança vira gap/plano em `planning-*`, nunca edição direta da semântica
   atual.

---

## Invariantes da plataforma (visão universal — `docs/development/future/PLAN-UNIVERSAL-PLATFORM.md`)

Estas regras **sempre** se aplicam, mesmo quando não há código de domínio novo
em jogo. São o mecanismo anti-"god language":

1. **Fronteira core → stdlib base → plataforma → pacotes oficiais → interop**
   (R1). Domínio pesado (`ml`, `bio`, `hpc`, `infra-<cloud>`) vai para
   **pacote oficial**, nunca para a stdlib base. Só entra na stdlib o que é
   "essencial à plataforma e pequeno".
2. **Interop-first** (R9). Para qualquer capacidade, a primeira pergunta é
   "já existe por fora e é melhor?" → FFI/interop (`kof.process`, `.so`, JVM,
   GraalJS). Nunca reimplementar Arrow/Parquet/BLAS/LAPACK/CUDA/NumPy/
   alinhadores/frameworks de ML.
3. **Escopo honesto por target** (R7): capacidades pesadas chegam **JVM-first**
   (interop), **Native** para sistemas/deploy, **JS** só web/edge. Nunca
   prometer paridade JS para domínios pesados.
4. **Nunca silencioso por domínio** (R6): todo gap de domínio tem código
   (`INFRA00x`, `DATA00x`, `SCI00x`, `BIO00x`, `SECPQ`, ...) + entrada na
   matriz de paridade. Nunca stub silencioso, nunca fallback fraco.
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
   (collections, functions, strings, errors, records, classes, concurrency, control-flow).
2. Leia `training/anti-patterns/` — em especial `java-like-code.md`,
   `chained-or-membership.md`, `fake-idioms.md`.
3. Se a dúvida persistir: **escreva um snippet e compile** (loop abaixo).

---

## Sintaxe real (verificada no compilador — 0.3.0-beta)

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
// sobrecarga de MÉTODO de classe ainda NÃO existe (§131, aberto)
```

### Variáveis (só dentro de funções/corpos — **não existe top-level `val`/`var`/`let`**)

```kof
var x = 10              // mutável
val y = 20              // imutável
String nome = "Mel"
String? nome2 = null    // nullability: forma TIPO-PRIMEIRO (idiomática no corpus)
var idade: Int? = null  // nullability: forma ANOTADA (também válida)
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

1. **Compilei?** (loop de verificação abaixo)
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

---

## Loop de verificação (obrigatório)

Sempre que escrever/alterar código Kof:

```bash
# 1. Compilar o módulo (rápido)
mvn -o -pl kof-compiler -am compile -q

# 2. Rodar os testes da área alterada
mvn test -o -pl kof-compiler -am -Dtest='KofAreaTest' -Dsurefire.failIfNoSpecifiedTests=false

# 3. Suíte completa antes de commit
mvn test -o -pl kof-compiler,kof-script,kof-c-compiler,kof-cli -am \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dmaven.test.failure.ignore=true
```

> **`-Dmaven.test.failure.ignore=true` é OBRIGATÓRIO na suíte completa.** Sem
> ele, o Maven é fail-fast por módulo: o **kof-compiler aborta o reactor** com
> as 59 falhas conhecidas do bug 59 (Native riscv/aarch) e **kof-script,
> kof-c-compiler e kof-cli nunca rodam** — você acha que validou tudo mas só
> viu 1086/59 do primeiro módulo. O total real com o flag é **~1207 testes**
> (compiler ~1086 + script 24 + kof-c 5 + cli 92, números de 08/09 — crescem
> com cada commit): as 59 falhas devem ser SÓ
> `NativeRiscv64E2ETest`/`NativeAarch64E2ETest`/`crossNative*` (bug 59).
> Qualquer falha fora dessas é sua — antes de commitar, confira os reports
> POR MÓDULO (`grep -rl FAILURE */target/ surefire-reports/*.txt`).
> (Lição registrada 08/09: sessões inteiras citaram "suíte 1085/59" sem os
> módulos finais terem rodado.)
>
> **Os números mudam com qemu no ambiente:** sem qemu, os ~59 cross-arch
> são **skipados** pelo guard (`4408eb6`) — mesma suíte vira
> `~1210/0/~64-skip`. Com qemu, **falham** (bug 59 aberto) —
> `~1207/59/3-skip`. Ambos os estados são "suíte verde" para a sua lane:
> o que importa é não ter falha FORA do par riscv/aarch.

Para validar um snippet isolado (ex.: confirmar se um idiom compila),
use o harness do projeto ou crie um teste E2E mínimo no pacote da área.

**Nunca** entregue código Kof que você não compilou.

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
| `docs/development/future/` (plans) | Planos futuros: migração legado (decompiler/translator/IR/differential) + plataforma universal (era `docs/future/`) |
| `docs/development/roadmap.md`, `docs/development/roadmap-audit.md`, `docs/development/ecosystem-coverage.md` | Roadmaps & auditoria de cobertura (fila P0→P5) |
| `docs/development/specification-gaps.md`, `docs/development/known-bugs.md` | Gaps de spec (20 SG-00x) + bugs abertos (37–40, CANVAS001) |
| `docs/development/native-multiarch.md`, `docs/development/DATABASE_VISION.md`, `docs/development/complexity-audit.md` | Native multiarch (NATIVE002) + DB vision + audit ≤500 |
| `docs/development/security-plan.md` | Plano de segurança (18 camadas, B/C/D pendentes) |
| `docs/development/plan-platform-completion.md`, `docs/development/plan-spring-independence.md` | Plans de plataforma & Spring independence (P3–P5) |
| `docs/development/future/ACTION_PLAN.md` | Ordem de implementação de `docs/development/future` (Tiers 0–12) |

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
