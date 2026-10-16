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
class CobolSemanticE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "cobol"; }
    @Override public List<String> libraryNames() { return List.of("cobol", "legacy"); }

    @Override public List<String> libraryMarkers() { return List.of("Legacy.kf", "Scanner.kf", "Lexer.kf", "Ast.kf", "Semantic.kf"); }

    private static final String PROBE = """
            import cobol.Lexer
            import cobol.Ast
            import cobol.Semantic

            main() {
                var src = "       PROCEDURE DIVISION.\n" +
                    "       MAIN-PARA.\n" +
                    "           DISPLAY ALPHA.\n" +
                    "           MOVE X TO Y.\n" +
                    "           TELEPORT Z.\n" +
                    "       END-PARA.\n" +
                    "           DISPLAY BETA.\n" +
                    "           STOP RUN.\n"
                var ts = tokenize(src, "fixed")
                var r = analyze(parseParagraphs(ts))
                println(r.sentences)
                println(r.unknownVerbs)
                println(r.verbs.size)
                var d = findVerbUse(r.verbs, "DISPLAY", "MAIN-PARA")
                if (d != null) {
                    println(d.count)
                    println(d.confidence)
                }
                var t = findVerbUse(r.verbs, "TELEPORT", "MAIN-PARA")
                if (t != null) {
                    println(t.verb)
                    println(t.confidence)
                }
                var unk = unknownVerbsOf(r)
                println(unk.get(0))
                var s = findVerbUse(r.verbs, "STOP", "END-PARA")
                if (s != null) {
                    println(s.count)
                }
            }
            """;

    // 150+250=400 -> R$ 4.00 ; 250-150=100 -> R$ 1.00 ; negate 150 -> R$ -1.50 ;
    // roundTo(155, 1, halfEven) -> 160 -> R$ 1.60 ; scale(150, 3/2) = 225 -> R$ 2.25 ;
    // JPY has 0 minor units: Money(0,"JPY") -> "JPY 0" ; roundTo(12345, 0) -> "JPY 12345"
    private static final String EXPECTED = "6\n2\n6\n1\nknown\nTELEPORT\nunknown\nPROCEDURE\n1";





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
    void cobolSemanticOnJvm() throws Exception {
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
    void cobolSemanticOnScript() throws Exception {
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
