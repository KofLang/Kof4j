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
 * `kof.financial` slice 4 — KofBOL fatia 5b (`libs/legacy/Copybook.kf`, D-KOFBOL): the 88-level condition names — parseCopybookFull + the generated named constants.
 * Exact minor-unit money: currency-safe arithmetic (a mismatch is an ERROR,
 * never a silent conversion), overflow-checked, integer-only rounding (6
 * modes, halfEven among them), exact rational scaling. Pure Kof over existing
 * primitives (record + Long + String + enum) — no new syntax, no core change.
 */
class KofbolFatia5bE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "legacy"; }

    @Override public List<String> libraryMarkers() { return List.of("Legacy.kf"); }

    private static final String PROBE = """
            import legacy.Copybook

            main() {
                var cpy = "       01  FLAGREC.\n" +
                    "           05  FLAG  PIC X.\n" +
                    "           88  FLAG-ON  VALUE 'Y'.\n" +
                    "           88  FLAG-OFF VALUE 'N'.\n"
                var cb = parseCopybookFull(cpy)
                println(cb.layout.fields.size)
                println(renderLayout(cb.layout, "flagrec"))
                println(renderConditions(cb.conditions))
                try { parseCopybook(cpy) } catch (String e) { println(e) }
                var multi = "       88  M VALUE 'A' 'B'.\n"
                try { parseCopybookFull(multi) } catch (String e) { println(e) }
                var thru = "       88  T VALUE 1 THRU 9.\n"
                try { parseCopybookFull(thru) } catch (String e) { println(e) }
                var nolit = "       88  N.\n"
                try { parseCopybookFull(nolit) } catch (String e) { println(e) }
            }
            """;

    // 150+250=400 -> R$ 4.00 ; 250-150=100 -> R$ 1.00 ; negate 150 -> R$ -1.50 ;
    // roundTo(155, 1, halfEven) -> 160 -> R$ 1.60 ; scale(150, 3/2) = 225 -> R$ 2.25 ;
    // JPY has 0 minor units: Money(0,"JPY") -> "JPY 0" ; roundTo(12345, 0) -> "JPY 12345"
    private static final String EXPECTED = "1\nvar flagrec = Layout(listOf(textOf(\"FLAG\", 1)))\nvar FLAG-ON = \"Y\"\nvar FLAG-OFF = \"N\"\nclause not supported yet: level 88 (LEG014)\nclause not supported yet: multiple 88 values / THRU (LEG014)\nclause not supported yet: multiple 88 values / THRU (LEG014)\nmalformed 88 condition: N. (LEG015)";





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
    void kofbolCond88OnJvm() throws Exception {
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
    void kofbolCond88OnScript() throws Exception {
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
