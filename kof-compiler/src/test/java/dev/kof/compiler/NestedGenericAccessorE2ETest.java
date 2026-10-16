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
 * known-bugs §706: a CHAINED generic accessor (`outer.get().get()` on a
 * `Box&lt;Box&lt;Int&gt;&gt;`) emitted the call site with the SUBSTITUTED return
 * descriptor (`()I`) while the class declares the ERASED one
 * (`()Ljava/lang/Object;`) → JVM {@code NoSuchMethodError} and native garbage.
 *
 * <p>Root: {@code ExpressionInstanceCallLowerer}'s fallback (when the semantic
 * analyzer did not cache a resolved method — e.g. the inner call of a chain)
 * used {@code ExpressionTyper.inferExprType}, which returns the SUBSTITUTED
 * type. Fix: for a USER class method, resolve the symbol by the receiver and
 * use its DECLARED return type (the erasure the class actually emits), falling
 * back to the inference only when there is no class symbol.
 *
 * <p>Oracle is the JVM. RED-first: the JVM face threw {@code NoSuchMethodError}
 * and the native targets printed garbage pre-fix.
 */
class NestedGenericAccessorE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            record Box<T>(T value) {
                T get() { return value }
            }

            record Pair<A, B>(A first, B second) {
                A firstKey() { return first }
                B secondVal() { return second }
            }

            Int nestedDouble() {
                var inner = Box<Int>(7)
                var outer = Box<Box<Int>>(inner)
                return outer.get().get()
            }

            Int tripleNested() {
                var b = Box<Box<Box<Int>>>(Box<Box<Int>>(Box<Int>(9)))
                return b.get().get().get()
            }

            Int methodChain() {
                var h = Box<Box<Int>>(Box<Int>(42))
                return h.get().get()
            }

            Int nestedField() {
                var p = Pair<Box<Int>, Box<String>>(Box<Int>(1), Box<String>("hi"))
                return p.first.value
            }

            String stringBox() {
                var p = Pair<Box<Int>, Box<String>>(Box<Int>(1), Box<String>("hi"))
                return p.second.value
            }

            Int twoParamChain() {
                var p = Pair<Box<Int>, Box<String>>(Box<Int>(5), Box<String>("x"))
                return p.firstKey().get()
            }

            main() {
                println(nestedDouble())
                println(tripleNested())
                println(methodChain())
                println(nestedField())
                println(stringBox())
                println(twoParamChain())
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "7", "9", "42", "1", "hi", "5", "end");

    @Test
    void jvmIsTheContractForNestedGenericAccessor() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnNestedGenericAccessor() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnNestedGenericAccessor() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "js saída: " + stdout);
        assertEquals(GOLDEN, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void nativeX86MatchesTheJvmOnNestedGenericAccessor() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, runNative("x86", Target.NATIVE, null));
    }

    @Test
    void nativeRiscv64MatchesTheJvmOnNestedGenericAccessor() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runNative("riscv64", Target.NATIVE_RISCV64, "riscv64"));
    }

    @Test
    void nativeAarch64MatchesTheJvmOnNestedGenericAccessor() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runNative("aarch64", Target.NATIVE_AARCH64, "aarch64"));
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

    private String runNative(String name, Target target, String arch) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, target);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), name + ": binário nativo ausente");
        ProcessBuilder pb = arch == null ? new ProcessBuilder(binary.toString())
                : NativeRiscv64E2ETest.qemu(arch, binary);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " (" + target + ")");
        assertEquals(0, p.exitValue(), () -> name + " saída: " + o);
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
