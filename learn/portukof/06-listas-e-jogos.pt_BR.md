[English](06-listas-e-jogos.md) | [Português](06-listas-e-jogos.pt_BR.md)

# 06 — Listas e Jogos

> **PortuKof — tudo executado de verdade.**

## Lista: muitos valores em uma caixa

```ptkf
principal() {
    val frutas = listaDe("banana", "maçã", "uva")
    escrevaln(frutas.tamanho())
    escrevaln(frutas.obter(0))
}
```

Saída **medida**: `3`, `banana`

- `tamanho()` → quantos itens
- `obter(i)` → o item na posição `i` (contando a partir do **0**)

## Dicionário: chave → valor

```ptkf
principal() {
    val notas = mapaDe("Ana", 10)
    escrevaln(notas.obter("Ana"))
}
```

Saída **medida**: `10`

`mapaDe` guarda pares "chave → valor": você busca pela chave, não pela posição.

## Registro: um pedacinho de mundo com campos nomeados

```ptkf
registro Aluno(texto nome, Int idade)

principal() {
    val a = Aluno("Ana", 12)
    escrevaln(a.nome)
    escrevaln(a.idade)
}
```

Saída **medida**: `Ana`, `12`

`registro` (record) agrupa dados relacionados — sem classe, sem burocracia.

## Mini-projeto: quiz de acertos

Junte tudo — listas, `enquanto`, `se`, contador:

```ptkf
principal() {
    val perguntas = listaDe("Quanto e 2 + 2?", "Quanto e 3 * 3?")
    val respostas = listaDe(4, 9)
    var acertos = 0
    var i = 0
    enquanto (i < perguntas.tamanho()) {
        escrevaln(perguntas.obter(i))
        var r = respostas.obter(i)
        se (r == respostas.obter(i)) {
            acertos = acertos + 1
        }
        i = i + 1
    }
    escrevaln("Acertos: " + acertos)
}
```

Saída **medida**:

```text
Quanto e 2 + 2?
Quanto e 3 * 3?
Acertos: 2
```

(Este quiz "confere as respostas sozinho"; para o jogador responder de verdade,
troque `respostas.obter(i)` por `leia()` — você aprendeu `leia` na aula 02.)

## Matemática pronta

```ptkf
principal() {
    escrevaln(matematica.raizQuadrada(9.0))
}
```

Saída **medida**: `3.0`

`matematica` é a biblioteca padrão — nomes em português, mesma implementação
da `math` canônica.

## Desafio

1. Lista dos seus 5 jogos/filmes favoritos; mostre o 3º item.
2. `mapaDe` com seu nome → idade da sua mãe; mostre.
3. Crie `registro Animal(texto nome, texto som)` e faça 3 bichos falarem: `diga(a.nome + " faz " + a.som)`.
