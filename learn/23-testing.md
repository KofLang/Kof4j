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

## Parameterized tests (input → expected tables)

When only the input changes, drive the test from a table instead of duplicating test
blocks. `testRows` (from `kof.test`) evaluates every row in isolation and reports all the
failures in one named message:

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

Each row is a `List<String>`; the lambda receives one row and asserts with the
`assertEqual*` helpers. A failure names the row by index and content
(`row 1 [2, 5]: expected 5, got 4`) and one bad row never stops the others.

## Deterministic time and randomness (test seams)

Logic that depends on the clock or on randomness is hard to test — unless the test injects the
source. `kof.test` provides deterministic seams, so the outcome is reproducible:

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

`fixedClock(millis)` freezes one instant; `scriptedClock(times)` returns the next reading per
call and, once exhausted, repeats the last. `seededRandom(seed)` gives the same sequence for the
same seed on every backend and every run — a failure comes back identically.

## Temporary resources (integration harness)

An integration test brings up a resource (a temp directory, a server, a database) and must clean
it up **even when the test fails**. `withTempDir` owns that lifecycle: it creates the directory,
runs the body and recursively removes the tree afterwards — the `finally` runs on both paths:

```kof
import kof.test

test "writes a file into a temp dir" {
    withTempDir("build/tmp-test", (d: String) -> {
        var f = File(Path(d).resolve("data.txt"))
        f.writeText("hello")
        assertEqualString("hello", f.readText(), "round trip")
    })
    // the directory is gone here, even if the body threw
}
```

A resource that comes up asynchronously is polled with `waitUntil(probe, attempts, intervalMs)`
— it probes, sleeps `intervalMs` between attempts, and returns the last result; it never throws
and never invents success:

```kof
test "server becomes ready" {
    withTempDir("build/tmp-srv", (d: String) -> {
        var up = waitUntil(() -> File(Path(d).resolve("ready")).exists(), 40, 25)
        assert(up, "server never became ready")
    })
}
```

The cleanup is pure Kof (`Directory.list()` + `File.delete()`), so it is identical on JVM, Native
and JS — `Directory.delete()` itself only removes an empty directory on JS (see `known-bugs` §618),
so `removeTree` walks the tree instead of relying on it.

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
