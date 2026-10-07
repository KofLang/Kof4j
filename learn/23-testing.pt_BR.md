[English](23-testing.md) | [Português](23-testing.pt_BR.md)

# 23 — Testes

> **Status: implementado — `test "nome" { }`, `kof test` + `assert` — 0.5.0-beta**
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

## Testes parametrizados (tabelas entrada → esperado)

Quando só a entrada muda, guie o teste por uma tabela em vez de duplicar blocos
`test`. O `testRows` (do `kof.test`) avalia cada linha isoladamente e reporta todas
as falhas numa única mensagem nomeada:

```kof
import kof.test

test "square table" {
    testRows(listOf(listOf("1", "1"), listOf("2", "4"), listOf("3", "9")), "square", (r: List<String>) -> {
        var input = r.get(0).toInt()
        if (input * input != r.get(1).toInt()) {
            throw "expected " + r.get(1) + ", got " + (input * input)
        }
    })
}
```

Cada linha é um `List<String>`; a lambda recebe uma linha e asserta com os helpers
`assertEqual*`. Uma falha nomeia a linha por índice e conteúdo
(`row 1 [2, 5]: expected 5, got 4`) e uma linha ruim nunca para as demais.

## Tempo e aleatoriedade determinísticos (seams de teste)

Lógica que depende do relógio ou de aleatoriedade é difícil de testar — a menos que o teste
injete a fonte. O `kof.test` fornece seams determinísticos, então o resultado é reprodutível:

```kof
import kof.test

Long elapsed(Long start, Long now) {
    return now - start
}

test "clock seam drives elapsed" {
    var clock = scriptedClock(listOf(100L, 400L, 900L))
    var start = clock()
    assertEqualLong(300L, elapsed(start, clock()), "first interval")
    assertEqualLong(800L, elapsed(start, clock()), "second interval")
}

test "random seam is reproducible" {
    var a = seededRandom(42)
    var b = seededRandom(42)
    assertEqualInt(a.next(6), b.next(6), "same seed, same sequence")
}
```

`fixedClock(millis)` congela um instante; `scriptedClock(times)` devolve a próxima medida por
chamada e, esgotada, repete a última. `seededRandom(seed)` dá a mesma sequência para a mesma seed
em todo backend e toda execução — a falha volta idêntica.

## Recursos temporários (harness de integração)

Um teste de integração sobe um recurso (um diretório temporário, um servidor, um banco) e precisa
limpá-lo **mesmo quando o teste falha**. O `withTempDir` cuida desse ciclo de vida: cria o
diretório, roda o corpo e remove a árvore recursivamente depois — o `finally` roda nos dois
caminhos:

```kof
import kof.test

test "escreve um arquivo num diretório temporário" {
    withTempDir("build/tmp-test", (d: String) -> {
        var f = File(Path(d).resolve("data.txt"))
        f.writeText("hello")
        assertEqualString("hello", f.readText(), "ida e volta")
    })
    // o diretório sumiu aqui, mesmo se o corpo lançou
}
```

Um recurso que sobe assincronamente é sondado com `waitUntil(probe, attempts, intervalMs)` — ele
sonda, dorme `intervalMs` entre tentativas e devolve o último resultado; nunca lança e nunca
inventa sucesso:

```kof
test "o servidor fica pronto" {
    withTempDir("build/tmp-srv", (d: String) -> {
        var up = waitUntil(() -> File(Path(d).resolve("ready")).exists(), 40, 25)
        assert(up, "o servidor nunca ficou pronto")
    })
}
```

O cleanup é Kof puro (`Directory.list()` + `File.delete()`), então é idêntico no JVM, Native e JS
— o `Directory.delete()` sozinho só remove um diretório vazio no JS (ver `known-bugs` §618), então
o `removeTree` percorre a árvore em vez de confiar nele.

## Testes estilo property (semeados, reprodutíveis)

Não existe uma superfície separada de property-runner: um *teste de property* é um
bloco `test` que semeia o `rng` e faz o loop, usando `assert(cond, msg)`. Mesma
seed ⇒ mesma sequência em todo backend, então uma falha é reprodutível.

```kof
test "addition commutes on random pairs" {
    rng.seed(42)
    var i = 0
    while (i < 200) {
        var a = rng.int(10000) - 5000
        var b = rng.int(10000) - 5000
        assert(a + b == b + a, "commutativity broke")
        i = i + 1
    }
}
```

Fixtures usam `close()` + `try/finally` — não há keyword `setup`/`teardown`:

```kof
test "writes then reads back" {
    var conn = db.connect(url)
    try {
        store(conn, record)
        assert(load(conn, record.id) != null)
    } finally {
        conn.close()
    }
}
```

Ver também `training/idioms/stdlib.md` (`rng`); prova: `PropertyTestIdiomE2ETest`.

## kof test (programas inteiros)

Arquivos `.kf` **sem** blocos `test` mantêm o contrato anterior: o arquivo é
um programa; PASS = exit code 0.

```bash
kof test src/tests/            # diretório — recursivo; cada dir é uma suíte nomeada
kof test math.kf               # arquivo único
kof test src/tests --target native
```

Dado um diretório, o `kof test` desce nos subdiretórios e trata cada diretório
como uma **suíte nomeada** (nome = caminho relativo à raiz dada; `.` = a raiz),
imprimindo um resumo `suite <nome>: P passed, F failed` por suíte. Um argumento
de arquivo único não imprime linhas de suíte.

Saída:

```text
PASS src/tests/math.kf
FAIL src/tests/integ/broken.kf
suite .: 1 passed, 0 failed
suite integ: 0 passed, 1 failed
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

## Tags e fixtures (fatia 3, 26/09)

Um teste carrega **tags opcionais** — literais de string extras apos o nome,
sintaxe nenhuma nova:

```kof
test "login feliz", "smoke", "auth" {
    assert(loginOk("mel", "kof"))
}
```

`kof test --tag smoke` mantem so os testes com aquela tag. O filtro e decidido
no **compile-time**, entao JVM, Native, JS e Script executam exatamente o mesmo
catalogo filtrado (paridade por construcao, rule 5):

```bash
kof test Suite.kf --tag smoke
```

```text
kof test: tag 'smoke' (1 of 2)
PASS login feliz
0 failed of 1 tests
```

Se nada casar, o runner avisa e passa (`no tests with tag 'x' (of 2)`) —
selecao vazia e reportada, nunca silencio.

`setup`/`teardown` sao **funcoes comuns sem argumentos** no mesmo arquivo.
Quando existem, o runner envolve cada teste: um `setup` que lanca **pula o
teste** (`SKIP <nome>: setup failed: <msg>` — nao e falha), e `teardown` roda
apos todo teste que o setup deixou rodar — inclusive os que falham (e o
`finally` do bloco do teste). Zero blocos novos, zero arquivo de convencao
(SG-023 manteve a superficie; o runner ganhou a semantica).
