package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #853 (parte "de arquivo") — `sha256File(path)` como BIBLIOTECA Kof
 * (`libs/security/Sha.kf`), composta de `File.readBytes()` + `crypto.sha256Bytes`.
 *
 * <p>O golden é o digest CONHECIDO do conteúdo `abc`
 * (`ba7816bf…15ad`, o mesmo que o `sha256sum` do host devolve — oráculo
 * independente da implementação Kof), conferido na JVM E no Script.
 */
class Sha256FileLibraryE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** O digest de "abc" — confirmado com `printf 'abc' | sha256sum` no host. */
    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private static String probe(Path file) {
        return """
                import security.Sha

                main() {
                    println(sha256File("%s"))
                }
                """.formatted(file.toAbsolutePath());
    }

    @Test
    void fileHashMatchesTheKnownDigestOnJvm() throws Exception {
        Path root = tmp.resolve("jvm");
        Files.createDirectories(root);
        Path data = root.resolve("abc.bin");
        Files.writeString(data, "abc", StandardCharsets.UTF_8);
        Path main = root.resolve("Main.kf");
        Files.writeString(main, probe(data));

        String out = withLibrary(root, () -> {
            Path outDir = root.resolve("out");
            CompilationResult r = driver.compileSources(List.of(main), outDir, Target.JVM, root);
            assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            assertEquals(0, p.waitFor(), "JVM run: " + o);
            return o;
        });
        assertEquals(ABC_SHA256, out, "o digest do arquivo (oraculo: sha256sum do host)");
    }

    @Test
    void fileHashOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Path data = root.resolve("abc.bin");
        Files.writeString(data, "abc", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Main.kf"), probe(data));

        KofInterpreter.Result r = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, r.exitCode(), "Script exit, stderr=" + r.stderr());
        assertEquals(ABC_SHA256, r.stdout().trim(), "o MESMO digest no Script");
    }

    /** Instala a lib no diretório que o compilador procura (`kof.install.dir`)
     *  e roda a ação com o módulo apontando para lá — mesmo desenho dos outros
     *  testes de biblioteca. */
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
        return "security";
    }

    @Override
    public List<String> libraryMarkers() {
        return List.of("Sha.kf");
    }
}
