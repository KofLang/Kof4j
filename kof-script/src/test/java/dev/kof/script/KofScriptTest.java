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
}
