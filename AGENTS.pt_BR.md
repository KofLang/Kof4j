# AGENTS.md

last: 0.5.0-beta

doing: #651-COMPLETA (superfície Buffer(U8) + token FFI B no x86-64 E no cross riscv64/aarch64; fatia B 29/09) + #678-D-SCRIPT-WARN-SURFACE-pousada (Script expõe WARNING do frontend) + unidade-3-fase-5-pinada (Buffer(U8) INOUT × spawn/await paridade runtime) + unidade-2-fase-5-pousada (#667 Script×extern FFI001 na linha da declaração + #668 face de compilação MEM020) + memory-safety-fase-4-FECHADA (#658/#659/#662) + #660-D-MEM021-SCALAR-pousado (c65f9ba18, mantenedora A/ERROR) + cadeia-de-evidencia-hardened (#664/#665/#669) + unidade-1-fase-5-pinada (#666) + tabela-ownership-pousada (#670) + celulas-defasadas-mortas (#671) + selftest-pt-provado (#672) + records-vivos-registrados (#673)

next: unidade 4 da fase 5 (medir-antes) / varredura-de-promocao (lane pipeline) / 14.4-rulesets (mantenedora)

location: repositório

state: ativo

intent: contrato-operacional-do-agente

constraint:

* qualidade-primeiro
* nenhum-bug-em-release
* sem-stubs
* zero-regressão
* compatibilidade-aditiva
* compilar-antes-da-entrega
* testar-com-a-mudança
* somente-branch-ativa
* sem-worktree-temporária
* mantenedora-controla-design
* mantenedora-controla-merge-na-main
* Kof-primeiro
* simplicidade-primeiro
* cross-target-honesto
* sem-fallback-silencioso
* sem-sintaxe-alucinada

decision:

* D-BRANCH-PIPELINE: active branch = `lab`; a promoção é explícita e unidirecional `lab → testing → prerelease → stable → release/x.y.z → tag` (`D-QUALITY-PIPELINE-2609`)
* D-KOF-FIRST: o contrato do Kof precede o comportamento de linguagens externas
* D-KOF-FIRST-IMPL: funcionalidades pós-0.5.0 são library-first
* D-MAKEALIVE
* D-KOF-AS-CLOUD
* D-BOOTSTRAP
* D-DB-GAPS
* D-GRAPHICS-GAMING
* D-KOFMD-ON-EDIT: todo documento editado por um agente é comprimido em Kofmd no mesmo commit
* D-KOFMD-OPERATING-STANDARD: todo agente pensa, raciocina, responde, executa e documenta em Kofmd — uniforme, sem variante por agente
* D-FUTURE-PROMOTION: antes de começar trabalho novo, migrar para `lab` com TODO o trabalho atual, então promover o plano MAIS FÁCIL de implementar de `docs/development/future/` para `docs/development/` e implementá-lo — nunca o mais interessante, nunca um plano de semântica congelada

---

## Ciclo operacional

state:

mode: autônomo

loop:

* ler DOING.md
* ler docs/status.md
* inspecionar git log e a suíte
* garantir que a branch ativa é `lab` — migrar todo o trabalho atual para `lab` ANTES de começar; `beta-*` está congelada (`D-BRANCH-PIPELINE`)
* se não houver tarefa viva sem dono, promover o plano de menor custo implementável de `docs/development/future/` (ver Promoção de futuro, `D-FUTURE-PROMOTION`)
* escolher a tarefa não atribuída de maior valor
* reivindicá-la em DOING.md com `dona = <ipv4-local>:<porta-opencode>` (PT) / `owner = <ipv4-local>:<porta-opencode>` (EN) — a **regra absoluta de identidade** (`D-AGENT-IDENTITY-IPPORT`, 01/10); um claim sem IP:PORT é INVÁLIDO (gate `scripts/check_owner_identity.sh`)
* executar um escopo completo
* testar
* fazer commit com DOING.md (todo commit atualiza a linha `dona = <ip>:<porta>`)
* fazer push através de scripts/sync-push.sh
* reler DOING.md
* continuar

rule:

