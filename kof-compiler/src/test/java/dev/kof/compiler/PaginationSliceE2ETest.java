package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * pagination P1 (D-PAGINATION, D-FUTURE-BATCH-2809B) — List.take/drop/slice
 * in-memory. Contrato: janela materializada (cópia, nunca view viva);
 * clamping honesto (n>size devolve o todo / vazio; offset>size devolve vazio,
 * SEM erro); negativo = erro nomeado "PAGINATION: ...". Golden medido no
 * oráculo JVM; os 4 alvos têm de bater (regra 5).
 */
class PaginationSliceE2ETest {

    static final String PROGRAM = """
            main() {
                val l: List<Int> = listOf(10, 20, 30, 40, 50)
                println(l.take(2).size)
                println(l.take(0).size)
                println(l.take(5).size)
                println(l.take(9).size)
                println(l.take(2).get(0))
                println(l.take(2).get(1))
                println(l.drop(2).size)
                println(l.drop(0).size)
                println(l.drop(5).size)
                println(l.drop(9).size)
                println(l.drop(2).get(0))
                println(l.drop(2).get(2))
                val a = l.slice(1, 2)
                println(a.size)
                println(a.get(0))
                println(a.get(1))
                println(l.slice(0, 2).size)
                println(l.slice(4, 10).size)
                println(l.slice(4, 10).get(0))
                println(l.slice(5, 3).size)
                println(l.slice(9, 3).size)
                println(l.slice(0, 0).size)
                val t = l.take(2)
                t.add(99)
                println(l.size)
                val s: List<String> = listOf("a", "b", "c")
                println(s.take(2).get(1))
                println(s.drop(1).get(0))
                println(s.slice(1, 1).get(0))
            }
            """;

    static final String GOLDEN =
            "2\n0\n5\n5\n10\n20\n3\n5\n0\n0\n30\n50\n2\n20\n30\n"
                    + "2\n1\n50\n0\n0\n0\n5\nb\nb\nb";

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
    void takeDropSliceRunOnJvm(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "P", PROGRAM, Target.JVM);
        assertTrue(r.success(), "pagination P1 must compile: " + r.diagnostics().getDiagnostics());
        assertEquals(GOLDEN, runJvm(tempDir, "P"));
    }

    @Test
    void takeDropSliceRunOnJs(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("PJ.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outPJ");
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
    void takeDropSliceRunOnNativeX86(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("PN.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outPN");
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
    void takeDropSliceRunOnCrossTargets(@TempDir Path tempDir) throws Exception {
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    NativeRiscv64E2ETest.hasToolchain(arch), "toolchain " + arch + " ausente");
            CompilationResult rc = compile(tempDir, "PC" + arch, PROGRAM, t);
            assertTrue(rc.success(), t + " compile: " + rc.diagnostics().getDiagnostics());
            String out = NativeRiscv64E2ETest.runQemu(arch,
                    outDirFor(tempDir, "PC" + arch, t).resolve("Default/Main"));
            assertEquals(GOLDEN, out, t + " must match the JVM oracle (parity rule 5)");
        }
    }

    @Test
    void negativeCountIsANamedErrorOnJvm(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "NEG", """
                main() {
                    val l: List<Int> = listOf(1, 2, 3)
                    val x = l.take(-1)
                    println(x.size)
                }
                """, Target.JVM);
        assertTrue(r.success(), "negative take compiles (runtime error): " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp",
                outDirFor(tempDir, "NEG", Target.JVM).toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertNotEquals(0, p.waitFor(), "negative take must fail, got:\n" + out);
        assertTrue(out.contains("PAGINATION: count must be >= 0"), "named error, got:\n" + out);
    }

    @Test
    void negativeSliceIsANamedErrorOnJvm(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "NEGS", """
                main() {
                    val l: List<Int> = listOf(1, 2, 3)
                    val x = l.slice(0, -1)
                    println(x.size)
                }
                """, Target.JVM);
        assertTrue(r.success(), "negative slice compiles (runtime error): " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp",
                outDirFor(tempDir, "NEGS", Target.JVM).toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertNotEquals(0, p.waitFor(), "negative slice must fail, got:\n" + out);
        assertTrue(out.contains("PAGINATION: limit/offset must be >= 0"), "named error, got:\n" + out);
    }

    @Test
    void wrongAritiesAndRefCountsAreRejected(@TempDir Path tempDir) throws Exception {
        String[] bad = {"l.take()", "l.drop(1, 2)", "l.slice(1)", "l.slice(1, 2, 3)"};
        for (String expr : bad) {
            CompilationResult r = compile(tempDir, "A" + expr.hashCode(), """
                    main() {
                        val l: List<Int> = listOf(1, 2)
                        val x = %s
                        println(x)
                    }
                    """.formatted(expr), Target.JVM);
            assertFalse(r.success(), expr + " must not compile");
            assertTrue(r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> d.code().equals("SEM025")),
                    expr + " expected SEM025: " + r.diagnostics().getDiagnostics());
        }
        CompilationResult rs = compile(tempDir, "RC", """
                main() {
                    val l: List<Int> = listOf(1, 2)
                    val x = l.take("nope")
                    println(x)
                }
                """, Target.JVM);
        assertFalse(rs.success(), "String count must not compile");
        assertTrue(rs.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM055")),
                "SEM055 expected: " + rs.diagnostics().getDiagnostics());
    }
}
