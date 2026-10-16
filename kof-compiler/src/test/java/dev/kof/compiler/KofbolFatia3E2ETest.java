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
 * `kof.financial` slice 4 — KofBOL fatia 3 (`libs/legacy/Legacy.kf`, D-KOFBOL): COMP-3 packed decimal with full sign/scale/overflow tests.
 * Exact minor-unit money: currency-safe arithmetic (a mismatch is an ERROR,
 * never a silent conversion), overflow-checked, integer-only rounding (6
 * modes, halfEven among them), exact rational scaling. Pure Kof over existing
 * primitives (record + Long + String + enum) — no new syntax, no core change.
 */
class KofbolFatia3E2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "legacy"; }

    @Override public List<String> libraryMarkers() { return List.of("Legacy.kf"); }

    private static final String PROBE = """
            import legacy.Legacy

            main() {
                var a = packComp3("12345", 5, 0)
                println(a.size)
                println(a.get(0))
                println(a.get(2))
                println(unpackComp3(a, 5, 0))
                var b = packComp3("-12.34", 4, 2)
                println(b.size)
                println(b.get(0))
                println(b.get(2))
                println(unpackComp3(b, 4, 2))
                var c = packComp3("7", 3, 2)
                println(c.size)
                println(unpackComp3(c, 3, 2))
                println(comp3Width(6))
                try { packComp3("1234567", 5, 0) } catch (String e) { println(e) }
                try { packComp3("12x", 3, 0) } catch (String e) { println(e) }
                try { unpackComp3(listOf(255, 12), 3, 0) } catch (String e) { println(e) }
                println(unpackComp3(listOf(18, 52, 95), 5, 0))
                try { unpackComp3(listOf(18, 52, 89), 5, 0) } catch (String e) { println(e) }
                try { unpackComp3(listOf(18, 52), 5, 0) } catch (String e) { println(e) }
            }
            """;

    // 150+250=400 -> R$ 4.00 ; 250-150=100 -> R$ 1.00 ; negate 150 -> R$ -1.50 ;
    // roundTo(155, 1, halfEven) -> 160 -> R$ 1.60 ; scale(150, 3/2) = 225 -> R$ 2.25 ;
    // JPY has 0 minor units: Money(0,"JPY") -> "JPY 0" ; roundTo(12345, 0) -> "JPY 12345"
    private static final String EXPECTED = "3\n18\n92\n12345\n3\n1\n77\n-12.34\n2\n7.00\n4\nvalue does not fit in 5 digits with scale 0 (LEG009)\nmalformed decimal: 12x (LEG010)\nbad digit nibble: 15 (LEG011)\n12345\nbad sign nibble: 9 (LEG012)\npacked width mismatch: 2 bytes for 5 digits (LEG013)";




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
    void kofbolComp3OnJvm() throws Exception {
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
    void kofbolComp3OnScript() throws Exception {
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
