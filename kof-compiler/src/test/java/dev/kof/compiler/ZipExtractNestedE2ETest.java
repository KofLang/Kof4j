package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §766/#982: `Zip.extractTo` com diretório aninhado em qualquer profundidade e
 * sem engolir falha de escrita. As faces nativas ficam em `ZipNativeFaceE2ETest`.
 */
class ZipExtractNestedE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void nestedDirectoriesStayDirectoriesAndDeepestFileSurvives(@TempDir Path dir) throws Exception {
        Path zip2 = dir.resolve("nested2.zip");
        writeZip(zip2, "hello", "d/", "d/e/", "d/e/f.txt");
        Path zip3 = dir.resolve("nested3.zip");
        writeZip(zip3, "deepcontent", "a/", "a/b/", "a/b/c/", "a/b/c/z.txt");
        Path probe = dir.resolve("probe");
        Files.createDirectories(probe);

        String program = """
                import archive.Zip

                void extract(String path, String dest) {
                    var z = Zip(path)
                    println("WRITTEN=" + z.extractTo(dest, 4096))
                }

                main() {
                    extract("%s", "%s")
                    extract("%s", "%s")
                }
                """;
        Path jvm2 = dir.resolve("jvm2");
        Path jvm3 = dir.resolve("jvm3");
        Files.writeString(probe.resolve("Main.kf"), program.formatted(zip2.toAbsolutePath(), jvm2.toAbsolutePath(),
                zip3.toAbsolutePath(), jvm3.toAbsolutePath()));
        assertEquals("WRITTEN=1\nWRITTEN=1", runOnJvm(probe), "só o arquivo real conta (JVM)");
        assertExtracted(jvm2, jvm3);

        Path script2 = dir.resolve("script2");
        Path script3 = dir.resolve("script3");
        Files.writeString(probe.resolve("Main.kf"), program.formatted(zip2.toAbsolutePath(), script2.toAbsolutePath(),
                zip3.toAbsolutePath(), script3.toAbsolutePath()));
        assertEquals("WRITTEN=1\nWRITTEN=1", runOnScript(probe), "só o arquivo real conta (Script)");
        assertExtracted(script2, script3);
    }

    @Test
    void aWriteFailureThrowsInsteadOfCountingAsWritten(@TempDir Path dir) throws Exception {
        Path zip = dir.resolve("collide.zip");
        writeZip(zip, "hello", "d/", "d/f.txt");
        Path dest = dir.resolve("saida");
        Files.createDirectories(dest);
        Files.writeString(dest.resolve("d"), "ja sou um arquivo, nao um diretorio");
        Files.writeString(dir.resolve("Main.kf"), """
                import archive.Zip

                main() {
                    try {
                        var z = Zip("%s")
                        println("WRITTEN=" + z.extractTo("%s", 4096))
                    } catch (String m) {
                        println("recusado: " + m)
                    }
                }
                """.formatted(zip.toAbsolutePath(), dest.toAbsolutePath()));

        for (String out : List.of(runOnJvm(dir), runOnScript(dir))) {
            assertTrue(out.startsWith("recusado: archive: could not create directory"),
                    "a colisão tem que estourar, não contar como sucesso: " + out);
        }
    }

    /** O último nome é o arquivo com `content`; os anteriores são diretórios. */
    private static void writeZip(Path zip, String content, String... names) throws Exception {
        try (OutputStream raw = Files.newOutputStream(zip); ZipOutputStream out = new ZipOutputStream(raw)) {
            for (int i = 0; i < names.length; i++) {
                out.putNextEntry(new ZipEntry(names[i]));
                if (i == names.length - 1) {
                    out.write(content.getBytes(StandardCharsets.UTF_8));
                }
                out.closeEntry();
            }
        }
    }

    private static void assertExtracted(Path two, Path three) throws Exception {
        assertTrue(Files.isDirectory(two.resolve("d/e")), "d/e tem que ficar diretório");
        assertEquals("hello", Files.readString(two.resolve("d/e/f.txt")), "f.txt não pode se perder");
        assertTrue(Files.isDirectory(three.resolve("a/b/c")), "a/b/c tem que ficar diretório");
        assertEquals("deepcontent", Files.readString(three.resolve("a/b/c/z.txt")), "z.txt não pode se perder");
    }

    private String runOnJvm(Path root) throws Exception {
        Path outDir = root.resolve("out");
        return withLibrary(root, () -> {
            CompilationResult r = driver.compileSources(List.of(root.resolve("Main.kf")), outDir, Target.JVM, root);
            assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
            Process p = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main")
                    .redirectErrorStream(true).start();
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
