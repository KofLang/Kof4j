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
class CobolLexerE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "cobol"; }
    @Override public List<String> libraryNames() { return List.of("cobol", "legacy"); }

    @Override public List<String> libraryMarkers() { return List.of("Legacy.kf", "Scanner.kf", "Lexer.kf"); }

    private static final String PROBE = """
            import cobol.Lexer

            main() {
                var fixed = "      *a comment\n" +
                    "       01  CUST.\n" +
                    "           05  NAME PIC X(10).\n"
                var ts = tokenize(fixed, "fixed")
                println(ts.size)
                println(ts.get(0).kind)
                println(ts.get(0).text)
                println(ts.get(0).line)
                println(ts.get(3).kind)
                println(ts.get(3).text)
                var free = "IDENTIFICATION DIVISION.\n" +
                    "PROGRAM-ID. ORDERLOG.\n" +
                    "*> a free comment\n" +
                    "MOVE 'HI' TO G.\n"
                var tf = tokenize(free, "free")
                println(tf.size)
                println(tf.get(3).kind)
                println(tf.get(3).text)
                println(tokenKinds(tf).size)
                var st = tokenize("MOVE 42 TO X.\n", "free")
                println(st.get(1).kind)
                println(st.get(1).text)
                var dir = tokenize(">>SOURCE FORMAT IS FREE\n", "free")
                println(dir.get(0).kind)
                try { tokenize("MOVE 'X.\n", "free") } catch (String e) { println(e) }
                try { tokenize("MOVE %% X.\n", "free") } catch (String e) { println(e) }
            }
            """;

    // 150+250=400 -> R$ 4.00 ; 250-150=100 -> R$ 1.00 ; negate 150 -> R$ -1.50 ;
    // roundTo(155, 1, halfEven) -> 160 -> R$ 1.60 ; scale(150, 3/2) = 225 -> R$ 2.25 ;
    // JPY has 0 minor units: Money(0,"JPY") -> "JPY 0" ; roundTo(12345, 0) -> "JPY 12345"
    private static final String EXPECTED = "11\nNUMBER\n01\n2\nNUMBER\n05\n12\nWORD\nPROGRAM-ID\n12\nNUMBER\n42\nPSEUDO\nunterminated string literal at line 1 (CB005)\nunexpected character at line 1: % (CB006)";





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
    void cobolLexerOnJvm() throws Exception {
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
    void cobolLexerOnScript() throws Exception {
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
