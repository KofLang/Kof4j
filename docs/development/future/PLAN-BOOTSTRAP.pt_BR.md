[English](PLAN-BOOTSTRAP.md) | [Português](PLAN-BOOTSTRAP.pt_BR.md)

# Plano estratégico — o Bootstrapper: Kof escrito em Kof (BS-1)

> **Estado (20/09): FUTURO — plano de design apenas, zero código.** Decisão
> `DECISIONS.md` §D-BOOTSTRAP (mantenedora, 20/09): o bootstrapper é o
> **objetivo final (north star)** da plataforma, alcançado como ÚLTIMA etapa
> de tudo. O rascunho foi antecipado (dono: lane `.18`); a execução é
> governada por three-states + R12: não pode começar antes de fechar o EXIT
> GATE 1.0 (`roadmap.md` §24, `PROPOSAL-1.0-EXIT-GATE.md`) nem pular nenhuma
> etapa existente. Todo "escape hatch" que o bootstrap parecer exigir é
> decisão rule-6 da mantenedora — **nenhum recurso de linguagem se justifica
> "pelo bootstrapper"** (D-BOOTSTRAP, à risca). O núcleo Java permanece a
> referência congelada; o corpus dourado E2E é o oráculo (Q0–Q7 valem para o
> bootstrap exatamente como valem para o compilador de hoje).

## 1. Objetivo

Um compilador Kof **escrito em Kof** (`kofc.kf`) que compila o corpus inteiro
para artefatos **byte-idênticos** aos que a implementação Java produz hoje —
e depois compila **a si mesmo** (ponto fixo, §6). Fecharia então,
ponta-a-ponta, o "Kof como nuvem de si mesmo": o compilador se compila,
provisiona sua infraestrutura (Makealive, etapa 7), roda nela (Native /
bare-metal via `../../PLAN-BAREMETAL-BOOT.md`) e hospeda os próprios
pacotes no registry (`kof deps`, etapa 1.4).

## 2. Condições de entrada (a execução não pode ser reivindicada antes de TODAS valerem)

| # | Condição | Onde rastreado |
|---|-----------|---------------|
| E1 | EXIT GATE 1.0 verde (EG-1..EG-10 num único candidato RC) | `roadmap.md` §24 |
| E2 | Zero release-blockers ABERTOS (`--rc-gate` rc=0) | scripts do gate |
| E3 | §388 fechada ✅ 21/09 — o bootstrapper é um programa PESADO de bytes (`writeBytes`/`readBytes`/coerção `Int[]` + contrato de impressão de array) — A: diagnóstico `SEM099` em compile-time; B: formato de container declarado (`D-ARRAY-PRINT`) | `known-bugs.pt_BR.md` §388 |
| E4 | FFI structs ratificadas (D6-1..D6-5) — o compilador precisa de controle byte-a-byte de buffers/structs | `DECISIONS.md` §D6-*, `ffi-abi-structs.md` |
| E5 | GC Native + multi-arquitetura estáveis (1.2) — o compilador self-hosted precisa rodar em Native, não só JVM | `roadmap.md` §23 TIER 1 |
| E6 | Inventário de gaps de linguagem (BS-A abaixo) revisado e escalado pela mantenedora | este plano |

## 3. Fases

**BS-A — Inventário de gaps (auditoria, zero código).** Passar o compilador
atual sobre si mesmo: para cada construto Java que o `kof-compiler` usa,
decidir se (i) é expressível em Kof hoje, (ii) é expressível com a stdlib
existente (nomear a face), ou (iii) é gap de linguagem → **aberto como gap
normal, independente do bootstrapper** (cada um justificado pelos próprios
méritos, nunca "pelo BS"). Gaps previstos (a confirmar na BS-A):
`sealed`/`switch` exaustivo sobre formas de AST (pattern matching), tipos
união recursivos para a AST sem boxing, buffers de bytes crescentes (família
E3/§388), construção de string em alto volume, hash maps com valores
mutáveis em loops quentes, controle de profundidade de recursão (o parser é
recursive-descent) e um caminho sem FFI para escrever ELF/Mach-O/PE (os
alvos de codegen de hoje são bibliotecas Java — o compilador Kof precisará
de emissores próprios ou da FFI ratificada na E4). Produto:
`future/BOOTSTRAP-GAPS.md` (artefato seguinte deste plano, ainda não criado) — tabela por construto × decisão × número da
issue).

**BS-B — Lexer + parser em Kof.** Surface puramente funcional (texto entra,
AST sai): o primeiro componente onde "expressível hoje" é provável. Prova:
teste diferencial contra o parser Java — mesmo corpus, mesmos dumps de AST,
byte-a-byte — o parser Java fica como oráculo (nunca substituído em
silêncio).

**BS-C — Semântica + codegen JVM em Kof (roda no runtime VELHO).** O
compilador Kof é um programa compilado PELO compilador Java, executando no
runtime Java, emitindo `.class` byte-iguais aos do compilador Java (estágio
2 de K&R). Prova: cada golden JVM dos `*E2ETest` roda com o `kofc` como
compilador sob teste e passa byte-idêntico.

**BS-D — Paridade de alvos completos (JS, Script, Native, KofC, Android).**
Portar emissor por emissor; a matriz de conformidade (`ConformanceMatrixTest`,
8 alvos) vira o portão do compilador Kof também. Prova: a matriz verde com o
`kofc` produzindo os artefatos.

**BS-E — Ponto fixo + aposentadoria do papel de bootstrap.** `kofc`
compilado por `kofc` (estágio 3): comparação tripla — `kofc` feito pelo
Java, `kofc` feito pelo `kofc`, e a segunda iteração — todos os artefatos
byte-idênticos (checagem quining: a saída do compilador sobre o próprio
fonte tem que reproduzi-lo exatamente). O núcleo Java passa a
**referência-apenas** (oráculo congelado, nunca deletado); registry +
Makealive consomem o `kofc` (fecha D-KOF-AS-CLOUD).

## 4. Disciplina de oráculo e testes

Zero fontes novas de verdade: o bootstrap passa **no mesmo corpus dourado**
de todo alvo — mesmos bytes, mesmas regras classe-§147/§149/§174, mesmos
Q0–Q7. Fuzzing diferencial (compilador Java vs compilador Kof sobre
programas gerados) é ferramenta de BS-C+, construída com `kof.test`, não um
framework novo. Todo golden que "precisasse de ajuste" para o `kofc` é um
BUG DO BOOTSTRAP, nunca edição de golden (o corpus é lei congelada,
D-BOOTSTRAP).

## 5. Mapa de dependências (o que já existe e o BS usa)

Plano `kof.file` (APIs de árvore de arquivos) · faces de bytes §388/E3 ·
FFI structs (E4, `ffi-abi-structs.md`) · `kof.test` + o próprio corpus
(~2,8k testes hoje) · `kof.workflow`/Makealive (etapa 7) para CI em
infra auto-provisionada · `PLAN-BAREMETAL-BOOT.md` (1.6) para o compilador
residente em Native · registry de pacotes (`kof deps`, 1.4) para hospedar o
compilador como pacote.

## 6. Fora de escopo / não-promessas

Sem datas, sem estimativas de esforço (planejar ≠ fila); nenhuma mudança de
linguagem decidida aqui (E6/BS-A apenas ABRE os gaps); nenhuma deleção do
núcleo Java (ele é o oráculo permanente); nenhum dialeto paralelo (o
bootstrapper usa a mesma semântica congelada de qualquer programa);
performance do `kofc` é observação de BS-C+, nunca motivo para enfraquecer a
byte-igualdade do §4.
