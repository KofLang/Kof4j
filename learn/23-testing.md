# 23 — Testes

> **Status: implementado — `test "nome" { }`, `kof test` + `assert` — 0.3.22-beta**
>
> Testar Kof é escrever Kof. A suíte estruturada declara casos com
> `test "nome" { }`; `kof test` roda cada teste isolado e reporta
> PASS/FAIL **por nome**, com exit code pelo resultado. Dentro do teste,
> `assert(cond[, "msg"])` marca a falha com mensagem clara.

## test "nome" { } — suíte estruturada

```kf
test "soma simples" {
    assert(2 + 2 == 4)
}

test "string igual" {
    assert("kof" == "kof", "strings iguais")
}

main() {
    // o programa real; o kof test o ignora (como cargo test)
}
```

```bash
kof test Suite.kf                     # jvm
kof test Suite.kf --target native     # native
kof test Suite.kf --target js         # js
```

Saída:

```text
PASS soma simples
PASS string igual
0 failed of 2 tests
```

Cada teste roda **isolado** (um falhando não interrompe os demais). O
compilador conhece os testes em compile-time — os nomes viram literais no
runner gerado, sem reflection. Falha = exit code ≠ 0, sem stack trace.

## assert

```kf
main() {
    assert(2 + 2 == 4)
    assert("kof" == "kof", "strings iguais")
    assert(listOf(1, 2).size == 2)
}
```

## kof test (programas inteiros)

Arquivos `.kf` **sem** blocos `test` mantêm o contrato anterior: o arquivo é
um programa; PASS = exit code 0.

```bash
kof test src/tests/            # diretório — um programa por arquivo
kof test math.kf               # arquivo único
kof test src/tests --target native
```

Saída:

```text
PASS src/tests/math.kf
FAIL src/tests/broken.kf
1 passed, 1 failed
```

O teste falha quando: o programa não compila, o processo sai com código ≠ 0
(ex.: um `assert` falso) ou o main não é encontrado.

## process.exit(code)

Para scripts e harnesses próprios: termina imediatamente com o código dado,
nos três targets, sem stack trace.

```kf
main() {
    if (!validar()) {
        process.exit(1)
    }
}
```

## Convenção

Cada arquivo `.kf` de teste é um programa executável independente (tem
`main()`). O `assert` é a primitive — não há framework nem annotations.

## Escrevendo uma suite

```kf
// math.kf — um arquivo por área
Int soma(Int a, Int b) {
    return a + b
}
main() {
    assert(soma(2, 3) == 5)
    assert(soma(-1, 1) == 0, "negativos")
    println("math ok")
}
```

## JUnit (não usar)

O ecossistema Java/JUnit **não** faz parte da linguagem — sem annotations,
sem framework. O teste Kof é a linguagem: `test "nome" { assert(...) }` é a
unidade de teste em qualquer target.

## Executando testes

```bash
kof test src/test/                        # diretório — um programa por arquivo
kof test math.kf                          # arquivo único
kof test src/test/ --target native        # target
```

## Próximo passo

[Build Tools →](24-build-tools.md)
