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
class KofJsE2ETest extends KofJsSupport {


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
        runJs(source, tempDir.resolve("out"), "70\n14\n2\n5.0");
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
        Files.writeString(source, SRC_EXEC_VARIABLES);
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
        Files.writeString(source, SRC_EXEC_IF_ELSE);
        runJs(source, tempDir.resolve("out"), "greater\nsmaller2");
    }

    @Test
    void execIfElseNested(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_IF_ELSE_NESTED);
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
        Files.writeString(source, SRC_EXEC_BOOLEAN_CONDITIONS);
        runJs(source, tempDir.resolve("out"), "a\neither\nnot b\nfalse\ntrue");
    }

    // 5. Loops ───────────────────────────────────────────────────────

    @Test
    void execWhileLoop(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_WHILE_LOOP);
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
        Files.writeString(source, SRC_EXEC_BREAK_CONTINUE);
        runJs(source, tempDir.resolve("out"), "1\n3\n4");
    }

    // 6. Functions ───────────────────────────────────────────────────

    @Test
    void execFunctions(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_FUNCTIONS);
        runJs(source, tempDir.resolve("out"), "5\nhey!\n10");
    }

    @Test
    void execRecursion(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_RECURSION);
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
        Files.writeString(source, SRC_EXEC_CLASSES);
        runJs(source, tempDir.resolve("out"), "Hello Mel\n30");
    }

    @Test
    void execClassFields(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_CLASS_FIELDS);
        runJs(source, tempDir.resolve("out"), "2");
    }

    // 8. Constructors ────────────────────────────────────────────────

    @Test
    void execConstructors(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_CONSTRUCTORS);
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
        Files.writeString(source, SRC_RECORD_WITH_EXPLICIT_CONSTRUCTOR_RUNS_ON_JS);
        runJs(source, tempDir.resolve("out"), "1");
    }

    // 9. Inheritance ─────────────────────────────────────────────────

    @Test
    void execInheritance(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_INHERITANCE);
        runJs(source, tempDir.resolve("out"), "...\nAu au\nRex");
    }

    // 10. Interfaces ─────────────────────────────────────────────────

    @Test
    void execInterfaces(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_INTERFACES);
        runJs(source, tempDir.resolve("out"), "Hi Mel");
    }

    // 11. Generics ───────────────────────────────────────────────────

    @Test
    void execGenerics(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_GENERICS);
        runJs(source, tempDir.resolve("out"), "3\n2");
    }

    // 12. List ───────────────────────────────────────────────────────

    @Test
    void execList(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_LIST);
        runJs(source, tempDir.resolve("out"), "Mel\n2\n3\ntrue\nfalse\nfalse\nKof2\n2\ntrue");
    }

    // 13. String API ─────────────────────────────────────────────────

    @Test
    void execStringApi(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_STRING_API);
        runJs(source, tempDir.resolve("out"), SRC_EXEC_STRING_API_2);
    }

    // String→número (github #51): toInt/toLong/toDouble/toFloat não existiam no
    // runtime JS — `texto.kof_string_to_int()` → TypeError em execução. Paridade
    // com JVM: parseInt/parseDouble de s.trim() validado (formato errado → throw).
    @Test
    void execStringToNumberConversion(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_STRING_TO_NUMBER_CONVERSION);
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
        Files.writeString(source, SRC_EXEC_ARRAYS);
        runJs(source, tempDir.resolve("out"), "5\n10\n20\n0");
    }

    // 15. JSON ───────────────────────────────────────────────────────

    @Test
    void execJson(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_JSON);
        runJs(source, tempDir.resolve("out"), "42\n\"text\"\ntrue\n[1,2,3]\n[\"a\",\"b\"]\n124\nok\n10");
    }

    @Test
    void execJsonObjects(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SRC_EXEC_JSON_OBJECTS);
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
        Files.writeString(source, SRC_EXEC_TRY_CATCH_FINALLY);
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
        Files.writeString(source, SRC_LOGICAL_AND_OR_SHORT_CIRCUIT);
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