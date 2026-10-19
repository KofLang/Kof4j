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
 * known-bugs §720 (JVM face): a class extending a GENERIC abstract base
 * (`class Impl(Int n) extends Base<Int>`) emitted a `super()` call whose OWNER
 * was the RAW superclass name `"Base&lt;Int&gt;"` (with the type argument) instead
 * of the ERASED `Base` — {@code VerifyError: Bad operand type when invoking
 * <init>} on the JVM.
 *
 * <p>Root: {@code CompilerRecordSupport.generateRecordConstructor} used
 * {@code rec.superClass()} RAW, unlike {@code CompilerClassLowering} (line 24)
 * and {@code CompilerIfaceRecordLowering} (line 48), which both call
 * {@code eraseTypeArgs}. Fix: erase the type args.
 *
 * <p>The NATIVE face of §720 was a SEPARATE mechanism (fixed 18/10): the
 * subclass vtable kept the base's abstract STUB at the inherited slot and
 * appended the erased BRIDGE elsewhere, so a {@code Base<Int>}-typed call
 * dispatched to the empty stub (returns 0) and {@code kof_unbox_int(0)} threw
 * {@code type error: ... cannot be cast}. Fix:
 * {@code NativeClassMeta.collectVirtualMethods} now lets a bridge replace a
 * SUPERCLASS-seeded slot too (§483 previously covered interfaces only). The
 * native faces below pin all three targets; {@code Impl(6).value()} keeps the
 * concrete-typed RAW slot reachable.
 *
 * <p>Oracle is the JVM. RED-first: the JVM face threw {@code VerifyError};
 * the native faces threw the cross-cast diagnostic before the vtable fix.
 */
class AbstractGenericBaseCtorE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            abstract class Base<T> {
                abstract T value()
                T twice() { return value() }
            }

            class Impl(Int n) extends Base<Int> {
                Int value() { return n }
            }

            main() {
                var x: Base<Int> = Impl(4)
                println(x.value())
                println(x.twice())
                println(Impl(6).value())
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n", "4", "4", "6", "end");

    @Test
    void jvmIsTheContractForAbstractGenericBase() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnAbstractGenericBase() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnAbstractGenericBase() throws Exception {
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

    // ── native faces (§720) ────────────────────────────────────────────────

    @Test
    void nativeX86MatchesTheJvmOnAbstractGenericBase() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = tmp.resolve("x86");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(r.success(), () -> "x86 compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "x86");
        assertEquals(0, p.exitValue(), () -> "x86 saída: " + o);
        assertEquals(GOLDEN, o);
    }

    @Test
    void riscv64MatchesTheJvmOnAbstractGenericBase() throws Exception {
        runCross("riscv64", Target.NATIVE_RISCV64);
    }

    @Test
    void aarch64MatchesTheJvmOnAbstractGenericBase() throws Exception {
        runCross("aarch64", Target.NATIVE_AARCH64);
    }

    private void runCross(String arch, Target target) throws Exception {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                arch + " cross toolchain/qemu absent");
        Path dir = tmp.resolve(arch);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, target);
        assertTrue(r.success(), () -> arch + " compile: " + r.diagnostics().getDiagnostics());
        String o = NativeRiscv64E2ETest.runQemu(arch, out.resolve("Default/Main"));
        assertEquals(GOLDEN, o.strip());
    }

    // ── harness ────────────────────────────────────────────────────────────

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
}
