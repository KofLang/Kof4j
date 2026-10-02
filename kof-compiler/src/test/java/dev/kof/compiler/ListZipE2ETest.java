package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * D-MULTIPARADIGMA-PHASE1A slice 1i — {@code zip} (truncating positional
 * pairing into {@code List<Pair<A,B>>}), reusing the {@code kof_*} per-target
 * pattern.
 *
 * <p>Surface (D-MULTIPARADIGMA-ZIP, plan §230): {@code xs.zip(ys)} with no
 * lambda; length is {@code min(xs.size, ys.size)}; pairs are the nominal
 * record {@code Pair<A,B>} (fields {@code first}/{@code second}).</p>
 */
class ListZipE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(boolean ok, String output) {}

    private static final String BASIC = """
            main() {
                var xs = listOf(1, 2, 3)
                var ys = listOf("a", "b")
                var z = xs.zip(ys)
                println(z.size)
                println(z.get(0).first())
                println(z.get(1).second())
                var total = 0
                for (var p in z) {
                    total = total + p.first()
                }
                println(total)
                var names = listOf("x", "yy", "zzz")
                var nums = listOf(10, 20, 30, 40)
                var w = names.zip(nums)
                println(w.size)
                println(w.get(2).first())
                println(w.get(2).second())
                var acc = ""
                for (var q in w) {
                    acc = acc + q.first()
                }
                println(acc)
                println(listOf(1).zip(listOf<Int>()).size)
                println(listOf<String>().zip(listOf(1, 2)).size)
            }
            """;

    private static final String EXPECTED = "2\n1\nb\n3\n3\nzzz\n30\nxyyzzz\n0\n0";

    /**
     * D-MULTIPARADIGMA-ZIP-NATIVE (30/09): reference-element zip runs on the
     * native targets (the element is a pointer, no raw-primitive/erasure
     * mismatch); the primitive-element case is refused at compile time with
     * `NAT008` (proven in {@link DomainGapCodesTest#zipPrimitiveElementOnNativeIsNat008}).
     */
    private static final String REFERENCE = """
            main() {
                var xs = listOf("a", "b", "c")
                var ys = listOf("x", "yy")
                var z = xs.zip(ys)
                println(z.size)
                println(z.get(0).first())
                println(z.get(1).second())
                var acc = ""
                for (var p in z) {
                    acc = acc + p.first()
                }
                println(acc)
                var names = listOf("x", "yy", "zzz")
                var more = listOf("q", "r", "s", "t")
                var w = names.zip(more)
                println(w.size)
                println(w.get(2).first())
                println(w.get(2).second())
                println(listOf("a").zip(listOf<String>()).size)
                println(listOf<String>().zip(listOf("a")).size)
            }
            """;

    private static final String EXPECTED_REFERENCE = "2\na\nyy\nab\n3\nzzz\ns\n0\n0";

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString());
        } catch (ReflectiveOperationException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            return new Run(false, "THROW: " + c);
        } finally {
            System.setOut(oldOut);
        }
    }

    private Run runJs(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, diags(r));
        var buf = new ByteArrayOutputStream();
        try {
            int rc = dev.kof.runtime.KofJsRunner.run(
                    out.resolve("Default.mjs"), buf,
                    new ByteArrayInputStream(new byte[0]), buf);
            return new Run(rc == 0, buf.toString());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage() + "\n" + buf);
        }
    }

    private Run runScript(Path src, Path root) {
        try {
            var r = driver.interpret(java.util.List.of(src), root, new String[0]);
            return new Run(r.exitCode() == 0, r.stdout());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage());
        }
    }

    private Run runNativeX86(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        if (!r.success()) return new Run(false, diags(r));
        Path binary = out.resolve("Default/Main");
        if (!Files.isRegularFile(binary)) return new Run(false, "no binary at " + binary);
        Process p = new ProcessBuilder(binary.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        return new Run(p.waitFor() == 0, output);
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    private static String norm(String s) {
        return s.replace("\r\n", "\n").trim();
    }

    @Test
    void zipManaged(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("Zip.kf");
        Files.writeString(src, BASIC);
        Run jvm = runJvm(src, tmp.resolve("o-zip-jvm"));
        assertTrue(jvm.ok(), () -> "JVM: " + jvm.output());
        assertEquals(EXPECTED, norm(jvm.output()), "JVM output");
        Run scr = runScript(src, tmp);
        assertTrue(scr.ok(), () -> "Script: " + scr.output());
        assertEquals(norm(jvm.output()), norm(scr.output()), "JVM x Script byte-a-byte");
        Run js = runJs(src, tmp.resolve("o-zip-js"));
        assertTrue(js.ok(), () -> "JS: " + js.output());
        assertEquals(norm(jvm.output()), norm(js.output()), "JVM x JS byte-a-byte");
    }

    @Test
    void zipNativeX86(@TempDir Path tmp) throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("ZipNative.kf");
        Files.writeString(src, REFERENCE);
        Run n = runNativeX86(src, tmp.resolve("o-zip-native"));
        assertTrue(n.ok(), () -> "Native: " + n.output());
        assertEquals(EXPECTED_REFERENCE, norm(n.output()), "Native output");
    }

    @Test
    void zipCross(@TempDir Path tmp) throws Exception {
        for (String arch : new String[]{"riscv64", "aarch64"}) {
            assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross " + arch + " toolchain + qemu ausente — pulando");
            Target t = arch.equals("riscv64") ? Target.NATIVE_RISCV64 : Target.NATIVE_AARCH64;
            Path src = tmp.resolve("Zip-" + arch + ".kf");
            Files.writeString(src, REFERENCE);
            Path out = tmp.resolve("o-zip-" + arch);
            CompilationResult r = driver.compile(src, out, t);
            assertTrue(r.success(), arch + " compile: " + diags(r));
            Path bin = out.resolve("Default/Main");
            assertTrue(Files.isRegularFile(bin), arch + " binary must exist");
            String output = NativeRiscv64E2ETest.runQemu(arch, bin);
            assertEquals(EXPECTED_REFERENCE, norm(output), arch + " output");
        }
    }
}
