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
 * `kof.financial` slice 4 — KofBOL fatia 5c (`libs/legacy/Copybook.kf`, D-KOFBOL): REDEFINES — the overlay view with an explicit decode offset (decodeFrom).
 * Exact minor-unit money: currency-safe arithmetic (a mismatch is an ERROR,
 * never a silent conversion), overflow-checked, integer-only rounding (6
 * modes, halfEven among them), exact rational scaling. Pure Kof over existing
 * primitives (record + Long + String + enum) — no new syntax, no core change.
 */
class KofbolFatia5cE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "legacy"; }

    @Override public List<String> libraryMarkers() { return List.of("Legacy.kf"); }

    private static final String PROBE = """
            import legacy.Copybook

            main() {
                var cpy = "       01  REC.\n" +
                    "           05  A    PIC X(4).\n" +
                    "           05  B    REDEFINES A PIC 9(4).\n" +
                    "           05  TOT  PIC 9(2).\n"
                var cb = parseCopybookFull(cpy)
                println(cb.layout.fields.size)
                println(renderLayout(cb.layout, "rec"))
                println(renderRedefines(cb.redefines))
                var rec = "123499"
                var vals = decode(cb.layout, rec)
                println("[" + vals.get(0) + "]")
                println("[" + vals.get(1) + "]")
                var view = decodeFrom(cb.redefines.get(0).view, rec, cb.redefines.get(0).offset)
                println("[" + view.get(0) + "]")
                var miss = "       05  C    REDEFINES ZZ PIC X.\n"
                try { parseCopybookFull(miss) } catch (String e) { println(e) }
                var occ = "       05  I  PIC X OCCURS 2 TIMES.\n       05  R  REDEFINES I(1) PIC X.\n"
                try { parseCopybookFull(occ) } catch (String e) { println(e) }
                var comp = "       05  D    PIC 9(3) COMP-3.\n"
                try { parseCopybookFull(comp) } catch (String e) { println(e) }
            }
            """;

    // 150+250=400 -> R$ 4.00 ; 250-150=100 -> R$ 1.00 ; negate 150 -> R$ -1.50 ;
    // roundTo(155, 1, halfEven) -> 160 -> R$ 1.60 ; scale(150, 3/2) = 225 -> R$ 2.25 ;
    // JPY has 0 minor units: Money(0,"JPY") -> "JPY 0" ; roundTo(12345, 0) -> "JPY 12345"
    private static final String EXPECTED = "2\nvar rec = Layout(listOf(textOf(\"A\", 4), digitsOf(\"TOT\", 2)))\n// B overlays offset 0\nvar BView = Layout(listOf(digitsOf(\"B\", 4))) // decode with decodeFrom(BView, rec, 0)\n[1234]\n[99]\n[1234]\nREDEFINES target not found: ZZ (LEG015)\nclause not supported yet: REDEFINES of an OCCURS item (LEG014)\nclause not supported yet: COMP-3 (LEG014)";





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
    void kofbolRedefOnJvm() throws Exception {
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
    void kofbolRedefOnScript() throws Exception {
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
