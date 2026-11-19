package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §717 — a generic body builds a {@code List<T>} and the
 * substituted call site (e.g. {@code Box<Int>.asList()} → {@code List<Int>})
 * read GARBAGE on the native targets while the JVM/Script/JS printed the
 * value.
 *
 * <p>THE ROOT (measured 18/11, this fix): inside the generic body the elem
 * {@code T} is a TypeVariable and the value is ALREADY a reference — the
 * wrapper's erased {@code Object} slot holds the boxed primitive
 * ({@code kof_box_int} at the constructor boundary), a real object or null.
 * The list's add therefore stored the BOX POINTER where the native list
 * contract stores the RAW element, and the call-site get (typed by the
 * SUBSTITUTED {@code List<Int>}) read the pointer as an int
 * ({@code 383778976}, {@code -618659680}, … — a fresh heap address each
 * run). The JVM never has the ambiguity: erasure there is
 * reference-everywhere ({@code ArrayList.add(Object)} over the already-boxed
 * field, {@code checkcast Integer; intValue} at the read).
 *
 * <p>THE FIX: the generic-body write normalizes through the new
 * tag-agnostic {@code kof_unbox_any_soft} — ANY box opens to its 8-byte
 * value at {@code [16]}, a reference passes through, null maps to 0 (the
 * same qword a concrete list stores). Builder and reader then agree: the
 * generic-built list carries the SAME physical representation as
 * {@code listOf(1, 2, 3)}, so the concrete read contract (fully covered by
 * the collections battery) is untouched.
 *
 * <p>Proof: RED-first — pre-fix every native face prints a pointer (the
 * test fails); the JVM/Script/JS faces were already green and stay so.
 */
class NativeGenericListElemE2ETest extends NativeCrossSupport {

    private static final String SRC = """
            record Box<T>(T value) {
                asList(): List<T> { return listOf(value) }
                twice(): List<T> { return listOf(value, value) }
            }
            wrap<T>(T v): List<T> { return listOf(v) }
            main() {
                var b = Box<Int>(5)
                println(b.asList().get(0))
                var two = b.twice()
                println(two.get(0))
                println(two.get(1))
                println(wrap<Int>(7).get(0))
                var s = Box<String>("hi")
                println(s.asList().get(0))
            }
            """;

    /** The JVM oracle — the same 5 lines on every target post-fix. */
    private static final String GOLDEN = String.join("\n", "5", "5", "5", "7", "hi");

    private Path writeSrc(Path tmp, String tag) throws Exception {
        Path p = tmp.resolve("Gen-" + tag + ".kf");
        Files.writeString(p, SRC, StandardCharsets.UTF_8);
        return p;
    }

    private String runScript(Path tmp, String tag) throws Exception {
        Path main = writeSrc(tmp, tag);
        KofInterpreter.Result result = driver.interpret(List.of(main), main.getParent(), new String[0]);
        assertEquals(0, result.exitCode(), () -> "script: " + result.stderr());
        return result.stdout().strip();
    }

    private String runJs(Path tmp, String tag) throws Exception {
        Path main = writeSrc(tmp, tag);
        Path out = tmp.resolve("out-" + tag);
        CompilationResult r = driver.compile(main, out, Target.JS);
        assertTrue(r.success(), () -> "js compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "js: " + stdout);
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    @Test
    void jvmReadsTheGenericListElements(@TempDir Path tmp) throws Exception {
        assertEquals(GOLDEN, runJvm(writeSrc(tmp, "jvm"), tmp.resolve("out-jvm")),
                "the JVM oracle — erasure there is reference-everywhere");
    }

    @Test
    void scriptReadsTheGenericListElements(@TempDir Path tmp) throws Exception {
        assertEquals(GOLDEN, runScript(tmp, "script"),
                "the interpreter was already correct (§733-style no-throw family untouched)");
    }

    @Test
    void jsReadsTheGenericListElements(@TempDir Path tmp) throws Exception {
        assertEquals(GOLDEN, runJs(tmp, "js"),
                "the JS face was already correct — stays so");
    }

    @Test
    void nativeX86ReadsTheGenericListElements(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain x86-64 absent");
        assertEquals(GOLDEN, runNative(writeSrc(tmp, "nat"), tmp.resolve("out-nat")),
                "the pre-fix native printed the BOX POINTER (a fresh heap address"
                        + " per run) instead of the element");
    }

    @Test
    void riscv64ReadsTheGenericListElements(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(has("qemu-riscv64"),
                "qemu-riscv64 absent — environmental skip");
        assertEquals(GOLDEN, runCross(writeSrc(tmp, "riscv"), tmp.resolve("out-riscv"),
                "qemu-riscv64", Target.NATIVE_RISCV64), "the cross face rides the same write");
    }

    @Test
    void aarch64ReadsTheGenericListElements(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(has("qemu-aarch64"),
                "qemu-aarch64 absent — environmental skip");
        assertEquals(GOLDEN, runCross(writeSrc(tmp, "aarch"), tmp.resolve("out-aarch"),
                "qemu-aarch64", Target.NATIVE_AARCH64), "the cross face rides the same write");
    }
}
