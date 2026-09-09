# DECOMPILER.md — Kof Decompiler (plano futuro)

**Status:** Plano futuro — NÃO implementado
**Data:** 22 de agosto de 2026

---

## 1. Objetivo

Recuperar código Kof idiomático a partir de artefatos compilados.

Primeiros alvos: `.class`, `.jar`, `.war`.

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

## 2. O que NÃO é

O decompiler **não é**:

- um "Java decompiler" que reconstrói o fonte Java original;
- uma reconstrução sintática do fonte perdido;
- um gerador de "Java com sintaxe Kof".

O objetivo é **Kof idiomático equivalente** — comportamento e estrutura,
não a forma original.

## 3. Prioridades

1. Equivalência semântica (o comportamento observável deve ser o mesmo);
2. Legibilidade (o resultado deve ser revisável por humanos);
3. Estrutura (classes, herança, interfaces, métodos, campos);
4. Tipos (primitivos, referências, arrays, generics recuperáveis);
5. Controle de fluxo (branches, loops, switches, exception regions);
6. Chamadas e dependências;
7. Exceptions;
8. Annotations e metadata quando presentes.

## 4. Informação Irrecuperável

Compilar é perder informação. O decompiler deve documentar o que não pode
recuperar:

- comentários;
- nomes locais (exceto quando debug info existir);
- estrutura sintática original;
- formatação;
- certas informações genéricas (erasure);
- intenção do programador;
- abstrações eliminadas na compilação.

> Decompilação é recuperação de **comportamento e estrutura** a partir das
> informações disponíveis — nunca do fonte original.

## 5. Confiança

Cada construção recuperada carrega um nível de confiança conceitual:

```text
Recovered exactly
Recovered with metadata
Inferred
Heuristic
Unknown
```

A plataforma nunca inventa informação silenciosamente para produzir código
que "parece válido".

## 6. Fases de Implementação

```text
Fase A  JVM Inspection          (.class/.jar + análise estrutural)
Fase B  JVM Bytecode IR         (Class File → Bytecode IR)
Fase C  Control Flow Recovery   (basic blocks, branches, loops, switches, exception regions)
Fase D  Type Recovery           (primitives, references, arrays, generics, inheritance)
Fase E  Kof Decompiler          (gerar Kof source)
```

> **Estado (08/09, `367d6c4`):** Fase D com genéricos ✅ — o parser lê o
> atributo `Signature` (JVMS 4.7.1/4.7.9.1) nos 3 níveis (classe, método,
> campo) e `Type.fromJvmSignature` recupera `List<String>`,
> `Map<String, Integer>`, arrays, wildcards e type-variables. Campos e
> métodos preferem a signature (EXACT) ao descriptor apagado por erasure.
>
> **Estado (08/09, este commit):** Fase C avançou — `do-while` (loop testado-
> embaixo) recuperado no `kof decompile`. Back-edge self/para-trás no bloco
> cond → `do { corpo } while (c)` (direção de CONTINUAÇÃO, sem inversão);
> corpo separado do teste → stub UNKNOWN honesto. Nunca mais `while` de corpo
> vazio com `return` dentro (código errado). Prova:
> `DecompileTest.bottomTestedLoopRecoversAsDoWhile` (17/17).
>
> **Estado (08/09, este commit):** Fase C — guard de **join compartilhado**
> (continue/&&/||/?:): re-entrar em bloco já emitido que não é o header do
> loop aberto → recusar (stub honesto). Nunca mais código errado compilável.
> `DecompileTest` 20/20 (inclui `diamondJoinShapesStayHonestStub` e o
> aninhado `recoversNestedWhileLoops` que continua recuperando).
>
> **Estado (09/09, este commit): robustez sobre código REAL (601 classes).**
> Rodar o decompiler sobre o próprio kof-compiler compilado expôs 3 bugs de
> parsing/length que só aparecem em bytecode de produção (javac, não os
> fixtures do teste). Correções (todas com teste):
> 1. **CP tags 16/17 invertidas** (`ClassFileParser`): JVMS 4.4 — `MethodType`
>    = tag 16 (u2), `Dynamic` = tag 17 (u2+u2). O parser tinha ao contrário →
>    qualquer classe com MethodType/CondY dessincronizava o constant pool
>    inteiro e CRASHAVA (`NumberFormatException "#378#513"`), matando o arquivo.
>    601→0 crash: antes só 1/601 decompilava SEM crash; agora 601/601.
> 2. **`length(0xba)=7` errado** (`BytecodeReader`): invokedynamic é 5 bytes
>    (opcode + u2 + 2 zero, JVMS 4.9.3). Com 7, o pc saltava a instrução
>    seguinte (`areturn`) → o teste do concat passava por ACIDENTE (fallback
>    "fim sem return"). Corrigido a captura de operandos (len==5&&0xba).
> 3. **`wide` drift** (`skipVariable`): `op == 0x84` era impossível (o op é
>    0xc4; 0x84 é o SUB-opcode) → `wide iinc` (6B) lido como 3, deslocando TODO
>    opcode seguinte. Agora lê o sub-opcode (0xc4,0x84 → 6 bytes; demais → 4).
> + **Marcador de truncamento**: instrução que não cabe no Code → Insn(-1)
>   → decoder default → null → stub honesto. A ferramenta NUNCA lança num
>   `.class` real (era o caminho que virava NumberFormatException/AIOOBE).
>
> Prova: `DecompileTest` 32/32 (+`invokedynamicIsFiveBytes`,
> `truncatedLastInstructionBecomesHonestStub`, `wideIincConsumesSixBytes`);
> medição sobre as 601 classes: 601/601 decompilam sem exceção, ~3306 métodos,
> 1812 stub (recuperação ~45%).

> **Fila Fase E medida (09/09):** `blockerSink` em `BytecodeDecoder` (custo
> zero quando null, uso offline) conta qual opcode derruba a recuperação
> sobre as 601 classes: `pop` 0x57 (347×), `instanceof` 0xc1 (165×),
> `checkcast` 0xc0 (137×), `new` 0xbb (126×, quase todo é `isJdkClass`
> recusando por R6), `astore_3`/arrays 0x4c (103×), `ifeq` 0x99 (102×).
> Ataque por ROI: pop/instanceof/checkcast são os 3 maiores. **Implementado e
> REVERTIDO no mesmo dia (lição R6):** emitidos como `x instanceof T`/`(x as
> T)`, a classe-alvo do bytecode é de DOMÍNIO (ex.: `DiagnosticCollector`) e
> não existe no `.kf` isolado → `kof check` falha "Undefined variable or
> type" — **recuperou código que não compila** (o teste de drift: decompile →
> check sobre as 601 classes; 12+ arquivos driftavam). A recuperação só é
> válida quando o nome do tipo já está em escopo (classes do MESMO arquivo
> recuperado) — requer o passes multi-classe do DECOMPILER (seção 7: resolver
> imports/usos), não um patch no decoder. `pop` sozinho também drifta: a
> heurística "tem parênteses = chamada" aceita `(x + (y))` aritmético.
> Fila correta da Fase E: primeiro multi-classe (tipo resolve), depois
> pop/instanceof/checkcast (bloqueados por aquele, não por eles mesmos).

## 7. Relação com o Compilador

O decompiler alimenta o pipeline existente:

```text
Legacy Semantic IR
        ↓
    Kof AST
        ↓
Kof Compiler (frontend existente)
        ↓
    Kof IR
        ↓
 JVM / Native
```

Não duplica o frontend do Kof. O ponto de entrada é o **Kof AST**.