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
class CobolProcDivE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "cobol"; }
    @Override public List<String> libraryNames() { return List.of("cobol", "legacy"); }

    @Override public List<String> libraryMarkers() { return List.of("Legacy.kf", "Scanner.kf", "Lexer.kf", "Ast.kf", "ProcDiv.kf"); }

    private static final String PROBE = """
            import cobol.Scanner
            import cobol.Lexer
            import cobol.Ast
            import cobol.ProcDiv

            main() {
                var prog = "       PROCEDURE DIVISION.\n" +
                    "       MAIN-PARA.\n" +
                    "           PERFORM CALC-PARA.\n" +
                    "           PERFORM CALC-PARA.\n" +
                    "           PERFORM REPORT.\n" +
                    "           STOP RUN.\n" +
                    "       CALC-PARA.\n" +
                    "           ADD 1 TO X.\n" +
                    "       REPORT.\n" +
                    "           DISPLAY TOTAL.\n"
                var paras = procedureOf(prog)
                println(paras.size)
                println(paras.get(0).name)
                var cs = performs(paras)
                println(cs.size)
                println(cs.get(0).from)
                println(cs.get(0).to)
                println(cs.get(0).count)
                println(cs.get(1).to)
                var ghost = "       PROCEDURE DIVISION.\n" +
                    "       MAIN-PARA.\n" +
                    "           PERFORM NOEXIST.\n"
                try { performs(procedureOf(ghost)) } catch (String e) { println(e) }
            }
            """;

    // 150+250=400 -> R$ 4.00 ; 250-150=100 -> R$ 1.00 ; negate 150 -> R$ -1.50 ;
    // roundTo(155, 1, halfEven) -> 160 -> R$ 1.60 ; scale(150, 3/2) = 225 -> R$ 2.25 ;
    // JPY has 0 minor units: Money(0,"JPY") -> "JPY 0" ; roundTo(12345, 0) -> "JPY 12345"
    private static final String EXPECTED = "3\nMAIN-PARA\n2\nMAIN-PARA\nCALC-PARA\n2\nREPORT\nPERFORM target not found: NOEXIST (CB009)";





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
    void cobolProcDivOnJvm() throws Exception {
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
    void cobolProcDivOnScript() throws Exception {
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
