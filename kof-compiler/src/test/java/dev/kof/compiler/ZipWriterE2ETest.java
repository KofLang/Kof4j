package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #852 — a ESCRITA de ZIP (`ZipWriter`, método 0 = STORED).
 *
 * <p>O oráculo é a implementação de REFERÊNCIA do JDK: `java.util.zip.ZipFile`
 * abre, verifica a CRC e LÊ o que o Kof escreveu — um escritor que só é lido pelo
 * próprio leitor prova pouco. Depois, o MESMO arquivo é lido de volta pela
 * livraria (leitor × escritor) e extraído com o `extractTo` confinado.
 *
 * <p>Determinismo: data/hora zero em todo cabeçalho, então o mesmo input sai
 * byte-idêntico — a mesma regra que o `TarWriter` segue e que os goldens exigem.
 */
class ZipWriterE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String ALFA = "conteudo do alfa\n";
    private static final String BETA = "xyz";
    private static final String ACCENTED = "café";
    private static final String ACCENTED_NAME = "acentuado-é.txt";

    private static String writerProbe(Path zip) {
        return """
                import archive.Zip

                main() {
                    var w = ZipWriter("%s")
                    w.addText("alfa.txt", "conteudo do alfa\\n")
                    w.addText("dir/beta.txt", "xyz")
                    w.addText("%s", "café")
                    println("bytes=" + w.close())
                }
                """.formatted(zip.toAbsolutePath(), ACCENTED_NAME);
    }

    /** **A REFERÊNCIA abre, verifica e lê o que o Kof escreveu.** */
    @Test
    void jdkZipFileReadsWhatKofWrote(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("jvm");
        Files.createDirectories(root);
        Path zip = root.resolve("novo.zip");
        Files.writeString(root.resolve("Main.kf"), writerProbe(zip));
        String out = runOnJvm(root);
        assertTrue(out.startsWith("bytes="), "o escritor devolve o tamanho: " + out);

        try (ZipFile ref = new ZipFile(zip.toFile())) {
            assertEquals(3, ref.size(), "a referência conta as três entradas");
            assertEquals(ALFA, readEntry(ref, "alfa.txt"), "conteúdo pelo JDK");
            assertEquals(BETA, readEntry(ref, "dir/beta.txt"), "conteúdo aninhado pelo JDK");
            assertEquals(ACCENTED, readEntry(ref, ACCENTED_NAME), "nome e conteúdo acentuados (UTF-8)");
            Enumeration<? extends ZipEntry> entries = ref.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                assertEquals(ZipEntry.STORED, e.getMethod(), "STORED (método 0): sem compressor");
            }
        }
    }

    private static String readEntry(ZipFile ref, String name) throws Exception {
        ZipEntry e = ref.getEntry(name);
        assertTrue(e != null, "a referência tem de achar '" + name + "'");
        return new String(ref.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
    }

    /** Leitor × escritor da MESMA livraria, e a extração confinada do que ele gerou.
     *  Cada face extrai no SEU destino: o `extractTo` recusa sobrescrever, então
     *  reusar a pasta faria o Script falhar por um motivo que não é o dele. */
    @Test
    void theReaderAndTheExtractionAgreeWithTheWriter(@TempDir Path dir) throws Exception {
        String expected = "validate=3\n"
                + "alfa.txt|17|0\n"
                + "dir/beta.txt|3|0\n"
                + ACCENTED_NAME + "|5|0\n"
                + "extraiu=3";
        for (String face : List.of("jvm", "script")) {
            Path root = dir.resolve(face);
            Files.createDirectories(root);
            Path zip = root.resolve("novo.zip");
            Files.writeString(root.resolve("Main.kf"), """
                    import archive.Zip

                    main() {
                        var w = ZipWriter("%s")
                        w.addText("alfa.txt", "conteudo do alfa\\n")
                        w.addText("dir/beta.txt", "xyz")
                        w.addText("%s", "café")
                        w.close()
                        var z = Zip("%s")
                        println("validate=" + z.validate())
                        for (var e in z.entries) {
                            println(e.name + "|" + e.uncompressedSize + "|" + e.method)
                        }
                        println("extraiu=" + z.extractTo("%s/saida", 4096))
                    }
                    """.formatted(zip.toAbsolutePath(), ACCENTED_NAME, zip.toAbsolutePath(),
                                  root.toAbsolutePath()));
            assertEquals(expected, "script".equals(face) ? runOnScript(root) : runOnJvm(root), face);
            assertEquals(ALFA, Files.readString(root.resolve("saida/alfa.txt")),
                    face + ": o que o escritor gerou, o extractTo entrega íntegro");
            assertEquals(ACCENTED, Files.readString(root.resolve("saida/" + ACCENTED_NAME)),
                    face + ": inclusive o nome acentuado — pelo caminho do confinamento");
        }
    }

    /** Determinismo e as recusas do escritor. */
    @Test
    void writesAreByteIdenticalAndBadNamesAreRefused(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("determinismo");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), """
                import archive.Zip

                main() {
                    var a = ZipWriter("%s")
                    a.addText("alfa.txt", "conteudo do alfa\\n")
                    a.close()
                    var b = ZipWriter("%s")
                    b.addText("alfa.txt", "conteudo do alfa\\n")
                    b.close()
                    println("ok")
                }
                """.formatted(dir.resolve("a.zip").toAbsolutePath(), dir.resolve("b.zip").toAbsolutePath()));
        assertEquals("ok", runOnJvm(root), "os dois escritores rodam");
        assertArrayEquals(Files.readAllBytes(dir.resolve("a.zip")), Files.readAllBytes(dir.resolve("b.zip")),
                "data/hora zero: o MESMO input tem de dar bytes IDÊNTICOS");

        Files.writeString(root.resolve("Main.kf"), """
                import archive.Zip

                main() {
                    duplicado("%s")
                    vazio("%s")
                }
                void duplicado(String p) {
                    try {
                        var w = ZipWriter(p)
                        w.addText("x.txt", "1")
                        w.addText("x.txt", "2")
                        w.close()
                        println("ACEITOU duplicado")
                    } catch (String m) {
                        println("recusado: " + m)
                    }
                }
                void vazio(String p) {
                    try {
                        var w = ZipWriter(p)
                        w.addText("", "1")
                        w.close()
                        println("ACEITOU vazio")
                    } catch (String m) {
                        println("recusado: " + m)
                    }
                }
                """.formatted(dir.resolve("dup.zip").toAbsolutePath(), dir.resolve("vazio.zip").toAbsolutePath()));
        String expected = "recusado: archive: entry 'x.txt' was already added — duplicate names are refused\n"
                + "recusado: archive: empty zip entry name";
        assertEquals(expected, runOnJvm(root), "duplicado e vazio recusados, com o motivo");
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
        return List.of("Zip.kf");
    }
}
