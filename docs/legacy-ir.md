# LEGACY_IR.md — Legacy Semantic IR

**Status:** EM DESENVOLVIMENTO — Fases B/C/D implementadas (ClassFileParser, CFG/analyze, Type Recovery, Confidence)
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