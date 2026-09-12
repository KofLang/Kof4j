# Anti-pattern — Chained-OR Membership (cadeia de `==`/`||`)

## Name

Testar pertencimento a um conjunto de valores com uma cadeia de comparações
(`x == "A" || x == "B" || x == "C" ...`) em vez de um conjunto.

## Problem

Cadeias extensas de `||` para verificar se um valor está em um grupo. É o
anti-idioma mais visível de "Java traduzido para Kof": ilegível, verboso,
fácil esquecer/duplicar uma entrada, intenção escondida, escala pessimamente.

> Kof deve deixar o código dizer **o que** está sendo feito (pertencimento a
> um conjunto), não **como** o programador implementou a busca. Se alguém
> escreve 50 `||` seguidos em Kof, a resposta não é "escreva melhor" — é
> "por que a Kof deixou isso passar?".

## Bad example (NUNCA)

```kof
Bool isQueryOperation(String operation) {
    return operation == "GetSession"
        || operation == "GetAccess"
        || operation == "GetDashboard"
        || operation == "GetToday"
        || operation == "GetTodayBoard"
        || operation == "ListIntakes"
        || operation == "GetIntake"
        || operation == "ListCompanies"
        || operation == "GetCompany"
        || operation == "ListCompanyMembers"
}
```

## Preferred approach (IDIOMÁTICO — verificado nos 3 targets)

`Set<T>` + `setOf(...)` variádico + `.contains(...)`:

```kof
Bool isQueryOperation(String operation) {
    val known = setOf(
        "GetSession",
        "GetAccess",
        "GetDashboard",
        "GetToday",
        "GetTodayBoard",
        "ListIntakes",
        "GetIntake",
        "ListCompanies",
        "GetCompany",
        "ListCompanyMembers"
    )
    return known.contains(operation)
}
```

Quando o conjunto é reutilizado, extraia para uma função que o devolve **ou**
declare como campo de classe — `Set<T>` como tipo declarado (campo, retorno
de função ou parâmetro) funciona nos 3 targets desde 0.2.6-beta (02/09, o
descriptor JVM de `kof.Set` foi mapeado para `java/util/HashSet`):

```kof
Set<String> knownOperations() {
    return setOf("GetSession", "GetAccess", "GetDashboard", "GetToday")
}

Bool isQueryOperation(String operation) {
    return knownOperations().contains(operation)
}
```

> **Histórico (fechado 02/09):** `setOf(...)` local sempre funcionou nos 3
> targets; mas `Set<T>` como **tipo declarado** (campo de classe ou retorno de
> função) falhava no **JVM** em runtime (`NoClassDefFoundError: kof/Set` —
> o descriptor `Lkof/Set;` não era materializado). Corrigido no
> `JvmTypeMapper` (mapeamento `kof.Set` → `java/util/HashSet`) + parser
> de membros de classe com retorno genérico (`Set<Int> foo()`, `List<String> bar()`).

## Why it is bad

| Cadeia de `||` | `setOf(...).contains(...)` |
|---|---|
| 10 linhas para 10 valores | 1 linha de intenção + os valores em lista |
| esquecer/duplicar entrada = bug silencioso | conjunto deduplica; adicionar = 1 linha |
| busca O(n) linear, intenção escondida | `Set` (hash) O(1) médio, intenção explícita |
| parece código gerado | parece código escrito por humano |

## Quando a cadeia curta é aceitável

Até **2** valores, `||` é mais direto do que criar um `Set`:

```kof
if (status == "active" || status == "pending") { ... }   // ok — 2 casos
```

Acima disso → `setOf(...).contains(...)`.

## O que AINDA NÃO existe (não alucine)

- **NÃO** existe operador `x in [...]` nem literal de conjunto `{"a","b"}` na
  linguagem hoje — usar isso **não compila** (ver `fake-idioms.md`).
- O idiom real e compilável é **`setOf(...).contains(x)`** (JVM/Native/JS,
  0.2.6-beta). Sintaxe `in` / literal de conjunto é evolução futura do
  compilador (planejamento de expressividade), não API atual.

## Anti-patterns relacionados

- `java-like-code.md` — Java traduzido literalmente para Kof
- `fake-idioms.md` — não ensinar/`in`, literal de conjunto, etc. (ainda não existe)
- Idiom correspondente: `training/idioms/collections.md` (seção "Membership")
