package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #852 — GZIP WRITING (`GzipWriter`, RFC 1952, DEFLATE STORED blocks).
 *
 * <p>The oracle is the host's `java.util.zip.GZIPInputStream`: it must read
 * EXACTLY what Kof wrote. A writer that is only read by its own reader proves
 * little. Then the same bytes go back through the library's own reader
 * (`gzipRead`/`gzipReadText`), and two writers with the same input must produce
 * byte-identical files (MTIME zero).
 *
 * <p>STORED is honest and SAYS so: like `ZipWriter`, this writer does not
 * compress — each chunk becomes a stored DEFLATE block (LEN/NLEN), which is a
 * valid RFC 1951 stream and needs no compressor. Compressed output is a
 * separate slice; a writer that claims "gzip" without compressing has to say it.
 */
class GzipWriterE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String ALFA = "conteudo do alfa\n";
    private static final String BETA = "xyz";
    private static final String ACCENTED = "café";

    private static byte[] readGzip(Path gz) throws Exception {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(gz))) {
            return in.readAllBytes();
        }
    }

    /** **A REFERÊNCIA abre e lê o que o Kof escreveu.** */
    @Test
    void jdkGzipInputStreamReadsWhatKofWrote(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("jvm");
        Files.createDirectories(root);
        Path gz = root.resolve("novo.gz");
        Files.writeString(root.resolve("Main.kf"), """
                import archive.Gzip

                main() {
                    var w = GzipWriter("%s")
                    w.addText("conteudo do alfa\\n")
                    w.addText("%s")
                    println("bytes=" + w.close())
                }
                """.formatted(gz.toAbsolutePath(), ACCENTED));
        String out = runOnJvm(root);
        assertTrue(out.startsWith("bytes="), "o escritor devolve o tamanho: " + out);
        assertArrayEquals((ALFA + ACCENTED).getBytes(StandardCharsets.UTF_8), readGzip(gz),
                "o GZIPInputStream do JDK le EXATAMENTE o que o Kof escreveu");
    }

    /** Blocos STORED de até 65535 bytes, multi-chamada e o stream vazio. */
    @Test
    void storedBlocksSpanChunksAndAnEmptyStreamIsValid(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("blocos");
        Files.createDirectories(root);
        Path gz = root.resolve("grande.gz");
        Path empty = root.resolve("vazio.gz");
        Files.writeString(root.resolve("Main.kf"), """
                import archive.Gzip

                main() {
                    var big = new Int[70000]
                    var i = 0
                    while (i < big.length) {
                        big[i] = i & 255
                        i = i + 1
                    }
                    var w = GzipWriter("%s")
                    w.addText("")
                    w.addBytes(big)
                    w.addText("%s")
                    println("n=" + w.close())
                    var e = GzipWriter("%s")
                    println("vazio=" + e.close())
                }
                """.formatted(gz.toAbsolutePath(), BETA, empty.toAbsolutePath()));
        String out = runOnJvm(root);
        assertEquals("n=70003\nvazio=0", out, "close devolve os bytes NAO comprimidos (ISIZE)");
        byte[] got = readGzip(gz);
        assertEquals(70000 + BETA.getBytes(StandardCharsets.UTF_8).length, got.length,
                "os tres pedacos atravessam os blocos e a referencia le todos");
        for (int i = 0; i < 70000; i++) {
            assertEquals(i & 255, got[i] & 255, "byte " + i + " do bloco grande");
        }
        assertEquals(BETA, new String(got, 70000, got.length - 70000, StandardCharsets.UTF_8),
                "o pedaco depois do bloco de 70000 bytes");
        assertEquals(0, readGzip(empty).length, "um gzip vazio e valido (bloco final vazio)");
    }

    /** Leitor × escritor da MESMA livraria, e o determinismo do cabeçalho. */
    @Test
    void theLibraryReadsItsOwnWriterAndWritesAreDeterministic(@TempDir Path dir) throws Exception {
        for (String face : List.of("jvm", "script")) {
            Path root = dir.resolve(face);
            Files.createDirectories(root);
            Path gz = root.resolve("rt.gz");
            Path a = root.resolve("a.gz");
            Path b = root.resolve("b.gz");
            Files.writeString(root.resolve("Main.kf"), """
                    import archive.Gzip

                    main() {
                        var w = GzipWriter("%s")
                        w.addText("%s")
                        w.close()
                        var back = gzipRead("%s", 4096)
                        var txt = gzipReadText("%s", 4096)
                        println("n=" + back.length + " txt=" + txt)
                        var x = GzipWriter("%s")
                        x.addText("%s")
                        x.close()
                        var y = GzipWriter("%s")
                        y.addText("%s")
                        y.close()
                        println("ok")
                    }
                    """.formatted(gz.toAbsolutePath(), ACCENTED, gz.toAbsolutePath(), gz.toAbsolutePath(),
                                  a.toAbsolutePath(), ACCENTED, b.toAbsolutePath(), ACCENTED));
            String run = "script".equals(face) ? runOnScript(root) : runOnJvm(root);
            assertEquals("n=5 txt=" + ACCENTED + "\nok", run, face);
            assertArrayEquals(Files.readAllBytes(a), Files.readAllBytes(b),
                    face + ": MTIME zero — o MESMO input tem de dar bytes IDENTICOS");
        }
    }

    // --- execução ------------------------------------------------------------

    private String runOnJvm(Path root) throws Exception {
        Path outDir = root.resolve("out");
        return withLibrary(root, () -> {
            CompilationResult r = driver.compileSources(List.of(root.resolve("Main.kf")), outDir, Target.JVM, root);
            assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            assertEquals(0, p.waitFor(), "JVM run: " + o);
            return o;
        });
    }

    private String runOnScript(Path root) throws Exception {
        KofInterpreter.Result r = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, r.exitCode(), "Script exit, stderr=" + r.stderr());
        return r.stdout().strip();
    }

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

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @Override
    public String libraryName() {
        return "archive";
    }

    @Override
    public List<String> libraryNames() {
        return List.of("archive", "image");
    }

    @Override
    public List<String> libraryMarkers() {
        return List.of("Gzip.kf");
    }
}
