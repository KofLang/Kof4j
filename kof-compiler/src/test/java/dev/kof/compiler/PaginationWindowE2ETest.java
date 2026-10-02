package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * pagination P2 (D-PAGINATION, D-FUTURE-BATCH-2809B) — {@code Window<T>} +
 * {@code window(...)} in-memory, pacote virtual {@code kof.pagination}
 * (library-first, D-KOF-FIRST item 12). Contrato (§7.1/§8): janela
 * materializada; {@code hasPrevious = offset > 0}; {@code hasNext} exato com
 * {@code total} e otimista ({@code page.size == limit}) sem ele; {@code total}
 * e {@code null} salvo pedido explicito. Golden medido no oraculo JVM; os 4
 * alvos tem de bater (regra 5).
 */
class PaginationWindowE2ETest {

    static final String PROGRAM = """
            import kof.pagination

            main() {
                val l: List<Int> = listOf(10, 20, 30, 40, 50)
                val w = window(l, 2, 1)
                println(w.items().size)
                println(w.offset())
                println(w.limit())
                if (w.hasPrevious()) println("t") else println("f")
                if (w.hasNext()) println("t") else println("f")
                if (w.total() != null) println("tot") else println("none")
                println(w.items().get(0))
                println(w.items().get(1))
                val w2 = window(l, 2, 1, true)
                if (w2.total() != null) println("tot") else println("none")
                if (w2.hasNext()) println("t") else println("f")
                val w3 = window(l, 2, 4)
                println(w3.items().size)
                if (w3.hasPrevious()) println("t") else println("f")
                if (w3.hasNext()) println("t") else println("f")
                val w4 = window(l, 2, 4, true)
                if (w4.hasNext()) println("t") else println("f")
                val w5 = window(l, 5, 0, true)
                if (w5.hasNext()) println("t") else println("f")
                if (w5.total() != null) println("tot") else println("none")
                val w6 = window(l, 0, 0, true)
                println(w6.items().size)
                if (w6.hasNext()) println("t") else println("f")
                val w7 = window(l, 2, 9, true)
                println(w7.items().size)
                if (w7.hasPrevious()) println("t") else println("f")
                if (w7.hasNext()) println("t") else println("f")
            }
            """;

    static final String GOLDEN =
            "2\n1\n2\nt\nt\nnone\n20\n30\ntot\nt\n1\nt\nf\nf\nf\ntot\n0\nt\n0\nt\nf";

    private final CompilerDriver driver = new CompilerDriver();

    private Path outDirFor(Path tempDir, String name, Target t) {
        return tempDir.resolve("out-" + name + "-" + t);
    }

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return driver.compile(source, outDirFor(tempDir, name, t), t);
    }

    private String runJvm(Path tempDir, String name) throws Exception {
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp",
                outDirFor(tempDir, name, Target.JVM).toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM run must exit 0, got:\n" + out);
        return out;
    }

    @Test
    void windowRunOnJvm(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "W", PROGRAM, Target.JVM);
        assertTrue(r.success(), "pagination P2 must compile: " + r.diagnostics().getDiagnostics());
        assertEquals(GOLDEN, runJvm(tempDir, "W"));
    }

    @Test
    void windowRunOnJs(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("WJ.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outWJ");
        CompilationResult r = driver.compile(src, outDir, Target.JS);
        assertTrue(r.success(), "js compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String txt = out.toString().replace("\r\n", "\n").trim();
        assertEquals(0, ec, "js run exit, output:\n" + txt);
        assertEquals(GOLDEN, txt, "js must match the JVM oracle (parity rule 5)");
    }

    @Test
    void windowRunOnScript(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("WS.kf");
        Files.writeString(src, PROGRAM);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(src), tempDir, new String[0]);
        assertEquals(0, ir.exitCode(), "script stderr: " + ir.stderr());
        assertEquals(GOLDEN, ir.stdout().replace("\r\n", "\n").trim(),
                "script must match the JVM oracle (parity rule 5)");
    }

    @Test
    void windowRunOnNativeX86(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("WN.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outWN");
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native run must exit 0, got:\n" + out);
        assertEquals(GOLDEN, out, "native x86_64 must match the JVM oracle (rule 5)");
    }

    @Test
    void windowRunOnCrossTargets(@TempDir Path tempDir) throws Exception {
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    NativeRiscv64E2ETest.hasToolchain(arch), "toolchain " + arch + " ausente");
            CompilationResult rc = compile(tempDir, "WC" + arch, PROGRAM, t);
            assertTrue(rc.success(), t + " compile: " + rc.diagnostics().getDiagnostics());
            String out = NativeRiscv64E2ETest.runQemu(arch,
                    outDirFor(tempDir, "WC" + arch, t).resolve("Default/Main"));
            assertEquals(GOLDEN, out, t + " must match the JVM oracle (parity rule 5)");
        }
    }

    @Test
    void negativeLimitIsANamedErrorOnJvm(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "NEGW", """
                import kof.pagination

                main() {
                    val l: List<Int> = listOf(1, 2, 3)
                    val w = window(l, -1, 0)
                    println(w.items().size)
                }
                """, Target.JVM);
        assertTrue(r.success(), "negative window compiles (runtime error): " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp",
                outDirFor(tempDir, "NEGW", Target.JVM).toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertNotEquals(0, p.waitFor(), "negative window must fail, got:\n" + out);
        assertTrue(out.contains("PAGINATION: limit/offset must be >= 0"), "named error, got:\n" + out);
    }

    @Test
    void windowIsAbsentWithoutTheImport(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "NOIMP", """
                main() {
                    val l: List<Int> = listOf(1, 2, 3)
                    val w = window(l, 1, 0)
                    println(w.items().size)
                }
                """, Target.JVM);
        assertFalse(r.success(), "window must not resolve without import kof.pagination");
    }
}
