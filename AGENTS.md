# AGENTS.md — Escrevendo Kof (guia para agentes de IA)

Este é o guia **obrigatório** para qualquer agente de IA (ou humano) que
escreva código Kof neste repositório. Leia antes de gerar qualquer `.kf`.

**Versão:** 0.3.0-beta · Última atualização: 05/09/2026 (modo autônomo definido; linha 0.3.0 + REFACTOR-500 em curso)

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
   Por isso o item 2b é contrato: quem volta — humano ou outra instância —
   deve conseguir retomar em ≤1 leitura do `DOING.md`, sem perguntar.
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
| "Qual a próxima prioridade?" | `docs/status.md`, `docs/backend-parity.md`, `planning-*` |
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
   introduziu e não consegue diagnosticar: registre em `docs/known-bugs.md`
   com reproduções, não "conserte" o teste para passar.
4. **Requisito genuinamente ausente do corpus** — nem `training/`, nem
   `learn/`, nem o compilador respondem: escreva a pergunta no DOING.md na
   linha do item e siga para outra tarefa (não trave o loop).

**O que o modo autônomo NÃO relaxa (nada):** todas as regras deste arquivo
continuam valendo — zero regressão, retrocompatibilidade aditiva, ≤500
linhas, R6 (nunca silencioso), suíte como gate de merge, commit por unidade.
Autonomia muda **quem decide a ordem**, nunca **o que é aceitável**.

**Nada fica para trás:** se algo **te impele de continuar** (colisão de lane,
refactor em curso por outro agente, semântica congelada, dependência não
fechada, gate vermelho sem causa na sua mudança, requisito ausente do corpus),
registre o bloqueio em `docs/future/blockers/` com: **o bloqueador**, **o que
bloqueia**, **estado/dono** e um **plano de execução** para retomar. Nunca
abandone silenciosamente um item — o blocker documentado é a memória de que
ele existe e de como destravar.

**Sinal para o humano:** `todowrite` é a janela desta conversa (atualize a
cada etapa); `DOING.md` é a memória entre sessões. Se o humano voltar e ler
esses dois, ele sabe exatamente onde você está e por quê.

---

## Salva-guardas — proteção de release (não-negociável)

> Estas regras existem porque **um push errado na `main` dispara um release**
> (`release.yml` roda a cada push em `main`). Aconteceu uma vez (05/09) e é
> **irreversível**: o bump de versão de uma branch de release vazou para `main`
> e publicou uma versão que ainda estava em desenvolvimento. Nunca mais.

1. **Nunca editar `VERSION`.** O bump é exclusivo do processo de release
   (`release.yml`) e da mantenedora. Agente que altera `VERSION` = revertido.
2. **Nunca commitar nem mergear na `main`.** `main` é a linha de release.
   Agente trabalha na própria branch e entrega via PR; só a mantenedora
   (ou o release.yml) toca a `main`.
3. **Nunca mergear branch de release (`main`, `beta-*`) PARA DENTRO da sua
   branch de trabalho.** Branch de release carrega `VERSION` + metadata de
   build + tags. Para sincronizar código, use `git cherry-pick` dos commits
   específicos — nunca um merge inteiro.
4. **Antes de qualquer push: conferir `git diff main..HEAD -- VERSION`.**
   Se `VERSION` mudou na sua branch, o merge está contaminado — pare e
   desfaça (`git checkout main -- VERSION`), nunca empurre.
5. **Confiar, mas verificar merges "inocentes".** `git merge origin/beta-*`
   numa branch de feature parece sync, mas puxa o bump. Se a tarefa foi
   "sincronizar com a beta", o delivery correto é **código + testes**, nunca o
   `VERSION` da release.

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
3. **Transparência cirúrgica de erros.** Erros vão para `docs/known-bugs.md`
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

Regra de ouro: **nunca dois agentes no mesmo gap ou no mesmo arquivo gigante**
(`NativeRuntime.java`, `CompilerDriver.java`) ao mesmo tempo. Se for
inevitável, combine no chat antes.

### Lição aprendida (04/09) — trabalhe SEMPRE em partes pequenas

> **Nunca tente gravar/produzir um artefato grande de uma vez.** O plano de
> refactoring `docs/refactoring/PLAN-SOLID-500.md` (120 classes, 8 fases) foi
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

---

## Congelamento de comportamento (obrigatório)

> **O comportamento previsto é lei.** "Comportamento previsto" = o que o corpus
> (`training/`, `learn/`, `docs/`) documenta e os testes (golden + E2E + suíte
> completa) provam. **Nenhum agente pode quebrar comportamento que já funciona.**

1. **Zero regressão.** Nenhum commit pode fazer um teste existente passar a
   falhar. A suíte completa (`mvn test`, hoje **840**) é **gate de merge** —
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
   `docs/known-bugs.md` é desvio do comportamento previsto e **deve ser
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

## Invariantes da plataforma (visão universal — `docs/future/PLAN-UNIVERSAL-PLATFORM.md`)

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

---

## Loop de verificação (obrigatório)

Sempre que escrever/alterar código Kof:

```bash
# 1. Compilar o módulo (rápido)
mvn -o -pl kof-compiler -am compile -q

# 2. Rodar os testes da área alterada
mvn test -o -pl kof-compiler -am -Dtest='KofAreaTest' -Dsurefire.failIfNoSpecifiedTests=false

# 3. Suíte completa antes de commit
mvn test -o -pl kof-compiler,kof-script,kof-c-compiler,kof-cli -am
```

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
| `learn/` | Tutorials passo a passo (00-introduction → 37-kofjs) |
| `docs/security-plan.md`, `docs/native-multiarch.md` etc. | Domínios específicos |
| `docs/future/` (plans) | Planos futuros: migração legado (decompiler/translator/IR/differential) + plataforma universal |
| `docs/future/ACTION_PLAN.md` | Ordem de implementação de `docs/future` (Tiers 0–12) |

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
