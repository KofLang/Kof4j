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

    // §108: o interpretador guarda Bool como Integer 0/1 na fronteira da
    // coleção → println(listOf(true)) dava [1, 0] vs JVM [true, false].
    // Box/unbox espelhando JvmOpCollections (Boolean ↔ Integer) na inclusão
    // e extração, nos 3 contêineres. Char NÃO precisa (JVM imprime [97,98]).
    // O discriminador é o toString do contêiner (println(l)) — println do
    // ELEMENTO individual passa pelo normalizeReturn do IR (dá "true" de
    // qualquer forma); o ArrayList.toString usa o toString do objeto cru.
    @Test
    void boolInCollectionsPrintsLikeJvm() throws IOException {
        parity("boolcoll", """
                main() {
                    var l = listOf(true, false)
                    println(l)
                    println(l.get(0))
                    println(l.contains(true))
                    var m = mapOf("yes", true)
                    println(m)
                    println(m.get("yes"))
                    println(m.containsKey("yes"))
                    var s = setOf(true)
                    println(s)
                    println(s.contains(true))
                    l.set(0, false)
                    println(l)
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
    void recordsInCollectionsUseContentEquals() throws IOException {
        // bug 104a: KofObj de record precisa de equals/hashCode/toString VIRTUAIS
        // — o JDK chama Object.* dentro de ArrayList.contains, HashMap e
        // List.toString; sem o override o interpretador batia por identidade.
        parity("reccoll", """
                record Point(Int x, Int y)
                main() {
                    var p1 = Point(1, 2)
                    var p2 = Point(1, 2)
                    println(listOf(p1).contains(p2))
                    println(setOf(p1).contains(p2))
                    println(mapOf(p1, 7).get(p2))
                    println(listOf(p1))
                    println(listOf(p1, Point(9, 9)).contains(p2))
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
    void staticFieldInitializer() throws IOException {
        // Regressão: campo estático com valor inicial (constante do campo no
        // bytecode JVM) precisa ser semeado no interpretador — sem isso o
        // REPL (var de topo → static) imprimia 0 em vez do valor.
        parity("static", """
                class G {
                    static Int counter = 41
                    static String label = "kof"
                }
                main() {
                    println(G.counter)
                    println(G.label)
                    G.counter = G.counter + 1
                    println(G.counter)
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

    // known-bugs #48 — json.decode<List<Record>> no interpretador: o
    // kof_json_decode_object_list (2 args) não era tratado (NoSuchMethodError).
    @Test
    void jsonDecodeListOfRecord() throws IOException {
        parity("json-decode-list", """
                import kof.json
                record P(Int x)
                main() {
                    var l = json.decode<List<P>>("[{\"x\":1},{\"x\":2}]")
                    println(l.size())
                    println(l.get(0).x)
                    println(l.get(1).x)
                }
                """);
    }

    @Test
    void printNullableStringNull() throws IOException {
        // §124: println(String? null) NPEava no interpretador (o scorer de
        // assinatura do invokeExternal empatava valueOf(char[]) com
        // valueOf(Object) p/ arg null e a ordem do getMethods() escolhia o
        // array). JVM/Native imprimem "null" — o interpretador tem de imprimir.
        parity("print-null-str", """
                String? nd() { return null }
                main() {
                    println(nd())
                }
                """);
        parity("map-miss-print", """
                main() {
                    var m = mapOf(1, "um")
                    m.remove(1)
                    println(m.get(1))
                }
                """);
    }

    @Test
    void printNullablePrimitiveNull() throws IOException {
        // §125 (decisão da mantenedora 12/09, opção A): println(<primitivo>?
        // null) imprime o DEFAULT do primitivo (precedente do map-miss,
        // SG-008), nunca "null". Antes o return de Int? crashava o bytecode
        // (VerifyError no JVM) e o interpretador (NoSuchMethodError
        // Integer.valueOf/1); agora o IR emite o default direto no return-site.
        parity("print-null-int", """
                Int? ni() { return null }
                main() {
                    println(ni())
                }
                """);
        parity("print-null-bool", """
                Bool? nb() { return null }
                main() {
                    println(nb())
                }
                """);
        parity("print-null-double", """
                Double? nd() { return null }
                main() {
                    println(nd())
                }
                """);
        parity("print-int-nullable-value", """
                Int? ni() { return 5 }
                main() {
                    println(ni() + 1)
                }
                """);
    }
}
