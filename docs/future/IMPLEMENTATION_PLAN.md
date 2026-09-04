# IMPLEMENTATION_PLAN.md — Roadmap de Implementação de docs/future

**Created:** 2026-09-04  
**Author:** agente-planning  
**Purpose:** Consolidated implementation plan for all docs/future documents

---

## Visão Geral

Esta é a versão consolidada do plano de implementação para todos os documentos em `docs/future/`. Cada item é uma etapa pequena, compilável, testável.

---

## FASE 1 — Fundamentos (Legacy IR → Bytecode)

### T1-PRIORITY: JVM Bytecode IR — Class File Parser

**Objetivo:** Parsear arquivos `.class` e gerar IR legível  
**Arquivo:** `LEGACY_IR.md` — Fase B  
**Dificuldade:** 🟡 Média  
**Dependências:** Nenhuma  
**Código existe:** `Parser` básico de bytecode no JRE  
**O que falta:** Class File → Bytecode IR (estrutura simples)  
**Implementação mínima:** 
- `kof inspect` CLI que lee `.class`
- Imprime: nome da classe, métodos, campos
- Teste: compila classe Java simples, roda `kof inspect`, verifica saída
**Critério DONE:** `kof inspect test/MyClass.class` imprime estrutura válida

| Task | Source Doc | Difficulty | Dependencies | Status | Definition of Done |
|------|-----------|------------|--------------|--------|-------------------|
| JVM-Class-File-Basic | LEGACY_IR.md | 🟡 M | Nenhuma | ✅ **COMPLETO** | Parser lê magic, versão,CP, fields, methods, Code attr |
| JVM-Inspect-CLI | LEGACY_MIGRATION.md | 🟢 E | JVM-Class-File-Basic | ✅ **COMPLETO** | `kof inspect <.class>` funciona com --json |

---

## FASE 2 — Análise e Recovery

### T2-HARD: Bytecode → Control Flow Graph

**Objetivo:** Recuperar blocos básicos e fluxos de controle  
**Arquivo:** `LEGACY_IR.md` — Fase C, `LEGACY_MIGRATION.md` — Fase C  
**Dificuldade:** 🔴 Alta  
**Dependências:** Phase 1 (IR básico funcional)  
**Código existe:** Algoritmo de análise de bytecode conhecido  
**O que falta:** Construir CFG a partir do bytecode  
**Implementação mínima:**
- Identificar `goto`, `if`, `invoke`, `return`
- Construir nós de bloco básico
- Teste: código if/while simples

| Task | Source Doc | Difficulty | Dependencies | Status | Definition of Done |
|------|-----------|------------|--------------|--------|-------------------|
| CFG-Basic-Blocks | LEGACY_IR.md | 🟡 M | Phase 1 | ✅ **EM CURSO** | BasicBlock identificado, instruções decodificadas |

**Progresso:** `ClassFileParser.expand()` com `Instruction`, `BasicBlock`, `analyze()`, `disassemble()`

### T3-MEDIUM: Type Recovery

**Objetivo:** Recuperar tipos primitivos, referências, arrays, generics  
**Arquivo:** `LEGACY_IR.md` — Fase D  
**Dificuldade:** 🟡 Média  
**Dependências:** Phase 2 (CFG)  
**Código existe:** `Type` system de Kof  
**O que falta:** Mapear tipos JVM → Kof types  
**Implementação mínima:**
- `Type.fromJvmDescriptor()` — parser de descriptors JVM → `Type` Kof
- `Type.describe()` — representação legível de `Type`
- `MethodInfo.returnTypeName()` / `parameterTypeNames()` — tipos recuperados
- `MethodInfo.instanceofCount` / `checkcastCount` — contagem por opcode bruto (0xC1/0xC0)

| Task | Source Doc | Difficulty | Dependencies | Status | Definition of Done |
|------|-----------|------------|--------------|--------|-------------------|
| Type-Recovery | LEGACY_IR.md | 🟡 M | Phase 2 | ✅ **COMPLETO** | `fromJvmDescriptor` + contagem instanceof/checkcast + `kof inspect` mostra tipos |

---

## FASE 3 — Tradução

### T4-MEDIUM: Java → Kof AST (partial)

**Objetivo:** Parser Java → AST intermediário  
**Arquivo:** `TRANSLATOR.md` — Fase F  
**Dificuldade:** 🟡 Média  
**Dependências:** Phase 3 (Type recovery)  
**Código existe:** `Parser` em `kof-compiler`  
**O que falta:** Parser Java específico, semandamento semântico  
**Implementação mínima:**
- Classes, métodos, campos básicos
- `if`/`while`/`for`
- Strings e arrays
- Teste: `public class A { void main(){}}}`

| Task | Source Doc | Difficulty | Dependencies | Status | Definition of Done |
|------|-----------|------------|--------------|--------|-------------------|
| Java-Inspect-CLI | TRANSLATOR.md | 🟢 E | Phase 1 | ✅ **COMPLETO** | `kof translate <file.java>` emite Kof (lexer+parser recursivo subset) |
| Java-AST-Basic | TRANSLATOR.md | 🟡 M | Java-Inspect-CLI | ✅ **COMPLETO** | classes/métodos/campos + if/while/for + strings; static main → top-level `main()` |

