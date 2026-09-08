# Plano Estratégico — Kof como Plataforma Universal

**Tipo:** visão de longo prazo / arquitetura futura (NÃO é ordem de implementação)
**Data:** 2 de setembro de 2026
**Base:** estado real 0.2.6-beta — frontend próprio (lexer, parser, AST, symbol
table, semantic, type checking), Kof IR backend-agnóstica, 7 targets
(jvm estável, native x86_64 estável, native.risc/native.arm toolchain+qemu,
js alpha GraalJS, kofc native-only, android Fase 1), stdlib como **tabelas de
dispatch em compile-time** com gaps diagnosticados, FFI real (SQLite `.so`
direto, FFM Vulkan compute, interop Java + GraalJS), `mvn test` 810.

> **Regra deste documento:** este é um exercício de planejamento estratégico
> e arquitetura futura. Ele NÃO altera, interrompe, reorganiza ou substitui o
> trabalho em andamento. Não implementa nada, não cria código de demonstração,
> não altera o roadmap atual, não move arquivos, não introduz dependências, não
> refatora, não abre frente nova. O estado atual do Kof permanece 100% intacto.
> Tudo que abaixo exigir mudança profunda no core é registrado como
> **dependência arquitetural futura**, nunca como ação.

Referências (não alteradas): `docs/roadmap.md` (visão), `docs/philosophy.md`
(intenção), `docs/architecture.md` (ADR multi-target),
`docs/ecosystem-coverage.md` (matriz de capacidades), `docs/stdlib.md`
(mecanismo de dispatch), `docs/plan-platform-completion.md` (execução atual).

---

## 0. A pergunta central

> *Se o Kof continuar evoluindo por anos, como transformá-lo de uma linguagem
> de programação em uma plataforma universal para software, sistemas,
> infraestrutura, automação, dados, segurança e ciência — sem destruir a
> simplicidade e a identidade da linguagem?*

A resposta, em uma frase: **a linguagem continua sendo uma; o que cresce é a
profundidade da stdlib e do ecossistema, e a especialização acontece em
bibliotecas/APIs/tooling — nunca em novos targets.** O Kof já tem, no estado
atual, os três ingredientes que tornam isso possível sem reescrever o core:

1. **Um frontend único + uma IR backend-agnóstica + backends plugáveis**
   (o substrato que isola a semântica do mecanismo).
2. **A stdlib como tabelas de dispatch em compile-time** com gaps
   diagnosticados (o mecanismo pelo qual um novo domínio entra como
   *nova tabela + novos runtimes*, e não como novo target).
