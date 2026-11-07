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
 * Universal platform Stage 4.5 — the ENGINE half ({@code libs/svg/Render.kf}):
 * static SVG text rendered to a PNG through librsvg + cairo, with the opaque
 * handles carried as {@code Long} (an extern may return the pointer as an
 * integer, the `j` slot) — the create→use→destroy pattern over the LANDED
 * FFI fatias, no new primitive.
 */
class SvgRenderE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;
    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "svg"; }
    @Override public List<String> libraryMarkers() { return List.of("Svg.kf", "Render.kf"); }

    private static final String PROBE = """
            import svg.Svg
            import svg.Render

            main() {
                var svg = "<svg xmlns=\\"http://www.w3.org/2000/svg\\" width=\\"8\\" height=\\"8\\"><rect x=\\"0\\" y=\\"0\\" width=\\"8\\" height=\\"8\\" fill=\\"red\\"/></svg>"
                var ok = renderSvgToPng(svg, "__PNG__", 8, 8)
                println(ok)
                println("done")
            }
            """;

    private Path writeProbe(Path root) throws Exception {
        Path dir = tmp.resolve("r" + System.nanoTime());
        Files.createDirectories(dir);
        Path png = dir.resolve("out.png");
        String src = PROBE.replace("__PNG__", png.toAbsolutePath().toString().replace('\\', '/'));
        Files.writeString(root.resolve("Main.kf"), src);
        return png;
    }

    private void assertPng(Path png) throws Exception {
        assertTrue(Files.exists(png), "PNG not written at " + png);
        byte[] b = Files.readAllBytes(png);
        assertTrue(b.length > 8, "PNG too small: " + b.length);
        assertEquals((byte) 0x89, b[0]); assertEquals((byte) 0x50, b[1]);
        assertEquals((byte) 0x4E, b[2]); assertEquals((byte) 0x47, b[3]);
    }

    private <T> T withLibrary(Path root, CheckedSupplier<T> action) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
        try { return action.get(); }
        finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
    }

    @Test
    void renderOnJvm() throws Exception {
        Path root = tmp.resolve("svg-jvm");
        Files.createDirectories(root);
        Path png = writeProbe(root);
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
        } finally { System.setOut(previousOut); }
        assertEquals("true\ndone", stdout.toString(StandardCharsets.UTF_8).strip());
        assertPng(png);
    }

    @Test
    void renderOnNativeX86() throws Exception {
        Path root = tmp.resolve("svg-native");
        Files.createDirectories(root);
        Path png = writeProbe(root);
        Path out = root.resolve("out-native");
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.NATIVE));
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        Path main = out.resolve("Default").resolve("Main");
        if (!Files.exists(main)) main = out.resolve("Main");
        assertTrue(Files.exists(main), () -> "no artifact at " + out);
        var proc = new ProcessBuilder(main.toString()).redirectErrorStream(true).start();
        String text = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertEquals("true\ndone", text, "native run: " + text);
        assertPng(png);
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> { T get() throws Exception; }
}
