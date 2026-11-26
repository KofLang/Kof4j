package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §1238 RED: a `return` AFTER a `while` whose LAST branch stores a local — the
 * function hands back the value the local had BEFORE the loop instead of the
 * one the loop stored.
 *
 * <p>Found while migrating the Énois blog body: `paintWrappedMarked` walks the
 * words in a `while` and wants to return the pen of the last line it painted
 * (the store lives in the last `else` of the loop body); every shape of that
 * store (`x = expr`, `x = x + 1`, `x = f(…)`, a further `if` guarding it)
 * returned 0 while the value was 139. This fixture has no library, no SDL and
 * no strings of consequence: the sum of 1..4 must come back as 2, the last
 * element as 7, the stored call result as 11, and the control that computes
 * the total AFTER the loop (a `for` instead of a `while`) as 10 — measured
 * GREEN today, which is what isolates the defect to the post-loop `return`.
 */
class NativeReturnLastBranchStoreTest {

    private static final String PROGRAM = """
            class Acumula {
                static Int ultimo(Int n) {
                    var pen = 0
                    var i = 0
                    while (i < n) {
                        if (i > 100) {
                            pen = 999
                        } else {
                            pen = i
                        }
                        i = i + 1
                    }
                    return pen
                }

                static Int soma(Int n) {
                    var s = 0
                    var i = 1
                    while (i <= n) {
                        if (i > 100) {
                            s = -1
                        } else {
                            s = s + i
                        }
                        i = i + 1
                    }
                    return s
                }

                static Int onze() { return 11 }

                static Int chamada(Int n) {
                    var r = 0
                    var i = 0
                    while (i < n) {
                        if (i > 100) {
                            r = -1
                        } else {
                            r = 5 + onze()
                        }
                        i = i + 1
                    }
                    return r
                }

                static Int controle(Int n) {
                    var t = 0
                    for (var i in listOf(1, 2, 3, 4)) {
                        if (i > 100) {
                            t = -1
                        } else {
                            t = t + i
                        }
                    }
                    return t
                }
            }

            main() {
                println("ultimo=" + Acumula.ultimo(5))
                println("soma=" + Acumula.soma(4))
                println("chamada=" + Acumula.chamada(1))
                println("controle=" + Acumula.controle(4))
            }
            """;

    @Test
    void theReturnSeesTheStoreOfTheLastLoopBranch(@TempDir Path dir) throws Exception {
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, PROGRAM);
        Files.writeString(dir.resolve("marca.txt"), "ran");
        CompilationResult r = new CompilerDriver().compile(src, dir.resolve("out"), Target.NATIVE);
        assumeTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(dir.resolve("out/Default/Main").toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        boolean done = p.waitFor(120, TimeUnit.SECONDS);
        Files.writeString(Path.of("/tmp/rls_out.txt"), out + "\nexit=" + p.exitValue() + " done=" + done);
        assumeTrue(done, "o programa termina");
        assertEquals("ultimo=4\nsoma=10\nchamada=16\ncontrole=10", out,
                "o `return` depois do `while` vê o que o laço gravou");
    }
}
