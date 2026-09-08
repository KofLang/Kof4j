# Kof Language Reference

**Versão da especificação:** 0.3.0-beta · **Extraída de:** `kof-compiler` (branch `beta-0.3.0`, 06/09/2026)

Esta é a **referência da linguagem Kof**. Ela descreve *o que é um programa Kof
válido* e *qual é o significado desse programa* — independentemente de como o
compilador atual o implementa.

> **Regra fundadora desta referência:** nada aqui é inventado. Cada regra é
> extraída do código do compilador, dos testes ou de comportamento observável
> verificado por execução. Onde o comportamento não pôde ser determinado com
> segurança, a regra está marcada **UNSPECIFIED**. Uma especificação honesta
> sobre o que *não* sabe vale mais que uma especificação falsa e completa.

---

## Linguagem ≠ Compilador ≠ Target

Estes são três níveis distintos, frequentemente confundidos na documentação
antiga do projeto. A separação é o propósito central desta referência:

`text
Kof Language Specification  (este diretório)
        │
        │ define (o que é um programa válido e o que ele significa)
        ▼
   Linguagem Kof            (um conjunto de regras, não um binário)
        │
        │ implementada por
        ▼
   Kof Compiler             (uma implementação específica, em Java)
        │
        ├── Frontend (Lexer, Parser, AST, Análise Semântica)
        ├── Middle-end (IR, Otimizações)
        └── Backends (JVM, Native, JS)
        │
        │ produz
        ▼
   Targets                  (JVM, Native x86_64/riscv64/aarch64, JS, Android)
`

- **Kof** é a *linguagem de programação*. Existe como conjunto de regras.
- **Kof Compiler** é *uma implementação* da linguagem (o `kof-compiler` deste
  repositório, escrito em Java). Não é a definição da linguagem.
- **Kof4J** é o *backend/linha JVM* (bytecode via ASM). **KofNative** é o
  *backend nativo* (asm x86_64/riscv64/aarch64). **KofJS** é o *backend
  JavaScript* (ESM). São **targets de compilação**, não dialetos da linguagem.

A intenção conceitual é:

`text
mesma linguagem Kof ──┬── JVM
                      ├── Native
                      └── JS
`

e **não** `Kof JVM` / `Kof Native` / `Kof JS` como linguagens semanticamente
diferentes. Quando há divergência real entre targets, ela é registrada como
*limitação de target* ou *comportamento dependente de target* (ver
[specification-status.md](specification-status.md) e
[../specification-gaps.md](../specification-gaps.md)), nunca escondida.

---

## O que cada documento responde

| Documento | Pergunta que responde |
|---|---|
| [lexical-structure.md](lexical-structure.md) | Quais são os tokens válidos? (identificadores, literais, operadores, comentários, keywords) |
| [grammar.md](grammar.md) | Qual é a gramática formal? (EBNF léxico e sintático, precedência, associatividade) |
| [syntax.md](syntax.md) | Como se escreve cada construção? (forma concreta, exemplos) |
| [types.md](types.md) | Quais tipos existem e como se escrevem? |
| [type-system.md](type-system.md) | Quais operações são válidas? Quando há erro de tipo? O que o sistema de tipos garante? |
| [expressions.md](expressions.md) | Semântica de cada expressão e operador. |
| [statements.md](statements.md) | Semântica de cada statement e controle de fluxo. |
| [functions.md](functions.md) | Declaração, tipos, parâmetros, retorno, recursão, ponto de entrada. |
| [closures.md](closures.md) | Lambdas, function types, captura de variáveis. |
| [classes.md](classes.md) | Classes, records, enums, interfaces, entities, herança, visibilidade. |
| [modules.md](modules.md) | Pacotes, imports, resolução de nomes, unidade de compilação. |
| [semantics.md](semantics.md) | Modelo de execução, ordem de avaliação, escopo, tempo de vida, erros. |
| [specification-status.md](specification-status.md) | Classificação de cada feature (Stable/Experimental/…). |

A **implementação do compilador** (pipeline, IR, otimizações, backends) tem
documento próprio: [../compiler-architecture.md](../compiler-architecture.md).
Detalhes internos de Java, classes do compilador e estruturas de implementação
**não pertencem** a esta referência — exceto quando são necessários para
explicar um comportamento observável da linguagem (nesse caso, a referência
cita o arquivo-fonte como evidência, não como definição).

---

## Legenda de status

Cada regra pode carregar uma etiqueta. As categorias usadas nesta referência
são as que fazem sentido para o estado atual do Kof (beta):

| Etiqueta | Significado |
|---|---|
| **Stable** | Comportamento definido pela linguagem, congelado (regra de semântica congelada 0.2.6-beta). Não muda sem bump de versão + migração. |
| **Experimental** | Implementado e testável, mas sujeito a mudança. Não congelado. |
| **Implementation-defined** | A linguagem não fixa o resultado; o compilador atual decide. Outro compilador Kof pode divergir legitimamente. |
| **Target-specific** | O comportamento observável depende do target (JVM/Native/JS). Documentado como diferença, não escondido. |
| **Unspecified** | A linguagem ainda não define este ponto. Não é "qualquer coisa vale" — é "a especificação não sabe ainda". |
| **Planned** | Existe plano/documento, mas **não** está implementado. Nunca deve ser usado como se existisse. |

A etiqueta **Unspecified** é preferível a uma regra inventada. Ver
[specification-status.md](specification-status.md) para a classificação por
feature e [../specification-gaps.md](../specification-gaps.md) para o catálogo
de lacunas (SG-00x) e divergências entre documentação, código e testes.

---

## Como esta referência é verificável

Toda afirmação normativa aponta para uma **evidência**:

- **Código** — `arquivo.java:linha` no `kof-compiler` (ex.: precedência em
  ExpressionParser (precedence)).
- **Teste** — um teste na suíte que demonstra a regra (ex.:
  `PackagesE2ETest`, `KofSwitchExprE2ETest`).
- **Execução** — comportamento observado rodando um programa (usado para
  distinguir "compila" de "funciona"; marcado como *probe* quando não há teste
  dedicado).

Quando código, teste e documentação divergem, a divergência é registrada em
[../specification-gaps.md](../specification-gaps.md) — nunca resolvida
silenciosamente a favor de uma das fontes.

---

## Conformance (possibilidade futura, não implementada)

Uma definição de conformidade seria:

> Um compilador Kof é **conforme à especificação** quando aceita todos os
> programas que a especificação declara válidos, rejeita os que ela declara
> inválidos (com os diagnósticos especificados), e produz para cada programa
> válido o significado que a especificação define.

Hoje isso **não pode ser rigorosamente definido** porque partes da linguagem
estão **Unspecified** ou **Implementation-defined** (subtipagem por herança não
é checada no type checker; coerção `bool→numérico` passa na análise mas não tem
emissão; `val` não impede reatribuição; generics sem variance/bounds). A
seção "Conformance" de [specification-status.md](specification-status.md)
lista exatamente o que ainda impede uma definição rigorosa. Não há, por ora,
um *conformance suite* formal — mas os testes E2E por target são o embrião de
um, e cada regra desta referência marca se tem teste (evidência) ou é
candidata a novo teste de conformidade.
