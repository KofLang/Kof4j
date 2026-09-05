# DECOMPILER.md — Kof Decompiler

**Status:** EM DESENVOLVIMENTO — `kof decompile` recupera estrutura + corpos (ver §7)
**Data:** 22/08/2026 · update 05/09/2026

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

## 8. Estado real (05/09)

`kof decompile <file.class>` recupera, por método, um corpo Kof idiomático:

| Recuperado (confiança EXACT) | Fallback honesto (UNKNOWN → stub) |
|---|---|
| classes, extends, implements, campos, assinaturas | `throw "body not recovered"` para o que não encaixa |
| tipos (primitivos/Strings/arrays) | `switches` (tableswitch/lookupswitch) |
| aritmética `(a+b)` e negação `-x` | exceções (`try/catch/finally`) |
| comparações (`>`, `==`, …) | casts (`checkcast`), operações com estado raro |
| if/else → if-expression | `invokedynamic`, `monitor`, `newarray` |
| while → `while (cond) { ... }` com locals (`var`/`=`) | `switch` (tableswitch/lookupswitch) |
| chamadas (`this.m(...)`, `Owner.m(...)` — invokestatic/virtual) | múltiplos catches aninhados |
| acesso a campo (`this.f`, `Owner.f`) + atribuição (`this.f = v`) | — |
| criação de objeto (`X(...)` — `new`+`dup`+`<init>`) | — |
| try/catch → `try { ... } catch (String e) { ... }` (exceção = String) | `finally` (bloco duplicado + handler catch-all) |
| switch → `switch (e) { case N: return v; default: return v }` (tableswitch/lookupswitch) | cast, `newarray`, `invokedynamic` |

Recuperação cobre a **Fase B (bytecode IR)**, **C (CFG com branches/loops/exceções/switch)** e
**D (tipos)** — e a **Fase E** para o subconjunto linear/branch/loop/call/campo/try/switch.
Item restante (DECOMPILER §3): `finally` — reconhecimento de bloco duplicado
(caminho normal + handler catch-all com `athrow`) + mapeamento de stdlib
(ex.: `System.out.println` → `println`) — o caso mais difícil da disciplina.