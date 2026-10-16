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
 * `kof.financial` slice 4 — KofBOL fatia 6 (`libs/legacy/Adapter.kf`, D-KOFBOL): the adapter/anti-corruption layer + the return-code model.
 * Exact minor-unit money: currency-safe arithmetic (a mismatch is an ERROR,
 * never a silent conversion), overflow-checked, integer-only rounding (6
 * modes, halfEven among them), exact rational scaling. Pure Kof over existing
 * primitives (record + Long + String + enum) — no new syntax, no core change.
 */
class KofbolFatia6E2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "legacy"; }

    @Override public List<String> libraryMarkers() { return List.of("Legacy.kf"); }

    private static final String PROBE = """
            import legacy.Legacy
            import legacy.Adapter

            main() {
                var lay = Layout(listOf(textOf("name", 4), digitsOf("amt", 3)))
                var m = toMap(lay, decode(lay, "JOHN042"))
                var n = m.get("name")
                if (n != null) {
                    println("[" + n + "]")
                }
                var back = valuesOf(lay, m)
                println(encode(lay, back))
                println(response(0, "done").status)
                println(response(4, "hmm").status)
                println(response(8, "bad").status)
                println(response(16, "severe").status)
                println(isOk(0))
                println(isOk(8))
                var r = response(12, "rc-12")
                println(r.returnCode)
                println(r.message)
                try { response(-1, "x") } catch (String e) { println(e) }
                var m2 = mapOf<String, String>()
                m2.put("name", "JOHN")
                try { valuesOf(lay, m2) } catch (String e) { println(e) }
                try { toMap(lay, listOf("A")) } catch (String e) { println(e) }
            }
            """;

    // 150+250=400 -> R$ 4.00 ; 250-150=100 -> R$ 1.00 ; negate 150 -> R$ -1.50 ;
    // roundTo(155, 1, halfEven) -> 160 -> R$ 1.60 ; scale(150, 3/2) = 225 -> R$ 2.25 ;
    // JPY has 0 minor units: Money(0,"JPY") -> "JPY 0" ; roundTo(12345, 0) -> "JPY 12345"
    private static final String EXPECTED = "[JOHN]\nJOHN042\nOK\nWARNING\nERROR\nSEVERE\ntrue\nfalse\n12\nrc-12\ninvalid return code: -1 (LEG017)\nmissing binding for field amt (LEG016)\nvalue count 1 != field count 2 (LEG004)";





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
    void kofbolAdapterOnJvm() throws Exception {
        Path root = tmp.resolve("money-jvm");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE.formatted(root.resolve("kof.db")));
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
        assertEquals(EXPECTED, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void kofbolAdapterOnScript() throws Exception {
        Path root = tmp.resolve("money-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE.formatted(root.resolve("kof.db")));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals(EXPECTED, result.stdout().strip());
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
