[English](kof-expr.md) | [Português](kof-expr.pt_BR.md)

# Idioms — Kof.expr (padrões de texto)

**Status:** disponível · **Introduzido:** 0.6.7 · **Decisão:** `D-KOF-EXPR` · **Referência:** [docs/kof-expr.pt_BR.md](../../docs/kof-expr.pt_BR.md)

## O que é

`Kof.expr` é o modo nativo da linguagem de declarar um PADRÃO DE TEXTO como um
valor top-level nomeado. NÃO é regex: regex descreve como caracteres são
reconhecidos através de notação compacta; `Kof.expr` descreve a intenção do
padrão através de construções explícitas (`letter`, `one_or_more`, `capture`,
`between`).

## Formas canônicas

```kof
// declaração — um padrão nomeado, top-level
expr date {
    capture("day")   { exactly(2) digit }
    "/"
    capture("month") { exactly(2) digit }
    "/"
    capture("year")  { exactly(4) digit }
}

// match + capturas tipadas (um campo de record por captura)
var m = date.match("25/12/2025")
if (m != null) {
    println(m.year)
}

// teste booleano
if (wrd.matches(input)) { ... }

// busca / substituição
var at = wrd.find(text)            // primeiro índice ou -1
var all = wrd.findAll(text)        // todo casamento
var fixed = wrd.replace(text, "X") // todo casamento substituído

// composição por referência — padrões nomeiam padrões
expr row { date "-" word }
```

## Regras

* as classes são SEMÂNTICAS (`letter`/`digit`/`whitespace`/`word`/`hex`) —
  Unicode 16.0.0 pinado no compilador, idêntico em todo alvo; `hex` é ASCII
* quantificadores: `one_or_more` / `zero_or_more` / `optional` / `exactly(n)` /
  `between(min, max)`
* alternação: `one_of { … }` / `none_of { … }`
* âncoras: `^` / `$`
* sujeitos acima do orçamento documentado (2.000 caracteres em JVM/JS, 200 no
  Script) arremessam EXPR-E2 ANTES de casar — o matcher é limitado a pilha por
  design
* códigos de recusa são explícitos (EXPR001–EXPR004): construções não
  suportadas recusam em tempo de compilação, nunca um casamento errado silencioso
* uma declaração chamada `word` colide com o átomo de classe — o compilador
  recusa (família EXPR001); nomeie padrões de outro jeito
