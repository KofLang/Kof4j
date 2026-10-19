package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #852 — a ESCRITA de TAR (`TarWriter`, ustar). O oráculo é o **`tar` do host**:
 * o arquivo é escrito pelo Kof e quem o lê é a implementação de referência. Um
 * escritor que só é lido pelo próprio leitor prova pouco — a referência aceitar
 * os BYTES é o que prova o formato.
 *
 * <p>Determinismo: o `mtime` é zero de propósito, então escrever o mesmo input
 * duas vezes dá arquivos **byte-idênticos** (o que os goldens do repositório
 * exigem de qualquer artefato).
 */
class TarWriterE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String ALFA = "conteudo do alfa\n";      // 17 bytes
    private static final String BETA = "xyz";                     // 3
    private static final String ACCENTED = "café";                // 5 bytes em UTF-8
    private static final String ACCENTED_NAME = "acentuado-é.txt";

    private static String writerProbe(Path tar) {
        return """
                import archive.Tar

                main() {
                    var w = TarWriter("%s")
                    w.addText("alfa.txt", "conteudo do alfa\\n")
                    w.addText("dir/beta.txt", "xyz")
                    w.addText("%s", "café")
                    println("bytes=" + w.close())
                }
                """.formatted(tar.toAbsolutePath(), ACCENTED_NAME);
    }

    /** O `tar` do host lista e LÊ o que o Kof escreveu, com os bytes certos. */
    @Test
    void hostTarReadsWhatKofWrote(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("jvm");
        Files.createDirectories(root);
        Path tar = root.resolve("novo.tar");
        Files.writeString(root.resolve("Main.kf"), writerProbe(tar));
        String out = runOnJvm(root);
        assertEquals("bytes=4096", out, "3 entradas (512+512 cada) + os dois blocos de fim");

        Assumptions.assumeTrue(hasHostTar(), "host tar required");
        assertEquals("alfa.txt\ndir/beta.txt\n" + ACCENTED_NAME, hostTar(tar, "-tf").strip(),
                "a listagem da REFERÊNCIA tem de bater");
        assertEquals(ALFA, hostTar(tar, "-xOf", "alfa.txt"), "conteúdo pelo tar do host");
        assertEquals(BETA, hostTar(tar, "-xOf", "dir/beta.txt"), "conteúdo aninhado");
        assertEquals(ACCENTED, hostTar(tar, "-xOf", ACCENTED_NAME), "nome e conteúdo acentuados (UTF-8)");

        Path extracted = dir.resolve("extraido");
        Files.createDirectories(extracted);
        assertEquals(0, new ProcessBuilder("tar", "-xf", tar.toString(), "-C", extracted.toString())
                .inheritIO().start().waitFor(), "extração pela referência");
        assertEquals(ALFA, Files.readString(extracted.resolve("alfa.txt")));
        assertEquals(BETA, Files.readString(extracted.resolve("dir/beta.txt")));
        assertEquals(ACCENTED, Files.readString(extracted.resolve(ACCENTED_NAME)));
    }

    /** O MESMO caminho no Script, e o arquivo conferido pela referência de novo. */
    @Test
    void hostTarReadsWhatTheScriptWrote(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("script");
        Files.createDirectories(root);
        Path tar = root.resolve("novo.tar");
        Files.writeString(root.resolve("Main.kf"), writerProbe(tar));
        assertEquals("bytes=4096", runOnScript(root), "a MESMA face no Script");

        Assumptions.assumeTrue(hasHostTar(), "host tar required");
        assertEquals(ALFA, hostTar(tar, "-xOf", "alfa.txt"), "Script → tar do host");
        assertEquals(ACCENTED, hostTar(tar, "-xOf", ACCENTED_NAME), "Script → tar do host (acentuado)");
    }

    /** A FAMÍLIA concorda consigo mesma: o leitor Kof lê o que o escritor Kof fez. */
    @Test
    void theReaderAgreesWithTheWriter(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("ida-e-volta");
        Files.createDirectories(root);
        Path tar = root.resolve("novo.tar");
        Files.writeString(root.resolve("Main.kf"), """
                import archive.Tar

                main() {
                    var w = TarWriter("%s")
                    w.addText("alfa.txt", "conteudo do alfa\\n")
                    w.addText("dir/beta.txt", "xyz")
                    w.addText("%s", "café")
                    w.close()
                    var es = tarEntries("%s")
                    for (var e in es) {
                        println(e.name + "|" + e.size + "|" + tarReadEntry("%s", e, 4096).length)
                    }
                    println("conteudo=" + tarReadEntry("%s", es.get(2), 4096).length)
                }
                """.formatted(tar.toAbsolutePath(), ACCENTED_NAME, tar.toAbsolutePath(),
                              tar.toAbsolutePath(), tar.toAbsolutePath()));
        String expected = "alfa.txt|17|17\ndir/beta.txt|3|3\n" + ACCENTED_NAME + "|5|5\nconteudo=5";
        assertEquals(expected, runOnJvm(root), "ida e volta pela família (JVM)");
        assertEquals(expected, runOnScript(root), "ida e volta pela família (Script)");
    }

    /** Determinismo e recusas: mesmo input → bytes idênticos; nome impossível → erro. */
    @Test
    void writesAreByteIdenticalAndImpossibleNamesAreRefused(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("determinismo");
        Files.createDirectories(root);
        Path a = root.resolve("a.tar");
        Path b = root.resolve("b.tar");
        Files.writeString(root.resolve("Main.kf"), """
                import archive.Tar

                main() {
                    var w1 = TarWriter("%s")
                    w1.addText("alfa.txt", "conteudo do alfa\\n")
                    w1.close()
                    var w2 = TarWriter("%s")
                    w2.addText("alfa.txt", "conteudo do alfa\\n")
                    w2.close()
                    println("iguais=" + (File("%s").readBytes().length == File("%s").readBytes().length))
                }
                """.formatted(a.toAbsolutePath(), b.toAbsolutePath(), a.toAbsolutePath(), b.toAbsolutePath()));
        assertEquals("iguais=true", runOnJvm(root), "os dois arquivos têm o mesmo tamanho");
        assertArrayEquals(Files.readAllBytes(a), Files.readAllBytes(b),
                "mtime zero: o MESMO input tem de dar bytes IDÊNTICOS");

        Files.writeString(root.resolve("Main.kf"), """
                import archive.Tar

                main() {
                    try {
                        var w = TarWriter("%s")
                        w.addText("%s", "x")
                        w.close()
                        println("ACEITOU")
                    } catch (String m) {
                        println("recusado: " + m)
                    }
                }
                """.formatted(root.resolve("longo.tar").toAbsolutePath(), "n".repeat(101) + ".txt"));
        assertEquals("recusado: archive: tar entry name over 100 bytes (GNU long names are not supported yet): "
                + "n".repeat(101) + ".txt", runOnJvm(root), "nome acima de 100 bytes é RECUSADO, não truncado");
    }

    // --- execução ------------------------------------------------------------

    private static boolean hasHostTar() {
        return Files.isExecutable(Path.of("/usr/bin/tar")) || Files.isExecutable(Path.of("/bin/tar"));
    }

    /** `tar <opt> <arquivo> <membros…>` — a ordem IMPORTA: o arquivo vai logo
     *  depois da opção que carrega o `f` (com ele no fim, o tar lê o MEMBRO como
     *  se fosse o arquivo). */
    private static String hostTar(Path tar, String opt, String... members) throws Exception {
        List<String> cmd = new java.util.ArrayList<>(List.of("tar", opt, tar.toString()));
        cmd.addAll(List.of(members));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "tar " + opt + " " + tar + ": " + o);
        return o;
    }

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
        return List.of("Tar.kf");
    }
}
