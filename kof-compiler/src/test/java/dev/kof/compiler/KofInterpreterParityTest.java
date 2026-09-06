package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate de paridade PERMANENTE: o KofInterpreter (target de execução direta)
 * deve produzir saída byte-idêntica ao backend JVM compilado + fork, para um
 * corpus que cobre os construtores da linguagem. Paridade por construção —
 * mesma IR, dois executores. Qualquer divergência é bug do interpretador.
 */
class KofInterpreterParityTest {

    private void parity(String label, String code) throws IOException {
        Path d = Files.createTempDirectory("kip-" + label);
        Path f = d.resolve("Main.kf");
        Files.writeString(f, code);

        // interpretado (sem bytecode, sem fork)
        String interpOut;
        int interpExit;
        try {
            KofInterpreter.Result r = new CompilerDriver().interpret(List.of(f), d, new String[0]);
            interpOut = r.stdout();
            interpExit = r.exitCode();
        } catch (KofInterpretException e) {
            interpOut = "FRONTEND-ERR";
            interpExit = -1;
        }

        // compilado + fork JVM real
        String jvmOut;
        int jvmExit;
        Path outDir = d.resolve("o");
        CompilationResult cr = new CompilerDriver().compile(f, outDir, Target.JVM);
        if (!cr.success()) {
            jvmOut = "FRONTEND-ERR";
            jvmExit = -1;
        } else {
            try {
                ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
                pb.redirectErrorStream(false);
                Process p = pb.start();
                jvmOut = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                jvmExit = p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }

        assertEquals(jvmExit, interpExit, label + ": exit code divergente");
        assertEquals(jvmOut, interpOut, label + ": stdout divergente (interpretado vs JVM)");
    }

    @Test
    void arithmeticAndComparisons() throws IOException {
        parity("arith", """
                main() {
                    println(10 + 20 * 3)
                    println(100 / 7)
                    println(17 % 5)
                    println(2 < 3)
                    println(3 <= 3)
                    println(4 > 5)
                    println(-7)
                    println(!true)
                    println(6 & 3)
                    println(6 | 3)
                    println(6 ^ 3)
                    println(1 << 4)
                    println(256 >> 2)
                }
                """);
    }

    @Test
    void floatsAndLongs() throws IOException {
        parity("float", """
                main() {
                    var a = 1.5
                    var b = 2.5
                    println(a + b)
                    println(a * b)
                    println(a < b)
                    var big: Long = 9000000000
                    println(big + 1)
                    println(big > 100)
                    var c: Float = 3.25
                    println(c * 2)
                }
                """);
    }

    @Test
    void stringsAndChars() throws IOException {
        parity("string", """
                main() {
                    var s = "Hello World"
                    println(s.length)
                    println(s.substring(6))
                    println(s.contains("World"))
                    println(s.startsWith("He"))
                    println(s.endsWith("ld"))
                    println(s.indexOf("o"))
                    println(s.toUpperCase())
                    println(s.split(" ").length)
                    println(s.charAt(1))
                    println("a" + "b" + "c")
                    println("ab" == "ab")
                    println("42".toInt() + 1)
                    var c = 'A'
                    println(c)
                    println(c + 1)
                }
                """);
    }

    @Test
    void collectionsAndHigherOrder() throws IOException {
        parity("coll", """
                main() {
                    var l = listOf(1, 2, 3, 4, 5)
                    println(l.size())
                    println(l.get(0))
                    println(l.contains(3))
                    println(l.isEmpty())
                    println(l.map((x: Int) -> x * 2).reduce((a: Int, b: Int) -> a + b, 0))
                    println(l.filter((x: Int) -> x % 2 == 0).size())
                    var m = mapOf("a", 1)
                    m.put("b", 2)
                    println(m.get("a"))
                    println(m.size())
                    println(m.containsKey("b"))
                    var s = setOf("x", "y", "z")
                    println(s.size())
                    println(s.contains("y"))
                    s.remove("y")
                    println(s.contains("y"))
                }
                """);
    }

    @Test
    void recordsAndClasses() throws IOException {
        parity("rec", """
                record Point(Int x, Int y)
                class Counter {
                    Int count
                    public constructor() { this.count = 0 }
                    void inc() { this.count = this.count + 1 }
                    Int get() { return this.count }
                }
                main() {
                    var p1 = Point(1, 2)
                    var p2 = Point(1, 2)
                    var p3 = Point(9, 9)
                    println(p1 == p2)
                    println(p1 == p3)
                    println(p1)
                    println(p1.x())
                    var c = Counter()
                    c.inc()
                    c.inc()
                    c.inc()
                    println(c.get())
                }
                """);
    }

    @Test
    void controlFlow() throws IOException {
        parity("flow", """
                classify(n: Int): String {
                    if (n < 0) { return "neg" }
                    else if (n == 0) { return "zero" }
                    else { return "pos" }
                }
                main() {
                    println(classify(-5))
                    println(classify(0))
                    println(classify(7))
                    var i = 0
                    while (i < 5) { println(i); i = i + 1 }
                    for (var it in listOf("a", "b", "c")) { println(it) }
                    var sum = 0
                    for (var k in listOf(10, 20, 30)) { sum = sum + k }
                    println(sum)
                    var x = 2
                    var desc = switch (x) {
                        case 1 -> "um"
                        case 2 -> "dois"
                        default -> "outro"
                    }
                    println(desc)
                }
                """);
    }

    @Test
    void patternMatching() throws IOException {
        parity("match", """
                record Point(Int x, Int y)
                describe(o: Object): String {
                    switch (o) {
                        case String s: return "str:" + s
                        case Point(var px, var py): return "pt:" + px + "," + py
                        default: return "other"
                    }
                }
                main() {
                    println(describe("hi"))
                    println(describe(Point(3, 4)))
                    println(describe(42))
                }
                """);
    }

    @Test
    void nullSafety() throws IOException {
        parity("null", """
                find(k: String): String? {
                    if (k == "ok") { return "achou" }
                    return null
                }
                main() {
                    var v = find("ok")
                    if (v != null) { println(v) }
                    var w = find("no")
                    if (w == null) { println("nada") }
                    else { println(w) }
                }
                """);
    }

    @Test
    void closuresCapture() throws IOException {
        parity("closure", """
                main() {
                    var base = 10
                    var addN = (x: Int) -> x + base
                    println(addN(5))
                    base = 20
                    println(addN(5))
                    var make = (n: Int) -> (x: Int) -> x * n
                    var dbl = make(2)
                    println(dbl(21))
                }
                """);
    }

    @Test
    void tryCatchFinallyThrow() throws IOException {
        parity("try", """
                risky(n: Int): Int {
                    if (n < 0) { throw "negativo" }
                    return n * 2
                }
                main() {
                    try {
                        println(risky(5))
                    } catch (String e) {
                        println("caught:" + e)
                    } finally {
                        println("fin1")
                    }
                    try {
                        println(risky(-1))
                    } catch (String e) {
                        println("caught:" + e)
                    } finally {
                        println("fin2")
                    }
                }
                """);
    }

    @Test
    void recursion() throws IOException {
        parity("recur", """
                fib(n: Int): Int {
                    if (n < 2) { return n }
                    return fib(n - 1) + fib(n - 2)
                }
                fact(n: Int): Int {
                    if (n <= 1) { return 1 }
                    return n * fact(n - 1)
                }
                main() {
                    println(fib(10))
                    println(fact(6))
                }
                """);
    }

    @Test
    void arrays() throws IOException {
        parity("array", """
                main() {
                    var arr = new Int[5]
                    var i = 0
                    while (i < 5) { arr[i] = i * i; i = i + 1 }
                    println(arr.length)
                    println(arr[4])
                    var names = new String[2]
                    names[0] = "a"
                    names[1] = "b"
                    println(names[0] + names[1])
                }
                """);
    }

    @Test
    void enums() throws IOException {
        parity("enum", """
                enum Color { RED, GREEN, BLUE }
                main() {
                    println(Color.RED)
                    println(Color.values().size())
                }
                """);
    }

    @Test
    void spawnAwait() throws IOException {
        parity("spawn", """
                compute(): Int { return 21 }
                main() {
                    val h = spawn compute()
                    println(await h * 2)
                    var i = 0
                    while (i < 3) {
                        val hh = spawn compute()
                        println(await hh)
                        i = i + 1
                    }
                }
                """);
    }

    @Test
    void jsonEncode() throws IOException {
        parity("json", """
                import kof.json
                record User(String name, Int age)
                main() {
                    println(json.encode(User("mel", 26)))
                    println(json.encode(listOf(1, 2, 3)))
                    println(json.encode(mapOf("k", 9)))
                }
                """);
    }
}
