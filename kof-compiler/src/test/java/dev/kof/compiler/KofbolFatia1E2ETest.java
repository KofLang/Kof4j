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
 * `kof.financial` slice 4 — KofBOL fatia 1 (`libs/legacy/Legacy.kf`, D-KOFBOL): fixed-width PIC X / PIC 9 encode/decode, ASCII, pure Kof.
 * Exact minor-unit money: currency-safe arithmetic (a mismatch is an ERROR,
 * never a silent conversion), overflow-checked, integer-only rounding (6
 * modes, halfEven among them), exact rational scaling. Pure Kof over existing
 * primitives (record + Long + String + enum) — no new syntax, no core change.
 */
class KofbolFatia1E2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "legacy"; }

    @Override public List<String> libraryMarkers() { return List.of("Legacy.kf"); }

    private static final String PROBE = """
            import legacy.Legacy

            main() {
                var lay = Layout(listOf(textOf("name", 10), digitsOf("amount", 5)))
                var rec = encode(lay, listOf("JOHN", "42"))
                println("[" + rec + "]")
                println(rec.length)
                var vals = decode(lay, rec)
                println("[" + vals.get(0) + "]")
                println("[" + vals.get(1) + "]")
                println(numericOf(vals.get(1)))
                println(totalLength(lay))
                var p = fieldPic("suffix", "X(3)")
                println(p.kind + ":" + p.length)
                var q = fieldPic("bal", "9(7)")
                println(q.kind + ":" + q.length)
                try { encode(lay, listOf("JOHN", "123456")) } catch (String e) { println(e) }
                try { encode(lay, listOf("JOHN", "4X")) } catch (String e) { println(e) }
                try { decode(lay, "SHORT") } catch (String e) { println(e) }
                try { encode(lay, listOf("JOHN")) } catch (String e) { println(e) }
                try { fieldPic("x", "A(3)") } catch (String e) { println(e) }
                try { fieldPic("x", "X(1x)") } catch (String e) { println(e) }
            }
            """;

    // 150+250=400 -> R$ 4.00 ; 250-150=100 -> R$ 1.00 ; negate 150 -> R$ -1.50 ;
    // roundTo(155, 1, halfEven) -> 160 -> R$ 1.60 ; scale(150, 3/2) = 225 -> R$ 2.25 ;
    // JPY has 0 minor units: Money(0,"JPY") -> "JPY 0" ; roundTo(12345, 0) -> "JPY 12345"
    private static final String EXPECTED = "[JOHN      00042]\n15\n[JOHN      ]\n[00042]\n42\n15\nX:3\n9:7\nvalue too long for field amount: 6 > 5 (LEG003)\nnon-digit in numeric field amount (LEG002)\nrecord length 5 != layout length 15 (LEG001)\nvalue count 1 != field count 2 (LEG004)\nunsupported PIC clause: A(3) (LEG005)\nmalformed PIC count: X(1x) (LEG006)";


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
    void kofbolOnJvm() throws Exception {
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
    void kofbolOnScript() throws Exception {
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