---

## FASE 4 — Decompilation

### T5-HIGH: Kof AST → Source (structural)

**Objetivo:** Gerar código Kof a partir do IR recuperado  
**Arquivo:** `DECOMPILER.md` — Fase E  
**Dificuldade:** 🔴 Alta  
**Dependências:** Phase 3 (AST), Phase 4 (CFG)  
**Código existe:** `KofParser`, `KofCompiler`  
**O que falta:** Encoder Kof AST → Syntax, preservando estrutura  
**Implementação mínima:**
- Gerar classes, métodos, campos
- Teste: decompila classe gerada, compila novamente

| Task | Source Doc | Difficulty | Dependencies | Status | Definition of Done |
|------|-----------|------------|--------------|--------|-------------------|
| Decompiler-Structural | DECOMPILER.md | 🔴 H | Type Recovery | ✅ **COMPLETO** | `kof decompile X.class` gera esqueleto `.kf` compilável (corpos = throw stub honesto) |
| Decompiler-Confidence | DECOMPILER.md | 🟡 M | Structural | ✅ **COMPLETO** | `Confidence` enum (exact/with-metadata/inferred/heuristic/unknown); decompiler marca campos=EXACT, corpos=UNKNOWN |

---

## FASE 5 — Differential Testing

### T6-EASY: Differential Testing Framework

**Objetivo:** Comparar comportamento legacy vs Kof  
**Arquivo:** `DIFFERENTIAL_TESTING.md`  
**Dificuldade:** 🟢 Fácil (mas necessita infra)  
**Dependências:** Phase 1-5 (inspect + decompile)  
**Código existe:** Test framework básico  
**O que falta:** Script que executa e compara outputs  
**Implementação mínima:**
- `kof compare legacy.jar kof-app/`  
- Comparar stdout, exit code, stderr  
- Teste: app simples com saída verificável

| Task | Source Doc | Difficulty | Dependencies | Status | Definition of Done |
|------|-----------|------------|--------------|--------|-------------------|
| Diff-Framework | DIFFERENTIAL_TESTING.md | 🟢 E | Inspect-CLI | ✅ **COMPLETO** | `kof compare <legacy.class|jar> <file.kf>` compara stdout/stderr/exit |
| Diff-Tests-Corpus | DIFFERENTIAL_TESTING.md | 🟡 M | Framework | ✅ **COMPLETO** | 3+ casos (hello, add, divergência, lógica pura) |

---

## FASE 6 — Migração Completa

### T7-MEDIUM: Migration Reports

**Objetivo:** Generar relatórios de migração com rastreabilidade  
**Arquivo:** `LEGACY_MIGRATION.md` — Fase H  
**Dificuldade:** 🟡 Média  
**Dependências:** Phase 5 (differential)  
**Código existe:** `PostgresException`, `Logger` patterns  
**O que falta:** RMSE + Traceability + Human-review prompts  
**Implementação mínima:**
- Relatório JSON com % recovered, warnings  
- Teste: migrar app simples, verifica relatório

| Task | Source Doc | Difficulty | Dependencies | Status | Definition of Done |
|------|-----------|------------|--------------|--------|-------------------|
| Migration-Reports | LEGACY_MIGRATION.md | 🟡 M | Diff | ✅ **COMPLETO** | `kof migrate <.class\|.java>` emite relatório traceable (recovered %, manual review) |

### T8-MEDIUM: Interpreter Mode

**Objetivo:** Execute código legado sem compilar  
**Arquivo:** `LEGACY_MIGRATION.md`  
**Dificuldade:** 🟡 Média  
**Dependências:** Phase 1-6  

---

## FASE 7 — TIER 2: Fundações do compilador (quebrado em subtarefas)

> Quebra do TIER 2 do `ACTION_PLAN.md` (2.1–2.5) em incrementos menores,
> cada um compilável e testável isoladamente. Ordem = dependência.

### 2.1 FFI formalizado (R3: assinatura externa em compile-time)

| # | Subtarefa | Dificuldade | DoD |
|---|-----------|-------------|-----|
| 2.1.1 | Sintaxe `extern`: lexer + parser reconhecem declaração top-level de função externa (`extern name(Int): Int`) sem tocar semântica existente | 🟢 E | ✅ `extern` parseia (PARSE não emitido) |
| 2.1.2 | Node de AST (`extern`) + type-check da assinatura (tipos primitivos/refs/arrays validados) | 🟡 M | ✅ `ExternalFunctionNode` + parsing de tipos |
| 2.1.3 | Gap honesto por target: chamada a `extern` emite `FFI001` (diagnóstico), nunca stub silencioso | 🟢 E | ✅ lowering emite `FFI001` (nunca drop silencioso) |
| 2.1.4 | Binding JVM-first: FFM (`java.lang.foreign`) para `.so` (padrão já usado em `JvmVkRuntime` M32.1) | 🔴 H | ✅ JVM Int→Int real (`extern "libc.so.6" abs` → 5) |
| 2.1.5 | Binding Native: `dlsym` + marshalling ABI (primitive widths) no `NativeRuntime`/`NativeBackend` | 🔴 H | `extern` conecta a `.so` no Native |
| 2.1.6 | Marshalling avançado: ponteiros/struct/array (fronteira segura, lifetime pelo GC) | 🔴 H | matriz/struct cruza a fronteira |
| 2.1.7 | JS: gap honesto `FFI002` (web/edge sem FFI nativo) | 🟢 E | extern no JS → FFI002 documentado |

