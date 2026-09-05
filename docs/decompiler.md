# DECOMPILER.md — Kof Decompiler

**Status:** COMPLETO (núcleo) — `kof decompile` recupera estrutura + tipos + controle de fluxo completo
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

`kof decompile <file.class>` recupera, por método, um corpo Kof idiomático.

**Cobertura completa das Fases B–E** (bytecode IR, CFG com branches/loops/
exceções/switch/finally, tipos, geração de fonte):

| Capacidade | Saída |
|---|---|
| estrutura + tipos | `class`, campos, assinaturas, primitivos/String/arrays |
| aritmética/negação/comparação | `(a+b)`, `-x`, `x>0`, `a==b` |
| if/else | `if (c) a else b` |
| while + locals | `var v1 = arg0; while (...) { ... }` |
| chamadas | `this.m(...)`, `Owner.m(...)` |
| campo | `this.f`, `this.f = v` |
| criação de objeto | `X(...)` |
| try/catch | `try { ... } catch (String e) { ... }` |
| try/finally | `try { return R } finally { F }` |
| switch | `switch (e) { case N: return v; default: ... }` |

**Fallback honesto** (UNKNOWN → stub) só para: cast, `newarray`,
`invokedynamic`, stdlib com mapeamento não-trivial, múltiplos catches
aninhados — nunca fabrica comportamento.

Mapeamento de **stdlib** (parcial — as chamadas mais comuns):
- `System.out.println(x)` → `println(x)` · `System.out.print(x)` → `print(x)`
- `String.length()` → `.length` (propriedade) · `String.equals(b)` → `== b`
- demais (`Math.*`, coleções) caem no fallback honesto — extensão futura.
