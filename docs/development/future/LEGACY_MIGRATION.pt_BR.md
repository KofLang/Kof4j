[English](LEGACY_MIGRATION.md) | [Português](LEGACY_MIGRATION.pt_BR.md)

# LEGACY_MIGRATION.md — Plataforma de Migração de Software Legado

**Status:** **REABERTO pela mantenedora (`D-DEPRIORITIZED-REOPEN`, lote 28/09)** —
ainda só-plano aqui; a promoção a trabalho corrente é uma-por-vez por
`D-FUTURE-PROMOTION`. O código já
no repo fica. — **doc central e único da plataforma de
migração** (caiu de `future/` em 12/09; FUNDIU `LEGACY_IR.md` no §4 e
`DIFFERENTIAL_TESTING.md` no §8 em 13/09 — conceitos duplicados, zero
conteúdo único; work-logs técnicos vivem em `DECOMPILER.md`/`TRANSLATOR.md`).
A plataforma existe: `kof inspect/decompile/translate/compare/migrate`
registrados no `Main.java` (contagem de testes = fonte única em
`roadmap.md` §23 TIER 3–5; `inspect` é o comando de IR stats, sem classe de
teste própria). Recuperação de corpo de método ainda é parcial — relatório
rastreável o expõe de forma honesta)
**Escopo:** plataforma de migração (implementação iniciada na 0.3.x)
**Criado:** 22 de agosto de 2026 · **Última consolidação:** 13/09/2026

---

## 1. Visão

Kof não é apenas uma linguagem para criar software novo.

A visão de longo prazo é uma plataforma capaz de **analisar, recuperar,
traduzir e modernizar sistemas legados para Kof**:

> Preservar a funcionalidade de software legado enquanto modernizamos sua
> implementação para Kof.

A plataforma deve trabalhar tanto com código-fonte disponível quanto com
sistemas onde o código original foi perdido — utilizando bytecode, binários,
metadados, artefatos de build e comportamento observável como fontes de
informação.

**Esta documentação é arquitetura + estado de implementação (§3 tem a tabela
real: comandos ✅ no CLI, cobertura parcial honesta).**

---

## 2. Princípio Fundamental

A plataforma não assume `Legacy → Java → Kof`.

Quando possível, utiliza o caminho direto:

```text
Legacy
   ↓
Legacy Semantic IR
   ↓
Kof AST
   ↓
Kof IR
```

Para JVM:

```text
JVM Bytecode
     ↓
Bytecode Analysis
     ↓
Legacy Semantic IR
     ↓
Kof AST
```

Java pode ser uma **origem suportada** (via translator), mas nunca uma
**representação intermediária obrigatória**. Isso evita uma etapa artificial
de geração de Java entre o artefato legado e o Kof.

---

## 3. Componentes Planejados

| Comando | Propósito | Status |
|---------|-----------|--------|
| `kof inspect <input>` | Análise estrutural de `.class`/`.jar`/binários | ✅ `Inspect.java` (Main.java:25) |
| `kof decompile <input>` | Recuperação de código Kof a partir de artefatos compilados | ✅ `Decompile.java` (Main.java:26; corpo parcial → stub honesto) |
| `kof translate <input>` | Migração de código-fonte (primeiro alvo: Java → Kof) | ✅ `Translate.java` (Main.java:27; subconjunto Java) |
| `kof migrate <input>` | Migração completa com relatório | ✅ `Migrate.java` (Main.java:29; relatório rastreável) |
| `kof compare <legacy> <kof>` | Teste diferencial entre sistemas | ✅ `Compare.java` (Main.java:28; stdout/exit/stderr) |

**Todos os comandos existem no CLI** (verificados 12/09 — `Main.java:25-29`;
contagem de testes = fonte única em `roadmap.md` §23 TIER 3–5). O que
permanece em desenvolvimento é a
**cobertura** da recuperação (corpos de método complexos → stub UNKNOWN
honesto; subconjunto Java do translator).

### 3.1 `kof inspect` (implementado)

Análise de sistemas existentes. Responsabilidades:
identificar formato, plataforma, versão; analisar dependências; identificar
classes, métodos, interfaces, campos, tipos; identificar chamadas externas,
reflection, carregamento dinâmico, JNI/FFM/native calls; identificar metadata,
debug information, serialization, recursos; estimar recuperabilidade.

Saída conceitual (valores ilustrativos — nenhuma métrica é real sem
implementação e metodologia definidas):

```text
Classes:              1842
Methods:              17391
Reflection:           detected
Native calls:         detected
Debug metadata:       partial

Recoverability:
Types                  HIGH
Control Flow           HIGH
Method Signatures      HIGH
Local Names            LOW
Comments               NONE
```

### 3.2 `kof decompiler` (implementado)

Destinado à recuperação de código Kof a partir de `.class`/`.jar`/`.war`.

