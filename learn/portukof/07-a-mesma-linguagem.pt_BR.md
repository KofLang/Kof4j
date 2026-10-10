[English](07-a-mesma-linguagem.md) | [Português](07-a-mesma-linguagem.pt_BR.md)

# 07 — A Mesma Linguagem

> **PortuKof — tudo executado de verdade.**

## Dois programas, um compilador

Você escreveu muitas linhas em PortuKof. A boa notícia: você já sabe Kof.
Os dois programas abaixo são **o mesmo programa** — só as palavras-troca, os
nomes e os textos continuam idênticos (identificadores e strings **nunca** são
traduzidos):

```ptkf
Int dobrar(Int n) { retorna n * 2 }

principal() {
    var total = 0
    para (var i em listaDe(1, 2, 3)) {
        total = total + dobrar(i)
    }
    se (total > 5) {
        diga("grande: " + total)
    } senao {
        diga("pequeno: " + total)
    }
}
```

```kf
Int dobrar(Int n) { return n * 2 }

main() {
    var total = 0
    for (var i in listOf(1, 2, 3)) {
        total = total + dobrar(i)
    }
    if (total > 5) {
        println("grande: " + total)
    } else {
        println("pequeno: " + total)
    }
}
```

Saída **medida** dos dois: `grande: 12`. E um teste do compilador prova que os
dois viram o **MESMO AST** depois da normalização de superfície — não
"parecido": igual (`PortuKofSurfaceE2ETest`).

## A extensão é quem decide, nunca o conteúdo

| arquivo | superfície |
|---|---|
| `jogo.ptkf` | PortuKof |
| `jogo.kf` / `jogo.kof` | Kof |

Um arquivo `.kf` cheio de `se/enquanto/diga` não vira PortuKof. Um `.ptkf` com
`if/while/println` funciona, mas é Kof canônico escrito por engano em português.

## `kof fmt`: o formatador fala português

```bash
kof fmt jogo.ptkf
```

O formatador parseia `.ptkf` pelo pipeline do perfil e reimprime **em
português a partir da AST** — nunca traduz texto com regex, nunca quebra seus
identificadores (`meuContador` continua `meuContador`), nunca toca strings nem
comentários. E `diga`/`diz` ficam como você escreveu.

## Erros em português

O arquivo `.ptkf` recebe mensagens de diagnóstico traduzidas, com código,
linha e coluna — você aprende a ler erros na sua língua.

## O que você já sabe agora

Depois das 7 aulas você domina a lógica de programação: variáveis, decisões,
repetições, funções, coleções, registros, entrada e saída. Isso **não expira**
— é a base de Python, JavaScript, Rust, Java, e do próprio Kof.

## Próximos passos

1. Reescreva um programa que você já fez em outra linguagem — em PortuKof.
2. Leia a referência completa:
   [`docs/languages/portukof.md`](../../docs/languages/portukof.md).
3. Rode o MESMO `.ptkf` em todos os alvos — JVM, nativo, JS, RISC-V, ARM —
   como qualquer programa Kof.
4. E quando quiser voltar ao inglês: troque só as palavras. A máquina é a mesma.
