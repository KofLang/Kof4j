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
}
