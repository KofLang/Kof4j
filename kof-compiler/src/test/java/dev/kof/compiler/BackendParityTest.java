package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backend parity tests: the same Kof source compiled to JVM and KofJS must
 * produce the same observable behavior (stdout, exit code).
 *
 * Native is added when the toolchain is available and the compiled binary
 * runs (see NativeDebugTest conventions).
 */
class BackendParityTest {

    private final CompilerDriver driver = new CompilerDriver();

    private record RunResult(int exitCode, String output) {
    }

    private RunResult runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            return new RunResult(ec, output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private RunResult runJs(Path outDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new ByteArrayInputStream(new byte[0]), out);
        return new RunResult(exitCode, out.toString().trim());
    }

    private void assertParity(Path source, Path outJvm, Path outJs, String expected) throws IOException {
        CompilationResult rjvm = driver.compile(source, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        CompilationResult rjs = driver.compile(source, outJs, Target.JS);
        assertTrue(rjs.success(), "JS compile failed: " + rjs.diagnostics().getDiagnostics());

        RunResult jvm = runJvm(outJvm);
        RunResult js = runJs(outJs);

        assertEquals(0, jvm.exitCode(), "JVM exit code, output: " + jvm.output());
        assertEquals(0, js.exitCode(), "JS exit code, output: " + js.output());
        assertEquals(jvm.output(), js.output(), "stdout parity JVM vs JS");
        assertEquals(expected, jvm.output(), "expected output");
    }

    private void runParity(String program, String expected, Path tempDir, String name) throws IOException {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        assertParity(source, tempDir.resolve(name + "-jvm"), tempDir.resolve(name + "-js"), expected);
    }

    @Test
    void parityHelloWorld(@TempDir Path tempDir) throws IOException {
        runParity("main() { println(\"Hello, Kof!\") }", "Hello, Kof!", tempDir, "hello");
    }

    @Test
    void parityStringDoubleConcat(@TempDir Path tempDir) throws IOException {
        // 02/09: "str" + double descartava o operando FP (yield incondicional
        // no guard de concat) → saída vazia. Agora concatena em JVM/JS/Native.
        runParity("""
                main() {
                    println("r=" + 2.5)
                    println("d=" + 1.5 + 2.5)
                    println("pi=" + 3.14159)
                    println("f=" + 1.5f)
                }
                """, "r=2.5\nd=1.52.5\npi=3.14159\nf=1.5", tempDir, "concat");
    }

    @Test
    void parityArithmetic(@TempDir Path tempDir) throws IOException {
        runParity("""
                main() {
                    println(10 + 20 * 3)
                    println(100 / 7)
                    println(17 % 5)
                    println(2.5 * 3)
                    println(2147483647 + 1)
                }
                """, "70\n14\n2\n7.5\n-2147483648", tempDir, "arith");
    }

    @Test
    void parityControlFlow(@TempDir Path tempDir) throws IOException {
        runParity("""
                main() {
                    var sum = 0
                    for (var i = 0; i < 5; i++) {
                        if (i == 2) {
                            continue
                        }
                        sum = sum + i
                    }
                    println(sum)
                    var i = 0
                    while (i < 3) {
                        i = i + 1
                    }
                    println(i)
                    do {
                        i = i - 1
                    } while (i > 0)
                    println(i)
                }
                """, "8\n3\n0", tempDir, "flow");
    }

    // SG-006 — short-circuit de &&/||: `x != null && x.length > 0` NÃO pode
    // NPE no JS (o operando direito não é avaliado quando o esquerdo é false).
    // JVM/Native usam short-circuit por labels; JS usa &&/|| nativos (que também
    // fazem short-circuit). Trava a paridade nos 4 targets.
    @Test
    void parityShortCircuitAndOr(@TempDir Path tempDir) throws IOException {
        // SEM048: null não é fabricável — T? vem de API (mapOf().get()).
        runParity("""
                main() {
                    var s = mapOf("k", "valor").get("missing")
                    if (s != null && s.length > 0) {
                        println("nao-vazio")
                    } else {
                        println("vazio")
                    }
                    var t = mapOf("k", "abc").get("k")
                    if (t != null && t.length > 0) {
                        println("nao-vazio")
                    } else {
                        println("vazio")
                    }
                }
                """, "vazio\nnao-vazio", tempDir, "shortcircuit");
    }

    @Test
    void parityFunctions(@TempDir Path tempDir) throws IOException {
        runParity("""
                Int factorial(Int n) {
                    if (n <= 1) {
                        return 1
                    }
                    return n * factorial(n - 1)
                }

                main() {
                    println(factorial(6))
                }
                """, "720", tempDir, "funcs");
    }

    @Test
    void parityClassesAndList(@TempDir Path tempDir) throws IOException {
        runParity("""
                class User {
                    String name
                    Int age

                    constructor(String name, Int age) {
                        this.name = name
                        this.age = age
                    }
                }

                main() {
                    var users = listOf(User("Mel", 30), User("Kof", 25))
                    for (var i = 0; i < users.size; i++) {
                        println(users.get(i).name)
                        println(users.get(i).age)
                    }
                    println(users.size)
                }
                """, "Mel\n30\nKof\n25\n2", tempDir, "classes");
    }

    @Test
    void parityRecordsAndJson(@TempDir Path tempDir) throws IOException {
        runParity("""
                class User(
                    String name
                )

                main() {
                    var users = listOf(User("Mel"), User("Kof"))
                    println(json.encode(users))
                }
                """, "[{\"name\":\"Mel\"},{\"name\":\"Kof\"}]", tempDir, "json");
    }

    @Test
    void parityStrings(@TempDir Path tempDir) throws IOException {
        runParity("""
                main() {
                    var s = "Hello World"
                    println(s.length)
                    println(s.toUpperCase())
                    println(s.substring(6))
                    println(s.indexOf("World"))
                    println(s.startsWith("He"))
                    println(s.replace('l', 'L'))
                    println("a" + "b" + 1)
                    println("abc" == "abc")
                    println("abc" != "abd")
                }
                """, "11\nHELLO WORLD\nWorld\n6\ntrue\nHeLLo WorLd\nab1\ntrue\ntrue", tempDir, "strings");
    }

    @Test
    void parityExceptions(@TempDir Path tempDir) throws IOException {
        runParity("""
                main() {
                    try {
                        throw "boom"
                    } catch (String e) {
                        println("caught: " + e)
                    } finally {
                        println("done")
                    }
                }
                """, "caught: boom\ndone", tempDir, "exceptions");
    }

    @Test
    void parityColor32Bit(@TempDir Path tempDir) throws IOException {
        // 32-bit ARGB color type + named palette — no hex/ANSI conversion by hand
        runParity("""
                class Color {
                    Int value

                    constructor(Int value) {
                        this.value = value
                    }

                    Int red() { return (this.value >> 16) & 0xFF }
                    Int green() { return (this.value >> 8) & 0xFF }
                    Int blue() { return this.value & 0xFF }
                    Int alpha() { return (this.value >> 24) & 0xFF }
                    String ansi() {
                        return "\\u001b[38;2;" + this.red() + ";" + this.green() + ";" + this.blue() + "m"
                    }
                }

                class Colors {
                    static Int primary = 0xFF6750A4
                    static Int success = 0xFF4CAF50
                }

                main() {
                    var c = Color(Colors.primary)
                    println(c.red())
                    println(c.green())
                    println(c.blue())
                    println(c.alpha())
                    var s = Color(Colors.success)
                    println(s.red())
                    println(s.green())
                    println(s.blue())
                    println(c.ansi() == "\\u001b[38;2;103;80;164m")
                }
                """, "103\n80\n164\n255\n76\n175\n80\ntrue", tempDir, "color");
    }

    @Test
    void parityArrayAndSwitch(@TempDir Path tempDir) throws IOException {
        runParity("""
                main() {
                    var arr = new Int[4]
                    arr[0] = 7
                    arr[1] = 3
                    println(arr.length)
                    println(arr[0] + arr[1])
                    var x = 2
                    switch (x) {
                        case 1:
                            println("one")
                        case 2:
                            println("two")
                        default:
                            println("other")
                    }
                }
                """, "4\n10\ntwo", tempDir, "array");
    }

    // ── paridade cross-target dos bugs corrigidos na varredura do
    //    interpretador (06/09, regra 5): os mesmos casos que o
    //    KofScriptTest.interpreterParitySweep trava JVM×interpretado
    //    agora travam JVM×JS.

    @Test
    void parityStaticFieldSimpleName(@TempDir Path tempDir) throws IOException {
        // bug (lowering): campo estático por nome simples em método estático
        // baixava this inexistente (VerifyError no JVM compilado).
        runParity("""
                class Counter {
                    static Int count = 0
                    static Int bump() {
                        count = count + 1
                        return count
                    }
                }
                main() {
                    println(Counter.bump())
                    println(Counter.bump())
                    println(Counter.count)
                }
                """, "1\n2\n2", tempDir, "staticfield");
    }

    @Test
    void parityListContainsUnknownElement(@TempDir Path tempDir) throws IOException {
        // bug 35: contains(int) em List de elemento Unknown não boxeava o
        // argumento → VerifyError no JVM compilado.
        runParity("""
                main() {
                    var l = listOf()
                    println(l.isEmpty())
                    println(l.size)
                    println(l.contains(1))
                }
                """, "true\n0\nfalse", tempDir, "contains");
    }

    @Test
    void parityNullEquality(@TempDir Path tempDir) throws IOException {
        // bug 36: null == null baixava if_icmpeq (UnknownType→primitivo) →
        // VerifyError no JVM compilado.
        // SEM048: null não é mais fabricável (literal banido); T? vem de API.
        runParity("""
                main() {
                    var a = mapOf("x", 1).get("y")
                    var b = mapOf("x", 1).get("z")
                    println(a == b)
                    println(a != b)
                }
                """, "true\nfalse", tempDir, "nulleq");
    }

    // Paridade cross-target (regra 5, 07/09): os mesmos casos do
    // KofScriptTest.interpreterParitySweep (grupo A — paridade total)
    // agora travam JVM×JS. Os 25 que têm paridade JVM==JS ficam como gate
    // permanente. EXCLUÍDOS (bug documentado, não gate):
    //   - float-print  → JS formata double inteiro como "5" (JVM "5.0"):
    //     "parece bug mas é esperado" (known-bugs.md), não divergência.
    //   - record-eq-hash → bug 42 (hashCode ausente no JS: TypeError).
    //   - finally-return → bug 45 (JS perde o valor de retorno: undefined).
    // Native×JVM é coberto em NativeE2ETest; divergências Native estão em
    // known-bugs.md 41 (static-field), 43 (unicode length), 44 (FP 6 casas).
    @Test
    void parityCrossTargetGroupA(@TempDir Path tempDir) throws IOException {
        String[][] cases = {
            {"int-overflow", "main() {\n var a = 2147483647\n println(a + 1)\n}", "-2147483648"},
            {"mod-neg", "main() {\n println(-7 % 3)\n println(7 % -3)\n}", "-1\n1"},
            {"long-div", "main() {\n var a = 10000000000L\n println(a / 3L)\n println(a % 7L)\n}", "3333333333\n4"},
            {"cast-chain", "main() {\n var d = 9.9\n println(d as Int)\n var l = 70000L\n println(l as Int)\n println(66 as Char)\n}", "9\n70000\n66"},
            {"unicode-str", "main() {\n var s = \"café\"\n println(s.length)\n println(s.charAt(3))\n println(s + \"!\")\n}", "4\n233\ncafé!"},
            {"str-ops", "main() {\n var s = \"a,b,,c\"\n println(s.split(\",\").length)\n println(\"Hello World\".toLowerCase())\n println(\"  x  \".trim() + \"|\")\n}", "4\nhello world\nx|"},
            {"map-null-val", "main() {\n var m = mapOf(\"a\", 1)\n m.put(\"b\", 2)\n println(m.get(\"a\"))\n println(m.size)\n}", "1\n2"},
            {"empty-list", "main() {\n var l = listOf()\n println(l.isEmpty())\n println(l.size)\n println(l.contains(1))\n}", "true\n0\nfalse"},
            {"null-eq", "main() {\n var a = mapOf(\"x\", 1).get(\"y\")\n var b = mapOf(\"x\", 1).get(\"z\")\n println(a == b)\n println(a != b)\n}", "true\nfalse"},
            {"null-eq-shortcut", "main() {\n var a = mapOf(\"x\", 1).get(\"y\")\n var b = mapOf(\"x\", 1).get(\"z\")\n if (a == b) { println(\"iguais\") } else { println(\"dif\") }\n if (a != b) { println(\"ne\") } else { println(\"nao-ne\") }\n}", "iguais\nnao-ne"},
            {"set-dedup", "main() {\n var s = setOf(1, 2, 2, 3, 3, 3)\n println(s.size)\n println(s.contains(2))\n println(s.contains(9))\n}", "3\ntrue\nfalse"},
            {"nested-if-expr", "main() {\n var x = 5\n var r = if (x > 0) if (x > 10) \"big\" else \"small\" else \"neg\"\n println(r)\n}", "small"},
            {"switch-expr", "main() {\n var v = 3\n var d = switch (v) {\n case 1 -> \"one\"\n case 2 -> \"two\"\n case 3 -> \"three\"\n default -> \"other\"\n }\n println(d)\n}", "three"},
            {"break-continue", "main() {\n var sum = 0\n for (var i in listOf(1,2,3,4,5)) {\n if (i == 2) { continue }\n if (i == 4) { break }\n sum = sum + i\n }\n println(sum)\n}", "4"},
            {"lambda-chain", "main() {\n var l = listOf(1,2,3,4)\n var r = l.filter((x: Int) -> x > 1).map((x: Int) -> x * 10).reduce((a: Int, b: Int) -> a + b, 0)\n println(r)\n}", "90"},
            {"lambda-capture-mut", "main() {\n var n = 0\n var inc = () -> { n = n + 1 }\n inc()\n inc()\n inc()\n println(n)\n}", "3"},
            {"array-2d", "main() {\n var a = new Int[3]\n a[0] = 10\n a[1] = 20\n a[2] = 30\n println(a[0] + a[1] + a[2])\n println(a.length)\n}", "60\n3"},
            {"static-field", "class Counter {\n static Int count = 0\n static Int bump() {\n count = count + 1\n return count\n }\n}\nmain() {\n println(Counter.bump())\n println(Counter.bump())\n println(Counter.count)\n}", "1\n2\n2"},
            {"static-field-plus-eq", "class Counter2 {\n static Int count = 0\n static Int bump() {\n count += 2\n return count\n }\n}\nmain() {\n println(Counter2.bump())\n println(Counter2.bump())\n println(Counter2.count)\n}", "2\n4\n4"},
            {"string-num-concat", "main() {\n println(\"n=\" + 42)\n println(1 + 2 + \"x\")\n println(\"x\" + 1 + 2)\n}", "n=42\n3x\nx12"},
            {"bool-logic", "main() {\n println(true && false)\n println(true || false)\n println(!true)\n println((1 < 2) == (3 > 2))\n}", "false\ntrue\nfalse\ntrue"},
            {"bitwise", "main() {\n println(6 & 3)\n println(6 | 3)\n println(6 ^ 3)\n println(1 << 4)\n println(256 >> 2)\n}", "2\n7\n5\n16\n64"},
            {"deep-recursion", "Int fact(Int n) {\n if (n <= 1) {\n return 1\n }\n return n * fact(n - 1)\n}\nmain() {\n println(fact(10))\n}", "3628800"},
            {"list-of-mixed", "main() {\n var l = listOf(1, 2, 3)\n l.add(4)\n l.set(0, 99)\n println(l.get(0))\n println(l.size)\n println(l.remove(1))\n println(l.size)\n}", "99\n4\n2\n3"},
            {"map-iter", "main() {\n var m = mapOf(\"x\", 1)\n m.put(\"y\", 2)\n m.put(\"z\", 3)\n var ks = m.keys()\n var sum = 0\n for (var k in ks) {\n sum = sum + m.get(k)\n }\n println(sum)\n}", "6"},
        };
        var divergentes = new StringBuilder();
        for (String[] c : cases) {
            Path dir = tempDir.resolve(c[0]);
            Files.createDirectories(dir);
            Path source = dir.resolve("Main.kf");
            Files.writeString(source, c[1]);
            CompilationResult rjvm = driver.compile(source, dir.resolve("jvm"), Target.JVM);
            CompilationResult rjs = driver.compile(source, dir.resolve("js"), Target.JS);
            if (!rjvm.success() || !rjs.success()) {
                divergentes.append("\n[").append(c[0]).append("] compile falhou: JVM=")
                        .append(rjvm.success()).append(" JS=").append(rjs.success());
                continue;
            }
            RunResult jvm = runJvm(dir.resolve("jvm"));
            RunResult js = runJs(dir.resolve("js"));
            if (jvm.exitCode() != 0 || js.exitCode() != 0
                    || !jvm.output().equals(js.output())
                    || !c[2].equals(jvm.output())) {
                divergentes.append("\n[").append(c[0]).append("] JVM=<").append(jvm.output())
                        .append("> JS=<").append(js.output()).append("> esperado=<").append(c[2]).append(">");
            }
        }
        assertEquals("", divergentes.toString().trim(),
                "paridade cross-target JVM×JS (grupo A do sweep interpretado):");
    }
}