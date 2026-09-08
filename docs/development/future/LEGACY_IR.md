# LEGACY_IR.md — Legacy Semantic IR (plano futuro)

**Status:** Plano futuro — NÃO implementado
**Data:** 22 de agosto de 2026

---

## 1. Papel

A Legacy Semantic IR é a representação intermediária entre o formato de
origem e o AST Kof:

```text
Legacy Input
     ↓
Legacy Frontend
     ↓
Legacy Semantic IR
     ↓
Kof AST
```

Ela existe para que cada adaptador (bytecode JVM, Java, COBOL, etc.) produza
a MESMA representação semântica — permitindo que o restante do pipeline
(decompilador, translator, differential testing) seja independente da origem.

## 2. Conceitos Representados

- types;
- functions;
- methods;
- fields;
- inheritance;
- interfaces;
- calls;
- control flow;
- exceptions;
- memory operations;
- external calls;
- constants;
- data flow;
- metadata;
- dynamic behavior;
- unknown operations.

## 3. Informação Desconhecida

Um conceito central: representar **informação desconhecida** sem inventá-la.

```text
UnknownType
UnknownCall
UnknownField
UnknownBehavior
```

A ferramenta nunca deve fabricar código aparentemente válido para preencher
lacunas. O desconhecido permanece desconhecido e é relatado.

## 4. Confidence Model

Cada elemento recuperado carrega um nível de confiança conceitual:

```text
Recovered exactly        — observado diretamente no artefato
Recovered with metadata  — observado + metadata (debug info, signatures)
Inferred                 — derivado de análise (data flow, tipos)
Heuristic                — plausível, baseado em heurística
Unknown                  — não recuperável
```

O objetivo é distinguir sempre:

```text
informação observada
```

de:

```text
informação inferida
```

Isso é especialmente importante quando o fonte original não existe.

## 5. Source Mapping

A IR preserva relações entre:

```text
Legacy Source
      ↕
Legacy Semantic IR
      ↕
Kof AST
      ↕
Kof Source
      ↕
Kof IR
```

Isso permite: diagnostics, debugging, auditoria, comparação, revisão humana,
ferramentas de migração.

## 6. Relação com o Kof Type System

A Legacy IR usa conceitos do type system Kof quando existem equivalentes
(primitivos, classes, arrays, generics). Conceitos exclusivos de legado
(bytecode `verification types`, tipos COBOL) são representados na própria IR
com mapeamento explícito para Kof quando possível.

## 7. Fases de Implementação

As Fases B, C e D do roadmap (`LEGACY_MIGRATION.md`):

```text
Fase B  JVM Bytecode IR         (Class File → Bytecode IR)
Fase C  Control Flow Recovery
Fase D  Type Recovery
```

> **Estado (08/09, `367d6c4`):** Fase D ✅ — `Type.fromJvmDescriptor`
> (descriptors) **+** `Type.fromJvmSignature` (atributo `Signature`,
> JVMS 4.7.9.1: genéricos, wildcards, type-variables, arrays) lido em
> `ClassFileParser` nos 3 níveis. Provas: `ClassFileE2ETest.
> genericSignatureRecovery` + `DecompileTest` 16/16.
>
> **Estado (08/09, este commit):** Fase C — recuperação de **loop testado-
> embaixo (`do-while`)**. O `BytecodeStatements.struct` distingue back-edge
> self/para-trás no bloco cond (`s <= b.start`, impossível em while/for top-
> tested) e emite `do { corpo } while (c)` (teste na direção de CONTINUAÇÃO,
> sem inversão). Antes emitia um `while` de corpo VAZIO com o `return` dentro
> (código semanticamente errado — violava "never invent silently"); agora o
> corpo é recuperado e o caso de corpo-separado-do-teste degrada p/ o stub
> UNKNOWN honesto. Prova: `DecompileTest.bottomTestedLoopRecoversAsDoWhile`
> (unário + binário, `do { i = i - 1 } while (i > 0)` compila de volta no JVM).
>
> **Estado (08/09, este commit):** Fase C — **fix R6 de ponto de junção**:
> `struct()` re-entrava em bloco já emitido só por `isLoopHeader` e, em
> shapes com join compartilhado (`continue` de `for` — o incremento é o join;
> `&&`/`||` — os braços caem no mesmo bloco; `?:`), emitia código **errado
> mas compilável** (ex.: `for`+`continue` perdia o incremento no caminho
> normal; `&&` sugava o `return` final p/ dentro do `else`). Agora re-entrar
> num bloco que NÃO é o header do loop atualmente aberto (parâmetro `header`
> threadado pela recursão) → recusar → stub UNKNOWN honesto. Provas:
> `DecompileTest.diamondJoinShapesStayHonestStub` + `recoversNestedWhileLoops`
> (aninhado legítimo continua recuperando) + `DecompileTest` 20/20.