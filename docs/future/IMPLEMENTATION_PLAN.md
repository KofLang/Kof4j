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

## FASE 7 — Platform Universal

### T9-HARD: Type System Expansion

**Objetivo:** Tipos avançados (Map, Set, Option, enum, sealed)  
**Dificuldade:** 🔴 Alta  
**Dependências:** Compiler core stable  

### T10-HARD: Concurrency Primitives

**Objetivo:** Channels, select!, async/await completo  
**Dificuldade:** 🔴 Alta  
**Dependências:** `spawn`/`await` existentes

### T11-HARD: FFI Formalized

**Objetivo:** `extern "c"` functions, .so/.dll loading  
**Dificuldade:** 🔴 Alta  
**Dependências:** Runtime threading

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

## Status Atual (24h)

| Phase | Status | Próximos Passos |
|-------|--------|-----------------|
| Phase 1 | ✅ Parcial (JVM_CLASS_FILE parser tem, mas não em CLI) | CLI `kof inspect` |
| Phase 2 | ❌ Pending | Block para CFG |
| Phase 3 | ❌ Pending | Parser Java sem |
| Phase 4 | ❌ Pending | Decompiler sem |
| Phase 5 | ❌ Pending | Framework sem |
| Phase 6 | ❌ Pending | Reports sem |
| Phase 7 | ❌ Pending | Domínio sem infra |
| Phase 8 | ❌ Pending | Priority only |

---

## Próxima Ação Recomendada

**Priority #1: `JVM-Class-File-Basic`** — Este é o bloco inicial. Criar `kof inspect` CLI que lê class file e imprime JSON.

**Motivo:** É o **único item sem dependências** e **todos os outros** dependem dele.

---

## Regras Transversais

1. **Nunca inventar** — IR sem dados é "Unknown", não "Heuristic"
2. **Confidence Model** — Sempre marcar Nível de Confiança
3. **Traceability** — Sempre mapear Legacy → Kof
4. **Non-blocking** — Se não for recuperável, reportar, não inventar
5. **Test-first** — Cada implementação começa com teste