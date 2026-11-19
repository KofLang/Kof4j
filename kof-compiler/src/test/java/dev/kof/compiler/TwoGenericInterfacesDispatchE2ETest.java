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
 * known-bugs §719 — a class implementing TWO generic interfaces dispatched the
 * SECOND interface's method to the FIRST interface's slot on every native
 * target: {@code class AB(Int v) implements A<Int>, B<Int>} printed
 * {@code 5|5} on native where the JVM printed {@code 5|6}.
 *
 * <p>THE ROOT (traced 14/10, confirmed live 19/11): the native backend used a
 * SINGLE flat vtable per class, seeded interface-by-interface in traversal
 * order — {@code AB_vtable = [a, b]} while {@code B_vtable = [b]}. The call
 * site resolved the slot from the INTERFACE alone ({@code b} → 0) and hit
 * {@code a} at index 0. One flat table cannot satisfy two interfaces whose
 * methods both start at 0.
 *
 * <p>THE FIX (19/11): the vtable layout is UNTOUCHED (class dispatch, lambdas,
 * FFI trampolines and the valueOf tables keep byte-identical slots). Each
 * class now APPENDS an itable section after the vtable terminator — pairs
 * {@code (ifaceId, table)} terminated by {@code (0,0)}, then one table per
 * interface in the interface's OWN layout order. The INTERFACE call site walks
 * the vtable to the terminator, finds its interface's table by id and reads
 * the slot there. Deterministic ids (sorted scan of the class map), emitted by
 * both the x86 and the cross emitters.
 *
 * <p>Proof: RED-first — pre-fix the native faces print {@code 5|5} (the test
 * fails); the JVM/Script/JS faces were already green and stay so.
 */
class TwoGenericInterfacesDispatchE2ETest extends NativeCrossSupport {

    private static final String SRC = """
            interface A<T> { T a() }
            interface B<T> { T b() }
            class AB(Int v) implements A<Int>, B<Int> {
                Int a() { return v }
                Int b() { return v + 1 }
            }
            interface P { Int p() }
            interface Q { Int q() }
            interface R { Int r() }
            class PQR(Int n) implements P, Q, R {
                Int p() { return n * 10 }
                Int q() { return n * 100 }
                Int r() { return n * 1000 }
            }
            main() {
                var y = AB(5)
                var x: A<Int> = y
                var z: B<Int> = y
                println(x.a())
                println(z.b())
                var t = PQR(2)
                var p: P = t
                var q: Q = t
                var r: R = t
                println(p.p())
                println(q.q())
                println(r.r())
            }
            """;

    /** The JVM oracle — the same 5 lines on every target post-fix. */
    private static final String GOLDEN = String.join("\n", "5", "6", "20", "200", "2000");

    private Path writeSrc(Path tmp, String tag) throws Exception {
        Path p = tmp.resolve("Two-Ifaces-" + tag + ".kf");
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
    void jvmDispatchesBothInterfaces(@TempDir Path tmp) throws Exception {
        assertEquals(GOLDEN, runJvm(writeSrc(tmp, "jvm"), tmp.resolve("out-jvm")),
                "the JVM oracle (itables/structural dispatch)");
    }

    @Test
    void scriptDispatchesBothInterfaces(@TempDir Path tmp) throws Exception {
        assertEquals(GOLDEN, runScript(tmp, "script"), "the interpreter was already correct");
    }

    @Test
    void jsDispatchesBothInterfaces(@TempDir Path tmp) throws Exception {
        assertEquals(GOLDEN, runJs(tmp, "js"), "the JS face was already correct — stays so");
    }

    @Test
    void nativeX86DispatchesBothInterfaces(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain x86-64 absent");
        assertEquals(GOLDEN, runNative(writeSrc(tmp, "nat"), tmp.resolve("out-nat")),
                "pre-fix the SECOND interface's method dispatched to the FIRST interface's slot");
    }

    @Test
    void riscv64DispatchesBothInterfaces(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(has("qemu-riscv64"),
                "qemu-riscv64 absent — environmental skip");
        assertEquals(GOLDEN, runCross(writeSrc(tmp, "riscv"), tmp.resolve("out-riscv"),
                "qemu-riscv64", Target.NATIVE_RISCV64), "the cross rides the same itables");
    }

    @Test
    void aarch64DispatchesBothInterfaces(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(has("qemu-aarch64"),
                "qemu-aarch64 absent — environmental skip");
        assertEquals(GOLDEN, runCross(writeSrc(tmp, "aarch"), tmp.resolve("out-aarch"),
                "qemu-aarch64", Target.NATIVE_AARCH64), "the cross rides the same itables");
    }
}
