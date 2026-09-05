# DIFFERENTIAL_TESTING.md — Teste Diferencial de Migrações

**Status:** EM DESENVOLVIMENTO — implementado (`kof compare`)
**Data:** 22 de agosto de 2026

---

## 1. Objetivo

Validar que uma migração preserva comportamento:

```text
Legacy Program
      ↓
Test Vector
      ↓
Original Runtime
      ↓
Expected Behavior

             VS

Test Vector
      ↓
Kof Program
      ↓
Observed Behavior
```

Objetivo:

```text
Legacy behavior
      ≈
Kof behavior
```

## 2. O que Comparar

Quando possível:

- stdout;
- stderr;
- exit code;
- exceptions;
- return values;
- arquivos;
- database mutations;
- mensagens;
- protocolos;
- outputs estruturados;
- side effects observáveis.

## 3. Divergências

A plataforma deve identificar divergências **automaticamente** e classificá-las:

```text
equivalente
divergente dentro do escopo definido
divergente fora do escopo
comportamento indefinido
```

Sistemas críticos exigem mais do que "compilou":

```text
compile
+
static analysis
+
behavioral testing
+
differential testing
+
manual review
+
migration report
```

## 4. Sistemas Sem Código-Fonte

Quando o fonte foi perdido, o comportamento observado do binário original
é a fonte de verdade:

```text
Binary
+
Metadata
+
Dependencies
+
Configuration
+
Database
+
Observed Behavior
```

A plataforma trata isso como **software archaeology** — não como conversão
trivial.

## 5. Migration Report

Uma migração produz um relatório com rastreabilidade. Estrutura conceitual
(valores ilustrativos — nenhuma métrica é real sem implementação e
metodologia):

```text
Kof Migration Report

Input:     legacy-application.jar
Output:    kof-application/

Recovered:     94.2%
Warnings:      17
Unrecoverable: 3
Manual review: 12 locations

Behavioral tests:
    183 passed
    2 divergent
```

## 6. Fases de Implementação

Fases G e H do roadmap (`LEGACY_MIGRATION.md`).

Antes de implementar: infraestrutura mínima de execução de um programa
original vs o programa Kof com os mesmos vetores de entrada, comparando
as saídas observáveis.