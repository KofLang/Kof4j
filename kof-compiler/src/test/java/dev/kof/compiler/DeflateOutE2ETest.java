package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Universal platform #993 fatia 1a — the DEFLATE writer's literals-only
 * FIXED-block face ({@code libs/archive/Deflate.kf}, BTYPE=01, universal
 * #993). Pure Kof over the same composition the #852 reader used: no new
 * core primitive, no per-target code.
 *
 * <p>Two oracles, both exercised by the same probe run: (1) the probe itself
 * round-trips through the library's own {@code image.Inflate} and prints the
 * recovered text; (2) the test parses the probe's printed byte list and feeds
 * it to {@code java.util.zip.Inflater} — the JDK reads what Kof wrote.
 */
class DeflateOutE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "archive"; }

    @Override public List<String> libraryMarkers() { return List.of("Deflate.kf", "Zip.kf", "Gzip.kf"); }

    // The probe: compress the payload bytes (literals-only FIXED block),
    // print the compressed length + one decimal byte per line, then
    // round-trip through the library's own Inflate and print the recovered
    // text between markers.
    private static final String PROBE = """
            import archive.Deflate
            import image.Inflate

            main() {
                var data = new Int[40]
                var i = 0
                while (i < 40) {
                    data[i] = 97 + (i % 26)
                    i = i + 1
                }
                var enc = DeflateOut(data.length * 2 + 16)
                var n = enc.finish(data)
                println(n)
                var j = 0
                while (j < n) {
                    println(enc.bytes[j] & 255)
                    j = j + 1
                }
                var dec = Inflate(enc.bytes, data.length)
                var got = dec.run()
                var ok = 1
                var k = 0
                while (k < got) {
                    if (dec.out[k] != data[k]) { ok = 0 }
                    k = k + 1
                }
                if (ok == 1) { println("rt-ok") } else { println("rt-bad") }
                println("probe-done")
            }
            """;

    @Test
    void jdkInflaterReadsTheKofStream() throws Exception {
        Path root = tmp.resolve("deflate-jvm");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        Path out = root.resolve("out");
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JVM));
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());

        byte[] raw = stdoutOf(out);
        String text = new String(raw, StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
        String[] lines = text.split("\n");
        assertEquals("probe-done", lines[lines.length - 1], "the probe must complete");
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 40; i++) expected.append((char) ('a' + (i % 26)));
        assertTrue(text.contains("rt-ok"),
                "the library's own Inflate must round-trip the Kof-written stream: " + text);
        int n = Integer.parseInt(lines[0]);
        assertTrue(n > 0 && n < 200, "sane compressed length: " + n);

        byte[] compressed = new byte[n];
        for (int i = 0; i < n; i++) {
            compressed[i] = (byte) Integer.parseInt(lines[1 + i]);
        }
        // raw DEFLATE (RFC 1951) — no zlib preamble, so nowrap=true
        Inflater inflater = new Inflater(true);
        inflater.setInput(compressed);
        byte[] recovered = new byte[64];
        int got = inflater.inflate(recovered);
        inflater.end();
        for (int i = 0; i < got; i++) {
            assertEquals('a' + (i % 26), recovered[i] & 255, "position " + i);
        }
        assertEquals(40, got, "the JDK Inflater must read all 40 letters (got " + got + ")");
    }

    @Test
    void roundTripOnScript() throws Exception {
        Path root = tmp.resolve("deflate-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertTrue(result.stdout().strip().endsWith("probe-done"),
                "the script face must complete the round-trip preparation");
    }

    private byte[] stdoutOf(Path out) throws Exception {
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
        return stdout.toByteArray();
    }

    private <T> T withLibrary(Path root, CheckedSupplier<T> action) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        // the round-trip oracle lives in ANOTHER library (image) — copy it in
        Path imageRoot = root.resolve("kof-install/lib/kof-libs/image");
        Files.createDirectories(imageRoot);
        Files.copy(Path.of("../libs/image/Inflate.kf"), imageRoot.resolve("Inflate.kf"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
        try (var walk = Files.walk(root.resolve("kof-install"))) {
            walk.filter(Files::isRegularFile).forEach(f -> System.err.println("INSTALLED " + root.relativize(f)));
        }
        try {
            return action.get();
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
