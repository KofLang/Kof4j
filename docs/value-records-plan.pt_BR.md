[English](value-records-plan.md) | [Português](value-records-plan.pt_BR.md)

# Value Records — tipos-valor first-class (PROMOVIDO para `docs/` 14/10 · TIER 2.7)

estado:
last: PROMOVIDO para `docs/` 14/10 (de `docs/development/`) — TODOS OS CINCO PASSOS POUSADOS com prova executada: 2.7.1 a keyword de front-end `value` (contextual, como `sealed`; PortuKof `valor registro`); 2.7.2 a ABI JVM (a classe record comum + `SymbolTable.ClassSymbol.isValueRecord`); 2.7.3 a ABI Native (a representação de heap atual — semântica de valor já entregue por imutabilidade + igualdade estrutural; pass-by-value invisível, explicitamente adiado); 2.7.4 a ABI JS (`Object.freeze(this)` no construtor do value record); 2.7.5 a célula de conformância `valuerecord` (4 alvos DONE) + a linha na matriz + o idiom em `training/idioms/records` + a seção no `learn/07`. TAMBÉM consertei no caminho: os rebuilds de IRClass no `Optimizer`/`JsBackend` descartavam `isRecord` (latente), e o modificador contextual PortuKof agora é canonicalizado.
próximo: contrato de referência — nenhuma fatia pendente. Trabalho futuro (uma decisão separada, se algum dia quiser): a otimização de pass-by-value/alocação em pilha no Native adiada; o erro de compilação de identidade (nenhum construto Kof exige identidade hoje).
local: docs/value-records-plan.pt_BR.md
dona: 192.168.100.14:0, flatt CLI, sem servidor opencode neste host; porta n/a
estado: promoted

**Estado:** PROMOVIDO para `docs/` 14/10 — **TODOS OS CINCO PASSOS POUSADOS com prova executada** (2.7.1 a keyword de front-end `value`; 2.7.2 a ABI JVM + `ClassSymbol.isValueRecord`; 2.7.3 a ABI Native — heap atual, pass-by-value adiado; 2.7.4 a ABI JS — `Object.freeze`; 2.7.5 a célula de conformância `valuerecord` DONE em 4 alvos + a linha na matriz + o idiom + a seção do learn). A frente está autorizada (`D-VALUE-RECORD` 16/09; `D-VALUE-RECORDS-GO` 28/09: `value` aplica SÓ a `record`, coleções guardam boxado, uso onde identidade é exigida = erro de compilação).
**Fonte:** `DECISIONS.md` §D-VALUE-RECORD (16/09, aceita) · issue #275 ·
`../roadmap.md` §23 TIER 2.7 (fila de passos 2.7.1–2.7.5)

## 1. Objetivo

Permitir que o programador declare que um agregado definido pelo usuário tem
**semântica de valor e nenhuma identidade de objeto observável** — para tipos
pequenos orientados a dados (vetores, coordenadas, cores, intervalos, tokens de
parser, estado de iterador), onde uma alocação de objeto separada adiciona
pressão de alocação, trabalho de GC, indireção e pior localidade de cache.
Depender de escape analysis da JVM não expressa intenção e não vale nos backends
Native/JS.

## 2. Proposta (sintaxe candidata)

```kof
value record Vec2(Float x, Float y)
value record Color(Int r, Int g, Int b, Int a)
```

Um `value record` tem o **mesmo modelo de dados imutável e conciso** de um
`record` existente; o modificador `value` adiciona "sem identidade". O `record`
comum mantém a semântica intacta.

## 3. Contrato (de `DECISIONS.md` §D-VALUE-RECORD)

- Igualdade/hash por campos (já é o contrato de record).
- **Aditivo e retrocompatível** (regra 2 do freeze): código existente continua
  compilando e rodando.
- A linguagem **intencionalmente não garante "alocação na stack"** — a garantia é
  semântica de valor + ausência de identidade; o armazenamento físico é decisão
  do backend.
- Fronteira: **core stdlib**, não pacote oficial (R1).

## 4. ABI por alvo (decisão de escopo antes do código — R7 escopo honesto)

| Alvo | Representação candidata |
|------|--------------------------|
| JVM | value/inline class (semântica de valor no `invokevirtual`, sem identidade `Object`) |
| Native | passagem por valor (struct por valor / registradores) |
| JS | objeto plano congelado (sem identidade) |

A ABI por alvo é uma **decisão de escopo explícita** antes de qualquer código
landar; o alvo JS pode legitimamente landar por último (R7).

## 5. Fila de passos

A ordem executável vive em `../roadmap.md` §23 **TIER 2.7** (2.7.1 keyword
`value` no front-end → 2.7.2 ABI JVM → 2.7.3 ABI Native → 2.7.4 ABI JS → 2.7.5
paridade + docs). Este documento é a justificativa de design; não duplica a fila.

## 6. Questões abertas (decisões da mantenedora)

- `value` vale também para `class`, ou só para `record`?
- Interação com genéricos/coleções (ex.: `List<Vec2>` — achatado ou boxed?)
- Diagnóstico exato quando um `value record` é usado onde a identidade é exigida.
- Se a representação JS é congelada/selada ou um objeto plano.

## 7. O que NÃO fazer

- **Não** prometer alocação na stack nem modelo de ownership/borrowing (não-objetivo
  permanente).
- **Não** mudar a semântica do `record` comum.
- **Não** abrir a frente sem autorização (regra 6 / R12): frentes novas não abrem
  antes do estágio SYSTEMS fechar.
