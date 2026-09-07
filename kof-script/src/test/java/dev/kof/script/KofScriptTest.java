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
     *
     * GRUPO B (complementar): casos onde o caminho COMPILADO tem bug
     * pré-existente registrado em docs/known-bugs.md (VerifyError do
     * emitter/lowering) — aqui o interpretador é o oráculo e o teste trava
     * a saída CORRETA dele, documentando o bug do compilado.
     */
    @Test
    void interpreterParitySweep(@TempDir Path tmp) throws Exception {
        String[][] cases = {
            {"int-overflow", "main() {\n var a = 2147483647\n println(a + 1)\n}"},
            {"mod-neg", "main() {\n println(-7 % 3)\n println(7 % -3)\n}"},
            {"long-div", "main() {\n var a = 10000000000L\n println(a / 3L)\n println(a % 7L)\n}"},
            {"cast-chain", "main() {\n var d = 9.9\n println(d as Int)\n var l = 70000L\n println(l as Int)\n println(66 as Char)\n}"},
            {"float-print", "main() {\n println(1.0 / 3.0)\n println(2.5 * 2.0)\n println(7.0 / 2.0)\n}"},
            {"unicode-str", "main() {\n var s = \"café\"\n println(s.length)\n println(s.charAt(3))\n println(s + \"!\")\n}"},
            {"str-ops", "main() {\n var s = \"a,b,,c\"\n println(s.split(\",\").length)\n println(\"Hello World\".toLowerCase())\n println(\"  x  \".trim() + \"|\")\n}"},
            {"map-null-val", "main() {\n var m = mapOf(\"a\", 1)\n m.put(\"b\", 2)\n println(m.get(\"a\"))\n println(m.size)\n}"},
            {"empty-list", "main() {\n var l = listOf()\n println(l.isEmpty())\n println(l.size)\n println(l.contains(1))\n}"},
            {"null-eq", "main() {\n var a = null\n var b = null\n println(a == b)\n println(a != b)\n}"},
            {"null-eq-shortcut", "main() {\n var a = null\n var b = null\n if (a == b) { println(\"iguais\") } else { println(\"dif\") }\n if (a != b) { println(\"ne\") } else { println(\"nao-ne\") }\n}"},
            {"set-dedup", "main() {\n var s = setOf(1, 2, 2, 3, 3, 3)\n println(s.size)\n println(s.contains(2))\n println(s.contains(9))\n}"},
            {"nested-if-expr", "main() {\n var x = 5\n var r = if (x > 0) if (x > 10) \"big\" else \"small\" else \"neg\"\n println(r)\n}"},
            {"switch-expr", "main() {\n var v = 3\n var d = switch (v) {\n case 1 -> \"one\"\n case 2 -> \"two\"\n case 3 -> \"three\"\n default -> \"other\"\n }\n println(d)\n}"},
            {"break-continue", "main() {\n var sum = 0\n for (var i in listOf(1,2,3,4,5)) {\n if (i == 2) { continue }\n if (i == 4) { break }\n sum = sum + i\n }\n println(sum)\n}"},
            {"record-eq-hash", "record P(Int x, Int y)\nmain() {\n var a = P(1,2)\n var b = P(1,2)\n println(a == b)\n println(a)\n println(a.x())\n println(a.hashCode() == b.hashCode())\n}"},
            {"finally-return", "Int f() {\n try {\n return 1\n } finally {\n println(\"fin\")\n }\n}\nmain() {\n println(f())\n}"},
            {"lambda-chain", "main() {\n var l = listOf(1,2,3,4)\n var r = l.filter((x: Int) -> x > 1).map((x: Int) -> x * 10).reduce((a: Int, b: Int) -> a + b, 0)\n println(r)\n}"},
            {"lambda-capture-mut", "main() {\n var n = 0\n var inc = () -> { n = n + 1 }\n inc()\n inc()\n inc()\n println(n)\n}"},
            {"array-2d", "main() {\n var a = new Int[3]\n a[0] = 10\n a[1] = 20\n a[2] = 30\n println(a[0] + a[1] + a[2])\n println(a.length)\n}"},
            {"static-field", "class Counter {\n static Int count = 0\n static Int bump() {\n count = count + 1\n return count\n }\n}\nmain() {\n println(Counter.bump())\n println(Counter.bump())\n println(Counter.count)\n}"},
            {"static-field-plus-eq", "class Counter2 {\n static Int count = 0\n static Int bump() {\n count += 2\n return count\n }\n}\nmain() {\n println(Counter2.bump())\n println(Counter2.bump())\n println(Counter2.count)\n}"},
            {"string-num-concat", "main() {\n println(\"n=\" + 42)\n println(1 + 2 + \"x\")\n println(\"x\" + 1 + 2)\n}"},
            {"bool-logic", "main() {\n println(true && false)\n println(true || false)\n println(!true)\n println((1 < 2) == (3 > 2))\n}"},
            {"bitwise", "main() {\n println(6 & 3)\n println(6 | 3)\n println(6 ^ 3)\n println(1 << 4)\n println(256 >> 2)\n}"},
            {"deep-recursion", "Int fact(Int n) {\n if (n <= 1) {\n return 1\n }\n return n * fact(n - 1)\n}\nmain() {\n println(fact(10))\n}"},
            {"list-of-mixed", "main() {\n var l = listOf(1, 2, 3)\n l.add(4)\n l.set(0, 99)\n println(l.get(0))\n println(l.size)\n println(l.remove(1))\n println(l.size)\n}"},
            {"map-iter", "main() {\n var m = mapOf(\"x\", 1)\n m.put(\"y\", 2)\n m.put(\"z\", 3)\n var ks = m.keys()\n var sum = 0\n for (var k in ks) {\n sum = sum + m.get(k)\n }\n println(sum)\n}"},
        };
        var divergentes = new StringBuilder();
        for (String[] c : cases) {
            Path dir = tmp.resolve(c[0]);
            Files.createDirectories(dir);
            Path f = dir.resolve("Main.kf");
            Files.writeString(f, c[1]);
            var interp = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
            assertTrue(interp.success(), "[" + c[0] + "] interpretador falhou: " + interp.stderr());
            var comp = KofScript.runFileCompiled(f, dev.kof.compiler.Target.JVM, new String[0]);
            assertTrue(comp.success(), "[" + c[0] + "] compilado falhou: " + comp.stderr());
            if (interp.exitCode() != comp.exitCode()
                    || !norm(interp.stdout()).equals(norm(comp.stdout()))) {
                divergentes.append("\n[").append(c[0]).append("] interp(exit=")
                        .append(interp.exitCode()).append(")=<").append(norm(interp.stdout()))
                        .append("> comp(exit=").append(comp.exitCode()).append(")=<")
                        .append(norm(comp.stdout())).append(">");
            }
        }
        assertEquals("", divergentes.toString().trim(),
                "varredura de paridade interpretado vs JVM compilado:");
    }

    /**
     * Grupo B (estrutura pronta p/ regressões): casos onde o caminho
     * COMPILADO tem bug pré-existente (VerifyError do emitter/lowering,
     * docs/known-bugs.md) e o interpretador é o oráculo — trava a saída
     * correta enquanto o compilado espera correção. VAZIO desde 06/09:
     * empty-list (bug 35) e null-eq (bug 36) foram CORRIGIDOS no compilado
     * e promovidos ao grupo A (paridade total).
     */
    @Test
    void interpreterCorrectWhereCompiledCrashes(@TempDir Path tmp) throws Exception {
        String[][] cases = {
            // (vazio — ver comentário)
        };
        for (String[] c : cases) {
            Path dir = tmp.resolve(c[0]);
            Files.createDirectories(dir);
            Path f = dir.resolve("Main.kf");
            Files.writeString(f, c[1]);
            var interp = KofScript.runFile(f, dev.kof.compiler.Target.JVM);
            assertTrue(interp.success(), "[" + c[0] + "] " + interp.stderr());
            assertEquals(c[2], norm(interp.stdout()),
                    "[" + c[0] + "] interpretador deve dar a saída correta (compilado: bug registrado):");
        }
    }

    private static String norm(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").trim();
    }
}
