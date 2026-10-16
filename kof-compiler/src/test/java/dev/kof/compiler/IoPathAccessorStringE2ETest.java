package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue #847: the {@code kof.io} accessors {@code path()}/{@code name()} are
 * documented as returning a {@code String} and their RUNTIME value always was
 * one, but the typers returned the RECEIVER type — so {@code var p =
 * File("dados/x.txt").path()} made {@code p} a {@code File} and the first String
 * use failed ({@code SEM102: 'File' has no field 'length'}), while the same call
 * on a String receiver ({@code Path(...).fileName().path()}) escaped the
 * identity handling entirely and emitted a call to a non-existent
 * {@code java.lang.String.path()} ({@code NoSuchMethodError} at run time).
 *
 * <p>The fix mirrors what the FIELD form ({@code .path}/{@code .name} on a
 * String) already does — it is an identity typed as {@code String} — for the
 * METHOD form, and types the accessor as {@code String} on the Io receivers too.
 *
 * <p>Oracle is the JVM.
 */
class IoPathAccessorStringE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** Face 1 (SEM102 pre-fix) + face 2 (NoSuchMethodError pre-fix). */
    private static final String SRC = """
            main() {
                var p = File("dados/x.txt").path()
                println("len=" + p.length)
                println("lower=" + p.toLowerCase())
                println("resolve=" + Path("dados").resolve("y.txt").path())
                println("fileName=" + Path("dados/x.txt").fileName())
                println("fileNome=" + Path("dados/x.txt").fileName().path())
                println("identidade=" + (File("a/b.txt").path() == "a/b.txt"))
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "len=11",
            "lower=dados/x.txt",
            "resolve=dados/y.txt",
            "fileName=x.txt",
            "fileNome=x.txt",
            "identidade=true");

    @Test
    void jvmTypesTheIoAccessorsAsString() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    private String runJvm(String name) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> "compila (#847): " + r.diagnostics().getDiagnostics());
        // -Xdiag: o launcher/validate pode reportar a mensagem enganosa de
        // "componentes de runtime do JavaFX" quando o real é um VerifyError —
        // o diagnótico completo é o que importa aqui (anti-patterns, bug 1).
        Process p = new ProcessBuilder("java", "-Xdiag", "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8", "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " jvm");
        assertEquals(0, p.exitValue(), () -> name + ": " + o);
        return o;
    }
}