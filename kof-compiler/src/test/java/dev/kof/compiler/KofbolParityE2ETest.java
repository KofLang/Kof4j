package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `kof.financial` slice 4 — KofBOL fatia 8 (D-KOFBOL): the PORTABLE-CORE parity — the same probe on JVM/Script/JS/Native x86 (fixed-width, EBCDIC, COMP-3, the adapter, the copybook parser).
 * Exact minor-unit money: currency-safe arithmetic (a mismatch is an ERROR,
 * never a silent conversion), overflow-checked, integer-only rounding (6
 * modes, halfEven among them), exact rational scaling. Pure Kof over existing
 * primitives (record + Long + String + enum) — no new syntax, no core change.
 */
class KofbolParityE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "legacy"; }

    @Override public List<String> libraryMarkers() { return List.of("Legacy.kf", "Copybook.kf", "Adapter.kf"); }

    private static final String PROBE = """
            import legacy.Legacy
            import legacy.Copybook
            import legacy.Adapter

            main() {
                var lay = Layout(listOf(textOf("cust", 4), digitsOf("qty", 5)))
                var rec = encode(lay, listOf("ACME", "42"))
                println("[" + rec + "]")
                var vals = decode(lay, rec)
                println("[" + vals.get(1) + "]")
                var b = encode37("HELLO")
                println(b.get(0))
                println(decode37(b))
                var m = toMap(lay, vals)
                var n = m.get("qty")
                if (n != null) {
                    println("[" + n + "]")
                }
                var cb = "       01  R.\n           05  A PIC X(2) OCCURS 2 TIMES.\n"
                var full = parseCopybookFull(cb)
                println(full.layout.fields.size)
                println(renderLayout(full.layout, "r"))
                println(response(8, "bad").status)
            }
            """;

    // 150+250=400 -> R$ 4.00 ; 250-150=100 -> R$ 1.00 ; negate 150 -> R$ -1.50 ;
    // roundTo(155, 1, halfEven) -> 160 -> R$ 1.60 ; scale(150, 3/2) = 225 -> R$ 2.25 ;
    // JPY has 0 minor units: Money(0,"JPY") -> "JPY 0" ; roundTo(12345, 0) -> "JPY 12345"
    // the COMP-3 face: JVM/Script/Native only — the JS face is §734 (the
    // JS emitter returns undefined for this shape; catalogued OPEN).
    private static final String PROBE3 = """
            import legacy.Legacy
            import legacy.Adapter

            main() {
                var c = packComp3("-12.34", 4, 2)
                println(c.get(0))
                println(unpackComp3(c, 4, 2))
            }
            """;

    private static final String EXPECTED3 = "1\n-12.34";

    private static final String EXPECTED = "[ACME00042]\n[00042]\n200\nHELLO\n[00042]\n2\nvar r = Layout(listOf(textOf(\"A(1)\", 2), textOf(\"A(2)\", 2)))\nERROR";

    private <T> T withLibrary(Path root, CheckedSupplier<T> action) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
        try {
            return action.get();
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
    }

    @Test
    void parityOnJvm() throws Exception {
        Path root = tmp.resolve("money-jvm");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE3);
        Path out = root.resolve("out");
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JVM));
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new java.net.URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        assertEquals(EXPECTED3, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void parityOnScript() throws Exception {
        Path root = tmp.resolve("money-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE3);
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals(EXPECTED3, result.stdout().strip());
    }

    @Test
    void parityOnJs() throws Exception {
        Path root = tmp.resolve("money-js");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        Path out = root.resolve("out");
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(TestJdk.onPath("node"), out.resolve("Default.mjs").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String got = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, p.waitFor(), "JS exit: " + got);
        assertEquals(EXPECTED, got);
    }

    @Test
    void parityOnNativeX86() throws Exception {
        Assumptions.assumeTrue(dev.kof.compiler.nat.NativeToolchainGate.present(), "native toolchain absent");
        Path root = tmp.resolve("money-native");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE3);
        Path out = root.resolve("out");
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.NATIVE));
        assertTrue(r.success(), () -> "native compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(out.resolve("Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String got = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, p.waitFor(), "native exit: " + got);
        assertEquals(EXPECTED3, got);
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
