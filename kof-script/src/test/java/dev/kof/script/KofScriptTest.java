package dev.kof.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofScriptTest {

    @Test
    void evalPrintsHello() throws Exception {
        var r = KofScript.eval("""
                println("hello from script")
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("hello from script", r.stdout().trim());
    }

    @Test
    void evalWithTopLevelFnAndVar() throws Exception {
        // KofScript = Kof puro: função de topo com forma idiomática + var de
        // topo (vira global via KofScriptGlobals). Sem sugar de outra língua.
        var r = KofScript.eval("""
                add(a: Int, b: Int): Int = a + b
                main() {
                    var x = add(2, 3)
                    println(x)
                }
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("5", r.stdout().trim());
    }

    @Test
    void evalPatternMatching() throws Exception {
        var r = KofScript.eval("""
                main() {
                    var x: Object = "hello"
                    switch (x) {
                        case String s:
                            println("str:" + s)
                        default:
                            println("other")
                    }
                }
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("str:hello", r.stdout().trim());
    }

    @Test
    void evalInstanceofAndAs() throws Exception {
        var r = KofScript.eval("""
                main() {
                    var a: Object = "world"
                    if (a instanceof String) {
                        println("is string")
                    }
                    var b: Object = "test" as String
                    println(b)
                }
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("is string\ntest", r.stdout().trim().replace("\r\n","\n"));
    }

    @Test
    void runFileDirect(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("prog.kf");
        Files.writeString(f, """
                main() {
                    println(42)
                }
                """);
        var r = KofScript.runFile(f);
        assertTrue(r.success(), r.stderr());
        assertEquals("42", r.stdout().trim());
    }

    @Test
    void evalJsTarget() throws Exception {
        var r = dev.kof.compiler.Target.JS != null ? KofScript.eval("println(7)", dev.kof.compiler.Target.JS) : null;
        // JS eval uses embedded GraalJS, stdout is captured via KofJsRunner (which prints to System.out, not RunResult.stdout for JS)
        // For MVP, we just check success (JS stdout goes to System.out, not RunResult for JS path)
        // Instead test via runFile JS direct
        Path tmp = Files.createTempDirectory("jstest");
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, "main() { println(7) }");
        var r2 = KofScript.runFile(f, dev.kof.compiler.Target.JS);
        assertTrue(r2.success(), r2.stderr() + r2.stdout());
    }

    @Test
    void evalNativeTarget() throws Exception {
        Path tmp = Files.createTempDirectory("nativetest");
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, "main() { println(7) }");
        var r = KofScript.runFile(f, dev.kof.compiler.Target.NATIVE);
        assertTrue(r.success(), r.stderr() + r.stdout());
        assertEquals("7", r.stdout().trim());
    }

    @Test
    void evalPureKofWithTopLevelVarAndSpawn() throws Exception {
        // KofScript = Kof puro: modo script (sem main) — var de topo vira
        // global; concorrência é spawn/await (não `async`). Sem sugar.
        var r = KofScript.eval("""
                var counter = 0
                bump(): Int {
                    counter = counter + 1
                    return counter
                }
                println(bump())
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("1", r.stdout().trim());
    }

    @Test
    void jsSugarIsRejected() throws Exception {
        // let/const/async/fn NÃO existem no KofScript (não é JavaScript):
        // falham com o diagnóstico normal do parser Kof (R6: nunca silencioso).
        var r = KofScript.eval("""
                fn foo(a: Int): Int = a + 1
                main() {
                    let x = 5
                    println(foo(x))
                }
                """);
        assertFalse(r.success(), "fn/let não devem compilar em KofScript");
        assertTrue(r.stderr().contains("PARSE085"), "esperava PARSE085, veio: " + r.stderr());
    }

    @Test
    void interpreterRunsCollectionsAndRecords() throws Exception {
        // KofScript roda pelo interpretador da IR (sem fork de JVM): coleções,
        // records (== de conteúdo + toString), higher-order.
        var r = KofScript.eval("""
                record Point(Int x, Int y)
                main() {
                    var l = listOf(1, 2, 3)
                    println(l.map((v: Int) -> v * 2).reduce((a: Int, b: Int) -> a + b, 0))
                    var m = mapOf("k", 9)
                    println(m.get("k"))
                    var p1 = Point(1, 2)
                    var p2 = Point(1, 2)
                    println(p1 == p2)
                    println(p1)
                }
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("12\n9\ntrue\nPoint[x=1, y=2]", r.stdout().trim().replace("\r\n", "\n"));
    }

    @Test
    void interpreterRunsSpawnAwaitAndTryFinally() throws Exception {
        // spawn/await + try/catch/finally com exceção-as-String pelo
        // interpretador — mesma semântica do caminho compilado.
        var r = KofScript.eval("""
                work(): Int { return 21 }
                main() {
                    val h = spawn work()
                    println(await h * 2)
                    try {
                        throw "boom"
                    } catch (String e) {
                        println("caught:" + e)
                    } finally {
                        println("fin")
                    }
                }
                """);
        assertTrue(r.success(), r.stderr());
        assertEquals("42\ncaught:boom\nfin", r.stdout().trim().replace("\r\n", "\n"));
    }

    @Test
    void interpreterMatchesCompiledJvmOutput(@TempDir Path tmp) throws Exception {
        // Paridade por construção: o MESMO programa, interpretado vs compilado
        // + fork de JVM, produz saída idêntica.
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, """
                record Point(Int x, Int y)
                add(a: Int, b: Int): Int { return a + b }
                main() {
                    println(add(2, 3))
                    println("a" + "b")
                    println(Point(1, 2) == Point(1, 2))
                    println(listOf(1, 2, 3).size())
                    var i = 0
                    while (i < 3) { println(i); i = i + 1 }
                    for (var it in listOf("x", "y")) { println(it) }
                }
                """);
        var interp = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
        assertTrue(interp.success(), interp.stderr());
        // caminho compilado (bytecode + JVM real) para comparação
        var compiled = KofScript.runFileCompiled(f, dev.kof.compiler.Target.JVM, new String[0]);
        assertTrue(compiled.success(), compiled.stderr());
        assertEquals(compiled.stdout(), interp.stdout(), "interpretado deve casar o JVM compilado");
    }

    @Test
    void interpreterNullSafetyMatchesJvm(@TempDir Path tmp) throws Exception {
        // Regressão do null na pilha (função que retorna null): o interpretador
        // precisa empilhar null (LinkedList, não ArrayDeque) — paridade com JVM.
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, """
                find(k: String): String? {
                    if (k == "ok") { return "achou" }
                    return null
                }
                main() {
                    var v = find("ok")
                    if (v != null) { println(v) }
                    var w = find("no")
                    if (w == null) { println("nada") }
                }
                """);
        var interp = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
        assertTrue(interp.success(), interp.stderr());
        assertEquals("achou\nnada", interp.stdout().trim().replace("\r\n", "\n"));
        var compiled = KofScript.runFileCompiled(f, dev.kof.compiler.Target.JVM, new String[0]);
        assertEquals(compiled.stdout(), interp.stdout(), "null-safety: interpretado == JVM");
    }

    @Test
    void interpreterClosureCaptureMatchesJvm(@TempDir Path tmp) throws Exception {
        // Closure capturando variável mutável — mesma semântica de referência.
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, """
                main() {
                    var base = 10
                    var addN = (x: Int) -> x + base
                    println(addN(5))
                    base = 20
                    println(addN(5))
                }
                """);
        var interp = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
        assertTrue(interp.success(), interp.stderr());
        var compiled = KofScript.runFileCompiled(f, dev.kof.compiler.Target.JVM, new String[0]);
        assertEquals(compiled.stdout(), interp.stdout(), "closure capture: interpretado == JVM");
    }

    @Test
    void interpreterChannelMatchesJvm(@TempDir Path tmp) throws Exception {
        // channel<T>() com spawn de closure capturando o canal — receive
        // bloqueia até send, mesma ordem no interpretador e no JVM.
        Path f = tmp.resolve("Main.kf");
        Files.writeString(f, """
                import kof.time
                main() {
                    val c = channel<Int>()
                    spawn {
                        println("recv-wait")
                        val v = c.receive()
                        println("recv:" + v)
                    }
                    time.sleep(30)
                    println("pre-send")
                    c.send(42)
                    println("post-send")
                }
                """);
        var interp = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
        assertTrue(interp.success(), interp.stderr());
        String out = interp.stdout().replace("\r\n", "\n");
        // receive bloqueia até send: pre-send ANTES de recv:42 é garantido;
        // recv:42 vs post-send é corrida (o thread acorda no send e imprime
        // concorrente com o main) — não se pode exigir ordem entre eles.
        for (String line : new String[]{"recv-wait", "pre-send", "post-send", "recv:42"}) {
            assertTrue(out.contains(line), "faltou '" + line + "' em: " + out);
        }
        assertTrue(out.indexOf("pre-send") < out.indexOf("recv:42"),
                "receive deve bloquear até o send: " + out);
    }

    /**
     * Varredura de paridade interpretado vs JVM compilado sobre uma bateria
     * de edge-cases determinísticos (sem tempo/concorrência/I/O externo).
     * Cada caso roda nos dois caminhos e exige stdout+exitCode idênticos —
     * é a prova de que o refactor ≤500 e o interpretador preservam a
     * semântica do bytecode em superfícies além dos 16 casos do gate.
     */
    @Test
    void interpreterParitySweep(@TempDir Path tmp) throws Exception {
        String[][] cases = {
            {"div-zero", "main() { try { println(10 / 0) } catch (String e) { println(\"caught\") } }"},
            {"int-overflow", "main() { var a = 2147483647; println(a + 1) }"},
            {"mod-neg", "main() { println(-7 % 3); println(7 % -3) }"},
            {"long-div", "main() { var a = 10000000000L; println(a / 3L); println(a % 7L) }"},
            {"cast-chain", "main() { var d = 9.9; println(d as Int); var l = 70000L; println(l as Int); println(66 as Char) }"},
            {"float-print", "main() { println(1.0 / 3.0); println(2.5 * 2.0); println(7.0 / 2.0) }"},
            {"unicode-str", "main() { var s = \"café\"; println(s.length); println(s.charAt(3)); println(s + \"!\") }"},
            {"str-ops", "main() { var s = \"a,b,,c\"; println(s.split(\",\").length); println(\"Hello World\".toLowerCase()); println(\"  x  \".trim() + \"|\") }"},
            {"empty-list", "main() { var l = listOf(); println(l.isEmpty()); println(l.size); println(l.contains(1)) }"},
            {"map-null-val", "main() { var m = mapOf(\"a\", 1); m.put(\"b\", 2); println(m.get(\"a\")); println(m.get(\"zz\")); println(m.size) }"},
            {"set-dedup", "main() { var s = setOf(1, 2, 2, 3, 3, 3); println(s.size); println(s.contains(2)); println(s.contains(9)) }"},
            {"nested-if-expr", "main() { var x = 5; var r = if (x > 0) if (x > 10) \"big\" else \"small\" else \"neg\"; println(r) }"},
            {"switch-expr", "main() { var v = 3; var d = switch (v) { case 1 -> \"one\"; case 2 -> \"two\"; case 3 -> \"three\"; default -> \"other\" }; println(d) }"},
            {"break-continue", "main() { var sum = 0; for (var i in listOf(1,2,3,4,5)) { if (i == 2) { continue }; if (i == 4) { break }; sum = sum + i }; println(sum) }"},
            {"inheritance", "class A { Int x; public constructor(Int x) { this.x = x }; Int val() { return x * 2 } }; class B { Int y; public constructor(Int y) { this.y = y }; Int val() { return y * 3 } }; main() { println(A(5).val()); println(B(5).val()) }"},
            {"record-eq-hash", "record P(Int x, Int y); main() { var a = P(1,2); var b = P(1,2); println(a == b); println(a); println(a.x()); println(a.hashCode() == b.hashCode()) }"},
            {"pattern-match", "main() { var o = 42; var r = switch (o) { case Int n -> \"int:\" + n; case String s -> \"str\"; default -> \"other\" }; println(r) }"},
            {"null-eq", "main() { var a = null; var b = null; println(a == b); println(a != b) }"},
            {"nested-try", "main() { try { try { throw \"inner\" } catch (String e) { println(e); throw \"outer\" } } catch (String e) { println(e) } }"},
            {"finally-return", "Int f() { try { return 1 } finally { println(\"fin\") } }; main() { println(f()) }"},
            {"lambda-chain", "main() { var l = listOf(1,2,3,4); var r = l.filter((x: Int) -> x > 1).map((x: Int) -> x * 10).reduce((a: Int, b: Int) -> a + b, 0); println(r) }"},
            {"lambda-capture-mut", "main() { var n = 0; var inc = () -> n = n + 1; inc(); inc(); inc(); println(n) }"},
            {"array-2d", "main() { var a = new Int[3]; a[0] = 10; a[1] = 20; a[2] = 30; println(a[0] + a[1] + a[2]); println(a.length) }"},
            {"static-field", "Counter { static var count = 0; static Int bump() { count = count + 1; return count } }; main() { println(Counter.bump()); println(Counter.bump()); println(Counter.count) }"},
            {"string-num-concat", "main() { println(\"n=\" + 42); println(1 + 2 + \"x\"); println(\"x\" + 1 + 2) }"},
            {"bool-logic", "main() { println(true && false); println(true || false); println(!true); println((1 < 2) == (3 > 2)) }"},
            {"bitwise", "main() { println(6 & 3); println(6 | 3); println(6 ^ 3); println(1 << 4); println(256 >> 2) }"},
            {"deep-recursion", "Int fact(Int n) { if (n <= 1) { return 1 }; return n * fact(n - 1) }; main() { println(fact(10)) }"},
            {"list-of-mixed", "main() { var l = listOf(1, 2, 3); l.add(4); l.set(0, 99); println(l.get(0)); println(l.size); println(l.remove(1)); println(l.size) }"},
            {"map-iter", "main() { var m = mapOf(\"x\", 1); m.put(\"y\", 2); m.put(\"z\", 3); var ks = m.keys(); var sum = 0; for (var k in ks) { sum = sum + m.get(k) }; println(sum) }"},
        };
        var divergentes = new StringBuilder();
        for (String[] c : cases) {
            Path f = tmp.resolve(c[0] + ".kf");
            Files.writeString(f, c[1]);
            var interp = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
            var comp = KofScript.runFileCompiled(f, dev.kof.compiler.Target.JVM, new String[0]);
            if (interp.exitCode() != comp.exitCode()
                    || !norm(interp.stdout()).equals(norm(comp.stdout()))) {
                divergentes.append("\n[").append(c[0]).append("] interp(exit=")
                        .append(interp.exitCode()).append(")=<").append(norm(interp.stdout()))
                        .append("|").append(norm(interp.stderr())).append("> comp(exit=")
                        .append(comp.exitCode()).append(")=<").append(norm(comp.stdout()))
                        .append("|").append(norm(comp.stderr())).append(">");
            }
        }
        assertEquals("", divergentes.toString().trim(),
                "varredura de paridade interpretado vs JVM compilado:");
    }

    private static String norm(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").trim();
    }
}