* nunca perguntar quando as evidências do repositório podem responder
* nunca encerrar um turno com uma unidade não commitada
* nunca encerrar um turno apenas com um resumo
* exatamente um item do todowrite pode estar in_progress
* concluído significa comprovado, não pretendido
* NEXT STEP em DOING.md deve conter tarefa + arquivo + prova esperada

stop:

* decisão de semântica congelada é necessária
* colisão inevitável entre lanes
* regressão externa sem explicação
* requisito ausente do corpus/compiler
* repositório estável

stable:

bugs: fechados

development: concluído

future: implementado-ou-promovido

suite: verde

conformance: 5/5

on-stable-retrigger:

* verificar regressão
* se houver regressão: reivindicar e executar
* caso contrário: registrar recusa por estabilidade
* parar scripts/auto-loop.sh
* parar

---

## Heartbeat autônomo

entry:

* scripts/auto-loop.sh start
* scripts/auto-loop.sh status

constraint:

* uma sessão de heartbeat
* --attach obrigatório
* flock impede sobreposição
* saúde do servidor obrigatória
* parar o heartbeat quando o modo autônomo terminar

sessions:

heartbeat:

port: 9093

session: ses_f69e2a3f7ffe9J10aWcHEUOfW8

issue-watcher:

port: 9094

session: ses_f69c2cb03ffe2zDYCqW7fesphi

issue-watcher:

* despachar somente em issue nova
* despachar quando título/corpo for editado
* despachar em comentário externo
* comentário do próprio bot não dispara novamente
* comentário humano sempre dispara novamente
* fazer triagem antes de ampliar o escopo
* não tocar na frente de outra lane

---

## Autoridade

authority:

maintainer: Mel Santos

architecture: mantenedora

frozen-semantics: mantenedora

main-merge: mantenedora

rule:

* IA acelera a implementação
* IA não define arquitetura
* IA não redefine a semântica do Kof
* IA não faz merge de nenhum estágio na main
* toda mudança exige uma issue
* toda entrega exige prova

identity:

preferred: kof-agent-worker

by: ipv4-local + porta-opencode (absoluto, obrigatório — emenda 01/10, `D-AGENT-IDENTITY-IPPORT`)

fallback: maintainer-default

forbidden:

* email sintético
* Co-authored-by
* truques de identidade
* `dona = <ipv4>` SEM `:<porta>` — o gate rejeita

rule:

* identifique pelo **IPv4 local** (`hostname -I`) E pela **porta do servidor opencode** à qual a sessão está anexada (`ss -tln | grep opencode` / o `opencode -s ... --port <N>` ou `--attach http://127.0.0.1:<N>` em execução) — DOING §Operating-loop regra 9
* toda reivindicação `EM PROGRESSO`/`FEITO`/`CORRIGIDO`/`PARADA` leva `dona = <ipv4-local>:<porta>` (PT) / `owner = <ipv4-local>:<porta>` (EN) — nunca só "esta sessão" e nunca IPv4 puro (roteador/DHCP mudam o endereço; a porta desambigua sessões no mesmo host)
* o gate de enforcement é `scripts/check_owner_identity.sh` — rc=1 em qualquer claim com data ≥ `01/10` cujo IPv4 não tenha `:<porta>`
* nunca agir na lane de outro dono só pelo IP — confirme por sessão + lane + SHA do commit + IP:PORT (roteador/DHCP mudam ambos)

---

## Estado multiagente

source:

* DOING.md
* git
* compiler
* testes

claim:

* ler DOING.md antes de trabalhar
* item IN PROGRESS existente não é seu
* reivindicar antes da implementação
* **regra absoluta de identidade (`D-AGENT-IDENTITY-IPPORT`, 01/10): todo claim é `dona = <ipv4-local>:<porta-opencode>` (PT) / `owner = <ipv4-local>:<porta-opencode>` (EN) — um IPv4 puro ou "esta sessão" é INVÁLIDO e o gate `scripts/check_owner_identity.sh` rejeita (rc=1)**
* claim e primeira mudança compartilham o mesmo commit
* todo commit atualiza sua linha no DOING.md
* DONE exige data + SHA + prova
* trabalho abandonado retorna para OPEN com estado
* proprietário órfão pode ser reatribuído após verificação do código

collision:

