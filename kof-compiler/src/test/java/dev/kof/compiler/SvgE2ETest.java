package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Universal platform Stage 4.5 slice — the SVG WRITER
 * ({@code libs/svg/Svg.kf}): document/frame/rect/line/text primitives with
 * XML escaping, plus {@code barChart} (max-scaled, Int-quantized geometry,
 * empty series refuses SVG001). Pure Kof string building — no charting
 * engine, no FFI; the ENGINE bindings stay blocked (the plan records it).
 * Assertions are contains-based on TAGS and ESCAPED ENTITIES, and on the
 * DETERMINISTIC Int geometry (all emitted numbers are Ints, so no
 * Double-formatting divergence between targets).
 */
class SvgE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "svg"; }

    @Override public List<String> libraryMarkers() { return List.of("Svg.kf"); }

    private static final String PROBE = """
            import svg.Svg

            main() {
                var doc = barChart(listOf(2.0, 4.0, 1.0), 300, 200)
                println(doc.contains("<svg"))
                println(doc.contains("<rect"))
                println(doc.contains("</svg>"))
                println(doc.contains("height=\\"180\\""))
                println(doc.contains("width=\\"89\\""))
                var r = svgRect(0, 0, 10, 20, "#ff0000")
                println(r.contains("<rect") && r.contains("#ff0000"))
                var t = svgText(5, 5, "a<b&c")
                println(t.contains("&lt;") && t.contains("&amp;"))
                var frame = svgDocument(100, 50, svgRect(0, 0, 10, 10, "#000000"))
                println(frame.contains("viewBox") && frame.contains("</svg>"))
                try {
                    barChart(listOf(), 100, 100)
                } catch (String e) {
                    println(e.contains("SVG001"))
                }
            }
            """;

    // bar geometry is DETERMINISTIC Int math: usable 300-20=280, slot=93,
    // bar width=89, the max bar (4.0) spans the full plot height 180.
    private static final String EXPECTED = "true\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue";

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
    void svgOnJvm() throws Exception {
        Path root = tmp.resolve("svg-jvm");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
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
    void svgOnScript() throws Exception {
        Path root = tmp.resolve("svg-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
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
