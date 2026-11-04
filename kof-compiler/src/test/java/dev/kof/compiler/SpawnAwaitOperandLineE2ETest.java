package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #1002 (04/11, deploy/quality-gate probe axis 4): a bare `await` (or
 * `spawn`) at the end of a line — no operand — was lowered against the NEXT
 * line's expression (`await` + newline parsed as `await println("end")`,
 * measured in the JS op dump). The oracle JVM died at LOAD time with a silent
 * `VerifyError: Operand stack underflow`; Script leaked
 * `NoSuchElementException`; JS raised the internal COMP002; Native silently
 * emitted `kof_await(void)`.
 *
 * <p>Contract: `docs/language-reference/concurrency.md` — `await <handle>`
 * takes an operand; statements are line-based. The operand must start on the
 * SAME line as the keyword; otherwise the compile FAILS with an honest
 * diagnostic (never per-target garbage). Same-line forms (`await h`,
 * `spawn f()`, `spawn { ... }`) stay valid on all targets.
 */
class SpawnAwaitOperandLineE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String BARE_AWAIT = """
            main() {
                spawn {
                    println("bg")
                }
                await
                println("end")
            }
            """;

    private static final String BARE_SPAWN = """
            main() {
                spawn
                println("end")
            }
            """;

    private CompilationResult compile(String source, Path dir, Target target) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), source);
        return driver.compile(dir.resolve("Main.kf"), dir.resolve("out"), target);
    }

    private void expectSameLineDiagnostic(CompilationResult r, String keyword) {
        assertFalse(r.success(), "a bare `" + keyword + "` must fail to compile");
        String diags = "" + r.diagnostics().getDiagnostics();
        assertTrue(diags.contains("same line"), "diagnostic must explain the same-line rule: " + diags);
        assertTrue(diags.contains(keyword), "diagnostic must name the keyword: " + diags);
    }

    @Test
    void bareAwaitIsDiagnosedOnJvm(@TempDir Path tempDir) throws IOException {
        expectSameLineDiagnostic(compile(BARE_AWAIT, tempDir.resolve("jvm"), Target.JVM), "await");
    }

    @Test
    void bareAwaitIsDiagnosedOnScript(@TempDir Path tempDir) throws IOException {
        expectSameLineDiagnostic(compile(BARE_AWAIT, tempDir.resolve("script"), Target.SCRIPT), "await");
    }

    @Test
    void bareAwaitIsDiagnosedOnJs(@TempDir Path tempDir) throws IOException {
        expectSameLineDiagnostic(compile(BARE_AWAIT, tempDir.resolve("js"), Target.JS), "await");
    }

    @Test
    void bareSpawnIsDiagnosedOnJvm(@TempDir Path tempDir) throws IOException {
        expectSameLineDiagnostic(compile(BARE_SPAWN, tempDir.resolve("jvm"), Target.JVM), "spawn");
    }

    @Test
    void sameLineFormsStayValidOnJvm(@TempDir Path tempDir) throws IOException {
        CompilationResult r = compile("""
                bg(): void {
                    println("bg")
                }
                main() {
                    var h = spawn bg()
                    await h
                    spawn {
                        println("block")
                    }
                    println("end")
                }
                """, tempDir.resolve("jvm"), Target.JVM);
        assertTrue(r.success(), "same-line spawn/await forms must compile: "
                + r.diagnostics().getDiagnostics());
    }
}