3. **FFI real + interop (Java, GraalJS, `.so`)** (o que impede a
   reimplementação do mundo inteiro — "Kof não precisa possuir tudo;
   precisa conseguir integrar tudo").

Todo o restante deste documento é consequência desses três fatos.

---

# 1. Executive Vision

Kof evolui de *linguagem geral de aplicações* para **plataforma universal de
computação aplicada**: a mesma linguagem, o mesmo compilador, a mesma
semântica, os mesmos targets — usada para construir aplicações **e** para
automatizar infraestrutura, operar sistemas, analisar dados, executar pesquisa
científica, trabalhar com segurança e orquestrar pipelines complexos.

A ambição não é criar "KofDevOps", "KofData", "KofBio" (linguagens à parte).
É exatamente o contrário: **uma única linguagem** cuja capacidade cresce pela
quantidade e profundidade da stdlib e do ecossistema:

```text
                    KOF  (uma linguagem, um compilador, uma IR, targets JVM/Native/JS)
                     │
        ┌────────────┴────────────┐
        │                         │
      Core (pequeno)         Standard Library (a plataforma)
                                  │
          ┌──────────┬────────────┼────────────┐
          │          │            │            │
        Systems    Data       Security     Science
          │          │            │            │
       Infra       DataFrame    Crypto       Math
       IaC         ETL          Network      Biology
       Linux       Pipelines    Forensics    Chemistry
       CI/CD       ML/AI        Identity     Simulation
       Containers               (defensiva)  HPC
```

Os **targets continuam sendo mecanismos de execução** (JVM, Native, JS). A
especialização é feita por **bibliotecas, APIs, runtimes e tooling** — cada um
expresso com as construções normais da linguagem (records, classes, funções,
loops, concorrência, FFI) e, quando ajuda, por uma **sintaxe de intenção**
nova (como `entity` hoje, como `test "nome" { }` hoje) que o compilador
reduz a código normal.

A distinção que governa tudo:

> **A linguagem core continua pequena. A plataforma pode ser enorme.**
> **Capacidade universal ≠ linguagem inchada.** (ver §13 deste doc)

E o aprofundamento segue a regra da interoperabilidade:

> **Kof não precisa possuir tudo. Kof precisa conseguir integrar tudo.**
> O ecossistema científico, as nuvens, o Python/R, os drivers, as ferramentas
> existentes são *consumidos* por FFI/interop — não reimplementados.

---

# 2. Long-Term Philosophy

Princípios que devem guiar a evolução (todos já vigentes no projeto; aqui são
reafirmados como **invariantes** contra o crescimento):

1. **Uma linguagem, uma semântica, vários targets.** A intenção é única; o
   mecanismo muda por target. Nenhuma capacidade de domínio cria novo target.
2. **Intenção → Kof → compilador → backend.** O código expressa *o que*; a
   plataforma decide *como*. Se o programador precisa conhecer o mecanismo
   para escrever a intenção, o design falhou.
3. **Nunca silencioso.** Quando um target não realiza uma capacidade, o
   compilador diz em compile-time com um código de gap (padrão existente:
   `SECN00x`, `CONC00x`, `DB001`, `WEB002`, `HTTP002`...). Gaps de domínio
   seguem o mesmo padrão (`INFRA00x`, `DATA00x`, `SCI00x`, `BIO00x`).
4. **Compile-time > runtime magic.** O que o compilador pode conhecer
   (schema, validação, rotas, config, grafo de infraestrutura, stubs) é
   conhecido em compile-time. Reflection e discovery em runtime são
   *interop*, não fundação.
5. **Integrar, não reimplementar.** Kof consome o ecossistema existente
   (Java, C/C++/Rust, Python, R, Arrow, BLAS/LAPACK, CUDA, drivers, CLIs,
   REST/gRPC) via FFI/interop. Reimplementar o mundo inteiro é o risco nº 1.
6. **API pequena por domínio.** Cada namespace nasce com poucas funções
   úteis e bem escolhidas (regra vigente: ≤ ~10). Cresce por composição, não
   por acúmulo.
7. **Escalonamento por camadas de confiança.** core → stdlib → pacotes
   oficiais → ecossistema → externo (interop). Cada camada tem garantias e
   responsabilidades diferentes (ver §5).
8. **Estabilidade do core > velocidade do domínio.** O core é lento e
   estável; os domínios evoluem rápido. Um domínio novo nunca pode exigir
   mudança de semântica do core.
9. **Multi-target honesto.** Capacidades pesadas nascem onde a plataforma já
   é forte (JVM/Native) e ganham paridade progressiva; o que falta em JS é
   gap diagnosticado, não silêncios nem promessas.
10. **Segurança e correção científica por padrão.** Em segurança: default
    seguro, constante de tempo, formatos versionados (padrão vigente do
    `kof.security`). Em ciência: correção numérica e determinismo são
    requisitos de aceite, não "best effort".

---

# 3. Architectural Model

## 3.1 A hierarquia (model mental)

```text
Kof Core  (linguagem + compilador + runtime + tooling — PEQUENO, ESTÁVEL)
   │
   ├── Language      (sintaxe, tipos, controle, classes/records, generics,
   │                  lambdas, exceptions, concorrência spawn/await, IO básico)
   ├── Compiler      (frontend único → Kof IR → backends plugáveis)
   ├── Runtime       (JVM gerado / Native asm / JS mjs; allocator; GC; threads)
   └── Tooling       (CLI, LSP, formatter, test runner, bench, debug, packager)
          │
          ▼
     Kof Platform  (a stdlib — "a plataforma", cresce por namespaces)
          │
   ┌──────┼──────────┬───────────┬──────────────────┐
   ▼      ▼          ▼           ▼                  ▼
 Systems  Infra     Data      Security           Science
   │      │          │           │                  │
   └──────┴──────────┴───────────┴──────────────────┘
                     │
                     ▼
        Ecossistema (pacotes oficiais → comunidade → externo/interop)
```

A linguagem continua sendo uma. A seta vertical é **confiança**: o que está
em cima é mais estável e mais universal; o que está embaixo é mais específico,
evolui mais rápido e pode ser *desligado* (package opcional, interop externo).

## 3.2 Como um domínio novo entra (o mecanismo já existente)

Este é o ponto mais importante do modelo. Kof **não inventa nada novo** para
adicionar infraestrutura, dados, segurança ou ciência: reusa exatamente o
mecanismo que já entrega `kof.web`, `kof.security`, `kof.db` hoje.

```text
Intenção Kof  (ex.: infra "prod" { ... }  |  df.filter { ... }  |  scan("host"))
   ↓
SemanticAnalyzer   → tipos da chamada
   ↓
CompilerDriver     → lowering para KofCall("kof_*")   [tabela de dispatch do domínio]
   ↓
   ├── JvmRuntime    (Java/JVM: FFM, JNI, libs Java, Arrow JNI, CUDA-on-JVM)
   ├── NativeRuntime (asm x86-64: syscalls, .so direto, FFI, SIMD, pthread)
   └── JsBackend     (kof-runtime.mjs + kof_platform; GraalJS interop)
   ↓
gaps de target → diagnóstico em compile-time (INFRA00x, DATA00x, SCI00x, BIO00x)
```

Cada domínio é, portanto: **uma (ou poucas) tabelas de dispatch + as
implementações de runtime por target + um bloco de gaps documentado.** Não é
um novo target, não é um novo compilador, não é uma nova linguagem. É a
mesma arquitetura que já existe — o que muda é *quantas* tabelas e runtimes
existem.

Consequência direta: **a superfície de cada target é sempre um subconjunto
honesto e diagnosticado da superfície de cada domínio.** Isso é o que impede a
plataforma de virar um "tudo-faz" opaco: o que falta é sempre visível.

## 3.3 Onde vive cada coisa

| Camada | O que é | Exemplos hoje | Exemplos futuros |
|--------|---------|---------------|------------------|
| **Core** (linguagem) | Sintaxe, tipos, controle, abstrações, concorrência, IO mínimo | classes, records, generics, `spawn`/`await`, `try/catch`, `for-in` | (quase nada novo — ver §16) |
| **Stdlib base** (sempre ligada, pequena) | `kof.core`, `kof.collections`, `kof.io`, `kof.time`, `kof.json` | idem | idem (estável) |
| **Plataforma** (namespaces, o "Kof Platform") | `web`, `http`, `db`, `orm`, `security`, `config`, `log`, `observability`, `concurrent`, `cache`, `mq`, `validation`, `process`, `ui`, `test` | idem | + `infra`, `cloud`, `shell`, `ssh`, `data`/`dataframe`, `sci`/`math`, `bio` |
| **Pacotes oficiais** (opcionais, gerenciados) | Domínios pesados/focados | (nenhum ainda — `kofdeps` planejado) | `kof-infra-aws`, `kof-dataframe-parquet`, `kof-ml`, `kof-crypto-advanced`, `kof-bio`, `kof-hpc` |
| **Ecossistema** (comunidade) | Terceiros via registry | (planejado) | qualquer domínio |
| **Externo / interop** (NÃO é Kof) | JVM libs, `.so` C/C++/Rust, Python/R, REST/gRPC, CLIs, nuvens, bancos | Java interop, SQLite `.so`, FFM Vulkan, GraalJS, JDBC drivers, MongoDB driver | BLAS/LAPACK, CUDA, Arrow, Terraform/cloud APIs, NGS tools, HPC libs |

## 3.4 A fronteira (o que entra onde) — regra de decisão

Para qualquer capacidade candidata, a pergunta **na ordem**:

1. **Todo programa precisa e é pequeno?** → stdlib base (raro).
2. **É essencial para a plataforma e pequeno, mas opcional por programa?**
   → namespace da plataforma (o padrão vigente: `kof.web`, `kof.db`).
3. **É um domínio específico/pesado ou de nicho?** → **pacote oficial**
   (opcional, gerenciado; ex.: `kof-ml`, `kof-bio`). *Nunca* vira stdlib base.
4. **Já existe e é melhor por fora (Python, C, CUDA, driver, cloud API)?**
   → **interop/FFI**, não reimplementação.
5. **Kof não deve fazer isso?** → **non-goal** (ver §12).

Essa ordem é o mecanismo anti-"god language" (detalhado em §13).

---

# 4. Domain Expansion

Análise individual. Cada entrada declara: o que é, **o que o Kof já tem**, o
que falta, o veredito declarativo-vs-imperativo (quando aplicável), o
mecanismo recomendado, a estratégia de interop, e **o que NÃO fazer**.

> Convenção de estado usada em todas as seções (detalhada em §15.2):
> **A** já suportado · **B** suportado com pequenas extensões · **C** requer
> evolução arquitetural · **D** requer pesquisa · **E** provavelmente não vale
> a pena (ou é non-goal).

## 4.1 DevOps (operações, flujos de deploy, controle de sistemas)

**O que é:** automatizar o ciclo de vida de um sistema — provisionar,
configurar, deployar, monitorar, remediar — com a mesma linguagem.

**O que o Kof já tem (estado real):**
- `kof.process` (spawn de processos externos, stdin/stdout vivos) — **A**.
- `kof.web` + `kof.http` (APIs, retry/circuit, WebSocket/SSE, TLS) — **A**.
- `kof.db`/`kof.orm` (estado, migrações) — **A**.
- `kof.config`/`kof.log`/`kof.observability` (config typed, logging, health/metrics/request IDs) — **A**.
- `kof.concurrent` (`spawn`/`await`, `channel`, `scheduler.every/at`, `selectAny`, `cancel`) — **A**.
- `kof.security` (auth, JWT, rate limit, sessions, API keys) — **A**.
- `kof.test` + golden + `kof bench` (verificação contínua) — **A**.
- FFI/interop (JVM, `.so`, GraalJS) e tooling de distribuição (CLI, packager) — **A**.

**O que falta (o "DevOps" como domínio unificado):** orquestrar *várias*
fontes de estado (fichiers, processos, API de nuvem, k8s) num **grafo com
plan/apply/reconcile** — hoje isso é "B/C": as peças existem, o *modelo de
reconciliação declarativa* não.

**Mecanismo recomendado:** um namespace `kof.infra` (plataforma) construído
*por cima* do que já existe: `record`s para o estado desejado, funções para
"lêr o estado atual" (via `kof.http`/`kof.process`/FFI), um **grafo de
dependências** e um **loop de reconciliation** (`spawn`/`await` já fornecem a
concurrencia; `channel` a comunicação). O *plan* é um diff entre estado
desejado e estado atual — **computável em Kof normal** (dados + funções).
O *apply* é uma chamada a provider (FFI/REST/CLI). **Nada disso exige mudança
no core** — é biblioteca + tooling. **C** apenas no sentido de *novo namespace
+ novo tool* (`kof infra plan/apply`), não no sentido de linguagem.

**O que NÃO fazer:** não copiar o modelo de *provider plugins* do Terraform
como fundação; não criar um "KofAnsible" com inventories YAML à parte; não
transformar o `kof` em um shell. O valor do Kof aqui é *tipagem + concorrente
+ unificado*, não *mais um DSL de provisionamento*.

## 4.2 Kof Makealive — Infrastructure as Code (o substituto conceitual do Terraform)

**Nome do domínio:** **Kof Makealive** — a forma como a Kof expressa
infraestrutura como código tipado. (O nome é intencional: o código *dá vida* ao
estado desejado da infraestrutura — o plan "acorda" o mundo para o estado
declarado.)

**O que é:** infraestrutura expressa como dados + código tipado, com
idempotência, state, plan/diff, reconciliation.

**Veredito declarativo vs imperativo — a decisão central:**
Kof **suporta os dois** com a mesma semântica, e a recomendação é:

- **O modelo canônico é imperativo-tornado-dados** (a forma que o prompt
  mostra como alternativa): `let production = Infrastructure("production")`,
  depois `production.network(...)`, `production.database(...)`. Isso é Kof
  **puro hoje** (classes, records, funções, loops, condições, testes) —
  **A/B** — e é onde a linguagem brilha (tipos, abstrações, LSP, análise
  estática).
- **A forma declarativa** (`infra "prod" { network "main" { ... } }`) é uma
  **sintaxe de intenção** (como `entity` e `test "nome" { }` são hoje), que o
  compilador **reduz a construtores/records** — *não* a uma linguagem de HCL.
  É **C** (novo bloco de parsing + lowering), mas *reutiliza* o mecanismo de
  desugaring já existente para `entity`/`test`. Não deve virar uma mini-DSL
  com semântica própria: deve ser **sacar sobre records tipados**.

**Por que isso elimina classes de problemas das DSLs de infra:** porque o
"estado desejado" vira **dados tipados de primeira classe** — dá para validar
em compile-time, dar LSP, testar, parametrizar com funções/loops, versionar
com git, e diffar com o mesmo código. O HCL tradicional sofre com: tipos
fracos, sem LSP real, testes fracos, dificuldade de abstração. Kof resolve por
ser *a própria linguagem*.

**Pipeline (todo ele é runtime/tooling, não linguagem):**

```text
Kof source (infra declarativa OU imperativo sobre records)
   ↓ semantic analysis
   ↓ infraestrutura como grafo (dados tipados — IR de domínio)
   ↓ plan (diff estado atual vs desejado)      [Kof normal: funções + FFI]
   ↓ approval (tool: `kof infra plan --review`)
   ↓ apply (provider via FFI/REST/CLI — spawn/await)
   ↓ state (persistido — kof.db/kof.io)
   ↓ reconciliation (loop: spawn/await + channel; idempotência por construção)
```

**Mecanismo:** `record`s de recurso + `map` de dependências + **grafo acíclico
detetado em compile-time** (o compilador já faz análise; detectar ciclo de
dependência de recursos é trivial nesse modelo) + provider como *interface*
(cada cloud é uma implementação; o **AWS/Azure/GCP/OpenStack é interop/FFI**,
não reimplementação).

**Idempotência/state/secrets:** idempotência por *declaração do estado
desejado* (o apply convergente é idempotente por construção); state em
`kof.db` (ou JSON em `kof.io`); secrets via `kof.security.secrets` (já existe:
`KOF_*`, redaction) + KMS por FFI/interop. **B** (peças existem; o loop de
reconciliation é **C**).

**O que NÃO fazer:** não implementar HCL *dentro* do Kof; não criar um
repositório de "KofProviders" para *todas* as nuvens; não prometer
substituir Terraform no primeiro dia — entregar um **subconjunto tipado e
unificado** que cobre o caso real do time, e deixar o resto em interop.

## 4.3 Automação (scripts, shell, jobs, pipelines, CI/CD)

**O que é:** substituir a pilha fragmentada
(`Bash + Python + YAML + JSON + Terraform + Ansible + jq + sed + awk`) por uma
camada unificada e tipada.

**O que o Kof já tem (forte aqui):** `kof.process` (executar comandos, pipes,
stdin/stdout), `kof.io` (filesystem), `kof.http`, `kof.json`, `kof.config`,
`kof.time`/`scheduler` (cron-like `at("0 3 * * *")`), `spawn`/`channel`
(workers/filas), `kof.test`, `KofScript` (`kof script --watch`, REPL), FFI/CLI.
Isso cobre grande parte da "automação de máquinas" — **A/B**.

**O que falta:** um *modelo de job/pipeline* declarativo e um *executor*
compartilhado (fila de jobs, retry, dead-letter, checkpoints) — hoje há
`kof.mq` (pub/sub, **A**) e `scheduler` (**A**), mas não um **batch/pipeline
framework** (ver §3.8 do `ecosystem-coverage.md`: `PLANNED`). **C** (namespace
`kof.workflow`/`kof.batch`).

**Veredito:** automação é onde Kof tem **maior aderência natural** — o
"paradigma da intenção" (escrever `spawn`, `spawn { ... }`, `http.post`,
`Path(...).writeText` em vez de `bash`/`curl`/`jq`) já é exatamente o
anti-fragmento de scripts. A camada unificada é **B/C**: namespace + executor,
sobre primitivas existentes.

**O que NÃO fazer:** não reimplementar o bash (Kof **pode** orquestrá-lo via
`kof.process`); não criar um "YAML de jobs" separado — os jobs são **código
Kof** (dados + funções), testáveis e tipados. CI/CD: o *runner* é tooling; o
*pipeline* é código Kof (ex.: um job que compila, testa, publica — tudo com
as mesmas funções).

## 4.4 Cloud

**O que é:** provisionar e operar recursos de nuvem.

**Estratégia: 100% interop/FFI, zero reimplementação.** Kof não reescreve
provider SDKs. Consome: (a) via **JVM** — SDKs Java (AWS SDK etc.) por interop
direto; (b) via **CLI** — `kof.process` chamando `aws`/`gcloud`/`az`
(robusto, sem dependência); (c) via **REST** — `kof.http` + assinatura de
request (a camada de assinatura é a única coisa "novo" e pequena). **A/B**.

**O que entra no Kof:** apenas a **camada de abstração tipada** (records +
funções + `kof.infra`) que *orquestra* os provedores; a semântica de nuvem
fica fora. `kof.cloud`/`kof-infra-<provider>` como **pacotes oficiais**
(opcionais) — não stdlib base. **B** (abstração) + **A** (transporte via
`kof.http`/`kof.process`).

**O que NÃO fazer:** não criar "SDKs de nuvem em Kof"; não prometer paridade
com todos os recursos de todas as nuvens; não acoplar o core a um provedor.

## 4.5 Data Engineering (ETL, pipelines, streaming, formatos)

**O que é:** mover, transformar e persistir dados em escala.

**O que o Kof já tem:** `kof.json` (completo), `kof.io`, `kof.db`/`kof.orm`
(SQL, migrações, MongoDB), `List map/filter/reduce` (transformações),
`spawn`/`channel` (paralelismo de pipelines), `kof.mq` (pub/sub/streaming
básico), `kof.http` (ingestão via API). **A/B**.

**O que falta:** **DataFrame** tipado (colunas, lazy ops, particionamento),
formatos colunares (**Parquet/Arrow**), e um **engine de pipeline**
(checkpointing, replay, backpressure). **C/D**:
- DataFrame como **namespace** (`kof.data`/`dataframe`) — **C** (novo tipo de
  coleção + otimização).
- **Parquet/Arrow** — **A via interop**: Arrow tem bindings (JNI na JVM; `.so`
  no native). Kof **não reimplementa** Arrow; **conecta** a um Arrow/Parquet
  existente por FFI, expondo um wrapper tipado. (Isso é a regra do §11.)
- **Streaming** — `spawn`/`channel` já dão o esqueleto; o "engine" é
  biblioteca. **C**.

**Estratégia de interop (decisiva):** dados em Arrow (memória colunar
trocável) é o **padrão de troca** com Python/JVM/nativos. Kof define *o wrapper
tipado* e *o pipeline*; os bytes colunares são Arrow. Isso evita reinventar o
que o ecossistema científico já resolveu.

**O que NÃO fazer:** não reimplementar Parquet/Arrow do zero; não criar um
"SQL engine próprio" — `kof.db` já é SQL-first e o escopo é *orquestrar*
bancos, não *ser* um banco.

## 4.6 Data Science

**O que é:** análise estatística, modelagem, exploração, visualização.

**O que o Kof já tem:** `List map/filter/reduce`, `Map/Set`, `kof.io`,
`kof.json`, concorrência, testes, UI (plot básico via `kof.ui`? — hoje não;
ver gap). **A** (base de programação) / **D** (ciência).

**Estratégia: Kof *orquestra* o ecossistema científico, não o substitui.**
- **Estatística/probabilidade** — wrapper tipado sobre bibliotecas existentes
  (JVM tem stats; ou FFI a C/Fortran). **C** (namespace) + **A** (FFI).
- **Visualização** — gerar SVG/plot por FFI (ex.: libs de plotting) ou por
  `kof.ui` (DOM/SVG) como caminho leve; não criar um "matplotlib em Kof".
  **C/D**.
- **Notebooks/exploração** — `KofScript`/REPL já dão um "caderno" minimalista;
  o notebook *rich* é **E** (non-goal — é ferramenta de editor, não da
  linguagem). **B** (REPL) / **E** (notebook rich).

**O que NÃO fazer:** não prometer paridade com NumPy/Pandas/SciPy no primeiro
dia; não reimplementar álgebra linear; a entrada do Kof em data science é
**pipelines tipados + interop com o ecossistente**, não "SciPy em Kof".

## 4.7 Machine Learning

**O que é:** tensores, modelos, treinamento, inferência, métricas, pipelines.

**Estado real:** nada no Kof hoje (ver `ecosystem-coverage.md` §3.14: `PLANNED`,
decisão stdlib-vs-externo adiada à P3). **D** (pesquisa) — e a maioria é
**A via interop**.

**Estratégia (a mais "interop-first" de todas):**
- **Tensores / autograd / CUDA** — **A via FFI/interop**: Kof *não* reimplementa
  um framework de deep learning. Consome: (a) **JVM** — ONNX Runtime,
  TensorFlow/PyTorch via Java; (b) **Native** — `.so` (libtorch, onnxruntime,
  cuDNN/CUDA por FFI); (c) o wrapper Kof é **tipado e leve**
  (`model.infer(x)` → FFI). O *peso* fica no ecossistema; o *controle* fica no
  Kof.
- **Inferência** — **B**: `record` de input + chamada FFI + `kof.json` para I/O.
  Serve-se com `kof.web` (já existe HTTP/WebSocket/SSE).
- **Treinamento** — **D/A**: orquestrado (scripts Kof que chamam o trainer por
  FFI/CLI), não reimplementado. HPC/GPU é o gargalo (§4.11).
- **Experiment tracking** — **C**: namespace leve sobre `kof.db` + `kof.io`
  (registra métricas/artefatos). Pequeno e tipado.

**O que NÃO fazer:** **não construir um framework de ML em Kof** (isso é o
caminho para a god-language). Kof entra como **a camada de orquestração e
integração tipada** por cima de ONNX/PyTorch/libtorch/CUDA. O ganho de Kof:
*mesma linguagem* para o pipeline de dados, o deploy, o monitoramento e o
serviço — não para o autograd.

## 4.8 Cybersecurity

**O que é:** criptografia, rede, automação de segurança, forense, segurança
defensiva. (Ofensiva apenas em contexto legítimo/controlado — ver §12.)

**O que o Kof já tem (já é um dos pontos mais fortes):** `kof.security`
completo — `passwords` (PBKDF2-HMAC-SHA256 600k), `crypto` (SHA-256/512,
HMAC, AES-GCM, random seguro — **nos 3 targets**, Native em asm puro),
`jwt` (HS256 fixo), `secrets` (env + redaction), `constantTimeEquals`,
rate limit, sessions, API keys. **A** — e com gaps diagnosticados
(`SECN00x`).

 **O que falta (expansão):**
 - **Criptografia avançada** — RSA/ECC/P-256, X.509/TLS completo, ChaCha20-Poly1305.
   **B** (JVM via `javax`; Native por FFI a libs; formato versionado já existe).
   **Evolução estratégica completa em §4.8.1** (camadas, PQC híbrido, key
   management, `SecureChannel`, threat model, roadmap por maturidade).
- **Networking / parsing de protocolos** — sockets existem (`kof_net_*` emitidos);
  *parse* de pacotes/DNS é **C** (namespace `kof.net`/`kof.packet` — FFI a
  libs como `libpcap` para captura).
- **Forense** — análise de filesystem/binário/memória: **C/D** — em grande parte
  **A via FFI** (libs de parse) + `kof.io` + pipelines Kof. Kof é a *camada de
  orquestração tipada*, não o parser de ELF/PE do zero.
- **Automação de segurança / threat intel** — **B**: `kof.http` (APIs de
  threat-intel) + `kof.json` + `spawn`/`channel` (scan distribuído) +
  `kof.log` (SIEM-like). Encaixa naturalmente no modelo de automação (§4.3).
- **Defensiva** — monitoring/detection/auditoria: **B** sobre `kof.observability`
  + `kof.log` (correlation IDs já existem) + `kof.db`.

**Princípio do domínio:** **defesa primeiro**; ofensiva somente em contexto
legítimo (auditoria, pentest autorizado, pesquisa) — e sempre como *ferramenta
orquestrada*, não como "Kof é um framework de ataque". (Reflete a postura do
`docs/security-plan.md`.)

 **O que NÃO fazer:** não reimplementar stacks cripto do zero quando há
 implementações auditadas (FFI a libs); não transformar Kof em "Kali em Kof";
 não expor primitivas ofensivas sem o contexto de controle.
 
 ## 4.8.1 Kof Security — Evolução Estratégica (camada de segurança moderna + pós-quântica)
 
 > **Status: planejamento/arquitetura — NÃO implementar nesta fase.** Este
 > bloco é auditoria + estratégia, não código. O `kof.security` atual permanece
 > **intacto** (6 namespaces, 3 targets, `KofSecurityTest` 25 + `KofSecurityG9Test`
 > 3). Segue o princípio do domínio: **criptografia extremamente complexa para
 > quem implementa a plataforma, extremamente difícil de usar incorretamente para
 > quem usa a linguagem.**
 
 **Filosofia invariante:** *complexidade criptográfica por dentro, API simples por
 fora.* O dev comum nunca vê nonce, IV, padding, KDF, provider, encoding,
 rotação. E — regra absoluta — **o Kof simplifica a *utilização* da criptografia,
 mas NUNCA reinventa criptografia**: toda primitiva nova é **FFI a uma
 implementação auditada** (JCA/JCE no JVM, `liboqs`/`Relic`/`libsodium` no
 Native, `SubtleCrypto` no JS), nunca algoritmo próprio.
 
 ### A. Estado atual (auditoria real — 0.2.6-beta, `KofSecurity.java`)
 
 6 namespaces de intenção, compilados pelo mesmo padrão de dispatch de
 `kof.io`/`kof.web` (`KofSecurity.staticMethod` → `kof_sec_*` → 3 runtimes):
 
 | Namespace | APIs hoje | JVM | Native x86_64/riscv64 | JS |
 |-----------|-----------|-----|----------------------|-----|
 | `passwords` | `hash`/`verify`/`needsRehash` (PBKDF2-HMAC-SHA256, 600k) | ✅ javax.crypto | ✅ **asm puro** (getrandom, FIPS) | ✅ platform |
 | `crypto` | `sha256`/`sha512`/`hmacSha256`/`encryptAesGcm`/`decryptAesGcm`/`randomHex`/`randomInt` | ✅ JCA | ✅ **asm** (FIPS 180-4, GCM, getrandom) | ✅ sha/hmac JS puro; ❌ **AES-GCM = SECN002** |
 | `jwt` | `create(claims,secret[,ttl])`/`verify(token,secret[,iss,aud])`/`secret()` — **HS256 fixo** | ✅ | ✅ asm (b64url+HMAC) | ✅ |
 | `secrets` | `get(name[,fallback])`/`redact` | ✅ env | ✅ `/proc/self/environ` | ✅ platform |
 | `security` | `constantTimeEquals`/`random*`/`redact`/`csrf*`/`corsAllowed`/`csp/hsts/nosniff/frame/referrerHeader`/`rateLimit`/`session*`/`apiKey*` | ✅ | ✅ ct/redact/random/rate/session/apiKey (asm); ❌ csrf/cors/headers | ✅ ct/redact/random/rate/session/apiKey; ❌ csrf/cors/headers |
 | `auth` (web) | `secret`/`token`/`authenticated`/`claims`/`user`/`hasRole`/`hasPermission` | ✅ Bearer JWT + ThreadLocal | ❌ JVM-only | ❌ JVM-only |
 
 **Formatos serializados (versionados, sem ambiguidade):**
 `pbkdf2$sha256$<iter>$<saltB64>$<hashB64>` · `aesgcm$<ivB64>$<ctB64>` (chave 32B,
 IV 12B) · JWT RFC 7519 **HS256 fixo** (o `alg` **nunca** é aceito do token —
 trava confusão de algoritmo, `KofSecurityTest.jwtRejectsAlgorithmConfusionJvm`).
 
 **Gaps reais** (nunca silencioso — `KofSecurity.supportedOn` + `gapCode`):
 `SECN002` (AES-GCM fora de JVM/Native), csrf/cors/headers + `auth.*` (JVM-only).
 
 ### B. Arquitetura atual
 
 ```text
 Kof (intenção) → SemanticAnalyzer (tipa) → KofSecurity.staticMethod
    → kof_sec_* (nome runtime) → [ supportedOn? gapCode → diagnóstico ]
        → JvmRuntime     (javax.crypto / JCA / SecureRandom / MessageDigest)
        → NativeRuntime  (asm sem libc: getrandom, FIPS 180-4, GCM, constant-time)
        → JsBackend      (JS puro sha/hmac/pbkdf2 + kof_platform; AES-GCM = gap)
 ```
 
 **Camada de provider implícita já existe** (JVM = JCA, Native = asm, JS =
 `SubtleCrypto`/platform). O que falta é *formalizá-la* e adicionar camadas
 (keys, PQC, channel, typed) por cima — ver §F.
 
 ### C. Pontos fortes (o que já está bem projetado — NÃO tocar)
 
 - **Seguro por padrão**: PBKDF2 600k, AES-**GCM** (autenticado, nunca CBC),
   constant-time em toda comparação de segredo, JWT HS256 fixo (sem alg-confusion).
 - **Formato versionado** em todo artefato serializado → evolução sem quebrar dados.
 - **Multi-target honesto**: primitivas em asm sem libc; gap vira diagnóstico, nunca
   stub divergente.
 - **Zero ceremony**: intenção em Kof, sem annotation/container/config XML.
 - **Cross-check de vetores**: SHA-256/512/HMAC idênticos nos 3 targets e batem com
   FIPS 180-4 / RFC 2104 (`KofSecurityTest`).
 
 ### D. Gaps (o que está ausente — ordenado por valor)
 
 1. **Assimétrica** — RSA/ECC/P-256 (chave, sign/verify): **não existe** (só HMAC simétrico).
 2. **Pós-quântico** — **zero** (sem ML-KEM/ML-DSA/Falcon). A ameaça *harvest-now-decrypt-later* é real para dados longevos.
 3. **Gestão de chaves** — só env `KOF_*` + `secrets.get`; sem ciclo de vida (gerar/derivar/rotacionar/armazenar/revogar) tipado.
 4. **KDF / envelope** — AES-GCM toma a chave "pronta" (hex); não há HKDF, nem *key encapsulation*, nem binding de contexto.
 5. **Comunicação segura (channel)** — TLS parcial: `app.listenSecure` só no JVM (self-signed via keytool); sem abstração de canal (negociação + KEM + KDF + crypto + auth em uma API).
 6. **Tipos de segurança** — `String` carrega senha, hash, chave, segredo: o sistema de tipos **não impede** trocar `PasswordHash` por `Password`.
 7. **ChaCha20-Poly1305** — segunda AEAD (edge/dispositivos sem acelerador AES).
 
 ### E. Riscos (técnicos e criptográficos)
 
 - **Cripto caseira** (o risco nº1 do roadmap): qualquer PQC/KDF/channel "feito à mão" em Kof/asm é catastrófico. **Mitigação: FFI a libs auditadas sempre.**
 - **Combinação arbitrária de primitivas** ≠ protocolo seguro: ML-KEM + AES-GCM só é seguro com KDF (HKDF) + binding de contexto + versionamento + replay. **Mitigação: abstração de *canal* com protocolo versionado, não primitives soltas.**
 - **Downgrade attack** no modelo híbrido (forçar o par clássico). **Mitigação: híbrido *obrigatório* (clássico + PQ juntos), versionamento no cabeçalho do artefato, recusa de versão desconhecida.**
 - **Nonce/IV reuse** (GCM = catastrófico se reutilizar IV sob a mesma chave). **Mitigação: IV 12B via `randomHex` (CSPRNG) + limite de uso por chave documentado; nunca IV derivado do contador sem proteção.**
 - **Vazamento de segredos em logs/memory** — `redact` existe mas não é *enforceado* por tipo. **Mitigação: tipo `Secret` (não-printável) + redaction automática no `kof.log`.**
 - **Provider/dependency compromise** (JCA backdoor, CVE em `liboqs`). **Mitigação:** §23 (processo de auditoria + pinned versions).
 
 ### F. Arquitetura futura proposta (modular — hipótese a validar)
 
 ```text
 kof.security
 ├── crypto          (primitivas — FFI, NUNCA próprio)
 │   ├── hash        sha256/512 (+sha3 via FFI)
 │   ├── symmetric   AES-GCM (pronto) · ChaCha20-Poly1305 (FFI libsodium)
 │   ├── asymmetric  RSA/ECC/P-256 sign/verify (FFI JCA / liboqs)
 │   ├── signature   (sobre asymmetric)
 │   └── postquantum ML-KEM-768 (KEM) · ML-DSA-65 (sig) · híbrido  (FFI liboqs)
 ├── keys            generate · import · export · derive(HKDF) · rotate · store · revoke
 ├── secrets         get · redact · (tipo `Secret` não-printável)
 ├── passwords       hash/verify/needsRehash (pronto; migração Argon2 opcional)
 ├── jwt             create/verify (pronto; JWS HS384/512 + RS256 opcional)
 ├── certificates    X.509/PEM/PKCS#12 (FFI; hoje ausente)
 ├── tls             app.listenSecure(port, cert, key) + canal cliente (FFI/SSLSocket)
 ├── auth            context web (pronto no JVM; estender p/ Native/JS)
 ├── identity        RBAC/ABAC via claims (pronto `hasRole/hasPermission`)
 ├── tokens          (jwt + session + apiKey — consolidar)
 └── secure          channel(...)  →  SecureChannel (KEM+KDF+AEAD+auth+replay, §J)
 ```
 
 **Avaliação da hipótese:** faz sentido **em camadas**, mas **não** criar todos os
 sub-namespaces de uma vez. O que *protege* a linguagem: manter os 6 namespaces
 atuais estáveis e **acrescentar** `keys`/`secure`/`postquantum` como extensão
 (padrão `entity`→`orm`), **não** reorganizar o que já existe. `certificates`/
 `identity`/`tokens` podem começar como **funções dentro** de namespaces existentes
 antes de virar sub-tree.
 
 ### G. Design da API (idiomática — proposta, decidir por segurança não por estética)
 
 Preferir a forma **funcional de intenção** (consistente com o restante da
 linguagem; rejeitar builder `Security.encryption(...).build()` — anti-idiomático):
 
 ```kof
 // simétrica segura por padrão (esconde IV/alg/provider — já existe)
 let ct = crypto.encryptAesGcm(data, key)
 let pt = crypto.decryptAesGcm(ct, key)

 // assimétrica (NOVA — FFI)
 let kp   = keys.generatePair("P-256")
 let sig  = kp.sign(data)
 if (kp.verify(data, sig)) { ... }

 // pós-quântico (NOVA — FFI liboqs)
 let ct2 = crypto.encryptHybrid(data, key)     // ML-KEM-768 + AES-256-GCM (HKDF)
 let pt2 = crypto.decryptHybrid(ct2, key)
 let sig = crypto.signPq(data, kp)             // ML-DSA-65

 // gestão de chaves (NOVA)
 let k   = keys.derive(masterKey, "app/2026/db")   // HKDF — nunca usar senha como chave
 k.rotate()

 // canal seguro (NOVA — esconde KEM/KDF/AEAD/auth/replay)
 let ch  = secure.channel(peer, profile)
 ch.send(payload)

 // passwords (pronto — manter)
 let h   = passwords.hash(pw)
 if (passwords.verify(pw, h)) { ... }
 ```
 
 **Decisões a fixar (não estética):**
 - **Default seguro**: `encrypt(...)` = AES-256-GCM; `encryptHybrid(...)` =
   ML-KEM-768 + AES-256-GCM. Nunca expor `Cipher("AES/CBC/...")` como default.
 - **APIs perigosas escondidas**: primitives de nível baixo (`Cipher`/`Mac`
   diretos) **não** entram na API pública — só via FFI explícito/escape hatch.
 - **Warnings em compile-time**: usar chave < 128 bits, IV curto, ECB → diagnóstico
   `SECD00x` (aviso), nunca silencioso.
 - **Tipos** (§L/§15 do prompt): `Secret`, `KeyHandle`, `Signature` impedem
   semântica errada (usar `Secret` onde `String` comum é esperado, etc.).
 
 ### H. Estratégia pós-quântica (análise — NUNCA implementar à mão)
 
 > **Regra: não existe PQC no Kof ainda. Toda PQC é FFI a uma lib auditada
 > (JVM: provider futuro/`liboqs`; Native: `liboqs`/`Relic`; JS: não há → gap).**
 
 **O que cada primitiva resolve (e o que NÃO resolve):**
 
 | Algoritmo | Problema | Garantias dadas | **NÃO** dá |
 |-----------|----------|-----------------|------------|
 | **ML-KEM-768** (KEM) | *key encapsulation* → segredo compartilhado | confidencialidade do segredo; CCA | **não autentica** o par; não é AEAD de payload |
 | **HKDF** (KDF) | segredo bruto → chaves derivadas | derivação segura, binding de contexto | não cifra; não autentica por si |
 | **AES-256-GCM** (AEAD) | cifrar o payload | confidencialidade + **integridade** | reuso de IV quebra tudo (mitigado §E) |
 | **ML-DSA-65** (sig) | assinatura autêntica | integridade + não-repúdio de artefato | não cifra; não dá forward secrecy |
 
 **Modelo conceitual (o que SIM se combina — com KDF e binding, não "apenas
 colar"):**
 
 ```text
 ML-KEM-768 (encaps)  →  segredo efêmero
        ↓  HKDF (salt + info=versão+contexto)
   chaves { enc, mac }  →  AES-256-GCM (payload)  →  AEAD autenticado
   [opcional] ML-DSA-65  →  assinatura do envelope (quem/qual versão)
 ```
 
 **Onde entra cada peça obrigatória (não assumir que combinar = seguro):**
 - **KDF**: obrigatório — KEM devolve segredo bruto; HKDF deriva chaves AEAD.
 - **Binding de contexto**: `info` do HKDF carrega **versão do protocolo** +
   identificadores das partes → previne *key-confusion* entre canais.
 - **Autenticação**: ML-KEM puro é anônimo → exigir **assinatura (ML-DSA) ou
   pré-compartilhado** para autenticação do par; documentar.
 - **Anti-replay**: em canal persistente, counter/nonce no cabeçalho do envelope.
 - **Versionamento**: cabeçalho do artefato `hyb1$<kem>$<kemCtB64>$<ctB64>` (segue
   o padrão `aesgcm$...`); versão desconhecida → **recusar**, não adivinhar.
 - **Híbrido obrigatório**: sempre clássico **e** PQ (defesa em profundidade contra
   bug em um dos dois); **downgrade** bloqueado (recusa de versão só-clássico).
 - **Rotação/compatibilidade**: KEM usa chaves efêmeras (forward secrecy do canal);
   chaves de assinatura rotacionáveis (chave-pública distribuída por banda fora).
 
 ### I. Gestão de chaves (ciclo de vida)
 
 Abstrações a avaliar (expor o mínimo): `KeyHandle` (opaca — **nunca** `String`
 para material sensível), `PublicKey`/`PrivateKey`/`KeyPair`, `MasterKey`.
 **Não expor** bytes crus de chave privada como `String`.
 - **generate**: CSPRNG (`randomHex`/JCA) + tamanho mínimo por algoritmo.
 - **derive**: HKDF a partir de master key (nunca senha→chave direta).
 - **store**: env `KOF_*` → arquivo `0600` → variante `keychain`/platform (JS);
   **redact automático** em qualquer log.
 - **rotate/revocation**: `keys.rotate` gera sucessora + janelas de dual-key
   (formato versionado permite dual-read); revogação por allow-list de `KeyHandle`.
 
 ### J. Comunicação segura (abstração futura — `SecureChannel`)
 
 Objetivo: dev **nunca** implementa key-exchange/KEM/KDF/encryption/auth/nonce/
 replay. Propriedades alvo: confidencialidade, integridade, autenticação,
 forward secrecy (via KEM efêmero), anti-replay, negociação de algoritmo **e**
 **versão**, rotação, binding de contexto. Proposta: `secure.channel(peer, profile)`
 devolve handle com `send/receive/close`; por baixo = §H (híbrido) + ML-DSA
 (auth) + HKDF (derivação) + AES-256-GCM (payload) + counter (replay). **Classif.:
 D (pesquisa) / C** — depende de FFI PQC + formalização do protocolo versionado.
 
 ### K. Estratégia multi-target (JVM / Native / JS)
 
 | Camada | JVM | Native | JS |
 |--------|-----|--------|-----|
 | simétrica (GCM) | JCA | asm (pronto) | `SubtleCrypto` (fechar **SECN002**) |
 | hash/HMAC | JCA | asm (pronto) | JS puro (pronto) |
 | assimétrica (ECC/RSA) | JCA/`KeyPair` | FFI `liboqs`/`openssl` | `SubtleCrypto` |
 | **PQC (ML-KEM/DSA)** | FFI `liboqs`/provider | FFI `liboqs` | **não há** → gap `SECPQ` (diagnóstico, nunca stub) |
 | canal/TLS | `SSLContext` (pronto) | FFI `openssl` | `fetch`/`wss` (host) |
 
 **Regra:** API Kof idêntica; quando um target **não** tem a capacidade, **falha
 claramente** (`SECPQ`/`SECN00x`) — **nunca** fallback silencioso para algoritmo
 fraco (ex.: nunca "PQ indisponível → usa só clássico" sem aviso).
 
 ### L. Threat model (resumo — Threat / Impact / Likelihood / Mitigação / Resíduo)
 
 | Ameaça | Impact | Likelihood | Mitigação | Resíduo |
 |--------|--------|-----------|-----------|---------|
 | Vazamento de chave em log | alto | med | tipo `Secret` + redact automático | baixo |
 | Nonce/IV reuse (GCM) | alto | baixo | IV via CSPRNG + limite doc | baixo |
 | Harvest-now-decrypt-later | alto (dados longevos) | med | **PQC híbrido** (§H) | med |
 | Replay em canal | med | med | counter/nonce + versão | baixo |
 | Downgrade (só-clássico) | med | med | híbrido obrigatório + recusa de versão | baixo |
 | Confusão de algoritmo (JWT) | alto | baixo | **HS256 fixo** (pronto) | baixo |
 | Timing attack (comparação) | med | baixo | `constantTimeEquals` (pronto) | baixo |
 | Weak randomness | alto | baixo | CSPRNG (getrandom/SecureRandom) | baixo |
 | Dependency/provider CVE | med | med | pinned + audit (§23) | med |
 
 ### M. Estratégia de teste (NUNCA "encrypt→decrypt→funcionou")
 
 - **Known-answer / vetores oficiais**: FIPS 180-4 (pronto), RFC 2104 (pronto);
   para PQC: **vetores oficiais do NIST** (ML-KEM-768 / ML-DSA-65) — interop.
 - **Interop**: artefato Kof↔lib externa (ex.: `openssl`/`liboqs` decifra o que o
   Kof cifrou) — essencial para PQC/assimétrica.
 - **Negative/adversarial**: tamper (pronto), chave errada, IV reutilizado,
   alg-confusion (pronto), versão desconhecida, token malformado (pronto).
 - **Property/fuzzing**: round-trip + invariantes de integridade (GCM falha em
   bit flip) sob input aleatório.
 - **Regression**: paridade de valor entre targets (já existe para hash/HMAC).
 
 ### N. Roadmap (por maturidade — **sem datas**; cada fase com objetivo/
 dependência/risco/critério de conclusão)
 
 ```text
 S1 SECURITY FOUNDATION   (base — quase pronto)
    default seguro (GCM/PBKDF2/constant-time/HS256-fixo) + vetores + redact
    → já A; concluir: fechar SECN002 (AES-GCM JS) + vetores adversariais
 S2 SAFE DEFAULTS / TIPOS
    tipo `Secret`/`KeyHandle` + redaction em kof.log + warnings SECD00x
    → depende S1; risco baixo; critério: API sem exposição de chave crua
 S3 KEY MANAGEMENT
    keys.generate/derive(HKDF)/rotate/store(0600)/revoke
    → depende S2; FFI JCA/openssl; critério: ciclo de vida tipado + rotação dual-key
 S4 ADVANCED CRYPTO
    assimétrica (ECC/RSA sign/verify) + ChaCha20-Poly1305 + X.509/PEM
    → depende S3 + FFI; critério: interop com openssl + vetores
 S5 POST-QUANTUM RESEARCH
    ML-KEM-768 + ML-DSA-65 (FFI liboqs) + vetores NIST + interop
    → depende S4 + FFI formal; risco alto (só FFI auditado); critério: vetores NIST verdes
 S6 HYBRID CRYPTOGRAPHY
    encryptHybrid (ML-KEM + HKDF + AES-256-GCM) + formato versionado + anti-downgrade
    → depende S5; critério: interop híbrido + bloqueio de downgrade
 S7 SECURE COMMUNICATION
    secure.channel (KEM+KDF+AEAD+auth+replay) + TLS completo multi-target
    → depende S6; classif D/C; critério: canal E2E + forward secrecy
 S8 MULTI-TARGET SECURITY
    paridade honesta (gap = diagnóstico, nunca stub fraco) + perf/streaming
    → depende S7; critério: matriz de target sem divergência silenciosa
 ```
 
 ### O. Prioridades (NOW / NEXT / LATER / RESEARCH / AVOID)
 
 **Classificação (mesma legenda A–E do §14.2):**
 
 | Item | Classif. | Justificativa (estado real) |
 |------|----------|------------------------------|
 | AES-GCM no JS (SECN002) | **B** | `SubtleCrypto` no browser/Node; fecha gap, sem mudar core |
 | `keys.*` (gerar/derivar/rotacionar) | **B/C** | sobre JCA/openssl (FFI); `KeyHandle` opaco é extensão de tipo |
 | tipo `Secret`/redaction forçada | **B** | type-system + `kof.log`; impede vazamento |
 | assimétrica ECC/RSA (sign/verify) | **B** | FFI JCA/openssl; formato versionado já existe |
 | X.509/PEM/PKCS#12 | **C** | FFI + novo sub-namespace; TLS completo depende |
 | **ML-KEM/ML-DSA (PQC)** | **D (FFI)** | **não há** hoje; FFI `liboqs` + vetores NIST; pesquisa de integração |
 | híbrido (KEM+HKDF+AEAD) | **D/C** | protocolo versionado; anti-downgrade; depende PQC |
 | `secure.channel` | **D** | KEM+KDF+AEAD+auth+replay; formalização de protocolo |
 | ChaCha20-Poly1305 | **B** | FFI libsodium/`SubtleCrypto` |
 | TLS completo multi-target | **C** | JVM pronto (self-signed); Native/JS via FFI/fetch |
 | "Kali em Kof" / ofensiva sem contexto | **AVOID** | não expor primitivas ofensivas sem controle |
 | algoritmo próprio (qualquer) | **AVOID** | **regra absoluta** — só FFI a lib auditada |
 
 **NOW** (sem pesquisa profunda, estende o que já existe): fechar **SECN002**
 (AES-GCM JS) · tipo `Secret` + redaction forçada · `keys.derive` (HKDF) +
 `keys.rotate`.
 **NEXT**: `keys.*` completo · assimétrica (ECC/RSA) · ChaCha20-Poly1305 ·
 X.509/PEM.
 **LATER**: TLS completo multi-target · `secure.channel`.
 **RESEARCH** (só FFI a lib auditada + vetores oficiais): **ML-KEM-768** ·
 **ML-DSA-65** · **híbrido** (KEM+HKDF+AEAD, anti-downgrade) · interop PQC.
 **AVOID**: inventar qualquer algoritmo · expor `Cipher`/primitives de baixo
 nível como default · PQC no JS sem lib (gap `SECPQ`, não stub) · downgrade
 silencioso · "Kali em Kof".
 
 ### Non-goals deste bloco (espelha §24)
 
 Não inventar algoritmo · não implementar todos os algoritmos existentes · não
 expor API perigosa como default · não reorganizar os 6 namespaces atuais (só
 **acrescentar** `keys`/`secure`/`postquantum`) · não fazer o core depender de
 segurança · não transformar `kof.security` em framework gigante sem modularização.
 
 ## 4.9 Scientific Computing (física, química, engenharia, numérica, HPC)

**O que é:** cálculo numérico, simulação, SIMD/GPU, paralelismo, HPC, sinais,
imagem, áudio, embarcados.

**O que o Kof já tem:** aritmética FP real no Native (XMM — `FLT001` fechado),
arrays, `List map/filter/reduce`, `spawn` (threads), FFI (**Vulkan compute via
FFM já funciona** — `JvmVkRuntime`, chain instance→device→pipeline validada),
`kof.io`, benchmarks. **A** (base) / **D** (HPC).

**O que falta (recursos necessários, classificados):**
- **SIMD / vectorização** — **D**: hoje há XMM para FP escalar; SIMD de dados
  no Native codegen é pesquisa. (JVM: JIT já vetoriza.)
- **GPU** — **D/A**: FFM Vulkan *já é a prova de conceito* (compute). Generalizar
  (CUDA, OpenCL, ou mais Vulkan) é FFI + pesquisa de codegen.
- **Multithreading / async** — **A** (spawn/await/channels); data-parallel é **D**.
- **Memory control** — Kof é **GC** (filosofia: o programador não gerencia
  memória). Para HPC crítico, a saída é **FFI a código C/C++/Rust** (escapar
  para a zona sem GC), *não* ownership/borrowing no core. **D** (FFI avançado)
  / **E** (ownership no core — não fazer).
- **FFI / interop** — **A** (ponto forte: SQLite `.so`, FFM, Java, GraalJS).
  Formalizar FFI como primeira classe é **C** e *de baixo custo no core*.
- **Distributed** — **D**: `spawn`/`channel` dão o modelo; MPI/paralelismo
  distribuído é interop (FFI a MPI) + bibliotecas.

**Estratégia:** Kof é **a linguagem de orquestração científica tipada** (o
"glue" entre bibliotecas numéricas, GPUs e pipelines) — **não** o repositório
de kernels numéricos. A álgebra linear é **FFI a BLAS/LAPACK** (JVM tem
bindings; Native por `.so`), não reimplementação.

**O que NÃO fazer:** não reimplementar BLAS/LAPACK/NumPy; não criar ownership
no core; não prometer HPC "nativo" no primeiro dia — o caminho honesto é
**Kof orquestra + FFI executa**.

## 4.10 Bioinformatics (biotecnologia)

**O que é:** processamento de sequências DNA/RNA, FASTA/FASTQ, alinhamento,
variantes, pipelines genômicos, automação de laboratório.

**O que o Kof já tem:** `List map/filter/reduce` (transformação de sequências),
`kof.io`, `spawn`/`channel` (pipelines paralelos), `kof.db`, `kof.test`, FFI.
**A** (base) / **D** (domínio).

**Estratégia (100% "pacote oficial + interop"):**
- **Formatos (FASTA/FASTQ/VCF/BAM)** — **C**: um **pacote oficial**
  (`kof-bio`) com `record`s tipados para leitura/escrita. *Pequeno e fechado*
  (formatos estáveis) — bom candidato a pacote, não a stdlib base.
- **Alinhamento / variantes / estatística genômica** — **A via FFI**: consome
  ferramentas existentes (BLAST/EMBOSS/`htslib` por FFI/CLI) — Kof **não**
  reimplementa alinhadores.
- **Pipelines genômicos** — **B/C**: exatamente o modelo de
  automação/pipeline (§4.3/§4.5): jobs tipados + `spawn`/`channel` +
  checkpoints. Kof brilha aqui (pipelines complexos, tipados, testáveis).
- **HPC** — via §4.9 (FFI + orquestração distribuída).
- **Lab automation** — **B**: orquestração de equipamentos via `kof.http`
  (REST) + `kof.process` (CLI/serial por FFI).

**O que NÃO fazer:** não transformar Kof em "linguagem de biologia"; não
reimplementar alinhadores/variant callers; o papel de Kof é **a plataforma
moderna e tipada para construir pipelines científicos** — o pesquisador escreve
o pipeline em Kof e conecta às ferramentas científicas existentes por FFI/CLI.

## 4.11 HPC (computação de alto desempenho)

**O que é:** paralelismo massivo, GPU, HPC, distribuído.

**Classificação:** **D** (pesquisa) em tudo que é "novo"; **A** (interop) no
que consome. Kof **não se torna** um runtime HPC — ela **orquestra** HPC.

**Recurso por recurso:**
- **SIMD** — **D** (codegen Native) / **A** (JVM JIT vetoriza).
- **GPU** — **D/A** (FFM Vulkan é a semente; CUDA/OpenCL por FFI).
- **Multithreading** — **A** (spawn/pthread/virtual threads).
- **Async** — **A** (spawn/await) / **D** (event-loop em Native; CONC003 em JS).
- **Memory control** — **E** (ownership no core — não fazer); **D** (FFI a C/Rust
  para a zona sem GC).
- **FFI** — **A/C** (formalizar FFI como primeira classe — o caminho HPC *mais
  importante* do Kof).
- **Vectorization** — **D**.
- **Distributed** — **D** (FFI a MPI + orquestração por Kof).

**O que NÃO fazer:** não competir com OpenMP/MPI/CUDA como *runtime* — Kof é a
**camada de controle tipada por cima**; o peso de computação fica nas libs.

---

# 5. Standard Library Strategy

A estratégia de stdlib **é** a defesa contra a god-language. O princípio:
**a linguagem core permanece pequena; a plataforma cresce; o crescimento
acontece em camadas com garantias diferentes.**

## 5.1 As cinco camadas (o que pertence onde)

| Camada | Critério de entrada | Ligação | Exemplos |
|--------|--------------------|---------|----------|
| **1. Core** (linguagem) | essencial a *quase todo* programa; mudança de semântica | sempre (é a linguagem) | types, controle, classes/records, `spawn`/`await`, exceptions, IO mínimo |
| **2. Stdlib base** | essencial à plataforma; pequeno; estável | sempre ligada | `kof.core`, `kof.collections`, `kof.io`, `kof.time`, `kof.json` |
| **3. Plataforma** (namespaces) | essencial ao *caso de uso da plataforma* (web, dados, segurança); opcional por programa | ligada quando usada (detecção de uso em compile-time — padrão do SQLite: *link só quando o programa usa*) | `web`, `http`, `db`, `orm`, `security`, `config`, `log`, `observability`, `concurrent`, `cache`, `mq`, `validation`, `process`, `ui`, `test` + futuros `infra`, `shell`, `ssh`, `data`, `sci`, `bio` |
| **4. Pacotes oficiais** | domínio específico/pesado; evolui rápido; opcional | gerenciada por package manager (não ligada por padrão) | `kof-infra-aws`, `kof-infra-az`, `kof-dataframe-parquet`, `kof-ml`, `kof-crypto-advanced`, `kof-bio`, `kof-hpc` |
| **5. Ecossistema + Externo** | terceiros; ou "já existe e é melhor por fora" | registry / FFI / interop | qualquer pacote; JVM libs; `.so` C/C++/Rust; Python/R; Arrow; BLAS/LAPACK; CUDA; nuvens; bancos |

**Regras de fronteira (a ordem do §3.4 é a lei):**
- Se vai para a **camada 2**, deve ser pequeno e estável (raro).
- Se é **domínio pesado**, vai para a **camada 4** (pacote), **nunca** para a
  camada 2. `ml`, `bio`, `hpc` são **pacotes oficiais**, não stdlib base.
- Se **já existe por fora e é melhor** (Arrow, BLAS, CUDA, drivers), é
  **camada 5 (interop)** — wrapper tipado no Kof, motor por fora.
- A **camada 3** cresce, mas cada namespace é **pequeno** (≤ ~10 funções) e
  **capability-gated** (gaps diagnosticados).

## 5.2 O que deve permanecer **fora** do Kof

- **Reimplementações do motor** de Arrow/Parquet/BLAS/LAPACK/CUDA/NumPy —
  fora (camada 5, interop). Kof dá o *wrapper*, não o *motor*.
- **Provider SDKs completos** de nuvens — fora (camada 5, via JVM/CLI/REST).
- **Alinhadores/variant callers** genômicos — fora (camada 5, FFI/CLI).
- **Frameworks de deep learning** (autograd) — fora (camada 5, FFI).
- **Notebooks rich, IDE, kernel** — fora (ferramentas de editor).
- **Shell** — fora (Kof *orquestra* o shell via `kof.process`).
- **Banco de dados** (motor) — fora (`kof.db` orquestra; não é um SGBD).

## 5.3 Mecanismos anti-inchaço (a "não God Language" operacional)

(Expandido em §13; aqui o resumo operacional da stdlib.)
1. **Modularização da stdlib** — namespaces independentes, sem dependência
   inversa (regra vigente: módulo baixo nunca depende de alto).
2. **Pacotes oficiais** — camada 4 com package manager (`kofdeps`/registry
   planejado), versionados, opcionais.
3. **Capability-based APIs / optional modules** — ligar *apenas o que o
   programa usa* (padrão já usado: SQLite/MySQL `.so` linkado só quando o DSN
   literal aparece em compile-time). Generalizar esse mecanismo para todos os
   pacotes.
4. **Dependency boundaries** — um pacote só importa o que declara; o compilador
   valida a fronteira (a análise estática já existe).
5. **Stable core / experimental APIs** — tiers de estabilidade: *stable*
   (garantia de compatibilidade) vs *experimental* (pode mudar). A camada 4
   nasce experimental.
6. **Versioning / compatibility guarantees** — semântica de versão por camada
   (core é semântica estrita; pacotes seguem semver). O `VERSION` central já é
   a base.

---

# 6. Interoperability Strategy

> **Kof não precisa possuir tudo. Kof precisa conseguir integrar tudo.**

A interoperabilidade é o **cercas** da plataforma universal — é o que impede o
reimplementar o mundo e o que dá escala ao ecossistema. Kof *já* tem FFI real
(SQLite `.so` direto; FFM Vulkan; interop Java; GraalJS; JDBC drivers;
MongoDB driver). A estratégia é **formalizar e generalizar** isso.

## 6.1 Superfícies de interop (o que consumir e como)

| Alvo | Mecanismo | Estado | Uso na plataforma |
|------|-----------|--------|-------------------|
| **Java / JVM libs** | interop direta (bytecode compatível) | **A** | nuvens (AWS SDK), Arrow (JNI), ONNX, ML, HPC via JVM |
| **Native libs (C/C++)** | link `.so` direto (padrão SQLite) + FFI | **A** | BLAS/LAPACK, `libpcap`, `htslib`, parsers, CUDA |
| **Rust** | link `.so` / FFI (C ABI) | **A** | libs científicas, HPC |
| **Python** | subprocess/CLI por FFI (`kof.process`) + protocolo (REPL/JSON) | **B** | ecossistema científico (PyTorch, BioPython) como *tool*; não como dependência |
| **R** | subprocess/CLI por FFI | **B** | estatística/bio |
| **JavaScript** | GraalJS interop (`kof_platform`) | **A** | web/browser |
| **WebAssembly** | target futuro / interop | **D** | portabilidade de componentes |
| **REST** | `kof.http` (existe, retry/circuit) | **A** | nuvens, APIs, threat-intel, lab |
| **gRPC** | planejado (`app.grpc`, codegen `.proto`) | **B/C** | microserviços, ML serving |
| **Bancos** | JDBC (JVM) + wire protocol (Native) | **A/B** | dados |
| **Cloud APIs** | REST + FFI (assinatura de request) | **B** | infraestrutura |
| **CLI tools** | `kof.process` (pipes, stdin/stdout) | **A** | automação, HPC, ciência (blast, samtools) |
| **OS APIs** | syscalls (Native) / FFI | **A** | sistemas |

## 6.2 Princípios

1. **Wrapper tipado, motor por fora.** Kof expõe a API *tipada e idiomática*
   (`model.infer(x)`, `arrow.table(...)`, `blas.gemm(...)`); o *motor* roda no
   ecossistema por FFI/interop.
2. **FFI como primeira classe.** Formalizar FFI (declarar assinatura,
   estruturar ponteiros/arrays, alinhar ABI) como construção de compile-time —
   *reduzindo* a zona de "asm manual" hoje. (Dependência arquitetural futura,
   §7/§13.)
3. **Escapamento para a zona sem GC.** Para HPC/numérico crítico, FFI a
   C/C++/Rust é o caminho (não ownership no core). Kof fornece a fronteira
   segura (buffer, lifetime pelo GC na fronteira).
4. **Dados por Arrow.** O formato colunar é o **padrão de troca** entre Kof e
   o ecossistema científico (JVM/native/Python).
5. **Nunca acoplar o core a um alvo de interop.** Cada alvo é *opțional* e
   *capability-gated* (se a lib não está, gap diagnosticado).

---

# 7. Compiler Requirements

Análise honesta de *o que seria necessário*, por quê, o custo, e **se de
verdade é necessário**. Regra: **nada por "linguagens grandes têm".**

| Capacidade | Necessária? | Por quê | Custo | Veredito |
|-----------|-------------|---------|-------|----------|
| **Macros / codegen** | **Parcialmente** | stubs gRPC, DDL de `entity`, DSL de `infra` (sacar sobre records), codegen de pipeline | alto se aberto; baixo se **codegen de compile-time** (padrão já usado: `KofRuntime` gerado, runner de teste sintetizado, DDL de `entity`) | **C** — *formalizar* uma camada de **codegen de compile-time** (já existe implicitamente); **rejeitar** macros abertas (quebram análise estática + LSP) |
| **Metaprogramming** | Não em aberto | mesmo que macros | alto | **E** (macross abertas) / **C** (codegen fechada) |
| **Compile-time evaluation** | **Sim (leve)** | const-folding de config, validação de esquema, detecção de ciclos no grafo de `infra`, vetores de teste crypto | baixo (otimizador já faz constant folding) | **B** — estender o *otimizador* a constantes de domínio; não um "TCC geral" |
| **Generics mais avançados / variance / sealed** | **Parcialmente** | coleções científicas, type-safety em pipelines, sealed para domínios | médio | **B/C** — variance/sealed úteis; **evitar** type-classes (ver abaixo) |
| **Type classes / protocols** | **Não** | Kof tem *interfaces* + *records* + FFI; type-classes adicionam poder sem resolver o que falta | alto (novo eixo do type system) | **E** — preferir **interfaces + FFI**; reavaliar só se um domínio científico real exigir |
| **Reflection** | **Parcialmente (alvo de interop)** | ML/ciência (descobrir schemas dinamicamente), interop Java | médio; contradiz "compile-time > runtime magic" | **C** — reflection **restrita a interop** (nada de fundação); o schema conhecido em compile-time continua a regra |
| **FFI (declarações de assinatura)** | **Sim** | a espinha dorsal de interop/ciência/HPC | **baixo no core** (é lowering + runtime, não semântica) | **C** — *formalizar FFI* como primeira classe (o item de maior valor/custo de todo o §7) |
| **Annotations / decorators** | **Não** | Kof rejeita annotation-driven magic (roadmap §16); `entity`/`test` já são construções de intenção sem annotation | — | **E** — manter a rejeição; construções de intenção > annotations |
| **Package capabilities** | **Sim** | a fronteira core/plataforma/pacotes (camadas do §5); ligar só o que se usa | baixo-médio (já há detecção de uso por DSN) | **C** — generalizar *capability/link por uso* para todos os pacotes |
| **Effect system** | **Pesquisa** | recursos (arquivos, GPU, conexões), reconciliation de infra, GPU | alto (novo eixo) | **D** — *investigar*; hoje GC + `spawn` + `try/finally` cobrem; provavelmente **scoped resources** (RAII-like leve) bastam — *não* um effect system completo |
| **Resource management (RAII/scoped)** | **Sim (leve)** | lidar com handles de FFI, arquivos, GPU, conexões sem vazar | baixo-médio | **B/C** — um `auto-closed`/scope leve (sem ownership) |
| **Ownership / borrowing** | **Não** | Kof é **GC** (filosofia: o programador não gerencia memória) | alto; muda a identidade | **E** — rejeitar; a zona sem GC é por FFI a C/Rust, não por ownership no core |
| **Async** | **Sim (estender)** | reconciliation de infra, HPC, event-loop | médio | **B/D** — `spawn`/`await` já existem; *event-loop* em Native é pesquisa (CONC003 hoje em JS) |
| **Parallelism (data-parallel)** | **Pesquisa** | HPC, vetorialização, MapReduce de dados | alto | **D** — *spawn* (task-parallel) existe; data-parallel/SIMD é pesquisa + FFI |

**Resumo:** o core **não precisa** de macros abertas, type-classes, annotations,
ownership ou effect system completo. O core **precisa** (baixo custo, alto
valor): **FFI formalizado**, **codegen de compile-time formalizada**,
**package capabilities**, **resource scoped leve**, **compile-time eval leve**,
e (médio custo) **variance/sealed** e **reflection de interop**. O resto é
pesquisa ou rejeição — registrado como dependência arquitetural futura, **não
implementado**.

---

# 8. Runtime Requirements

O que **JVM**, **Native** e **JS** precisariam suportar. Regra vigente:
**JVM primeiro, port depois**; gaps diagnosticados.

## 8.1 JVM (o alvo "full interop")

O JVM é onde a plataforma universal tem **maior poder de interop** (acesso a
todo o ecossistema Java + FFM).

- **O que já tem:** virtual threads (concorrência massiva e barata), FFM
  (Vulkan compute validado), interop Java completo, GC, TLS, WebSocket/SSE.
- **O que precisaria:** (a) **FFM formalizado** para BLAS/LAPACK/CUDA/Arrow
  (a semente já existe em `JvmVkRuntime`); (b) **HPC/ML via interop**
  (ONNX/libtorch/CUDA por FFM/JNI); (c) **infra** via SDKs Java/REST
  (sem novo runtime — `kof.http`/FFM já dão o transporte).
- **Veredito:** o JVM é a **base da plataforma universal** — a maioria das
  capacidades pesadas (ML, HPC, nuvens) chega **primeiro** aqui, por interop.
  Custo no core: baixo (é FFM/interop, não nova semântica).

## 8.2 Native (o alvo "deploy/edge/sistemas")

O Native é onde o Kof **deploya sem JVM** — startup rápido, memória baixa,
sistemas embarcados/edge, HPC local, forense.

- **O que já tem:** ELF x86_64, syscalls, `spawn` (pthread), FP real (XMM),
  GC free-list, SQLite `.so` direto, MySQL wire protocol, crypto em asm.
- **O que precisaria (classificado):** (a) **GC mark-sweep** completo (hoje
  free-list; auto-GC desligado) — **C** (necessário para pipelines longos);
  (b) **FFI formalizado** (declarar `.so` sem asm manual) — **C**;
  (c) **SIMD / vectorização** — **D**; (d) **event-loop/async** (hoje
  pthread bloqueante) — **D** (necessário para servidores HPC/edge);
  (e) **codegen RISC/ARM** (hoje placeholder via qemu) — **C** (já em
  andamento); (f) **GPU** (Vulkan por FFI; CUDA por FFI) — **D/A**.
- **Veredito:** o Native é o alvo dos **domínios de sistemas** (infra edge,
  forense, automação, embarcados). O caminho HPC/numérico é **FFI a
  C/C++/Rust** (zona sem GC), não reimplementação. Custo: médio-alto
  (GC mark-sweep + event-loop são os dois itens caros).

## 8.3 JS (o alvo "web/browser")

O JS é **alpha** e, na visão universal, é **o alvo web**, não o alvo de
domínios pesados.

- **O que já tem:** ES Modules (GraalJS), `kof.http` via `Java HttpClient`
  interop, UI, `spawn` sequencial.
- **O que precisaria:** *pouco dos domínios universais*. O que falta em JS é
  **gap diagnosticado** (padrão `DB001`, `WEB001`, `CONC003`). A
  recomendação é **não prometer paridade de JS** para ML/HPC/forense — esses
  são **JVM/Native**. JS entra no "universal" apenas para **o lado web/edge da
  aplicação** (UI, APIs leves, dados leves).
- **Veredito:** JS mantém o papel de **web/edge**; os domínios pesados são
  JVM/Native primeiro. Custo: baixo (não acelerar JS para HPC).

**Síntese:** a plataforma universal é **JVM-first para interop pesado,
Native-first para sistemas/deploy, JS para web** — com gaps honestos. Isso
preserva o multi-target sem promessas falsas.

---

# 9. Tooling Requirements

Regra vigente (deve manter): **não existe parser paralelo** — todo tooling
consome o **mesmo frontend** do compilador. Consequência: todo tooling novo
ganha **diagnostics de domínio de graça** (o LSP já publica o mesmo conjunto
de erros do `kof check`).

| Tool | Estado | O que precisaria para a plataforma universal |
|------|--------|---------------------------------------------|
| **CLI** | 18 comandos (build/run/serve/check/test/script/repl/c/fmt/config/bench/profile/inspect/debug/info/lsp/install/version) | + **`kof infra plan/apply/destroy`** (orquestração de infra — *tooling*, não linguagem); + **`kof workflow run`** (executar pipelines/jobs); + **`kof deploy`** (build + package + publicar — sobre o packager existente). *Todos consomem o frontend.* |
| **LSP** | mínimo (hover/completion + diagnostics) | + completion/diagnostics **sensíveis ao domínio** (recurso `infra`, `entity`, `df`); go-to-definition em pacotes oficiais; semantic tokens por domínio. *Mesmo frontend → sem parser paralelo.* |
| **Package manager** | planejado (`kof init`, `kofdeps`, registry) | **obrigatório** para as camadas 4/5 (pacotes oficiais + ecossistema): resolução, versionamento, **capability/link por uso**, audit. É o que permite a plataforma crescer sem inchar o core. (Dependência arquitetural, §13.) |
| **Debugger** | MVP JVM (DAP + JDWP) | + Native (DWARF) + JS (source maps) — fases 4-7; **debug de pipelines/jobs** (ver estado de um job a execução). |
| **Profiler** | `kof bench`/`kof profile` (harness + baseline) | + **profiling de pipeline** (tempo por estágio de job); + **perf de HPC/FFI** (onde o tempo vai: Kof vs lib nativa). |
| **Formatter** | ✅ `kof fmt` (parser real) | estável; estender para as novas construções de intenção (`infra`, `entity`). |
| **Testing** | `kof.test` + golden + `kof bench` | + **property-based testing** (ciência: invariante numérica); + **golden diff** já cobre paridade multi-target; + **testes de recon** (infra: plan idempotente). |
| **Deployment** | `scripts/package.sh` + release CI (2 jobs × 3 plataformas) | + **deploy multi-alvo** (mesma fonte → JVM/Native/JS, já existe por `--target`); + artefato de *infra* (o plano como artifact versionado). |
| **Observability do tooling** | `kof.observability` (health/metrics/request IDs) | + **tracing/OpenTelemetry** (já `PLANNED`) — para rastrear pipelines de ponta a ponta. |

**Princípio:** o tooling é a **superfície de controle** da plataforma
universal. Como reusa o frontend, cada domínio novo (infra, data, sci) ganha
*check, LSP, debug e test* sem construir ferramentas paralelas.

---

# 10. Long-Term Roadmap

Sem datas. Evolução por **capacidades e maturidade**. Cada estágio: objetivo,
capacidades necessárias, dependências, riscos, impacto (linguagem / compiler /
runtime / stdlib / tooling), e **o que NÃO fazer**.

> Posicionamento do estado atual: o Kof **já passou de FOUNDATION** (core
> pronto) e está **no meio de SYSTEMS** (web, dados, segurança, concorrência,
> observabilidade prontos; o nível de *sistemas/infraestrutura/automação*
> como domínio unificado **ainda não** existe). O roadmap abaixo parte
> *daqui* — sem reescrever nada.

```text
FOUNDATION (✅ superado)
    ↓
SYSTEMS (em andamento — web/data/security/concurrency prontos)
    ↓
AUTOMATION (próximo: camadas unificadas)
    ↓
INFRASTRUCTURE (IaC + cloud)
    ↓
DATA (data engineering / science / ML)
    ↓
SECURITY (expansão: forense, rede, defensiva)
    ↓
SCIENTIFIC COMPUTING (numérico / HPC / SIMD-GPU)
    ↓
BIOINFORMATICS (formatos + pipelines genômicos)
    ↓
UNIVERSAL PLATFORM (plataforma integrada)
```

### Estágio 1 — SYSTEMS (consolidação do que já é "sistemas")
- **Objetivo:** fechar os gaps de paridade de *sistemas* que já existem (não
  abrir domínio novo): web/HTTP no Native/JS, GC mark-sweep, event-loop,
  tracing/OTel, query DSL tipada, package manager *básico*.
- **Capacidades:** HTTP002/WEB001/002, GC mark-sweep, CONC003 (JS async real),
  `User.query { where ... }`, `kofdeps`/registry MVP, tracing.
- **Dependências:** nada do core (são gaps + tooling).
- **Riscos:** espalhar paridade sem fechar (dobra a superfície "quase
  funciona").
- **Impacto:** linguagem ~0; compiler: gaps + codegen leve; runtime: GC +
  event-loop (Native); stdlib: fechar namespaces existentes; tooling:
  package manager MVP.
- **NÃO fazer:** não abrir `infra`/`data`/`sci` *antes* de fechar sistemas;
  não prometer paridade JS para domínios pesados.

### Estágio 2 — AUTOMATION (camada unificada)
- **Objetivo:** Kof como *camada unificada* de automação (substituir
  Bash+Python+YAML+jq+sed+awk **numa única linguagem tipada**).
- **Capacidades:** `kof.workflow`/`kof.batch` (jobs, pipelines, retry,
  checkpoints, dead-letter), `kof.shell` (shell idiomático sobre
  `kof.process`), `kof.ssh` (por FFI/interop), cron/scheduler maduro,
  pipelines de CI/CD como **código Kof**.
- **Dependências:** estágios 1 (concorrência, scheduler, mq prontos).
- **Riscos:** virar "shell em Kof" (vazar mecanismo).
- **Impacto:** linguagem 0; stdlib: novos namespaces pequenos; runtime:
  workers/jobs (sobre spawn/channel); tooling: `kof workflow run`.
- **NÃO fazer:** não reimplementar o bash; jobs são **código Kof**, não YAML.

### Estágio 3 — INFRASTRUCTURE (IaC + cloud) — o domínio **Kof Makealive**
- **Objetivo:** o **Kof Makealive** (§4.2): infraestrutura como
  **código Kof tipado** com
  plan/apply/state/reconciliation.
- **Capacidades:** `kof.infra` (records de recurso + grafo + diff),
  `infra "prod" { ... }` (sacar sobre records — **codegen de compile-time**),
  reconciliation loop (spawn/await + channel), state em `kof.db`,
  providers por **FFI/REST/CLI** (AWS/Azure/GCP — interop), secrets via
  `kof.security`.
- **Dependências:** estágios 1-2; **FFI formalizado** (dependência
  arquitetural); package capabilities.
- **Riscos:** copiar Terraform/provider-plugins; prometer paridade com todas
  as nuvens.
- **Impacto:** linguagem: *sacar* (codegen, não nova semântica); compiler:
  grafo de dependências + detecção de ciclo (compile-time); runtime: loop de
  reconciliation; stdlib: `infra` + pacotes oficiais `kof-infra-<provider>`;
  tooling: `kof infra plan/apply/destroy`.
- **NÃO fazer:** HCL dentro do Kof; repositório de providers para *tudo*;
  acoplar core a um provedor.

### Estágio 4 — DATA (data engineering / science / ML)
- **Objetivo:** camada científica **orquestrada** (não reimplementada).
- **Capacidades:** `dataframe` tipado (lazy, colunar), **Arrow/Parquet por
  FFI** (wrapper tipado), estatística/probabilidade (wrapper + FFI),
  `kof.ml` (inferência via FFI a ONNX/libtorch; training orquestrado),
  visualização leve (SVG/`kof.ui` + FFI), **experiment tracking** (leve,
  sobre `kof.db`/`kof.io`).
- **Dependências:** estágios 1-3; FFI; Arrow como padrão de troca.
- **Riscos:** reimplementar Arrow/NumPy/frameworks de ML (o risco nº 1 da
  god-language).
- **Impacto:** linguagem 0 (records/funções/`List` bastam); stdlib: `data` +
  pacotes `kof-ml`/`kf-dataframe-parquet`; runtime: FFI/Arrow; tooling:
  profiling de pipeline.
- **NÃO fazer:** **não construir framework de ML/NumPy em Kof** — Kof dá o
  *wrapper tipado + pipeline*, o *motor* fica por fora.

### Estágio 5 — SECURITY (expansão)
- **Objetivo:** de "segurança de aplicação" (já forte) a **segurança de
  plataforma** (rede, forense, defensiva) — e **camada criptográfica moderna
  + pós-quântica** (detalhamento completo em **§4.8.1**).
- **Capacidades:** crypto avançada (RSA/ECC/X.509/TLS — B/FFI), **PQC híbrido
  (ML-KEM-768 + ML-DSA-65 + HKDF + AES-256-GCM — D/FFI `liboqs`)**, `keys.*`
  (generate/derive/rotate/store), tipo `Secret` + redaction forçada,
  `secure.channel` (KEM+KDF+AEAD+auth+replay — D), `kof.net` / parsing de
  pacotes (FFI a `libpcap`), forense (FFI a libs de parse + pipelines Kof),
  automação de segurança / threat-intel (`kof.http` + `spawn`/`channel` +
  `kof.log`), defensiva (monitoring/detection/auditoria sobre
  `kof.observability` + `kof.log` + `kof.db`).
- **Regra absoluta (§4.8.1):** **nunca** cripto caseira — toda primitiva nova
  (incl. PQC) é FFI a lib auditada; API idêntica nos targets; gap = diagnóstico
  (`SECN00x`/`SECPQ`), nunca stub fraco.
- **Dependências:** estágios 1-3; FFI.
- **Riscos:** "Kali em Kof"; expor primitivas ofensivas sem contexto.
- **Impacto:** linguagem 0; stdlib: expandir `security` + `net`/`forensics`
  (pacotes); runtime: FFI a libs; tooling: audit (já existe).
- **NÃO fazer:** reimplementar stacks cripto auditadas; ofensiva sem
  contexto legítimo/controlado; defender-se *primeiro*.

### Estágio 6 — SCIENTIFIC COMPUTING (numérico / HPC)
- **Objetivo:** Kof como **linguagem de orquestração científica tipada** +
  zona numérica por FFI.
- **Capacidades:** álgebra linear por **FFI a BLAS/LAPACK** (wrapper),
  SIMD/vectorização (Native — pesquisa), GPU (Vulkan por FFI já existe;
  CUDA/OpenCL por FFI), data-parallel (pesquisa), **FFI formalizado**
  (a espinha dorsal), **scoped resources** (GPU/ficheros/conexões),
  distributed (FFI a MPI + orquestração Kof).
- **Dependências:** estágios 1-4; FFI formalizado; GC mark-sweep (estágio 1).
- **Riscos:** prometer HPC nativo; ownership no core (rejeitado).
- **Impacto:** linguagem: *scoped resources* leve; compiler: FFI +
  (pesquisa) codegen SIMD; runtime: GC + event-loop + FFI; stdlib: `sci`/
  `math` (pacotes); tooling: profiling HPC.
- **NÃO fazer:** reimplementar BLAS/LAPACK/NumPy; ownership/borrowing no core
  (a zona sem GC é por FFI a C/Rust).

### Estágio 7 — BIOINFORMATICS
- **Objetivo:** plataforma tipada para **pipelines científicos/genômicos**.
- **Capacidades:** `kof-bio` (pacote oficial): formatos FASTA/FASTQ/VCF/BAM
  (records tipados), leitura/escrita; alinhamento/variantes por **FFI/CLI**
  (BLAST/htslib — não reimplementar); **pipelines genômicos** (modelo de
  `workflow` do estágio 2 + checkpointing); HPC (estágio 6); lab automation
  (`kof.http` REST + `kof.process` por FFI).
- **Dependências:** estágios 2, 4, 6.
- **Riscos:** "linguagem de biologia"; reimplementar alinhadores.
- **Impacto:** linguagem 0; stdlib: pacote `kof-bio` (oficial); runtime: FFI;
  tooling: `kof workflow run` (pipelines).
- **NÃO fazer:** transformar Kof em linguagem exclusiva de biologia;
  reimplementar alinhadores/variant callers.

### Estágio 8 — UNIVERSAL PLATFORM (integração)
- **Objetivo:** uma aplicação **+** sua infra **+** seu deploy **+** seu
  pipeline de dados **+** sua segurança **+** sua pesquisa — **na mesma
  linguagem**, com a mesma experiência de desenvolvimento.
- **Capacidades:** integração total dos estágios 1-7; package manager maduro;
  LSP/debug/profiler por domínio; deploy multi-alvo (mesma fonte →
  JVM/Native/JS); documentação/corpus (`training/`) dos domínios.
- **Dependências:** todos os anteriores; package manager; FFI.
- **Riscos:** fragmentação do ecossistema; manutenção (o maior risco de
  longo prazo — ver §11).
- **Impacto:** linguagem **mantém-se pequena** (a prova final de que a
  god-language foi evitada); plataforma enorme e **modular**; tooling unificado.
- **NÃO fazer:** deixar o core crescer para "suportar" a plataforma — o core
  deve **não mudar** (ou mudar quase nada) até aqui.

---

# 11. Risks

Riscos de longo prazo, classificados por probabilidade × impacto, com
mitigação. (Reflete e expande os riscos do ADR e do roadmap vigentes.)

| Risco | Por quê é risco aqui | Impacto | Mitigação (já no modelo) |
|-------|----------------------|---------|--------------------------|
| **Scope explosion** (querer tudo) | a visão "universal" convida a dizer "sim" a todo domínio | Alto | §3.4/§5: ordem de fronteira (core→stdlib→pacotes→interop); pacotes oficiais para domínios pesados; non-goals explícitos (§12) |
| **Stdlib inchada** | cada domínio quer um namespace na stdlib base | Alto | §5: domínios pesados vão para **pacotes oficiais** (camada 4), nunca para stdlib base; API ≤ ~10 por namespace; capability/link por uso |
| **Fragmentação do ecossistema** | pacotes oficiais + comunidade + interop = muitos lugares | Médio-Alto | §5/§9: package manager com registry + versionamento + audit; camadas com garantias claras; "convergir, não duplicar" (regra vigente da auditoria) |
| **Carga de manutenção** | platform enorme × 3 targets × gaps | **Alto (o maior)** | §8: JVM-first para interop pesado, Native para sistemas, JS apenas web — *não* prometer paridade total; gaps diagnosticados (nunca "quase funciona"); DoD vigente (E2E + golden por target) |
| **Complexidade do compiler** | codegen, FFI, capability, reflection de interop | Médio-Alto | §7: rejeitar macros abertas/type-classes/annotations/ownership; só FFI + codegen fechada + capabilities + scoped resources (baixo custo no core) |
| **Complexidade do runtime** | GC mark-sweep, event-loop, SIMD, FFI | Médio | §8: cada target tem seu escopo honesto; Native foca sistemas, JS foca web; FFI é o caminho numérico (não reimplementação) |
| **Problemas de interoperabilidade** | FFI é zona de falha (ABI, lifetimes, bugs) | Médio | §6/§7: FFI **formalizado** (declaração de assinatura em compile-time); vetores de teste; zone sem GC por C/Rust com fronteira segura |
| **Barreiras de adoção** | "mais uma linguagem universal" não é argumento | Médio | §1/§2: o valor é *uma linguagem* para app + infra + dados + ciência + segurança — **menos ferramentas**, não mais; onboarding medido (regra vigente: `kof init && kof run` < 60s) |
| **Performance** | wrapper tipado sobre FFI pode ter overhead | Médio | §8/§9: *wrapper fino* (o motor é nativo); profiling de pipeline; FFI direto (sem reflection em runtime); `kof bench` com baselines |
| **Segurança** | domínio de segurança exposto a erros de design | Alto | §4.8/§2: default seguro, constante de tempo, formatos versionados (padrão vigente); defesa primeiro; ofensiva só em contexto controlado |
| **Correção científica** | bug numérico silencioso | Alto | §2: determinismo/correção como **requisito de aceite** (property-based testing, golden diff); FFI a libs auditadas (não reimplementação) |

---

# 12. Non-Goals

O que o Kof **NÃO** deve tentar ser (explícito e permanente):

1. **Não é uma god-language / "faz-tudo-igual"**. Não recria o mundo; integra.
2. **Não é um shell** — orquestra o shell (`kof.process`), não o substitui.
3. **Não é o motor** de Arrow/Parquet/BLAS/LAPACK/CUDA/NumPy — dá o wrapper
   tipado, o motor é FFI/interop.
4. **Não é um framework de deep learning** (autograd/treinamento) — serve e
   orquestra modelos por FFI (ONNX/libtorch).
5. **Não é um SGBD** — `kof.db` orquestra bancos; não é um motor SQL.
6. **Não é um repositório de providers de nuvem** para todas as nuvens —
   consome SDKs/CLI/REST; abstração tipada só.
7. **Não é um alinhador/variant caller genômico** — consome ferramentas
   científicas por FFI/CLI.
8. **Não é um "Kali em Kof"** — segurança defensiva primeiro; ofensiva só em
   contexto legítimo/controlado; nunca "framework de ataque".
9. **Não é um notebook/IDE/kernel** — ferramentas de editor, fora da
   linguagem.
10. **Não introduz ownership/borrowing no core** — Kof é GC; a zona sem GC é
    por FFI a C/Rust.
11. **Não usa annotations/macros abertas/type-classes como fundação** —
    construções de intenção + interfaces + FFI + codegen fechada.
12. **Não promete paridade JS para domínios pesados** — JS é web/edge;
    ML/HPC/forense são JVM/Native.
13. **Não cria um target por domínio** (KofDevOps/KofData/...) — sempre a
    mesma linguagem, mesma IR, mesmos targets.
14. **Não reimplementa o ecossistema científico imediatamente** — consome e
    orquestra o que existe (regra do §11 do enunciado: *Kof precisa conseguir
    integrar tudo, não possuir tudo*).

---

# 13. Não criar uma "God Language" — mecanismos

> *"Uma linguagem capaz de fazer muitas coisas"* ≠ *"uma linguagem inchada
> que tenta fazer absolutamente tudo."*

A distinção que governa a plataforma universal: **a linguagem core continua
pequena; a plataforma pode ser enorme.** Mecanismos concretos:

1. **Modularização da stdlib** — namespaces independentes, sem dependência
   inversa (regra vigente); cada domínio é um módulo com fronteira.
2. **Pacotes oficiais** — camada 4 (não stdlib base): domínio pesado/pesquiso
   vira pacote versionado, opcional, gerenciado. `ml`, `bio`, `hpc`,
   `infra-<cloud>` são **pacotes**, não stdlib.
3. **Capability-based APIs / optional modules** — ligar *só o que o programa
   usa* (padrão já usado: SQLite/MySQL `.so` linkado quando o DSN literal
   aparece em compile-time). Generalizar para todos os pacotes → o binário
   final carrega só o que precisa.
4. **Dependency boundaries** — um pacote declara importações; o compilador
   valida a fronteira (análise estática já existe). Impede dependência
   cíclica/oculta entre domínios.
5. **Stable core** — o core é lento e estável; garantias de compatibilidade
   estritas. A plataforma evolui rápido; o core quase não muda.
6. **Experimental APIs** — tiers de estabilidade. Camada 4 nasce
   *experimental* (pode mudar) e *promove* a *stable* quando amadurece.
7. **Versioning / compatibility guarantees** — semântica de versão por camada
   (core estrita; pacotes semver). `VERSION` central já é a base.
8. **Custo de entrada alto para o core** — qualquer mudança no core exige
   justificativa de "todo programa precisa e é pequeno" (§3.4). O resto é
   pacote/interop. **Essa regra é o mecanismo principal anti-god-language.**

**Teste de aceite:** ao fim do estágio UNIVERSAL, o **core da linguagem deve
ter crescido quase nada** (FFI, codegen, capabilities, scoped resources — e
pouco mais). Se o core tiver inchado para "suportar" a plataforma, o design
falhou.

---

# 14. Compatibilidade com o desenvolvimento atual (classificação)

Esta é a seção que ancora o plano **no estado real** — o que o Kof **já
possui** que naturalmente permite a evolução, e como cada capacidade futura se
classifica.

## 14.1 O que o Kof JÁ possui que naturalmente permite essa evolução

| O que já existe (estado real 0.2.6-beta) | Como habilita a visão universal |
|------------------------------------------|---------------------------------|
| **Frontend único + Kof IR backend-agnóstica + backends plugáveis** | O substrato: nova capacidade = nova tabela + runtime, **não** novo target/compilador |
| **Stdlib como tabelas de dispatch em compile-time + gaps diagnosticados** | O *mecanismo* pelo qual cada domínio (infra/data/sci/bio) entra sem tocar no core; "nunca silencioso" |
| **FFI real** (SQLite `.so` direto; FFM Vulkan; interop Java; GraalJS; JDBC) | Impede a reimplementação do mundo — a espinha dorsal de interop/ciência/HPC |
| **Concorrência** (`spawn`/`await`, `channel`, `scheduler`, `selectAny`, `cancel`) | Pipelines, workers, reconciliation de infra, orquestração distribuída |
| **Abstrações** (classes, records, generics, lambdas, pattern matching) | Toolkit para modelar domínio (recursos de infra, tensores, sequências genômicas) com tipos + LSP + testes |
| **`kof.db`/`kof.orm`** (SQL, migrações, MongoDB) | *State* de infra, experiment tracking, dados |
| **`kof.security`** (crypto/JWT/secrets/auth nos 3 targets) | O núcleo do domínio de segurança já existe |
| **`kof.web`/`kof.http`** (rotas, WS/SSE, TLS, retry/circuit) | API de ML serving, threat-intel, lab automation, cloud |
| **`kof.config`/`kof.log`/`kof.observability`** | Config typed, logging, health/metrics/request IDs — base de toda operação |
| **`kof.process`** (pipes, stdin/stdout) | Orquestrar shell/CLI/ferramentas científicas (HPC, bio) |
| **Modelo de intenção + "nunca silencioso" + API pequena** | As **regras** que impedem a god-language (§13) |
| **Tooling sobre o mesmo frontend** (LSP, check, fmt, test, bench, debug) | Todo domínio novo ganha diagnostics/LSP/test **de graça** |

## 14.2 Classificação das capacidades futuras

Legenda: **A** já suportado · **B** suportado com pequenas extensões · **C**
requer evolução arquitetural · **D** requer pesquisa · **E** provavelmente não
vale a pena (ou non-goal).

| Capacidade (domínio) | Classif. | Justificativa (estado real) |
|----------------------|----------|------------------------------|
| Concorrência para pipelines/workers | **A** | `spawn`/`await`/`channel`/`scheduler` prontos (JVM/Native/JS) |
| Orquestrar shell/CLI/ferramentas | **A** | `kof.process` pronto |
| HTTP/REST (apias, ML serving, threat-intel) | **A** | `kof.web` + `kof.http` (retry/circuit) prontos |
| JSON/IO/config/logging/observability | **A** | `kof.json`/`kof.io`/`kof.config`/`kof.log`/`kof.observability` prontos |
| State de infra / experiment tracking | **A/B** | `kof.db`/`kof.io` prontos; o *formato* de state é extensão |
| Crypto/JWT/secrets/auth (app) | **A** | `kof.security` v1+G9+G10 pronto nos 3 targets |
| Crypto: AES-GCM no JS (fechar SECN002) | **B** | `SubtleCrypto` (browser/Node); sem mudar core — §4.8.1 |
| Crypto: `keys.*` (gerar/derivar HKDF/rotacionar/store) + tipo `Secret` | **B/C** | FFI JCA/openssl + type-system; `KeyHandle` opaco — §4.8.1 |
| Crypto: assimétrica (ECC/RSA sign/verify) + X.509/PEM | **B/C** | FFI JCA/openssl; formato versionado já existe — §4.8.1 |
| Crypto: **PQC** (ML-KEM-768 KEM + ML-DSA-65 sig) | **D (FFI)** | **não há hoje**; FFI `liboqs` + vetores NIST; nunca caseiro — §4.8.1 |
| Crypto: **híbrido** (ML-KEM + HKDF + AES-256-GCM, anti-downgrade) | **D/C** | protocolo versionado; depende PQC — §4.8.1 |
| Crypto: `secure.channel` (KEM+KDF+AEAD+auth+replay) | **D** | formalização de protocolo; depende híbrido — §4.8.1 |
| Cloud providers (AWS/Azure/GCP) | **A/B** | via **interop** (JVM SDK / CLI `kof.process` / REST `kof.http`); abstração tipada = extensão |
| FFI (`.so`, C/C++/Rust) | **A→C** | SQLite/FFM já funcionam; **formalizar** FFI (assinatura em compile-time) = evolução |
| Interop Java / GraalJS | **A** | interop direta existente |
| SSH | **B** | sobre `kof.process`/FFI (libssh) |
| IaC: records + grafo + diff (imperativo) | **A/B** | Kof **puro** hoje (classes/records/funções); diff é Kof normal |
| IaC: `infra "prod" { }` (declarativa) | **C** | novo bloco de parsing + lowering (sacar sobre records; reusa desugaring de `entity`/`test`) |
| IaC: plan/apply/reconciliation loop | **C** | novo namespace + tool `kof infra`; **não** muda o core |
| IaC: providers de nuvem | **B** | **FFI/REST/CLI** (não reimplementar) |
| Batch/pipeline framework (jobs, checkpoint) | **C** | novo namespace `kof.workflow`/`kof.batch` sobre `spawn`/`channel`/`mq` |
| DataFrame tipado (lazy, colunar) | **C** | novo tipo de coleção + otimização (namespace `data`) |
| Arrow/Parquet | **A (interop)** | **FFI** a Arrow/Parquet existente; Kof dá o wrapper, não o motor |
| Estatística/probabilidade | **B** | wrapper + FFI (JVM stats / C libs) |
| ML: inferência | **B** | `record` + FFI a ONNX/libtorch + `kof.web` (servir) |
| ML: treinamento | **D/A** | orquestrado (Kof chama trainer por FFI/CLI); não reimplementado |
| ML: autograd/framework | **E** | **non-goal** — não construir framework de ML em Kof |
| GPU (compute) | **D/A** | FFM Vulkan **já funciona** (sede); generalizar CUDA/OpenCL por FFI = pesquisa |
| SIMD / vectorização (Native) | **D** | hoje XMM escalar; SIMD de dados no codegen = pesquisa |
| Data-parallel / MapReduce | **D** | task-parallel existe; data-parallel = pesquisa + FFI |
| Distributed (MPI) | **D** | FFI a MPI + orquestração Kof |
| HPC (numérico, álgebra linear) | **A (interop) + C/D** | **FFI a BLAS/LAPACK** (wrapper); zona sem GC por C/Rust |
| GC mark-sweep (Native) | **C** | free-list existe; mark-sweep pendente (necessário p/ pipelines longos) |
| Event-loop / async real (Native) | **D** | hoje pthread; event-loop = pesquisa (CONC003 é o caso JS) |
| Codegen de compile-time (stubs gRPC, DDL, infra) | **C** | já existe implicitamente (KofRuntime, runner de teste, DDL de `entity`); **formalizar** |
| Package manager / registry / capabilities | **C** | `kofdeps`/registry planejado; capability/link por uso já tem semente (DSN) |
| Scoped resources (RAII leve, sem ownership) | **B/C** | hoje GC + `try/finally`; scope leve para FFI/GPU/arquivos |
| Reflection de interop | **C** | restrita a interop (ML/ciência); não fundação |
| Variance / sealed (type system) | **B/C** | útil para coleções científicas/domínios; médio custo |
| Forense (binário/memória/fs) | **C/D (FFI)** | FFI a libs de parse + pipelines Kof; não reimplementar parsers |
| Parsing de pacotes / rede | **C (FFI)** | `kof.net` + FFI a `libpcap`; sockets já emitidos |
| Bio: formatos FASTA/FASTQ/VCF/BAM | **C** | pacote oficial `kof-bio` com records tipados |
| Bio: alinhamento/variantes | **A (interop)** | FFI/CLI a BLAST/htslib; não reimplementar |
| Notebooks rich / IDE / kernel | **E** | **non-goal** (ferramentas de editor) |
| Ownership/borrowing no core | **E** | **non-goal** (Kof é GC; zona sem GC por FFI) |
| Macros abertas / type-classes / annotations | **E** | **non-goal** (rejeitadas — ver §7) |
| SGBD / motor SQL próprio | **E** | **non-goal** (`kof.db` orquestra, não é motor) |

**Leitura:** a grande maioria das capacidades universais é **A/B** (já
suportado ou extensão) porque o *substrato* (frontend+IR+dispatch+FFI+
concorrência) já existe. As **C** são *novo namespace/pacote/tool* — evolução
arquitetural **sem** mudar o core. As **D** (SIMD, data-parallel, distributed,
event-loop) são pesquisa de verdade. As **E** são non-goals que **protegem** a
identidade da linguagem.

## 14.3 Capacidades que podem ser adicionadas **sem quebrar o core**

Tudo que é **A/B/C** da tabela acima entra **sem mudança de semântica do
core** — porque usa o mecanismo existente (tabela de dispatch + runtime por
target + FFI + codegen). Concretamente, podem ser adicionadas sem quebrar o
core: `infra`, `shell`, `ssh`, `workflow`/`batch`, `data`/`dataframe`,
`sci`/`math`, `bio`, `net`/`forensics`, `cloud`/`infra-<provider>` (pacotes),
FFI formalizado, package manager, scoped resources, codegen fechada.

**Só** as **D** (SIMD/GPU data-parallel/distributed/event-loop) e os
**rejeitados** (ownership/effect system completo/type-classes/macros abertas)
também são "adicionar sem quebrar" — mas por **pesquisa** ou por **rejeição**,
não por implementação no core.

---

# 15. Recomendações Arquiteturais concretas

Manter a arquitetura atual **preparada** para a visão universal **sem
interromper** o desenvolvimento presente. Cada item: o que, por quê, custo, e
o que **não** fazer. (Nenhum item abaixo é ação — são dependências
arquiteturais futuras e guardrails.)

## R1 — Travar a fronteira core/plataforma (a primeira e mais importante)
- **O quê:** adotar a ordem de decisão §3.4 como **regra invariante**
  (core → stdlib base → plataforma → pacotes oficiais → interop).
- **Por quê:** é o mecanismo anti-god-language; sem ele, todo domínio "quer"
  ser stdlib base.
- **Custo:** zero (é processo/decisão, não código).
- **Não fazer:** não permitir que um domínio pesado (ml/bio/hpc) entre na
  stdlib base.

## R2 — Generalizar "capability/link por uso" (já tem semente)
- **O quê:** estender o mecanismo do SQLite/MySQL (`.so` linkado só quando o
  DSN literal aparece em compile-time) a **todos** os pacotes/domínios.
- **Por quê:** o binário final carrega só o que usa → plataforma enorme,
  artefato pequeno; e "ligar só o que se usa" *é* a capability-based API.
- **Custo:** baixo (padrão existente no lowering).
- **Não fazer:** não ligar todos os domínios por padrão.

## R3 — Formalizar FFI como primeira classe (maior valor/custo do roadmap)
- **O quê:** declaração de assinatura de `.so`/função externa em compile-time
  (tipos, ABI, arrays/ponteiros), reduzindo a "asm manual" de hoje.
- **Por quê:** FFI é a espinha dorsal de interop/ciência/HPC; hoje é ad-hoc
  (SQLite `.so`, FFM Vulkan, MySQL scramble).
- **Custo:** **baixo no core** (é lowering + runtime, não semântica).
- **Não fazer:** não transformar FFI em "ponteio no core" — a fronteira é
  segura; a zona sem GC fica por fora.

## R4 — Formalizar codegen de compile-time (já existe implicitamente)
- **O quê:** tornar explícita a camada que hoje gera `KofRuntime`, sintetiza o
  runner de teste e gera o DDL de `entity`.
- **Por quê:** é o que permite `infra "prod" { }` (sacar sobre records), stubs
  gRPC e codegen de pipeline **sem** macros abertas.
- **Custo:** baixo-médio (consolida um padrão existente).
- **Não fazer:** **rejeitar** macros abertas (quebram análise estática + LSP).

## R5 — Introduzir tiers de estabilidade + pacotes oficiais (guardrail de crescimento)
- **O quê:** marcar cada namespace/pacote como *stable* ou *experimental*;
  camada 4 (pacotes oficiais) nasce experimental.
- **Por quê:** permite que domínios evoluam rápido **sem** comprometer o core.
- **Custo:** baixo (metadados + versionamento).
- **Não fazer:** não promover a *stable* sem DoD completo (E2E + golden por
  target — regra vigente).

## R6 — Manter o "nunca silencioso" para domínios novos
- **O quê:** todo gap de domínio tem código (`INFRA00x`, `DATA00x`, `SCI00x`,
  `BIO00x`) + entrada na matriz de paridade.
- **Por quê:** impede o "tudo-faz opaco" — o que falta é sempre visível.
- **Custo:** zero (padrão existente: `SECN00x`/`DB001`/`WEB002`...).
- **Não fazer:** nunca stub silencioso; nunca paridade parcial sem diagnóstico.

## R7 — Escopo honesto por target (JVM-first interop / Native sistemas / JS web)
- **O quê:** adotar explicitamente: capacidades pesadas chegam **JVM-first**
  (interop), **Native** para sistemas/deploy, **JS** só web/edge.
- **Por quê:** evita prometer paridade total (o maior risco de manutenção).
- **Custo:** zero (decisão de estratégia).
- **Não fazer:** não acelerar JS para ML/HPC/forense.

## R8 — Manter o tooling sobre o MESMO frontend
- **O quê:** todo tooling novo (LSP por domínio, `kof infra`, `kof workflow`,
  debugger de pipeline, package manager) **consome o frontend do compilador**.
- **Por quê:** diagnostics/LSP/test de cada domínio vêm de graça; sem parser
  paralelo.
- **Custo:** baixo (regra vigente).
- **Não fazer:** nunca construir parser/tooling paralelo.

## R9 — Interop-first como default de domínio
- **O quê:** para cada domínio, a **primeira** pergunta é "existe por fora e é
  melhor?" → FFI/interop. Só se não existir, construir.
- **Por quê:** é a regra que impede reimplementar o mundo (§11 do enunciado).
- **Custo:** zero (princípio).
- **Não fazer:** não reimplementar Arrow/BLAS/CUDA/frameworks de ML/alinhadores.

## R10 — Correto e determinístico por padrão (ciência)
- **O quê:** para capacidades científicas/ML, correção numérica e
  determinismo são **requisito de aceite** (property-based testing + golden
  diff).
- **Por quê:** bug numérico silencioso é inaceitável.
- **Custo:** baixo (testes, não código).
- **Não fazer:** não entregar "best effort" numérico como *stable*.

## R11 — Segurança: defesa primeiro
- **O quê:** domínios de segurança entram com default seguro, constante de
  tempo, formatos versionados (padrão vigente do `kof.security`); ofensiva só
  em contexto legítimo/controlado.
- **Por quê:** segurança é infra crítica; erro de design tem impacto alto.
- **Custo:** zero (padrão existente).
- **Não fazer:** não expor primitivas ofensivas sem contexto; não "Kali em
  Kof".

## R12 — Não interromper o presente (meta-regra)
- **O quê:** nenhum item deste plano é **ação** sobre o estado atual. O estado
  atual (0.2.6-beta, 810 testes, 7 targets) permanece **100% intacto**. Os
  itens **C/D** acima são **dependências arquiteturais futuras**, a serem
  retomadas pelo roadmap vigente (`roadmap.md` / `plan-platform-completion.md`)
  **após** a consolidação atual (P0-P5) — nunca como frente paralela agora.
- **Por quê:** o enunciado é explícito: preservar o trabalho em andamento.
- **Custo:** zero.
- **Não fazer:** não abrir `infra`/`data`/`sci` antes do estágio SYSTEMS
  (gap de paridade, GC, package manager) estar fechado.

---

# 16. Modelo mental final

```text
Kof Core  (linguagem + compilador + runtime + tooling — PEQUENO, ESTÁVEL, quase não muda)
   │
   ├── Language      tipos · controle · classes/records · generics · lambdas
   │                 exceptions · spawn/await · IO mínimo
   ├── Compiler      frontend único → Kof IR → backends plugáveis
   │                 (+ codegen fechada · capabilities · FFI · gaps)
   ├── Runtime       JVM (interop full) · Native (sistemas/deploy) · JS (web)
   └── Tooling       CLI · LSP · fmt · test · bench · debug · package manager
          │
          ▼
     Kof Platform  (a stdlib — cresce por namespaces pequenos + capability-gated)
          │
   ┌──────┼──────────┬───────────┬──────────────────┐
   ▼      ▼          ▼           ▼                  ▼
 Systems  Infra     Data      Security           Science
 (web,   (IaC,     (dataframe (crypto, net,     (math, HPC,
  http,   cloud)     + Arrow)   forensics)        bio)
   │      │          │           │                  │
   └──────┴──────────┴───────────┴──────────────────┘
                     │
                     ▼
        Ecossistema  (pacotes oficiais → comunidade → externo/interop)
                     (JVM · .so C/C++/Rust · Python/R · Arrow ·
                      BLAS/LAPACK · CUDA · nuvens · bancos · CLIs)
```

**A linguagem continua sendo uma.** O que muda de um domínio para outro é a
**biblioteca/API/runtime/tooling** — nunca a linguagem, nunca a IR, nunca os
targets. O que faz isso ser *plataforma universal* e *não* god-language é a
**fronteira** (§3.4/§5/§13): o core pequeno e estável, a plataforma enorme e
modular, e o mundo externo **integrado, não possuído**.

---

# 17. Conclusão — a resposta à pergunta central

> *Como transformar o Kof de linguagem em plataforma universal sem destruir a
> simplicidade e a identidade da linguagem?*

**Respondendo com o que o Kof JÁ tem:**

1. **A linguagem não muda.** Uma sintaxe, um type system, um compilador, uma
   IR, os mesmos targets (JVM/Native/JS). A evolução é 100% por **stdlib +
   pacotes + interop + tooling**.
2. **O mecanismo de expansão já existe.** A stdlib como *tabelas de dispatch
   em compile-time* com *gaps diagnosticados* é exatamente o meio pelo qual
   infra, dados, segurança e ciência entram **sem tocar no core**.
3. **FFI + interop são o moedeiro.** Kof **integra** o mundo (Arrow, BLAS,
   CUDA, Python/R, nuvens, bancos, CLIs) em vez de possuí-lo. Isso elimina o
   risco nº 1 (reimplementar tudo) e dá escala ao ecossistema.
4. **A identidade é preservada por regras, não por sorte.** "Intenção, nunca
   silencioso, API pequena, compile-time > magic, core pequeno e estável" —
   já são a filosofia; aqui viram **invariantes** com mecanismos concretos
   (fronteira, camadas, capabilities, tiers de estabilidade, FFI formal,
   codegen fechada, non-goals).
5. **O roadmap por estágios** (SYSTEMS → AUTOMATION → INFRA → DATA →
   SECURITY → SCIENTIFIC → BIO → UNIVERSAL) é por **capacidade/maturidade**,
   sem datas, e **parte do estado real** — sem reescrever nada, sem abrir
   frente agora.

No limite, o teste final é simples: **ao fim da visão universal, o core do Kof
deve ter crescido quase nada** — FFI, codegen, capabilities, scoped resources
e pouco mais. Se o core inchou para "suportar" a plataforma, a god-language
venceu. Se o core ficou pequeno enquanto a plataforma se tornou enorme e
modular, a linguagem manteve sua identidade. **É para isso que este plano
existe: garantir o segundo desfecho.**

*Documento de visão. Não altera, interrompe ou substitui o trabalho em
andamento. O estado atual do Kof permanece 100% intacto.*