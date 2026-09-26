[English](23-testing.md) | [Português](23-testing.pt_BR.md)

# 23 — Testing

> **Status: implemented — `test "nome" { }`, `kof test` + `assert` — 0.5.0-beta**
>
> Testing Kof is writing Kof. The structured suite declares cases with
> `test "nome" { }`; `kof test` runs each test in isolation and reports
> PASS/FAIL **by name**, with an exit code based on the result. Inside the
> test, `assert(cond[, "msg"])` marks the failure with a clear message.

## test "nome" { } — structured suite

```kf
test "soma simples" {
    assert(2 + 2 == 4)
}

test "string igual" {
    assert("kof" == "kof", "strings iguais")
}

main() {
    // the real program; kof test ignores it (like cargo test)
}
```

```bash
kof test Suite.kf                     # jvm
kof test Suite.kf --target native     # native
kof test Suite.kf --target js         # js
```

Output:

```text
PASS soma simples
PASS string igual
0 failed of 2 tests
```

Each test runs **in isolation** (one failing does not interrupt the others).
The compiler knows the tests at compile-time — the names become literals in
the generated runner, without reflection. Failure = exit code ≠ 0, without a
stack trace.

## assert

```kf
main() {
    assert(2 + 2 == 4)
    assert("kof" == "kof", "strings iguais")
    assert(listOf(1, 2).size == 2)
}
```

## Property-style tests (seeded, reproducible)

There is no separate property-runner surface: a *property test* is a `test` block
that seeds `rng` and loops, using `assert(cond, msg)`. Same seed ⇒ same sequence
on every backend, so a failure is reproducible.

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

Fixtures use `close()` + `try/finally` — there is no `setup`/`teardown` keyword:

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

See also `training/idioms/stdlib.md` (`rng`); proof: `PropertyTestIdiomE2ETest`.

## kof test (whole programs)

`.kf` files **without** `test` blocks keep the previous contract: the file is
a program; PASS = exit code 0.

```bash
kof test src/tests/            # directory — recurses; each dir is a named suite
kof test math.kf               # single file
kof test src/tests --target native
```

Given a directory, `kof test` walks its subdirectories and treats each directory
as a **named suite** (name = path relative to the given root; `.` = the root),
printing a `suite <name>: P passed, F failed` summary per suite. A single-file
argument prints no suite lines.

Output:

```text
PASS src/tests/math.kf
FAIL src/tests/integ/broken.kf
suite .: 1 passed, 0 failed
suite integ: 0 passed, 1 failed
1 passed, 1 failed
```

The test fails when: the program does not compile, the process exits with a
code ≠ 0 (e.g. a false `assert`), or main is not found.

## process.exit(code)

For scripts and your own harnesses: terminates immediately with the given
code, on all three targets, without a stack trace.

```kf
main() {
    if (!validar()) {
        process.exit(1)
    }
}
```

## Convention

Each `.kf` test file is an independent executable program (it has
`main()`). `assert` is the primitive — there is no framework and no
annotations.

## Writing a suite

```kf
// math.kf — one file per area
Int soma(Int a, Int b) {
    return a + b
}
main() {
    assert(soma(2, 3) == 5)
    assert(soma(-1, 1) == 0, "negativos")
    println("math ok")
}
```

## JUnit (do not use)

The Java/JUnit ecosystem is **not** part of the language — no annotations, no
framework. The Kof test is the language: `test "nome" { assert(...) }` is the
unit of testing on any target.

## Running tests

```bash
kof test src/test/                        # directory — one program per file
kof test math.kf                          # single file
kof test src/test/ --target native        # target
```

## Next step

[Build Tools →](24-build-tools.md)

## Tags and fixtures (fatia 3, 26/09)

A test carries **optional tags** — plain extra string literals after the
name, no new syntax:

```kof
test "login feliz", "smoke", "auth" {
    assert(loginOk("mel", "kof"))
}
```

`kof test --tag smoke` keeps only the tests carrying that tag. The filter is
decided at **compile time**, so JVM, Native, JS and Script execute the very
same filtered catalog (parity by construction, rule 5):

```bash
kof test Suite.kf --tag smoke
```

```text
kof test: tag 'smoke' (1 of 2)
PASS login feliz
0 failed of 1 tests
```

If nothing matches, the runner says so and passes (`no tests with tag 'x'
(of 2)`) — an empty selection is reported, never silent.

`setup`/`teardown` are **ordinary zero-argument functions** in the same file.
When they exist, the runner wraps every test: a `setup` that throws **skips
its test** (`SKIP <name>: setup failed: <msg>` — not a failure), and
`teardown` runs after every test that setup let run — including failing ones
(it is the `finally` of the test block). No new blocks, no convention file
(SG-023 kept the surface; the runner gained the semantics).
