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
class CobolScannerE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "cobol"; }
    @Override public List<String> libraryNames() { return List.of("cobol", "legacy"); }

    @Override public List<String> libraryMarkers() { return List.of("Legacy.kf", "Scanner.kf"); }

    private static final String PROBE = """
            import cobol.Scanner

            main() {
                var prog = "       IDENTIFICATION DIVISION.\n" +
                    "       PROGRAM-ID. CUSTOMER.\n" +
                    "       DATA DIVISION.\n" +
                    "      *a fixed comment\n" +
                    "       PROCEDURE DIVISION.\n" +
                    "           DISPLAY GREETING.\n"
                var freeSrc = ">>SOURCE FORMAT IS FREE\n" +
                    "*> a free comment\n" +
                    "IDENTIFICATION DIVISION.\n" +
                    "PROGRAM-ID. ORDERLOG.\n"
                var inv = scanSource(CobolSource("src/customer.cbl", prog))
                println(inv.programId)
                println(inv.format)
                println(inv.divisions.size)
                println(inv.comments)
                println(inv.lines)
                var inv2 = scanSource(CobolSource("src/order.cob", freeSrc))
                println(inv2.programId)
                println(inv2.format)
                println(isCopybook("cp/CUSTOMER.cpy"))
                println(isProgram("x.CBL"))
                var order = discoveryOrder(listOf("b/Two.cbl", "a/One.cbl", "b/One.cbl"))
                println(order.get(0))
                println(order.get(1))
                var proj = scanProject(listOf(CobolSource("src/customer.cbl", prog),
                    CobolSource("cp/CUST.cpy", "01 X PIC X.")), "ibm")
                println(proj.dialect)
                println(proj.format)
                println(proj.sources.size)
                println(proj.copybooks.size)
                println(proj.notes.size)
                var nopid = "       IDENTIFICATION DIVISION.\n"
                var p2 = scanProject(listOf(CobolSource("src/x.cbl", nopid)), "ibm")
                println(p2.notes.get(0))
                try { scanProject(listOf(CobolSource("src/x.cbl", nopid)), "") } catch (String e) { println(e) }
                try { scanProject(listOf(CobolSource("src/x.txt", "x")), "ibm") } catch (String e) { println(e) }
            }
            """;

    // 150+250=400 -> R$ 4.00 ; 250-150=100 -> R$ 1.00 ; negate 150 -> R$ -1.50 ;
    // roundTo(155, 1, halfEven) -> 160 -> R$ 1.60 ; scale(150, 3/2) = 225 -> R$ 2.25 ;
    // JPY has 0 minor units: Money(0,"JPY") -> "JPY 0" ; roundTo(12345, 0) -> "JPY 12345"
    private static final String EXPECTED = "CUSTOMER\nfixed\n3\n1\n6\nORDERLOG\nfree\ntrue\ntrue\na/One.cbl\nb/One.cbl\nibm\nfixed\n1\n1\n0\nCB001 no PROGRAM-ID in src/x.cbl\ndialect not determined from the sources \u2014 pass --dialect (CB002)\nunsupported source extension: src/x.txt (CB003)";





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
    void cobolScannerOnJvm() throws Exception {
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
    void cobolScannerOnScript() throws Exception {
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
