package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance Matrix (Fase 9 do plano de plataforma —
 * docs/development/conformance-matrix.md): cada caso trava a MESMA saída
 * esperada nos 4 targets — JVM (bytecode), Native (x86_64), Script
 * (interpretador de IR) e KofJS (GraalJS).
 *
 * Regra: a saída esperada é a do COMPORTAMENTO DOCUMENTADO (corpus +
 * backend-parity.md), não "o que o JVM imprime". Célula PARTIAL da matriz
 * = target excluído da asserção + bug registrado em known-bugs.md (ref no
 * comentário). Casos determinísticos apenas — concorrência/tempo têm
 * suítes próprias (SpawnE2ETest, KofTimeE2ETest).
 */
class ConformanceMatrixTest {

    private final CompilerDriver driver = new CompilerDriver();

    private record TargetResult(int exit, String out) {}

    private static String norm(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").trim();
    }

    private TargetResult runJvm(Path source, Path outDir) throws IOException {
        CompilationResult r = driver.compile(source, outDir, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int ec = p.waitFor();
            return new TargetResult(ec, norm(out));
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private TargetResult runScript(Path source, Path dir) {
        try {
            KofInterpreter.Result r = driver.interpret(List.of(source), dir, new String[0]);
            return new TargetResult(r.exitCode(), norm(r.stdout()));
        } catch (KofInterpretException e) {
            return new TargetResult(-1, "FRONTEND-ERR");
        }
    }

    private TargetResult runNative(Path source, Path outDir) throws IOException {
        CompilationResult r = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(r.success(), "Native compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binário deve existir");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int ec = p.waitFor();
            return new TargetResult(ec, norm(out));
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private TargetResult runJs(Path source, Path outDir) throws IOException {
        CompilationResult r = driver.compile(source, outDir, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                (InputStream) new ByteArrayInputStream(new byte[0]), out);
        return new TargetResult(ec, norm(out.toString()));
    }

    /**
     * Trava o MESMO output nos 4 targets. partial = targets com bug
     * registrado (célula PARTIAL da matriz) — excluídos da asserção,
     * cada um com o ref do bug.
     */
    private void matrix(String name, String program, String expected,
                        Set<String> partial, Path tempDir) throws IOException {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        Path source = dir.resolve("Main.kf");
        Files.writeString(source, program);
        var problemas = new StringBuilder();
        if (!partial.contains("jvm")) {
            TargetResult t = runJvm(source, dir.resolve("jvm"));
            if (t.exit() != 0 || !expected.equals(t.out()))
                problemas.append(" JVM(exit=").append(t.exit()).append(")=<").append(t.out()).append(">");
        }
        if (!partial.contains("native")) {
            TargetResult t = runNative(source, dir.resolve("nat"));
            if (t.exit() != 0 || !expected.equals(t.out()))
                problemas.append(" NATIVE(exit=").append(t.exit()).append(")=<").append(t.out()).append(">");
        }
        if (!partial.contains("script")) {
            TargetResult t = runScript(source, dir);
            if (t.exit() != 0 || !expected.equals(t.out()))
                problemas.append(" SCRIPT(exit=").append(t.exit()).append(")=<").append(t.out()).append(">");
        }
        if (!partial.contains("js")) {
            TargetResult t = runJs(source, dir.resolve("js"));
            if (t.exit() != 0 || !expected.equals(t.out()))
                problemas.append(" JS(exit=").append(t.exit()).append(")=<").append(t.out()).append(">");
        }
        assertEquals("", problemas.toString().trim(),
                "[" + name + "] esperado <" + expected + "> — divergências:");
    }

    // ===== Lote 1 — linguagem core (docs/development/conformance-matrix.md) =====

    @Test
    void conformanceCoreArithmetic(@TempDir Path tempDir) throws IOException {
        matrix("arith", """
                main() {
                    var a = 2147483647
                    println(a + 1)
                    println(-7 % 3)
                    println(7 % -3)
                }
                """, "-2147483648\n-1\n1", Set.of(), tempDir);
        matrix("longdiv", """
                main() {
                    var a = 10000000000L
                    println(a / 3L)
                    println(a % 7L)
                }
                """, "3333333333\n4", Set.of(), tempDir);
        matrix("cast", """
                main() {
                    var d = 9.9
                    println(d as Int)
                    var l = 70000L
                    println(l as Int)
                    println(66 as Char)
                }
                """, "9\n70000\n66", Set.of(), tempDir);
        // PARTIAL: Native bug 44 (6 casas + `5` sem `.0`); KofJS doc
        // "parece bug mas é esperado" (JS `String(5.0)` = `"5"`).
        matrix("floatprint", """
                main() {
                    println(1.0 / 3.0)
                    println(2.5 * 2.0)
                    println(7.0 / 2.0)
                }
                """, "0.3333333333333333\n5.0\n3.5", Set.of("native", "js"), tempDir);
        matrix("boollogic", """
                main() {
                    println(true && false)
                    println(true || false)
                    println(!true)
                    println((1 < 2) == (3 > 2))
                }
                """, "false\ntrue\nfalse\ntrue", Set.of(), tempDir);
        matrix("bitwise", """
                main() {
                    println(6 & 3)
                    println(6 | 3)
                    println(6 ^ 3)
                    println(1 << 4)
                    println(256 >> 2)
                }
                """, "2\n7\n5\n16\n64", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreStrings(@TempDir Path tempDir) throws IOException {
        // PARTIAL: Native bug 43 (UTF-8 byte-based: length 5, charAt 195).
        matrix("unicode", """
                main() {
                    var s = "café"
                    println(s.length)
                    println(s.charAt(3))
                    println(s + "!")
                }
                """, "4\n233\ncafé!", Set.of("native"), tempDir);
        matrix("strops", """
                main() {
                    var s = "a,b,,c"
                    println(s.split(",").length)
                    println("Hello World".toLowerCase())
                    println("  x  ".trim() + "|")
                }
                """, "4\nhello world\nx|", Set.of(), tempDir);
        matrix("concat", """
                main() {
                    println("n=" + 42)
                    println(1 + 2 + "x")
                    println("x" + 1 + 2)
                }
                """, "n=42\n3x\nx12", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreCollections(@TempDir Path tempDir) throws IOException {
        matrix("map", """
                main() {
                    var m = mapOf("a", 1)
                    m.put("b", 2)
                    println(m.get("a"))
                    println(m.size)
                }
                """, "1\n2", Set.of(), tempDir);
        matrix("emptylist", """
                main() {
                    var l = listOf()
                    println(l.isEmpty())
                    println(l.size)
                    println(l.contains(1))
                }
                """, "true\n0\nfalse", Set.of(), tempDir);
        matrix("setdedup", """
                main() {
                    var s = setOf(1, 2, 2, 3, 3, 3)
                    println(s.size)
                    println(s.contains(2))
                    println(s.contains(9))
                }
                """, "3\ntrue\nfalse", Set.of(), tempDir);
        matrix("listops", """
                main() {
                    var l = listOf(1, 2, 3)
                    l.add(4)
                    l.set(0, 99)
                    println(l.get(0))
                    println(l.size)
                    println(l.remove(1))
                    println(l.size)
                }
                """, "99\n4\n2\n3", Set.of(), tempDir);
        matrix("mapiter", """
                main() {
                    var m = mapOf("x", 1)
                    m.put("y", 2)
                    m.put("z", 3)
                    var ks = m.keys()
                    var sum = 0
                    for (var k in ks) {
                        sum = sum + m.get(k)
                    }
                    println(sum)
                }
                """, "6", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreNull(@TempDir Path tempDir) throws IOException {
        matrix("nulleq", """
                main() {
                    var a = null
                    var b = null
                    println(a == b)
                    println(a != b)
                }
                """, "true\nfalse", Set.of(), tempDir);
        matrix("nulleqshortcut", """
                main() {
                    var a = null
                    var b = null
                    if (a == b) { println("iguais") } else { println("dif") }
                    if (a != b) { println("ne") } else { println("nao-ne") }
                }
                """, "iguais\nnao-ne", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreControl(@TempDir Path tempDir) throws IOException {
        matrix("nestedif", """
                main() {
                    var x = 5
                    var r = if (x > 0) if (x > 10) "big" else "small" else "neg"
                    println(r)
                }
                """, "small", Set.of(), tempDir);
        matrix("switchexpr", """
                main() {
                    var v = 3
                    var d = switch (v) {
                        case 1 -> "one"
                        case 2 -> "two"
                        case 3 -> "three"
                        default -> "other"
                    }
                    println(d)
                }
                """, "three", Set.of(), tempDir);
        matrix("breakcont", """
                main() {
                    var sum = 0
                    for (var i in listOf(1,2,3,4,5)) {
                        if (i == 2) { continue }
                        if (i == 4) { break }
                        sum = sum + i
                    }
                    println(sum)
                }
                """, "4", Set.of(), tempDir);
        matrix("recursion", """
                Int fact(Int n) {
                    if (n <= 1) { return 1 }
                    return n * fact(n - 1)
                }
                main() {
                    println(fact(10))
                }
                """, "3628800", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreFunctions(@TempDir Path tempDir) throws IOException {
        matrix("lambdachain", """
                main() {
                    var l = listOf(1,2,3,4)
                    var r = l.filter((x: Int) -> x > 1).map((x: Int) -> x * 10).reduce((a: Int, b: Int) -> a + b, 0)
                    println(r)
                }
                """, "90", Set.of(), tempDir);
        matrix("lambdacapture", """
                main() {
                    var n = 0
                    var inc = () -> { n = n + 1 }
                    inc()
                    inc()
                    inc()
                    println(n)
                }
                """, "3", Set.of(), tempDir);
        matrix("array2d", """
                main() {
                    var a = new Int[3]
                    a[0] = 10
                    a[1] = 20
                    a[2] = 30
                    println(a[0] + a[1] + a[2])
                    println(a.length)
                }
                """, "60\n3", Set.of(), tempDir);
    }

    @Test
    void conformanceCoreRecordsAndStatics(@TempDir Path tempDir) throws IOException {
        matrix("record", """
                record P(Int x, Int y)
                main() {
                    var a = P(1,2)
                    var b = P(1,2)
                    println(a == b)
                    println(a)
                    println(a.x())
                }
                """, "true\nP[x=1, y=2]\n1", Set.of(), tempDir);
        // PARTIAL: bug 42 (hashCode ausente: JS TypeError, Native ld P_hashCode).
        matrix("recordhash", """
                record P(Int x, Int y)
                main() {
                    var a = P(1,2)
                    var b = P(1,2)
                    println(a.hashCode() == b.hashCode())
                }
                """, "true", Set.of("native", "js"), tempDir);
        // PARTIAL: bug 41 (Native stub vazio KofGetStatic/KofPutStatic → lixo).
        matrix("staticfield", """
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
                """, "1\n2\n2", Set.of("native"), tempDir);
        matrix("staticpluseq", """
                class Counter2 {
                    static Int count = 0
                    static Int bump() {
                        count += 2
                        return count
                    }
                }
                main() {
                    println(Counter2.bump())
                    println(Counter2.bump())
                    println(Counter2.count)
                }
                """, "2\n4\n4", Set.of("native"), tempDir);
    }
}
