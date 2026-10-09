package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §630 — a lambda whose value comes from a nested statement ({@code try}/
 * {@code catch}, {@code if}/{@code else}, {@code switch}, loops) was typed
 * {@code () -> Void}: the semantic scan only looked at a top-level
 * {@code return} or one {@code BlockStmt} level, so the returned value was
 * discarded and using the lambda as a value died {@code SEM033} ("received a
 * void value"). The lowering-side typer ({@code ExpressionTyper}) already
 * recursed; this pins the semantic side to the same traversal.
 *
 * <p>RED-first: pre-fix every nested-return case below fails to compile with
 * {@code SEM033}; the simple-block and void controls pass both before and
 * after. The real-world shape is a readiness probe
 * {@code () -> { try { net.connect(...); return true } catch { return false } }}
 * passed to {@code kof.test.waitUntil} (the server lifecycle helper).
 */
class LambdaTryReturnE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(int exitCode, String output) {}

    private Run runJvm(String source, Path tempDir) throws IOException, InterruptedException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        Path outDir = tempDir.resolve("out");
        CompilationResult r = driver.compile(src, outDir, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        return new Run(p.waitFor(), out);
    }

    @Test
    void tryCatchReturnInfersValueOnJvm(@TempDir Path tempDir) throws Exception {
        Run r = runJvm("""
                main() {
                    var f = () -> {
                        try {
                            return 1
                        } catch (String e) {
                            return 2
                        }
                    }
                    println(f())
                    var g = () -> {
                        try {
                            throw "boom"
                        } catch (String e) {
                            return 7
                        }
                    }
                    println(g())
                }
                """, tempDir);
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertEquals("1\n7", r.output());
    }

    @Test
    void nestedShapesReturnValuesOnJvm(@TempDir Path tempDir) throws Exception {
        Run r = runJvm("""
                Int pick(Int n) {
                    var f = () -> {
                        try {
                            if (n > 0) {
                                return 10
                            } else {
                                return 20
                            }
                        } catch (String e) {
                            return 30
                        }
                    }
                    return f()
                }

                Int loop(Int n) {
                    var f = () -> {
                        var i = 0
                        while (i < n) {
                            i = i + 1
                        }
                        return i
                    }
                    return f()
                }

                Int each(List<Int> xs) {
                    var f = () -> {
                        for (var x in xs) {
                            if (x > 2) {
                                return x
                            }
                        }
                        return -1
                    }
                    return f()
                }

                main() {
                    println(pick(1))
                    println(pick(-1))
                    println(loop(5))
                    println(each(listOf(1, 2, 3, 4)))
                }
                """, tempDir);
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertEquals("10\n20\n5\n3", r.output());
    }

    @Test
    void tryCatchReturnPassesAsHigherOrderArgument(@TempDir Path tempDir) throws Exception {
        Run r = runJvm("""
                import kof.test

                main() {
                    var n = 0
                    var ok = waitUntil(() -> {
                        n = n + 1
                        try {
                            if (n >= 3) {
                                return true
                            }
                            throw "not yet"
                        } catch (String e) {
                            return false
                        }
                    }, 10, 1)
                    println("ok=" + ok + " n=" + n)
                }
                """, tempDir);
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertEquals("ok=true n=3", r.output());
    }

    @Test
    void voidAndSimpleBlockLambdasUnchanged(@TempDir Path tempDir) throws Exception {
        Run r = runJvm("""
                main() {
                    var f = () -> {
                        return 1
                    }
                    println(f())
                    var v = () -> {
                        println("side")
                    }
                    v()
                }
                """, tempDir);
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertEquals("1\nside", r.output());
    }
}
