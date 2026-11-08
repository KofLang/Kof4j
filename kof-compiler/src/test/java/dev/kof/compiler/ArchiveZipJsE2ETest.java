package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #995 — `import archive.Zip` must compile WHOLE for the JS target. The
 * reporter's probe never calls {@code extractTo}, but the module compiles
 * eagerly, so the single {@code isSymlink()} call inside {@code extractTo}
 * (an IOJS001 refusal on JS) killed EVERY import of the library.
 *
 * <p>ROOT FIX (this slice): the JS host (GraalJS + Java bridges) gains the
 * REAL {@code isSymlink} face ({@code Files.isSymbolicLink} behind
 * {@code kof_platform.fileIsSymlink}) — the overwrite-safety check becomes
 * AVAILABLE on JS, never skipped (no silent fallback, no weakened guard).
 *
 * <p>Oracle is the JVM: the same probe runs on both targets and the stdout
 * must match byte-for-byte.
 */
class ArchiveZipJsE2ETest implements LibraryInstallSupport {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "archive"; }
    @Override public List<String> libraryMarkers() { return List.of("Deflate.kf", "Zip.kf", "Gzip.kf"); }

    /** The reporter's probe verbatim (paths parameterized). */
    private static final String PROBE = """
            import archive.Zip

            main() {
                var writer = ZipWriter("OUTPATH")
                writer.addText("documentos/nota.xml", "<nota>teste-kof</nota>")
                writer.addText("dados.csv", "id,valor\\n1,10\\n")
                println("ZIP_BYTES=" + writer.close())

                var z = Zip("OUTPATH")
                println("ENTRIES=" + z.entries.size())
                println("VALID=" + z.validate())
                for (var e in z.entries) {
                    println("FILE=" + e.name + " METHOD=" + e.method)
                    println("CONTENT=" + z.readText(e, 1048576))
                }
            }
            """;

    @Test
    void jsCompilesAndRunsTheWholeZipImportLikeTheJvm() throws Exception {
        // GOLDEN: the JVM oracle of the same probe.
        Path jvmRoot = tmp.resolve("jvm");
        Files.createDirectories(jvmRoot);
        Path jvmZip = jvmRoot.resolve("probe.zip").toAbsolutePath();
        Files.writeString(jvmRoot.resolve("Main.kf"), PROBE.replace("OUTPATH", jvmZip.toString()));
        Path jvmOut = jvmRoot.resolve("out");
        CompilationResult jvmResult = withLibrary(jvmRoot,
                () -> driver.compile(jvmRoot.resolve("Main.kf"), jvmOut, Target.JVM));
        assertTrue(jvmResult.success(), "JVM compile: " + jvmResult.diagnostics().getDiagnostics());
        String golden = runJvm(jvmOut);
        // validate() returns the count of valid entries (2 here) — the sanity
        // pin for the oracle, the equality below is the real contract.
        assertTrue(golden.contains("VALID=2"), "JVM oracle sanity: " + golden);

        // THE FACE UNDER TEST: the SAME probe for the JS target.
        Path jsRoot = tmp.resolve("js");
        Files.createDirectories(jsRoot);
        Path jsZip = jsRoot.resolve("probe.zip").toAbsolutePath();
        Files.writeString(jsRoot.resolve("Main.kf"), PROBE.replace("OUTPATH", jsZip.toString()));
        Path jsOut = jsRoot.resolve("out");
        CompilationResult jsResult = withLibrary(jsRoot,
                () -> driver.compile(jsRoot.resolve("Main.kf"), jsOut, Target.JS));
        assertTrue(jsResult.success(), "JS compile failed: " + jsResult.diagnostics().getDiagnostics());
        Files.copy(jsOut.resolve("Default.mjs"), Path.of("/tmp/zipjs-Default.mjs"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsOut.resolve("Default.mjs"), stdout,
                new ByteArrayInputStream(new byte[0]), stderr);
        String jsOutText = stdout.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
        assertEquals(0, exit, "JS exit: stderr=" + stderr + " stdout=" + stdout);
        assertEquals(golden, jsOutText, "JS output must match the JVM oracle");
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
