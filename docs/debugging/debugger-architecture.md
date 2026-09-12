# DEBUGGER_ARCHITECTURE.md — Arquitetura do Kof Debugger

**Status:** Fases 1-3 implementadas (DebugInfo na IR, metadata JVM, `kof-debug` MVP)
**Data:** 27 de agosto de 2026
**Versão:** 0.2.6-beta (810 testes; 7 targets; free-list + pthread spawn + FP XMM)

---

## 1. Princípio

> **O programador depura o programa Kof, nunca o artefato intermediário
> gerado pelo backend.**

O debugger preserva a identidade semântica Kof sobre qualquer target:

```text
Kof Source
    ↓
Debug Metadata (na IR)
    ↓
Kof Debug Information
    ↓
Debug Adapter (DAP)
    ↓
Editor
```

Cada backend traduz o modelo de debug Kof para o formato da plataforma
(JDWP/DWARF/Source Maps) — mas a abstração pública é sempre Kof.

## 2. Divisão de responsabilidades

| Camada | Responsabilidade |
|--------|------------------|
| Frontend | AST com posições de origem |
| CompilerDriver | **Debug Metadata** — cada op da IR sabe de onde veio |
| IR | KofDebugInfo (source files, functions, locals, scopes, mappings) |
| JvmBackend | LineNumberTable, LocalVariableTable, SourceFile |
| NativeBackend | símbolos, line tables (DWARF futuramente) |
| JsBackend | source maps Kof → JS |
| kof-debug | Debug Adapter (DAP) |
| Kof Editor | breakpoints, stack, variables (DAP) |

## 3. Debug Metadata na IR

Toda operação relevante da Kof IR carrega a relação:

```text
IR instruction
 ├── source = src/UserService.kf
 ├── line = 42
 ├── column = 17
 ├── function = UserService.find
 └── scope = method
```

O modelo interno (backend-agnóstico), implementado:

```text
KofDebugInfo(positions)
 └── Map<KofOperation, SourcePosition>   (identidade da op → arquivo/linha/coluna)

IRMethod  →  carrega KofDebugInfo
IRModule  →  carrega o nome do arquivo fonte (SourceFile)
```

A posição é registrada **no frontend** enquanto o corpo é rebaixado
(statement → range de ops), nunca sintetizada no backend — o backend
apenas traduz.

Não criar metadata específica para JVM — a informação de origem existe
**antes** do backend.

## 4. Mapeamento por target

| Target | Formato nativo | Exposição ao usuário |
|--------|----------------|----------------------|
| JVM | LineNumberTable, LocalVariableTable, SourceFile + JDWP | funções e linhas Kof |
| Native | símbolos + line tables (DWARF futuro) | funções e linhas Kof |
| KofJS | source maps Kof → JS + Node Inspector/Chrome | funções e linhas Kof |

## 5. Regras

- Nunca: `Kof → Java → debugger`
- Nunca: mostrar JVM slots, assembly ou JS gerado como experiência primária
- Código sintético (construtores gerados, lambdas, accessors) deve ser
  **escondido** — o usuário vê a abstração Kof original
- Build `--debug` preserva metadata extra; `--release` não destrói metadata
  essencial sem necessidade

## 6. Fases

```text
Fase 1  DebugInfo na IR (source location por op)                  ✅
Fase 2  JVM: SourceFile + LineNumberTable + LocalVariableTable    ✅
Fase 3  kof-debug MVP: DAP over stdio + JDWP cru                  ✅
        (launch, breakpoints por linha Kof, stopped, stack trace
         com funções/linhas Kof, continue, disconnect)
Fase 4  Kof Editor: breakpoints, toolbar, call stack, variables, stepping
Fase 5  Native: DWARF
Fase 6  JS: source maps + Node Inspector
Fase 7  Avançado: locals por frame, stepping, conditional/exception
        breakpoints, avaliação, async
```

Detalhes de implementação do `kof-debug` (Fase 3): ver `debug-adapter.md`.

## 7. Documentação relacionada

- `debugging.md` — visão de uso
- `debug-adapter.md` — o componente kof-debug
- `debugging-jvm.md`, `debugging-native.md`, `debugging-js.md` — por target