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
 * `kof.financial` slice 4 — KofBOL fatia 5a (`libs/legacy/Copybook.kf`, D-KOFBOL): OCCURS (fixed repeat) expansion — subscripted names, byte-exact widths.
 * Exact minor-unit money: currency-safe arithmetic (a mismatch is an ERROR,
 * never a silent conversion), overflow-checked, integer-only rounding (6
 * modes, halfEven among them), exact rational scaling. Pure Kof over existing
 * primitives (record + Long + String + enum) — no new syntax, no core change.
 */
class KofbolFatia5E2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "legacy"; }

    @Override public List<String> libraryMarkers() { return List.of("Legacy.kf"); }

    private static final String PROBE = """
            import legacy.Copybook

            main() {
                var cpy = "       01  ORDER.\n" +
                    "           05  CUST  PIC X(4).\n" +
                    "           05  ITEM  PIC X(2) OCCURS 3 TIMES.\n" +
                    "           05  TOTAL PIC 9(3).\n"
                var lay = parseCopybook(cpy)
                println(lay.fields.size)
                println(totalLength(lay))
                println(renderLayout(lay, "order"))
                var rec = encode(lay, listOf("ACME", "AB", "CD", "EF", "007"))
                println("[" + rec + "]")
                var vals = decode(lay, rec)
                println("[" + vals.get(2) + "]")
                println("[" + vals.get(4) + "]")
                var one = "       05  S  PIC 9 OCCURS 1 TIMES.\n"
                var lay1 = parseCopybook(one)
                println(renderLayout(lay1, "s"))
                var g = "       05  G.\n           10  X PIC X.\n"
                var occ = "       05  G OCCURS 2 TIMES.\n           10  X PIC X.\n"
                try { parseCopybook(occ) } catch (String e) { println(e) }
                try { parseCopybook(g) } catch (String e) { println(e) }
                var bad = "       05  Z PIC X OCCURS ZERO TIMES.\n"
                try { parseCopybook(bad) } catch (String e) { println(e) }
            }
            """;

    // 150+250=400 -> R$ 4.00 ; 250-150=100 -> R$ 1.00 ; negate 150 -> R$ -1.50 ;
    // roundTo(155, 1, halfEven) -> 160 -> R$ 1.60 ; scale(150, 3/2) = 225 -> R$ 2.25 ;
    // JPY has 0 minor units: Money(0,"JPY") -> "JPY 0" ; roundTo(12345, 0) -> "JPY 12345"
    private static final String EXPECTED = "5\n13\nvar order = Layout(listOf(textOf(\"CUST\", 4), textOf(\"ITEM(1)\", 2), textOf(\"ITEM(2)\", 2), textOf(\"ITEM(3)\", 2), digitsOf(\"TOTAL\", 3)))\n[ACMEABCDEF007]\n[CD]\n[007]\nvar s = Layout(listOf(digitsOf(\"S(1)\", 1)))\nclause not supported yet: OCCURS on a group item (LEG014)\nmalformed OCCURS count on Z (LEG015)";





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
    void kofbolOccursOnJvm() throws Exception {
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
    void kofbolOccursOnScript() throws Exception {
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
