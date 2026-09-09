package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KofJS End-to-End execution tests.
 *
 * These tests compile Kof source to JavaScript (Kof IR → JsIr → .mjs) and then
 * actually EXECUTE the generated module with Kof's embedded JavaScript engine
 * (dev.kof.runtime.KofJsRunner) — no Node.js or external runtime is required.
 * The tests assert on stdout and exit code.
 */
class KofJsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJs(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path jsFile = outDir.resolve("Default.mjs");
        assertTrue(Files.exists(jsFile), "Generated JS module should exist");
        ExecResult exec = execModule(jsFile, "");
        assertEquals(0, exec.exitCode(), "Exit code should be 0, output: '" + exec.output() + "'");
        assertEquals(expected, exec.output(), "Unexpected output");
        return exec.output();
    }

    private String runJsWithStdin(Path source, Path outDir, String stdin, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path jsFile = outDir.resolve("Default.mjs");
        ExecResult exec = execModule(jsFile, stdin);
        assertEquals(0, exec.exitCode(), "Exit code should be 0, output: '" + exec.output() + "'");
        assertEquals(expected, exec.output(), "Unexpected output");
        return exec.output();
    }

    private record ExecResult(int exitCode, String output) {
    }

    private ExecResult execModule(Path jsFile, String stdin) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(jsFile, out,
                new ByteArrayInputStream(stdin.getBytes()), out);
        return new ExecResult(exitCode, out.toString().trim());
    }

    // 1. Hello World ─────────────────────────────────────────────────

    @Test
    void execHelloWorld(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, "main() { println(\"Hello from KofJS\") }");
        runJs(source, tempDir.resolve("out"), "Hello from KofJS");
    }

    // 2. Arithmetic ──────────────────────────────────────────────────

    @Test
    void execArithmetic(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(10 + 20 * 3)
                println(100 / 7)
                println(17 % 5)
                println(2.5 * 2)
            }
            """);
        runJs(source, tempDir.resolve("out"), "70\n14\n2\n5");
    }

    @Test
    void execInt32Semantics(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 2147483647
                println(x + 1)
            }
            """);
        runJs(source, tempDir.resolve("out"), "-2147483648");
    }

    // 3. Variables ───────────────────────────────────────────────────

    @Test
    void execVariables(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                Int x = 10
                String name = "Mel"
                Bool active = true
                var y = x * 2
                println(x)
                println(name)
                println(active)
                println(y)
            }
            """);
        runJs(source, tempDir.resolve("out"), "10\nMel\ntrue\n20");
    }

    @Test
    void execAssignment(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 1
                x = x + 2
                x = x * 3
                println(x)
            }
            """);
        runJs(source, tempDir.resolve("out"), "9");
    }

    // 4. if/else ─────────────────────────────────────────────────────

    @Test
    void execIfElse(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 10
                if (x > 5) {
                    println("greater")
                } else {
                    println("smaller")
                }
                var y = 1
                if (y > 5) {
                    println("greater2")
                } else {
                    println("smaller2")
                }
            }
            """);
        runJs(source, tempDir.resolve("out"), "greater\nsmaller2");
    }

    @Test
    void execIfElseNested(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 7
                if (x > 5) {
                    if (x > 8) {
                        println("high")
                    } else {
                        println("mid")
                    }
                } else {
                    println("low")
                }
            }
            """);
        runJs(source, tempDir.resolve("out"), "mid");
    }

    @Test
    void execIfExpr(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var x = 3
                var label = if (x > 5) "big" else "small"
                println(label)
            }
            """);
        runJs(source, tempDir.resolve("out"), "small");
    }

    @Test
    void execBooleanConditions(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var a = true
                var b = false
                if (a) {
                    println("a")
                }
                if (a && b) {
                    println("both")
                }
                if (a || b) {
                    println("either")
                }
                if (!b) {
                    println("not b")
                }
                println(a == b)
                println(a != b)
            }
            """);
        runJs(source, tempDir.resolve("out"), "a\neither\nnot b\nfalse\ntrue");
    }

    // 5. Loops ───────────────────────────────────────────────────────

    @Test
    void execWhileLoop(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                var sum = 0
                while (i < 5) {
                    sum = sum + i
                    i = i + 1
                }
                println(sum)
            }
            """);
        runJs(source, tempDir.resolve("out"), "10");
    }

    @Test
    void execForLoop(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var sum = 0
                for (var i = 0; i < 5; i++) {
                    sum = sum + i
                }
                println(sum)
            }
            """);
        runJs(source, tempDir.resolve("out"), "10");
    }

    @Test
    void execDoWhile(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                do {
                    i = i + 1
                } while (i < 3)
                println(i)
            }
            """);
        runJs(source, tempDir.resolve("out"), "3");
    }

    @Test
    void execForIn(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var names = listOf("Mel", "Kof", "Kotlin")
                for (var n in names) {
                    println(n)
                }
            }
            """);
        runJs(source, tempDir.resolve("out"), "Mel\nKof\nKotlin");
    }

    @Test
    void execBreakContinue(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var i = 0
                while (true) {
                    i = i + 1
                    if (i == 2) {
                        continue
                    }
                    if (i > 4) {
                        break
                    }
                    println(i)
                }
            }
            """);
        runJs(source, tempDir.resolve("out"), "1\n3\n4");
    }

    // 6. Functions ───────────────────────────────────────────────────

    @Test
    void execFunctions(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            Int add(Int a, Int b) {
                return a + b
            }

            String shout(String s) {
                return s + "!"
            }

            main() {
                println(add(2, 3))
                println(shout("hey"))
                println(add(add(1, 2), add(3, 4)))
            }
            """);
        runJs(source, tempDir.resolve("out"), "5\nhey!\n10");
    }

    @Test
    void execRecursion(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            Int factorial(Int n) {
                if (n <= 1) {
                    return 1
                }
                return n * factorial(n - 1)
            }

            main() {
                println(factorial(5))
            }
            """);
        runJs(source, tempDir.resolve("out"), "120");
    }

    @Test
    void execLambdas(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var twice = (x: Int) -> x * 2
                println(twice(21))
            }
            """);
        runJs(source, tempDir.resolve("out"), "42");
    }

    // 7. Classes ─────────────────────────────────────────────────────

    @Test
    void execClasses(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class User {
                String name
                Int age

                constructor(String name, Int age) {
                    this.name = name
                    this.age = age
                }

                String greeting() {
                    return "Hello " + this.name
                }
            }

            main() {
                var u = User("Mel", 30)
                println(u.greeting())
                println(u.age)
            }
            """);
        runJs(source, tempDir.resolve("out"), "Hello Mel\n30");
    }

    @Test
    void execClassFields(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Counter {
                Int count

                void increment() {
                    this.count = this.count + 1
                }
            }

            main() {
                var c = Counter()
                c.increment()
                c.increment()
                println(c.count)
            }
            """);
        runJs(source, tempDir.resolve("out"), "2");
    }

    // 8. Constructors ────────────────────────────────────────────────

    @Test
    void execConstructors(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Point {
                Int x
                Int y

                constructor(Int x, Int y) {
                    this.x = x
                    this.y = y
                }
            }

            main() {
                var p = Point(3, 4)
                println(p.x)
                println(p.y)
            }
            """);
        runJs(source, tempDir.resolve("out"), "3\n4");
    }

    // #53 (metade JS): record com construtor explícito → o lowering injeta
    // super(Record.<init>) (exigido pelo verificador JVM), mas a classe JS de
    // record não tem pai → `SyntaxError: 'super' keyword unexpected here` e o
    // módulo inteiro caía. O emitter agora descarta o super sintético de
    // java.lang.Record. Paridade com JVM/script (ambos imprimem o valor).
    @Test
    void recordWithExplicitConstructorRunsOnJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Q(Int x) {
                constructor(Int x) {
                    this.x = x
                }
            }
            main() {
                println(Q(1).x())
            }
            """);
        runJs(source, tempDir.resolve("out"), "1");
    }

    // 9. Inheritance ─────────────────────────────────────────────────

    @Test
    void execInheritance(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class Animal {
                String name

                constructor(String name) {
                    this.name = name
                }

                String speak() {
                    return "..."
                }
            }

            class Dog extends Animal {
                constructor(String name) {
                    super(name)
                }

                String speak() {
                    return "Au au"
                }
            }

            main() {
                var a = Animal("bicho")
                var d = Dog("Rex")
                println(a.speak())
                println(d.speak())
                println(d.name)
            }
            """);
        runJs(source, tempDir.resolve("out"), "...\nAu au\nRex");
    }

    // 10. Interfaces ─────────────────────────────────────────────────

    @Test
    void execInterfaces(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            interface Greeter {
                String greet()
            }

            class Person implements Greeter {
                String name

                constructor(String name) {
                    this.name = name
                }

                String greet() {
                    return "Hi " + this.name
                }
            }

            main() {
                var g = Person("Mel")
                println(g.greet())
            }
            """);
        runJs(source, tempDir.resolve("out"), "Hi Mel");
    }

    // 11. Generics ───────────────────────────────────────────────────

    @Test
    void execGenerics(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            List<Int> ints() {
                return listOf(1, 2, 3)
            }

            main() {
                var xs = ints()
                println(xs.size)
                println(xs.get(1))
            }
            """);
        runJs(source, tempDir.resolve("out"), "3\n2");
    }

    // 12. List ───────────────────────────────────────────────────────

    @Test
    void execList(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var users = listOf("Mel", "Kof")

                println(users.get(0))
                println(users.size)

                users.add("Kotlin")
                println(users.size)
                println(users.contains("Kof"))
                println(users.contains("Java"))
                println(users.isEmpty())

                users.set(1, "Kof2")
                println(users.get(1))

                users.remove(0)
                println(users.size)

                users.clear()
                println(users.isEmpty())
            }
            """);
        runJs(source, tempDir.resolve("out"), "Mel\n2\n3\ntrue\nfalse\nfalse\nKof2\n2\ntrue");
    }

    // 13. String API ─────────────────────────────────────────────────

    @Test
    void execStringApi(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var s = "Hello World"

                println(s.length)
                println(s.toUpperCase())
                println(s.toLowerCase())
                println(s.substring(6))
                println(s.substring(0, 5))
                println(s.indexOf("World"))
                println(s.contains("ello"))
                println(s.startsWith("He"))
                println(s.endsWith("ld"))
                println(s.replace('l', 'L'))
                println(s.trim())
                println("a" + "b" + 1)
                println("abc" == "abc")
                println("abc" != "abd")
                println(s.charAt(1))
                var parts = s.split(" ")
                println(parts.length)
                println(parts[1])
            }
            """);
        runJs(source, tempDir.resolve("out"), """
            11
            HELLO WORLD
            hello world
            World
            Hello
            6
            true
            true
            true
            HeLLo WorLd
            Hello World
            ab1
            true
            true
            101
            2
            World""");
    }

    // String→número (github #51): toInt/toLong/toDouble/toFloat não existiam no
    // runtime JS — `texto.kof_string_to_int()` → TypeError em execução. Paridade
    // com JVM: parseInt/parseDouble de s.trim() validado (formato errado → throw).
    @Test
    void execStringToNumberConversion(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println("120000".toInt())
                println("7".toLong())
                println("2.5".toDouble())
                println("-12".toInt())
                try {
                    println("abc".toInt())
                } catch (String e) {
                    println("ERR")
                }
            }
            """);
        runJs(source, tempDir.resolve("out"), """
            120000
            7
            2.5
            -12
            ERR""");
    }

    // 14. Arrays ─────────────────────────────────────────────────────

    @Test
    void execArrays(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var arr = new Int[5]
                arr[0] = 10
                arr[1] = 20
                println(arr.length)
                println(arr[0])
                println(arr[1])
                println(arr[4])
            }
            """);
        runJs(source, tempDir.resolve("out"), "5\n10\n20\n0");
    }

    // 15. JSON ───────────────────────────────────────────────────────

    @Test
    void execJson(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(json.encode(42))
                println(json.encode("text"))
                println(json.encode(true))
                println(json.encode(listOf(1, 2, 3)))
                println(json.encode(listOf("a", "b")))

                var n = json.decode<Int>("123")
                println(n + 1)
                var s = json.decode<String>("\\"ok\\"")
                println(s)
                var xs = json.decode<List<Int>>("[10, 20]")
                println(xs.get(0))
            }
            """);
        runJs(source, tempDir.resolve("out"), "42\n\"text\"\ntrue\n[1,2,3]\n[\"a\",\"b\"]\n124\nok\n10");
    }

    @Test
    void execJsonObjects(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            class User(
                String name
            )

            main() {
                var users = listOf(User("Mel"), User("Kof"))
                println(json.encode(users))
                var u = json.decode<User>("{\\"name\\":\\"Mel\\"}")
                println(u.name)
            }
            """);
        runJs(source, tempDir.resolve("out"), "[{\"name\":\"Mel\"},{\"name\":\"Kof\"}]\nMel");
    }

    // 16. Exceptions ─────────────────────────────────────────────────

    @Test
    void execTryCatch(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "boom"
                } catch (String e) {
                    println("caught: " + e)
                }
                println("end")
            }
            """);
        runJs(source, tempDir.resolve("out"), "caught: boom\nend");
    }

    @Test
    void execTryFinally(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    println("body")
                } finally {
                    println("finally")
                }
                println("end")
            }
            """);
        runJs(source, tempDir.resolve("out"), "body\nfinally\nend");
    }

    @Test
    void execTryCatchFinally(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                try {
                    throw "x"
                } catch (String e) {
                    println("caught")
                } finally {
                    println("finally")
                }
                println("end")
            }
            """);
        runJs(source, tempDir.resolve("out"), "caught\nfinally\nend");
    }

    // 17. ESM module shape ───────────────────────────────────────────

    @Test
    void emitsEsModule(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            Int answer() {
                return 42
            }

            main() {
                println(answer())
            }
            """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "Compilation should succeed");
        String js = Files.readString(outDir.resolve("Default.mjs"));
        assertTrue(js.contains("import {"), "Generated module should use ESM imports");
        assertTrue(Files.exists(outDir.resolve("kof-runtime.mjs")), "Runtime module should exist");
        assertTrue(Files.exists(outDir.resolve("Default.mjs.map")), "Source map should exist");
    }

    // known-bugs #18 — kof-ui widget ids were `Object.keys(__kofNodes).length
    // + 1`: after remove() the length shrinks and the next widget REUSED a
    // live node's id (collision). Must use a monotonic counter.
    @Test
    void uiWidgetIdsUseMonotonicCounter(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var l = Label("hello")
                println("ok")
            }
            """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "Compilation should succeed");
        String runtime = Files.readString(outDir.resolve("kof-runtime.mjs"));
        assertFalse(runtime.contains("Object.keys(window.__kofNodes).length + 1"),
                "Length-based widget id would be reused after remove()");
        assertTrue(runtime.contains("kofNodeSeq"),
                "Runtime should use the monotonic kofNodeSeq counter");
    }

    // 18. Multiple source files ──────────────────────────────────────

    @Test
    void multipleSourceFiles(@TempDir Path tempDir) throws IOException {
        Path a = tempDir.resolve("A.kf");
        Files.writeString(a, """
            main() {
                println("from A")
            }
            """);
        Path b = tempDir.resolve("B.kf");
        Files.writeString(b, """
            main() {
                println("from B")
            }
            """);
        Path outA = tempDir.resolve("outA");
        Path outB = tempDir.resolve("outB");
        CompilationResult ra = driver.compile(a, outA, Target.JS);
        CompilationResult rb = driver.compile(b, outB, Target.JS);
        assertTrue(ra.success(), "A should compile");
        assertTrue(rb.success(), "B should compile");
        assertEquals("from A", execModule(outA.resolve("Default.mjs"), "").output());
        assertEquals("from B", execModule(outB.resolve("Default.mjs"), "").output());
    }

    // kof.io / kof.time on the KofJS target ──────────────────────────

    @Test
    void execStdlibTimeAndIo(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("kof-js-test.txt");
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var before = now()
                println(before > 0)
                var rc = writeFile("%s", "kof io")
                println(rc)
                var content = readFile("%s")
                println(content)
            }
            """.formatted(file.toString(), file.toString()));
        runJs(source, tempDir.resolve("out"), "true\n0\nkof io");
    }

    @Test
    void execReadLine(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var line = readLine()
                println("got: " + line)
            }
            """);
        runJsWithStdin(source, tempDir.resolve("out"), "hello stdin\n", "got: hello stdin");
    }

    @Test
    void logicalAndOrShortCircuit(@TempDir Path tempDir) throws IOException {
        // && / || booleanos devem short-circuitar no JS (o lado de não não é
        // avaliado). Antes o backend emitia & / | bitwise → os dois lados
        // eram sempre avaliados (f-rodou aparecia 3x em vez de 0x).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            Int f() {
                println("f-rodou")
                return 1
            }
            main() {
                if (false && f() > 0) {
                    println("x")
                }
                if (true || f() > 0) {
                    println("y")
                }
                var r = false && f() > 0
                println(r)
            }
            """);
        runJs(source, tempDir.resolve("out"), "y\nfalse");
    }

    @Test
    void bitwiseAndOrStillWorks(@TempDir Path tempDir) throws IOException {
        // & / | / ^ continuam bitwise (avaliando os dois lados) — o fix do
        // short-circuit não pode ter quebrado a aritmética de bits.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(3 & 5)
                println(3 | 5)
                println(3 ^ 5)
            }
            """);
        runJs(source, tempDir.resolve("out"), "1\n7\n6");
    }
}