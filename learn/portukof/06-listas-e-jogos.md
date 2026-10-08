[English](06-listas-e-jogos.md) | [Português](06-listas-e-jogos.pt_BR.md)

# 06 — Lists and Games

> **PortuKof — every output below was measured by running the program.**

## List

```ptkf
principal() {
    val frutas = listaDe("banana", "maçã", "uva")
    escrevaln(frutas.tamanho())
    escrevaln(frutas.obter(0))
}
```

Measured output: `3`, `banana`. `tamanho()` = size, `obter(i)` = item at index
(counting from **0**).

## Map (dictionary)

```ptkf
principal() {
    val notas = mapaDe("Ana", 10)
    escrevaln(notas.obter("Ana"))
}
```

Measured output: `10`

## Record

```ptkf
registro Aluno(texto nome, Int idade)

principal() {
    val a = Aluno("Ana", 12)
    escrevaln(a.nome)
    escrevaln(a.idade)
}
```

Measured output: `Ana`, `12`

## Mini-project: scoring quiz

Lists + `enquanto` (while) + `se` (if) + counter, all measured:

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

Measured output:

```text
Quanto e 2 + 2?
Quanto e 3 * 3?
Acertos: 2
```

## Math library

```ptkf
escrevaln(matematica.raizQuadrada(9.0))
```

Measured output: `3.0` — Portuguese names, identical implementation to
canonical `math`.