Pipeline:

```text
JVM Class File
       ↓
Class File Parser
       ↓
Bytecode IR
       ↓
Control Flow Graph
       ↓
Type Recovery
       ↓
Data Flow Analysis
       ↓
Semantic Recovery
       ↓
Kof AST
       ↓
Kof Source
```

O decompiler prioriza: equivalência semântica, legibilidade, estrutura, tipos,
controle de fluxo, chamadas, herança, interfaces, generics recuperáveis,
exceptions, annotations, metadata.

**Não tenta reconstruir artificialmente o Java original.** O objetivo é
produzir **Kof idiomático equivalente**, não fingir que o fonte original
foi recuperado.

### 3.3 `kof translate` (implementado)

Migração de código-fonte (primeiro alvo: Java → Kof).

```text
Java Source
     ↓
Java Parser
     ↓
Java AST
     ↓
Java Semantic Model
     ↓
Translation IR
     ↓
Kof AST
     ↓
Kof Source
```

O translator **não funciona por substituição textual** (`public → ...`).
Ele compreende a estrutura semântica do programa.

Suporte progressivo planejado: classes, interfaces, inheritance, generics,
overloads, constructors, exceptions, annotations, records, enums, lambdas,
nested classes, anonymous classes, static initialization, access modifiers,
Java standard library, chamadas de bibliotecas externas.

---

## 4. Legacy Semantic IR (era `LEGACY_IR.md` — FUNDIDA aqui 13/09)

A Legacy Semantic IR é a representação intermediária entre o formato de
origem e o AST Kof. Ela existe para que cada adaptador (bytecode JVM, Java,
COBOL, etc.) produza a MESMA representação semântica — permitindo que o
restante do pipeline (decompilador, translator, differential testing) seja
independente da origem.

Conceitos representados: types, functions, methods, fields, inheritance,
interfaces, calls, control flow, exceptions, memory/external operations,
constants, data flow, metadata, dynamic behavior e **unknown operations** —
informação desconhecida fica `UnknownType`/`UnknownCall`/`UnknownField`/
`UnknownBehavior`, nunca código fabricado "que parece válido".

### 4.1 Confidence Model (implementado — `Confidence.java`, 5 níveis)

```text
Recovered exactly        — observado diretamente no artefato
Recovered with metadata  — observado + metadata (debug info, signatures)
Inferred                 — derivado de análise (data flow, tipos)
Heuristic                — plausível, baseado em heurística
Unknown                  — não recuperável (vira stub honesto no .kf)
```

Cada elemento recuperado carrega o nível; a ferramenta distingue sempre
**observado** de **inferido**.

### 4.2 Source Mapping

A IR preserva as relações `Legacy Source ↕ Legacy Semantic IR ↕ Kof AST ↕
Kof Source ↕ Kof IR` — base de diagnostics, auditoria, comparação e do
relatório rastreável do `kof migrate` (§8.1).

### 4.3 Estado implementado (medido, nunca de memória)

- Fase B/C/D (JVM): `BytecodeReader/Decoder/Statements/Frame` +
  `Type.fromJvmDescriptor`/`fromJvmSignature` (atributo `Signature`, JVMS
  4.7.9.1 — genéricos/wildcards/type-variables, `367d6c4`); prova
  `ClassFileE2ETest.genericSignatureRecovery` + `DecompileTest` (contagem
  viva em `roadmap.md` §23 TIER 3–5).
- Recovery de **outras plataformas** (Native/JS/binários): não iniciada —
  é a Fase I (§9), gated por R12.

### 4.4 Informação Irrecuperável (era §5/`LEGACY_IR.md` §3)

Compilação é transformação com perda. Após `Source → Compiler → Bytecode`
desaparecem: comentários, nomes locais (sem debug info), estrutura sintática
original, formatação, informação genérica (erasure), intenção do programador e
abstrações eliminadas. **Decompilação não é recuperação do fonte original** —
é recuperação de comportamento e estrutura; o irrecuperável é EXPLÍCITO
(stub UNKNOWN), nunca inventado.

---

## 5. Relação com o Compilador Kof

A plataforma não duplica componentes existentes. Reutiliza:
Kof Lexer, Kof Parser, Kof AST, Kof Type System, Kof Semantic Model,
Kof IR, Kof Backend.

```text
              ┌───────────────┐
              │ Kof Source    │
              └───────┬───────┘
                      ↓
                 Kof Frontend
                      │
Legacy ───────────────┤
                      │
Java ─────────────────┤
                      │
JVM Bytecode ─────────┤
                      ↓
              Legacy Semantic IR
                      ↓
                   Kof AST
                      ↓
                  Kof Compiler
                      ↓
                  Kof IR
                      ↓
               JVM / Native
```

A ferramenta de migração é uma **extensão natural do compilador**,
não um segundo compilador independente.