* nunca dois agentes na mesma lacuna
* nunca dois agentes no mesmo arquivo gigante
* coordenar antes de uma colisão inevitável

shared-claim:

* números §NNN vêm da ponta remota
* executar git fetch antes de selecionar o número
* verificar novamente o número após rebase

sync:

before_commit:

* git fetch
* git pull --rebase --autostash
* inspecionar conflitos
* executar grep nos arquivos alterados procurando por ^<<<<<<<
* executar novamente os gates na árvore pós-rebase

after_commit:

* scripts/sync-push.sh
* verificar ahead=0
* verificar behind=0

conflict:

* preservar ambos os lados
* refazer a própria mudança sobre o conteúdo atual
* nunca usar checkout --ours
* nunca usar checkout --theirs
* nunca tratar conteúdo rebased como obsoleto

---

## Estado da documentação

state_model:

docs:

meaning: implementado-validado-decidido

development:

meaning: implementação-pendente

development/future:

meaning: planejado-não-iniciado

bugs-and-gaps:

meaning: registros-vivos-de-bugs-lacunas-paridade

audits:

meaning: snapshots-de-estado

development/DECISIONS.md:

meaning: decisões-da-mantenedora

rule:

* auditar development antes de novo trabalho
* implementado + validado → docs/
* parcialmente implementado → finalizar → docs/
* somente planejado → development/future/
* obsoleto/duplicado → consolidar
* trabalho concluído nunca permanece em development/
* future não é trabalho atual sem promoção
* não criar documentos de planejamento para evitar implementação
* uma decisão tomada em chat vira DECISIONS.md + fila no mesmo commit
* todo documento editado por um agente é comprimido em Kofmd no mesmo commit (`D-KOFMD-ON-EDIT`)

hot_docs:

