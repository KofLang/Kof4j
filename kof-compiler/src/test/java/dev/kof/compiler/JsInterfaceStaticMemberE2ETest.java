package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §724: an interface with a STATIC member produced
 * {@code ReferenceError: Shape is not defined} on the JS backend — the JS
 * lowering drops EVERY interface ({@code JsLoweringContext.skipClass}: "JS has
 * no runtime interface"), but a STATIC call {@code Shape.sides()} names the
 * interface, so it must be a real JS entity.
 *
 * <p>Fix: an interface with a static member is emitted as a JS class (a
 * pure-abstract interface still needs no entity); abstract methods (no body)
 * are skipped by the class emitter.
 *
 * <p>Oracle is the JVM.
 */
class JsInterfaceStaticMemberE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            interface Shape {
                Int area()
                static Int sides() { return 4 }
                static String name() { return "shape" }
            }

            interface Cfg {
                static Int MAX = 99
            }

            class Sq(Int s) implements Shape {
                Int area() { return s * s }
            }

            main() {
                var q: Shape = Sq(3)
                println(q.area())
                println(Shape.sides())
                println(Shape.name())
                println(Cfg.MAX)
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n", "9", "4", "shape", "99", "end");

    @Test
    void jvmIsTheContractForInterfaceStaticMember() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnInterfaceStaticMember() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnInterfaceStaticMember() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "§724: o JS não definiu a interface (ReferenceError) — saída: " + stdout);
        assertEquals(GOLDEN, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void nativeX86MatchesTheJvmOnInterfaceStaticMember() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = tmp.resolve("x86");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(r.success(), () -> "x86 compile: " + r.diagnostics().getDiagnostics());
        Path binary = out.resolve("Default/Main");
        Process p = new ProcessBuilder(binary.toString()).redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "x86");
        assertEquals(0, p.exitValue(), () -> "x86 saída: " + o);
        assertEquals(GOLDEN, o);
    }

    // ── harness ────────────────────────────────────────────────────────────

    private String runJvm(String name) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " jvm");
        assertEquals(0, p.exitValue(), () -> name + " (oráculo JVM): " + o);
        return o;
    }

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}