## 6. Segurança e Legalidade

A plataforma é uma **ferramenta de engenharia de software**. O usuário deve
possuir autorização e direitos adequados sobre o software analisado.

A plataforma não promete contornar: DRM, proteção contra cópia, controles de
acesso, mecanismos de segurança, licenciamento.

Foco: preservação, interoperabilidade e modernização autorizada.

---

## 7. Formatos Futuros

A arquitetura prepara adaptadores de formatos além da JVM:

```text
Source / Binary
       ↓
Legacy Adapter
       ↓
Legacy Semantic IR
       ↓
Kof
```

Exemplos potenciais (NÃO implementar no início): COBOL, PL/I, Assembly,
binários legados, bytecode proprietário, VMs customizadas.

O objetivo é impedir que o projeto fique conceitualmente preso à JVM.

---

## 8. Teste Diferencial (era `DIFFERENTIAL_TESTING.md` — FUNDIDO aqui 13/09)

Valida que uma migração preserva comportamento: mesmo vetor de entrada no
programa original e no Kof, comparando **saídas observáveis**.

- **O que comparar (`kof compare`, implementado):** stdout, stderr, exit code
  (`Compare.java`, `--stdin`/`--arg`; prova `CompareTest` 6/6). Além de stdout
  comparável — exceptions tipadas, return values, arquivos, DB mutations,
  protocolos, side effects — é o pendente da Fase G.
- **Classificação de divergência:** equivalente · divergente dentro do escopo
  · divergente fora do escopo · comportamento indefinido.
- **Critério de aceite de um sistema crítico:**
  `compile + static analysis + behavioral testing + differential testing +
  manual review + migration report` — "compilou" não basta.
- **Sistemas sem código-fonte:** quando o fonte foi perdido, o comportamento
  observável do binário original é a fonte de verdade (binary + metadata +
  dependencies + configuration + database + observed behavior). Isso é
  **software archaeology**, não conversão trivial.

### 8.1 Migration Report (`kof migrate`, Fase H)

Relatório com rastreabilidade da migração. Estrutura conceitual (os números
abaixo são ILUSTRATIVOS — nenhuma métrica é real sem implementação e
metodologia definidas):

```text
Kof Migration Report

Input:     legacy-application.jar
Output:    kof-application/

Recovered:     94.2%        ← % de unidades SEM stub UNKNOWN
Warnings:      17
Unrecoverable: 3
Manual review: 12 locations

Behavioral tests:
    183 passed
    2 divergent
```

---

## 9. Ordem de Implementação

Não começar tentando suportar todos os sistemas legados.

```text
Fase A  JVM Inspection          (.class/.jar + análise estrutural)
Fase B  JVM Bytecode IR         (Class File → Bytecode IR)
Fase C  Control Flow Recovery   (basic blocks, branches, loops, switches, exception regions)
Fase D  Type Recovery           (primitives, references, arrays, generics, inheritance)
Fase E  Kof Decompiler          (gerar Kof source)
Fase F  Java Translator         (Java Source → Kof)
Fase G  Differential Testing    (Legacy vs Kof)
Fase H  Migration Reports       (relatórios completos)
Fase I  Additional Frontends    (COBOL, PL/I, Assembly, formatos proprietários)
```

Antes de implementar: definir a arquitetura, validar com protótipos pequenos,
e só então transformar os protótipos em componentes oficiais.

---

## 10. Critério de Sucesso

A iniciativa é bem-sucedida quando um sistema legado real produz:

```text
Legacy System
      ↓
Analysis
      ↓
Recoverable Semantics
      ↓
Kof Implementation
      ↓
Behavioral Verification
      ↓
Modern Deployment
```

com: rastreabilidade, diagnostics, relatório de limitações, testes
diferenciais, revisão humana, código Kof legível, compilação nativa/JVM,
comportamento compatível dentro do escopo definido.

**Pergunta central:**

> Estamos recuperando comportamento real ou apenas fabricando código que
> parece plausível?

Se a ferramenta não consegue distinguir essas duas coisas, a migração não é
confiável.

---

## 11. Documentação Relacionada

- `DECOMPILER.md` — work-log técnico da recuperação de corpo (Fase E/§7:
  pipeline, joins estruturais, records, drift-check no corpus)
- `TRANSLATOR.md` — subconjunto Java → Kof (Fase F; dono ativo da lane tradutor)
- ~~`LEGACY_IR.md`~~ → §4 deste doc (FUNDIDA 13/09 — concepts+status; não
  havia conteúdo único além do work-log do decompiler)
- ~~`DIFFERENTIAL_TESTING.md`~~ → §8 deste doc (FUNDIDO 13/09)
- `roadmap.md` §23 (TIER 3–5) — ordem/prioridades com status medido
- `docs/audits/PLANNING-FUTURE-AUDIT.md` — auditoria planejado×realizado