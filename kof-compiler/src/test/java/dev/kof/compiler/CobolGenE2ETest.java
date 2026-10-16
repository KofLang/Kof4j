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
class CobolGenE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "cobol"; }
    @Override public List<String> libraryNames() { return List.of("cobol", "legacy"); }

    @Override public List<String> libraryMarkers() { return List.of("Legacy.kf", "Scanner.kf", "Lexer.kf", "Ast.kf", "ProcDiv.kf", "Gen.kf"); }

    private static final String PROBE = """
            import cobol.Scanner
            import cobol.Lexer
            import cobol.Ast
            import cobol.Gen
import cobol.ProcDiv

            main() {
                var prog = "       PROCEDURE DIVISION.\n" +
                    "       HELLO-PARA.\n" +
                    "           DISPLAY 'HELLO KOF'.\n" +
                    "       BYE-PARA.\n" +
                    "           DISPLAY 'BYE'.\n" +
                    "           MOVE X TO Y.\n"
                var paras = procedureOf(prog)
                var r = genProcedure(paras)
                println(r.okCount)
                println(r.cbCount)
                println(r.kof)
            }
            """;

    // 150+250=400 -> R$ 4.00 ; 250-150=100 -> R$ 1.00 ; negate 150 -> R$ -1.50 ;
    // roundTo(155, 1, halfEven) -> 160 -> R$ 1.60 ; scale(150, 3/2) = 225 -> R$ 2.25 ;
    // JPY has 0 minor units: Money(0,"JPY") -> "JPY 0" ; roundTo(12345, 0) -> "JPY 12345"
    private static final String EXPECTED = "2\n1\nhelloPara() {\n    println(\"HELLO KOF\")\n}\nbyePara() {\n    println(\"BYE\")\n    // CB010: verb MOVE not mapped yet\n}\n\nmain() {\n    helloPara()\n    byePara()\n}";





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
    void cobolGenOnJvm() throws Exception {
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
        var allOut = stdout.toString(StandardCharsets.UTF_8).strip();
        assertEquals(EXPECTED, allOut);
        // the PLAN'S ACCEPTANCE SEED: the generated Kof must compile AND run.
        // The generated source is everything from the first `fun ` in the
        // captured stdout (the probe prints r.kof as its last output).
        var at = allOut.indexOf("() {");
        var lineStart = allOut.lastIndexOf("\n", at) + 1;
        var kofText = allOut.substring(lineStart);
        var genSrc = root.resolve("Generated.kf");
        Files.writeString(genSrc, kofText + "\n");
        Path gout = root.resolve("gen-out");
        CompilationResult gr = driver.compileSources(List.of(genSrc), gout, Target.JVM, root);
        assertTrue(gr.success(), () -> "generated compile: " + gr.diagnostics().getDiagnostics());
        var genStdout = new ByteArrayOutputStream();
        var prev = System.out;
        System.setOut(new java.io.PrintStream(genStdout, true, StandardCharsets.UTF_8));
        try (var loader = new java.net.URLClassLoader(
                new java.net.URL[]{gout.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, loader)
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(prev);
        }
        assertEquals("HELLO KOF\nBYE", genStdout.toString(StandardCharsets.UTF_8).strip());
        // PHASE 8 (plan §30): the generated source survives the CANONICAL
        // FORMATTER — format → compile → run → the SAME output, and the
        // formatter is idempotent on it.
        var canon = dev.kof.compiler.KofFormatter.format(kofText, "Generated.kf");
        assertEquals(canon, dev.kof.compiler.KofFormatter.format(canon, "Generated.kf"),
                "the formatter is not idempotent on the generated source");
        var canonSrc = root.resolve("Canonical.kf");
        Files.writeString(canonSrc, canon);
        Path cout = root.resolve("canon-out");
        CompilationResult cr = driver.compileSources(List.of(canonSrc), cout, Target.JVM, root);
        assertTrue(cr.success(), () -> "canonical compile: " + cr.diagnostics().getDiagnostics());
        var canonStdout = new ByteArrayOutputStream();
        var prev2 = System.out;
        System.setOut(new java.io.PrintStream(canonStdout, true, StandardCharsets.UTF_8));
        try (var loader2 = new java.net.URLClassLoader(
                new java.net.URL[]{cout.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, loader2)
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(prev2);
        }
        assertEquals("HELLO KOF\nBYE", canonStdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void cobolGenOnScript() throws Exception {
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