* DOING.md
* DOING.pt_BR.md
* docs/status.md
* docs/status.pt_BR.md
* docs/development/*-plan.md
* CHANGELOG.md
* CHANGELOG.pt_BR.md
* docs/bugs-and-gaps/known-bugs.md
* docs/bugs-and-gaps/known-bugs.pt_BR.md
* docs/development/roadmap.md §23

kofmd:

* hot_docs usam blocos de estado canônicos
* ordem dos campos: last, doing, next, location, state
* adicionar constraint/decision quando necessário
* informação tipada deve ser tipada
* prosa deve carregar somente informação que não pode ser representada estruturalmente
* nunca duplicar campos na prosa
* obrigatório ao editar: todo documento tocado por um agente é comprimido no mesmo commit
* learn/ e training/ são excluídos da compressão Kofmd
* padrão operacional: todo agente pensa, raciocina, responde, executa e documenta em Kofmd — uniforme, sem variante por agente (`D-KOFMD-OPERATING-STANDARD`)
* evidência antes de inferência; `unknown` em vez de `probably`; nunca fabricar api/sintaxe/comportamento/decisão/resultado/contrato
* `implemented` != `verified`; só declarar resultado com prova executada
* `last` = estado anterior imediatamente relevante; `next` = próxima intenção, não backlog
* prosa somente onde a estrutura não carrega a informação
* coordenação: reivindicar antes de trabalhar; em colisão de lane esperar o dono ou parar, nunca disputar a worktree compartilhada; nunca encerrar turno com unidade não commitada; push só via `scripts/sync-push.sh`

---

## Promoção de futuro

intent: futuro-não-é-trabalho-atual-sem-promoção

rule:

* antes de começar trabalho novo: migrar para `lab` com TODO o trabalho atual primeiro; nunca começar em `beta-*` ou checkout destacado (`D-BRANCH-PIPELINE`)
* promover exatamente UM plano de `docs/development/future/` para `docs/development/` e implementá-lo
* escolher o MAIS FÁCIL de implementar (menor custo) — nunca o mais interessante, nunca o maior

mais fácil (maior vence):

* nenhuma decisão `D-*` necessária: não é semântica congelada, não é primitiva central ausente
* aditivo e library-first: Kof consegue expressá-lo sem mudar a superfície da linguagem (`D-KOF-FIRST`)
* dependências já medidas em código (o plano nomeia arquivos/linhas reais)
* escopo único e coeso para uma lane (uma responsabilidade)
* existe caminho de teste agora (prova RED-first é definível)

inelegível:

* precisa de decisão de semântica congelada ou `D-*` da mantenedora primeiro
* precisa de nova primitiva central ou sintaxe
* aceita gap, entrega stub ou enfraquece asserção
* a razão é "seria legal" em vez de "é o incremento completo mais barato"

fluxo:

* reescrever o plano com status `UNDER DEVELOPMENT` + estado real + como-terminar
* movê-lo para `docs/development/<plan>.md` (+PT) no MESMO commit que o reivindica
* enfileirá-lo em `roadmap.md` §23 e apontar `docs/status.md` para ele
* reivindicar em DOING.md (tarefa + arquivo + prova esperada), implementar, testar, commitar, pushar

fallback:

* se NENHUM plano for implementável sem decisão da mantenedora, NÃO inventar um — registrar o achado e parar

---

## Filosofia Kof

intent: simplicidade

law:

* Kof deve ser mais simples que as alternativas
* código expressa intenção, não mecanismo
* complexidade pertence à plataforma
* domínio pertence às abstrações de domínio
* sem cerimônia
* sem idiomas de linguagens traduzidas
* sem código com aparência de código gerado
* sem infraestrutura desnecessária

rule:

1: usar primitivas de intenção do Kof
2: usar capacidades da stdlib/plataforma
3: usar tipos de domínio
4: evitar cerimônia de getters/setters/builders/service-repository-controller
5: compilar a sintaxe antes de usá-la
6: comportamento não suportado pelo target deve ser diagnosticado
7: nomes descrevem responsabilidade
8: Kof não é Java/Kotlin/C#/outra linguagem
9: verificação da filosofia vem antes da classificação de feature-gap
10: contrato do Kof precede comportamento externo
11: superfície da linguagem deve permanecer extremamente simples
12: funcionalidades são library-first quando Kof consegue expressá-las

---

## Kof-first

flow:

* funcionalidade
* Kof consegue expressá-la?
* sim → implementar biblioteca Kof
* não → identificar a capacidade fundamental ausente
* adicionar a menor primitiva
* implementar a funcionalidade em Kof

core:

supplies: mecanismos

libraries:

supply: política-e-abstração

for_core_change:

* por que Kof não consegue expressá-la
* capacidade fundamental ausente
* menor mudança possível no core
* reutilização liberada pela primitiva
* futura substituição em Kof

forbidden:

* APIs de runtime específicas da funcionalidade
* sintaxe específica da funcionalidade quando uma biblioteca é suficiente
* branches `if feature == X`
* hacks de superfície específicos do backend

acceptance:

normal:

* código-fonte Kof
* biblioteca Kof
* testes
* documentação

primitive-required:

* código-fonte Kof
* primitiva mínima
* implementação no backend
* biblioteca Kof
* testes
* documentação

direction:

* self-hosting progressivo
* biblioteca por biblioteca
* sem obrigação de reescrita

---

## Comportamento externo

before_external_research:

* provar que o reproducer é Kof válido
* identificar o contrato Kof que governa o comportamento
* procurar idioma/abstração Kof
* medir o comportamento real

bug:

condition: implementação difere do contrato Kof

gap:

condition: necessidade legítima não possui abstração Kof adequada

external_sources:

provide:

* princípios
* invariantes
* trade-offs
* bugs conhecidos

do_not_provide:

* sintaxe automática
* API automática
* semântica automática

contract_authority:

* DECISIONS.md
* documentação normativa
* testes golden/conformance
* matriz de paridade
* implementação

---

## Gate de qualidade

intent: nenhum-bug-em-release

flow:

* reproduzir
* corrigir a causa-raiz
* adicionar teste de prova
* executar testes relevantes
* executar a suíte
* procurar casos-limite
* fazer commit
* fazer push

Q0:

* bug corrigido na causa-raiz
* teste falha no código antigo

Q1:

* toda funcionalidade possui um teste
* toda correção de bug possui teste de regressão
* teste e implementação compartilham o commit
* refactor preserva a prova existente

Q2:

command: mvn -o -pl kof-compiler -am compile -q

condition: verde-antes-do-push

Q3:

cover_when_applicable:

* limite numérico
* vazio/null
* limites
* erro esperado
* cross-target
* idempotência
* concorrência
* falha de interop/recurso

rule:

* somente happy path é insuficiente

Q4:

hunt:

* entrada extrema
* divergência cross-target
* fronteira do contrato
* regressão em comportamento vizinho
* comportamento não coberto

Q5:

* sem falso verde
* sem asserções enfraquecidas
* sem skip oculto
* red próprio deve ser corrigido ou revertido

Q6:

* suíte é o mínimo, não o teto

Q7:

forbidden:

* stub
* placeholder
* TODO/FIXME fingindo conclusão
* implementação vazia
* facade que retorna null
* facade que retorna zero
* branch não tratada engolida
* suporte fingido como not-implemented

unsupported:

* diagnóstico explícito XXX00x

existing_stub:

* catalogar em known-bugs.md ou specification-gaps.md
* anotar a lacuna no código
* planejar através do modelo de estado da documentação

---

## Congelamento de comportamento

constraint:

* zero regressão
* compatibilidade aditiva
* refactors preservam semântica
* bugs alinham implementação ao contrato Kof
* paridade cross-target
* semântica congelada exige decisão da mantenedora

frozen:

* operadores
* precedência
* ordem de avaliação
* segurança contra null
* content ==
* exceções de String
* spawn/await
* List/Map/Set

contract_change:

* decisão da mantenedora
* incremento de versão
* documentação
* migração

---

## Plataforma

boundary:

core → stdlib base → plataforma → pacotes oficiais → interop

rule:

* domínios pesados pertencem aos pacotes oficiais
* stdlib permanece essencial e pequena
* registrar novo namespace no ledger da stdlib
* gate de máquina: scripts/check_stdlib_boundary.sh

interop:

* preferir capacidade externa existente
* usar FFI/interop quando apropriado
* não reimplementar engines externas
* engine gráfica/game é propriedade do Kof conforme D-GRAPHICS-GAMING

targets:

JVM: pesado/interop-first

Native: sistemas/deploy

JS: web/edge

rule: nunca prometer paridade não suportada

diagnostics:

* todo domínio não suportado possui um gap code
* toda lacuna entra na matriz de paridade
* sem fallback silencioso

stability:

* pacote começa como experimental
* promoção exige DoD completo
* 3 targets ou lacunas diagnosticadas
* E2E por target
* benchmark quando aplicável
* docs/training sincronizados

security:

* bibliotecas criptográficas auditadas
* defaults seguros
* primitivas constant-time
* formatos versionados
* diagnósticos SECN00x/SECPQ

determinism:

* correção obrigatória
* determinismo obrigatório quando aplicável
* prova property-based + golden para ciência/ML

non_goals:

* macros abertas
* type classes
* fundação de annotations
* ownership/borrowing
* sistema completo de effects
* Kali-in-Kof
* target-por-domínio
* engine própria de SQL/Arrow/ML

---

## Gate de código

before_code:

* ler training/idioms/<area>.md
* ler training/anti-patterns/
* compilar sintaxe incerta

idiom:

membership: setOf(...).contains(x)

equality: ==

strings: + / +=

data: record

nullability: T? + narrowing

concurrency: spawn / await

collections: listOf / mapOf / setOf

json: json.encode/decode

db: db.connect

http: http.get

loops: for (var x in xs)

condition: if-expression

branching: switch

functions: type-before-name or type-after-params

top-level: functions/classes only

forbidden_foreign_forms:

* fun
* func
* fn
* val/var/let no nível superior
* let
* const
* async fn
* Thread
* Executor
* Runnable
* Optional
* Result
* JavaBeans
* cerimônia Service/Repository/Controller
* construtores primários Java/Kotlin
* sintaxe estrangeira de membership
* sintaxe estrangeira de pattern
* operadores de null específicos de linguagem que não são definidos pelo Kof

rule:

* se a sintaxe for incerta: compilar
* resultado do compilador prevalece sobre a memória

---

## Corpus

training/idioms:

purpose: formas canônicas de Kof

training/anti-patterns:

purpose: formas proibidas/estrangeiras

training/anti-patterns/fake-idioms.md:

purpose: funcionalidades que não existem

training/anti-patterns/chained-or-membership.md:

purpose: OR repetido → membership

training/anti-patterns/java-like-code.md:

purpose: Java traduzido → Kof

learn:

purpose: tutoriais

docs:

purpose: documentação técnica normativa/estável

rule:

* training/learn responde perguntas de sintaxe
* compilador confirma sintaxe
* docs define contratos atuais
* corpus é memória de longo prazo do agente

corpus_update:

new_idiom → training/idioms/

new_antipattern → training/anti-patterns/

hallucinated_feature → fake-idioms.md

---

## Verificação

compile:

* mvn -o -pl kof-compiler -am compile -q

focused:

* mvn test -o -pl kof-compiler -am -Dtest='<AreaTest>' -Dsurefire.failIfNoSpecifiedTests=false

full:

* mvn test -o -pl kof-compiler,kof-script,kof-c-compiler,kof-cli -am
  -Dsurefire.failIfNoSpecifiedTests=false
  -Dmaven.test.failure.ignore=true

reports:

* grep -rl "FAILURE" */target/surefire-reports/*.txt

rule:

* inspecionar reports por módulo
* guard ambiental não é uma regressão do Kof
* nunca afirmar que está verde sem prova executada

native:

* toolchain Linux obrigatório
* validação Windows Native pode usar WSL
* ausência de as/ld/qemu é uma condição do ambiente
* skips ambientais documentados permanecem explícitos

javafx:

rule: mensagem de runtime ausente nunca é aceita como benigna

action:

* expor a exceção subjacente
* diagnosticar a causa-raiz
* corrigir
* testar

---

## Commit e entrega

before_commit:

* Q0-Q7 satisfeitos
* compilação verde
* testes relevantes verdes
* suíte completa executada
* árvore pós-rebase inspecionada
* docs editados por agente comprimidos para Kofmd
* DOING.md atualizado

commit:

* unidade coesa e completa
* implementação + prova juntas
* DOING.md atualizado no mesmo commit

push:

* somente scripts/sync-push.sh
* verificar remoto 0/0

release:

* agentes podem fazer push da branch de desenvolvimento ativa (`lab`)
* agentes nunca promovem/fazem merge de um estágio no seguinte (promoção é gate da mantenedora até `14.3`)
* a mantenedora realiza o merge de release

---

## Partes pequenas

rule:

* uma unidade coesa por ação
* fazer commit cedo de unidades concluídas
* editar arquivos grandes incrementalmente
* evitar writes gigantes
* alvo de ≤500 linhas/classe
* 500–599 é dívida tolerada
* ≥600 bloqueia CI
* dividir por responsabilidade, nunca por sufixo numérico

---

## Autoverificação final

ready:

* compilado
* testado
* suíte completa executada
* causa-raiz corrigida
* casos-limite relevantes cobertos
* cross-target verificado
* sem falso verde
* sem stub
* sem idioma estrangeiro
* sem infraestrutura desnecessária
* abstração Kof preferida
* teste e mudança compartilham commit
* DOING.md atualizado — todo claim leva `dona = <ipv4>:<porta>` (`D-AGENT-IDENTITY-IPPORT`, gate `scripts/check_owner_identity.sh`)
* remoto sincronizado

if_any_false:

state: não-pronto

next: corrigir-antes-da-entrega

---

## Cola rápida

Kof:

* intenção
* simplicidade
* compile-first
* library-first
* qualidade-primeiro
* diagnósticos honestos
* zero regressão

functions:

* String f(Int x) { ... }

data:

* record Point(Int x, Int y)

collections:

* listOf
* mapOf
* setOf

membership:

* setOf("A", "B").contains(x)

strings:

* a == b
* a + "!"

errors:

* throw "msg"
* catch (String e)

null:

* String?
* if (x != null)

concurrency:

* spawn
* await

loops:

* for (var x in xs)

top_level:

* functions
* classes

rule:

* se parece Java, repense
* se a sintaxe for incerta, compile
* se Kof consegue expressar, escreva em Kof
* se não consegue, adicione o menor mecanismo
* nunca entregue sem prova
