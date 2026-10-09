package dev.kof.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §7.1 rule-5 parity: the tag filter is decided at COMPILE TIME (system property
 * {@code kof.test.tag}), so the SAME filtered catalog must run on every target
 * — the plan's "rule-5 parity by construction" claim. {@link TestTagsE2ETest}
 * pins JVM and JS; this class pins the remaining advertised test target, Native
 * x86-64 (the same {@code --tag smoke,ui} multi-tag value as
 * {@link TestTagsMultiE2ETest}).
 *
 * <p>The cross native targets (riscv64/aarch64) are NOT advertised test targets
 * ({@code kof test} usage: {@code --target jvm|native|js}) and the test harness
 * main references {@code kof_process_exit}, which the cross runtime does not
 * link — so the rule-5 proof lives on the host native target here.</p>
 *
 * <p>Separate class by responsibility and to keep each under the 500-line test
 * ceiling (Phase 3 of {@code test-architecture-plan}).</p>
 */
class TestTagsNativeE2ETest implements NativeToolchainAssumptions {

    @TempDir Path tmp;

    private static final String SUITE = """
            test "soma", "smoke" {
                assert(2 + 2 == 4)
            }
            test "janela", "ui" {
                assert("kof" == "kof")
            }
            test "banco", "db" {
                assert(true)
            }
            """;

    /** Compila em modo harness com o filtro e devolve o binário nativo. */
    private Path buildNative(String filter, String tag) throws IOException {
        System.setProperty("kof.test.tag", filter);
        try {
            Path src = tmp.resolve("T-" + tag + ".kf");
            Files.writeString(src, SUITE);
            Path out = tmp.resolve("o-" + tag);
            CompilationResult r = new CompilerDriver().compileForTests(src, out, Target.NATIVE);
            assertTrue(r.success(), tag + " native compile: " + r.diagnostics().getDiagnostics());
            Path bin = out.resolve("Default/Main");
            assertTrue(Files.isRegularFile(bin), tag + " native binary must exist at " + bin);
            return bin;
        } finally {
            System.clearProperty("kof.test.tag");
        }
    }

    private static String runX86(Path bin) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "x86-64 exit, output: " + out);
        return out;
    }

    @Test
    void multiTagFilterRunsTheSameReducedCatalogOnNativeX86() throws Exception {
        assumeToolchain("as", "ld");
        Path bin = buildNative("smoke,ui", "x86");
        assertEquals("kof test: tag 'smoke,ui' (2 of 3)\nPASS soma\nPASS janela\n────────\n"
                + "0 failed of 2 tests", runX86(bin),
                "Native x86-64 runs the SAME filtered catalog as JVM/JS (rule 5)");
    }

    @Test
    void multiTagFilterAllUnknownIsAnHonestNoOpOnNativeX86() throws Exception {
        assumeToolchain("as", "ld");
        Path bin = buildNative("alpha,beta", "x86none");
        assertEquals("kof test: tag 'alpha,beta' (0 of 3)\nno tests with tag 'alpha,beta' (of 3)",
                runX86(bin), "R6 on native: nenhuma casa = recusa nomeada");
    }
}