**Critical path 2.1:** 2.1.1 → 2.1.2 → 2.1.3 antes de qualquer binding.

### 2.2 Codegen de compile-time formalizado (R4)

| # | Subtarefa | Dificuldade | DoD |
|---|-----------|-------------|-----|
| 2.2.1 | Inventário do codegen implícito existente (`KofRuntime`, runner de teste sintetizado, DDL de `entity`) | 🟢 E | lista fechada dos points atuais |
| 2.2.2 | Hook formal de codegen (fechado, não-macro): interface estável p/ gerar em compile-time | 🟡 M | um provider usa o hook |
| 2.2.3 | Migrar DDL de `entity` + runner de teste para o hook formal | 🟡 M | comportamento idêntico (same suite) |
| 2.2.4 | Base de `infra "prod" { }` (sacar sobre records) | 🟡 M | `infra` emite records de recurso |

### 2.3 Compile-time eval leve

| # | Subtarefa | Dificuldade | DoD |
|---|-----------|-------------|-----|
| 2.3.1 | Estender constant-folding a constantes de domínio (config, validação de schema) | 🟢 E | const de domínio dobra em compile-time |
| 2.3.2 | Detecção de ciclos no grafo de `infra` em compile-time | 🟡 M | ciclo → diagnóstico |

### 2.4 Scoped resources (RAII leve, sem ownership)

| # | Subtarefa | Dificuldade | DoD |
|---|-----------|-------------|-----|
| 2.4.1 | `auto-closed`/scope leve para handles (arquivo/GPU/conexão/FFI) sobre `try/finally` | 🟡 M | recurso fecha ao sair do escopo |
| 2.4.2 | Fronteira segura de buffer p/ zona sem GC (handles de FFI) | 🟡 M | handle liberado pelo GC na fronteira |

### 2.5 Variance / sealed (opcional, postergável)

| # | Subtarefa | Dificuldade | DoD |
|---|-----------|-------------|-----|
| 2.5.1 | Avaliar necessidade real (coleções científicas, pipelines) antes de abrir | 🔴 R | decisão escrita; não implementar até um domínio exigir |

---

## FASE 8 — Domínios Específicos

### T12-VERY-HARD: Science & Bio

**Objetivo:** BLAS/LAPACK, SciPy-like, genomics  
**Dificuldade:** 🔴 Muito Alta  
**Dependências:** FFI, type system, pkg manager

---

## Critical Path

Os itens que **bloqueiam** outros:

| Item | Bloqueia | Razão |
|------|----------|-------|
| `JVM-Class-File-Basic` | Todos os outros | Não há IR sem parser |
| `Decompiler-Structural` | `Diff-Framework` | Precisa de código Kof para comparar |
| `Diff-Framework` | `Migration-Reports` | Sem comparação, não há relatório |

**Itens paralelos (podem ser feitos juntos):**

- `JVM-Inspect-CLI` vs `Java-AST-Basic` vs `Decompiler `-`
- `Diff-Tests-Corpus` pode ter múltiplos casos
- Domínios (Science, Bio, IaC) são independentes

---

## Status Atual

| Fase | Componente | Status |
|------|-----------|--------|
| A/B | JVM inspect + Bytecode IR (`kof inspect`) | ✅ concluído |
| C | CFG Basic Blocks | ✅ concluído |
| D | Type Recovery | ✅ concluído |
| E | Decompiler (`kof decompile`) | ✅ concluído |
| E | Decompiler-Confidence | ✅ concluído |
| F | Java Translator (`kof translate`) | ✅ concluído |
| G | Differential Testing (`kof compare`) | ✅ concluído |
| H | Migration Reports (`kof migrate`) | ✅ concluído |
| 2.1–2.5 | TIER 2 (FFI/codegen/ct-eval/RAII/variance) | ⏳ quebrado em subtarefas, não iniciado (gated por TIER 1) |

## Próxima Ação Recomendada

**Tier 2.1.1** — sintaxe `extern` (lexer + parser) como primeiro osso do FFI
formalizado, desde que o gate TIER 1 esteja resolvido; caso contrário,
fechar primeiro os gaps do estágio SYSTEMS (R12).

---

## Regras Transversais

1. **Nunca inventar** — IR sem dados é "Unknown", não "Heuristic"
2. **Confidence Model** — Sempre marcar Nível de Confiança
3. **Traceability** — Sempre mapear Legacy → Kof
4. **Non-blocking** — Se não for recuperável, reportar, não inventar
5. **Test-first** — Cada implementação começa com teste