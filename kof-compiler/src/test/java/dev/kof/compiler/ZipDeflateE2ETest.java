package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Universal platform #993 ZIP-side integration — {@code ZipWriter.addDeflated}
 * writes a method-8 (DEFLATE) entry through the library's own {@code
 * DeflateOut}, per-entry (the ZIP format's method field is per entry). The
 * STORED path stays byte-identical (freeze rule 2). Oracles: the JDK {@code
 * ZipFile} reads what Kof wrote (both entries), and the library's own {@code
 * ZipReader} round-trips the deflated entry through {@code image.Inflate}.
 */
class ZipDeflateE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "archive"; }

    @Override public List<String> libraryMarkers() { return List.of("Deflate.kf", "Zip.kf", "Gzip.kf"); }

    // One DEFLATED entry (repetitive → compresses) + one STORED entry — the
    // per-entry choice is the whole point of the ZIP method field.
    private static final String PROBE = """
            import archive.Zip

            main() {
                var w = ZipWriter("OUTPATH")
                var big = new Int[300]
                var i = 0
                while (i < 300) {
                    big[i] = 97 + (i % 3)
                    i = i + 1
                }
                w.addDeflated("big-abc.txt", big)
                w.addText("small-stored.txt", "stored control")
                var size = w.close()
                println("SIZE=" + size)
                println("probe-done")
            }
            """;

    @Test
    void jdkZipFileReadsBothEntries(@TempDir Path jvmDir) throws Exception {
        Path zip = jvmDir.resolve("probe-method8.zip");
        Path root = jvmDir.resolve("src");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE.replace("OUTPATH", zip.toString()));
        Path out = root.resolve("out");
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JVM));
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        String text = runJvm(out);
        assertTrue(text.contains("probe-done"), "the probe must complete: " + text);

        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry deflated = zf.getEntry("big-abc.txt");
            assertNotNull(deflated, "the deflated entry must exist");
            assertEquals(8, deflated.getMethod(), "the deflated entry must carry method 8");
            byte[] big = zf.getInputStream(deflated).readAllBytes();
            assertEquals(300, big.length);
            for (int i = 0; i < 300; i++) {
                assertEquals('a' + (i % 3), big[i] & 255, "position " + i);
            }
            ZipEntry stored = zf.getEntry("small-stored.txt");
            assertNotNull(stored, "the stored entry must exist");
            assertEquals(0, stored.getMethod(), "the stored entry keeps method 0");
            assertEquals("stored control", new String(zf.getInputStream(stored).readAllBytes(),
                    StandardCharsets.UTF_8));
        }
    }

    @Test
    void deflatedEntryIsSmaller(@TempDir Path jvmDir) throws Exception {
        Path zip = jvmDir.resolve("probe-smaller.zip");
        Path root = jvmDir.resolve("src");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE.replace("OUTPATH", zip.toString()));
        Path out = root.resolve("out");
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JVM));
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        runJvm(out);
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry deflated = zf.getEntry("big-abc.txt");
            assertTrue(deflated.getCompressedSize() < 300,
                    "the deflated entry must be smaller than its 300 uncompressed bytes: "
                            + deflated.getCompressedSize());
        }
    }

    @Test
    void libraryReaderRoundTripsTheDeflatedEntry() throws Exception {
        Path root = tmp.resolve("zip-reader");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE.replace("OUTPATH",
                root.resolve("probe-reader.zip").toString()));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertTrue(result.stdout().contains("probe-done"), "script face: " + result.stdout());
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
        // Zip.kf's reader half imports image.Inflate — the image library must
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

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
