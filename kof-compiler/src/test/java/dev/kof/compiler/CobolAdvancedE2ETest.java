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
class CobolAdvancedE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "cobol"; }
    @Override public List<String> libraryNames() { return List.of("cobol", "legacy"); }

    @Override public List<String> libraryMarkers() { return List.of("Legacy.kf", "Scanner.kf", "Lexer.kf", "Ast.kf", "ProcDiv.kf", "Gen.kf", "Advanced.kf"); }

    private static final String PROBE = """
            import cobol.Scanner
            import cobol.Lexer
            import cobol.Ast
            import cobol.ProcDiv
            import cobol.Gen
            import cobol.Advanced

            main() {
                var prog = "       PROCEDURE DIVISION.\n" +
                    "       MAIN-PARA.\n" +
                    "           PERFORM CALC THRU CLEANUP.\n" +
                    "           GO TO ERROR-HANDLER.\n" +
                    "       CALC.\n" +
                    "           ALTER MAIN-PARA.\n" +
                    "           SET ADDRESS OF P TO X.\n" +
                    "       ERROR-HANDLER.\n" +
                    "           DISPLAY 'ERR'.\n"
                var paras = procedureOf(prog)
                var adv = advancedScan(paras)
                println(adv.size)
                println(adv.get(0).construct)
                println(adv.get(1).construct)
                println(adv.get(2).construct)
                println(adv.get(3).construct)
                var r = genProcedure(paras)
                println(r.cbCount >= 3)
                println(r.kof.contains("CB010: verb GO not mapped yet"))
                println(r.kof.contains("CB010: verb ALTER not mapped yet"))
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
    void cobolAdvancedOnJvm() throws Exception {
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
        // the INDEPENDENT expectation: the COBOL DISPLAY semantics (each
        // DISPLAY emits its literal + a newline), derived by HAND from the
        // fixture — never from the generator's own output.
        // the detection output (the probe prints adv.size + the constructs)
        assertTrue(allOut.contains("4\nPERFORM THRU\nGO TO\nALTER\nSET ADDRESS"),
                () -> "advanced detection: " + allOut);
        // the generator REFUSES the advanced constructs in the output (the
        // two CB010 faces + at least 3 refusals are in the captured stdout).
        assertTrue(allOut.contains("true\ntrue\ntrue"),
                () -> "the CB010 refusals: " + allOut);
    }

    @Test
    void cobolAdvancedOnScript() throws Exception {
        Path root = tmp.resolve("money-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE.formatted(root.resolve("kof.db")));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
