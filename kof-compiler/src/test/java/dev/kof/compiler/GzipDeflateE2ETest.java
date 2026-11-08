package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #993 integration slice — {@code GzipWriter} gains the method-8 face: the
 * 2-arg constructor ({@code GzipWriter(path, 8)}) compresses the member with
 * the library's own {@code DeflateOut} (FIXED blocks + LZ77), while the 1-arg
 * constructor keeps the STORED behavior byte-for-byte (freeze rule 2 — the
 * existing golden/control tests stay untouched). Method vocabulary mirrors
 * the archive format itself (0 = stored, 8 = deflate). The oracle is the JDK
 * {@code GZIPInputStream}, which must read what Kof wrote.
 */
class GzipDeflateE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "archive"; }

    @Override public List<String> libraryMarkers() { return List.of("Deflate.kf", "Zip.kf", "Gzip.kf"); }

    // method 8: a repetitive payload must compress (the .gz is smaller than
    // the raw text) and the JDK GZIPInputStream must read it back.
    private static final String PROBE_DEFLATE = """
            import archive.Gzip

            main() {
                var w = GzipWriter("OUTPATH", 8)
                var half = "abcabc"
                var i = 0
                while (i < 50) {
                    w.addText(half)
                    i = i + 1
                }
                var isize = w.close()
                println("ISIZE=" + isize)
                println("probe-done")
            }
            """;

    // the 1-arg constructor keeps STORED — the control face (freeze rule 2).
    private static final String PROBE_STORED = """
            import archive.Gzip

            main() {
                var w = GzipWriter("OUTPATH")
                w.addText("stored control")
                var isize = w.close()
                println("ISIZE=" + isize)
                println("probe-done")
            }
            """;

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @Test
    void method8CompressesAndJdkReadsIt(@TempDir Path jvmDir) throws Exception {
        Path gz = jvmDir.resolve("probe-method8.gz");
        Path root = jvmDir.resolve("src");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE_DEFLATE.replace("OUTPATH", gz.toString()));
        Path out = root.resolve("out");
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JVM));
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        String text = runJvm(out);
        assertTrue(text.contains("probe-done"), "the probe must complete: " + text);
        int isize = Integer.parseInt(line(text, "ISIZE="));
        assertEquals(300, isize, "ISIZE counts the uncompressed bytes");

        long gzSize = Files.size(gz);
        assertTrue(gzSize < 300, "a 300-byte abc-cycle member must compress below its raw size: "
                + gzSize + " bytes");

        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(gz))) {
            byte[] recovered = in.readAllBytes();
            assertEquals(300, recovered.length);
            for (int i = 0; i < 300; i++) {
                assertEquals('a' + (i % 3), recovered[i] & 255, "position " + i);
            }
        }
    }

    @Test
    void storedDefaultStaysByteIdentical(@TempDir Path jvmDir) throws Exception {
        Path gz = jvmDir.resolve("probe-stored.gz");
        Path root = jvmDir.resolve("src");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE_STORED.replace("OUTPATH", gz.toString()));
        Path out = root.resolve("out");
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JVM));
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        String text = runJvm(out);
        assertTrue(text.contains("ISIZE=14"), "stored control: " + text);

        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(gz))) {
            byte[] recovered = in.readAllBytes();
            assertEquals("stored control", new String(recovered, StandardCharsets.UTF_8));
        }
    }

    @Test
    void method8OnScript() throws Exception {
        Path root = tmp.resolve("gz-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE_DEFLATE.replace("OUTPATH",
                root.resolve("probe.gz").toString()));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertTrue(result.stdout().contains("ISIZE=300"), "script ISIZE: " + result.stdout());
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
        // Gzip.kf's reader half imports image.Inflate — the image library must
        // be installed alongside archive (per-file import rule).
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
}
