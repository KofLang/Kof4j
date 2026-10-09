[English](README.md) | [Português](README.pt_BR.md)

# Aprender Programação com PortuKof 🚀

**PortuKof é o Kof em português.** A mesma linguagem, os mesmos erros, o mesmo
poder — só que as palavras são suas: `principal`, `se`, `enquanto`, `escreva`.

## Para quem é esta trilha

- **Crianças e adolescentes** (10+) que querem aprender lógica de programação
  sem brigar com o inglês no primeiro dia.
- **Professores** procurando uma primeira linguagem que nunca vira dialeto de
  brinquedo: o que se aprende aqui é programação de verdade.
- **Usuários de Kof** que querem ver a superfície pt-BR funcionando ponta a ponta.

## A ideia central (sem truque)

PortuKof **não é uma linguagem separada**. É a superfície humana do Kof.
Por baixo é tudo igual:

```text
programa .ptkf → mesmo lexer/parser → MESMA AST → MESMO IR → MESMOS backends
```

Quem termina esta trilha sabe: variáveis, decisões, laços, funções, listas,
registros — e roda o MESMO código na JVM, nativo, RISC-V, ARM e JS, exatamente
como qualquer programa Kof.

## Como rodar (3 jeitos)

```bash
kof script ola_mundo.ptkf   # rápido (interpretado, ótimo para brincar)
kof run ola_mundo.ptkf      # compila e roda na JVM
kof check ola_mundo.ptkf    # só confere erros, sem rodar
```

Todo programa desta trilha foi **executado de verdade**; as saídas mostradas
são reais. Os exemplos oficiais vivem em `examples/portukof/`.

## A trilha

| Aula | O que você aprende |
|---|---|
| [01 — Primeiro programa](01-primeiro-programa.pt_BR.md) | `principal`, `escreva`, `diga`, `escrevaln` |
| [02 — Variáveis e textos](02-variaveis-e-textos.pt_BR.md) | `var`, `val`, `+`, `tamanho()`, `leia()` |
| [03 — Decisões](03-decisoes.pt_BR.md) | `se/senao`, `escolha`, `conjuntoDe().contem()` |
| [04 — Repetições](04-repeticoes.pt_BR.md) | `para…em`, `enquanto`, `sair`, `segue` |
| [05 — Funções](05-funcoes.pt_BR.md) | tipo antes do nome, `retorna`, corpo-expressão |
| [06 — Listas e jogos](06-listas-e-jogos.pt_BR.md) | `listaDe`, `mapaDe`, `registro`, quiz |
| [07 — A mesma linguagem](07-a-mesma-linguagem.pt_BR.md) | gêmeos `.ptkf`/`.kf`, `kof fmt`, próximos passos |

## Dúvidas de sintaxe

A referência completa (tabelas de vocabulário, diagnósticos PT, açúcar de fala
`diga`/`diz`, tooling) está em
[`docs/languages/portukof.pt_BR.md`](../../docs/languages/portukof.pt_BR.md).
O compilador é a autoridade: na dúvida, `kof check`.
