package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Universal platform #993 fatia 1b — the LZ77 hash-chain matcher
 * ({@code libs/archive/Deflate.kf}). Compression becomes real: a repetitive
 * payload compresses, and both oracles (the JDK {@code Inflater} nowrap and
 * the library's own {@code image.Inflate}) read the stream byte-exact.
 */
class DeflateLz77E2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "archive"; }

    @Override public List<String> libraryMarkers() { return List.of("Deflate.kf", "Zip.kf", "Gzip.kf"); }

    // A highly repetitive payload: "abc" repeated — the matcher must find long
    // back-references and shrink the output well below the input, and the Kof
    // round-trip must recover every byte.
    private static final String PROBE = """
            import archive.Deflate
            import image.Inflate

            main() {
                var n = 300
                var data = new Int[n]
                var i = 0
                while (i < n) {
                    data[i] = 97 + (i % 3)
                    i = i + 1
                }
                var enc = DeflateOut(n * 2 + 64)
                var compressed = enc.finish(data)
                println("IN=" + n)
                println("OUT=" + compressed)
                var j = 0
                while (j < compressed) {
                    println(enc.bytes[j] & 255)
                    j = j + 1
                }
                var dec = Inflate(enc.bytes, n)
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
    void matcherCompressesAndBothOraclesReadIt() throws Exception {
        Path root = tmp.resolve("lz77-jvm");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        Path out = root.resolve("out");
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JVM));
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        String text = runJvm(out);

        int in = Integer.parseInt(line(text, "IN="));
        int compressed = Integer.parseInt(line(text, "OUT="));
        assertEquals(300, in);
        assertTrue(compressed < in / 2, "a repetitive payload must compress well below half: "
                + compressed + " of " + in);
        assertTrue(text.contains("rt-ok"), "the Kof round-trip must recover every byte: " + text);

        // the JDK is the second oracle: parse the printed bytes
        String[] lines = text.split("\n");
        int start = -1;
        for (int i = 0; i < lines.length; i++) if (lines[i].startsWith("OUT=")) { start = i + 1; break; }
        assertTrue(start > 0);
        byte[] raw = new byte[compressed];
        for (int i = 0; i < compressed; i++) raw[i] = (byte) Integer.parseInt(lines[start + i]);
        Inflater inflater = new Inflater(true);
        inflater.setInput(raw);
        byte[] recovered = new byte[400];
        int got = inflater.inflate(recovered);
        inflater.end();
        assertEquals(300, got);
        for (int i = 0; i < 300; i++) {
            assertEquals('a' + (i % 3), recovered[i] & 255, "JDK position " + i);
        }
    }

    @Test
    void matcherOnScript() throws Exception {
        Path root = tmp.resolve("lz77-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertTrue(result.stdout().contains("rt-ok"), "script round-trip: " + result.stdout());
    }

    private static String line(String text, String prefix) {
        for (String l : text.split("\n")) if (l.startsWith(prefix)) return l.substring(prefix.length());
        return "-1";
    }

    private String runJvm(Path out) throws Exception {
        var stdout = new java.io.ByteArrayOutputStream();
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
        return stdout.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
    }

    private <T> T withLibrary(Path root, CheckedSupplier<T> action) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        Path imageRoot = root.resolve("kof-install/lib/kof-libs/image");
        Files.createDirectories(imageRoot);
        Files.copy(Path.of("../libs/image/Inflate.kf"), imageRoot.resolve("Inflate.kf"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
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
