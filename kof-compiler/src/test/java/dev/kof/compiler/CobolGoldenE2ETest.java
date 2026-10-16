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
class CobolGoldenE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "legacy"; }
    @Override public List<String> libraryNames() { return List.of("legacy"); }

    @Override public List<String> libraryMarkers() { return List.of("Legacy.kf", "Scanner.kf", "Lexer.kf", "Ast.kf", "Gen.kf", "Project.kf"); }

    private static final String PROBE = """
            import legacy.Copybook
            import legacy.Legacy

            main() {
                var maybe = File("../tests/golden/cobol-copybook-customer/input.txt").readText()
                var cpy = ""
                if (maybe != null) {
                    cpy = maybe
                }
                var cb = parseCopybookFull(cpy)
                println(renderLayout(cb.layout, "customer"))
                var word = File("../tests/golden/cobol-ebcdic-hello/input.txt").readText()
                var w = ""
                if (word != null) {
                    w = word.trim()
                }
                var b = encode37(w)
                var s = ""
                var i = 0
                while (i < b.size) {
                    s = s + b.get(i)
                    i = i + 1
                    if (i < b.size) {
                        s = s + " "
                    }
                }
                println(s)
                var comp = File("../tests/golden/cobol-comp3-signed/input.txt").readText()
                var cl = ""
                if (comp != null) {
                    cl = comp
                }
                var rows = cl.split("\n")
                var dec = rows[0].trim()
                var digits = rows[1].trim().toInt()
                var scale = rows[2].trim().toInt()
                var c = packComp3(dec, digits, scale)
                var s2 = ""
                i = 0
                while (i < c.size) {
                    s2 = s2 + c.get(i)
                    i = i + 1
                    if (i < c.size) {
                        s2 = s2 + " "
                    }
                }
                println(s2)
            }
            """;

    // 150+250=400 -> R$ 4.00 ; 250-150=100 -> R$ 1.00 ; negate 150 -> R$ -1.50 ;
    // roundTo(155, 1, halfEven) -> 160 -> R$ 1.60 ; scale(150, 3/2) = 225 -> R$ 2.25 ;
    // JPY has 0 minor units: Money(0,"JPY") -> "JPY 0" ; roundTo(12345, 0) -> "JPY 12345"





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
    void cobolGoldenOnJvm() throws Exception {
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
        var got = stdout.toString(StandardCharsets.UTF_8).strip().split("\n");
        assertEquals(Files.readString(Path.of("../tests/golden/cobol-copybook-customer/expected.txt")).strip(), got[0]);
        assertEquals(Files.readString(Path.of("../tests/golden/cobol-ebcdic-hello/expected.txt")).strip(), got[1]);
        assertEquals(Files.readString(Path.of("../tests/golden/cobol-comp3-signed/expected.txt")).strip(), got[2]);
    }

    @Test
    void cobolGoldenOnScript() throws Exception {
        Path root = tmp.resolve("money-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE.formatted(root.resolve("kof.db")));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        var got = result.stdout().strip().split("\n");
        assertEquals(Files.readString(Path.of("../tests/golden/cobol-copybook-customer/expected.txt")).strip(), got[0]);
        assertEquals(Files.readString(Path.of("../tests/golden/cobol-ebcdic-hello/expected.txt")).strip(), got[1]);
        assertEquals(Files.readString(Path.of("../tests/golden/cobol-comp3-signed/expected.txt")).strip(), got[2]);
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
