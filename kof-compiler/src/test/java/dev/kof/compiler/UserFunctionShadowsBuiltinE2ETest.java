package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #931/#932 — a lei do sombreamento no nível de FUNÇÃO (o §243 que já valia
 * para classes): um `extern` ou uma função top-level declarada pelo programa
 * com nome de builtin NÃO é roubada pelo branch de nome fixo do builtin.
 *
 * <p>Pré-fix, nos três alvos: `extern poll(...)` era interceptado pelo typer
 * aridade-cego do `poll` (type lost → Object, VerifyError de double-unbox no
 * JVM; Integer→boxed no Native) e `writeFile(String, String)` do usuário era
 * emitido como `kof_write_file` (a chamada do usuário desaparecia em
 * silêncio). Oráculo é o JVM.
 */
class UserFunctionShadowsBuiltinE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    /** #932: a função do usuário vence o builtin de mesmo nome. */
    private static final String USER_FN = """
            writeFile(String path, String content): Int {
                println("user writeFile called")
                return 7
            }

            main() {
                writeFile("/tmp/kof-shadow-test.txt", "hello")
                println("done")
            }
            """;

    /** #931: extern com nome de builtin nativo do KOF resolve pela FFI.
     *  poll(NULL, 0, 0) → 0, determinístico. */
    private static final String EXTERN_FN = """
            extern "libc.so.6" poll(Long fds, Long nfds, Int timeout): Int

            main() {
                Int r = poll(0L, 0L, 0)
                println("r=" + r)
            }
            """;

    @Test
    void userWriteFileWinsOverTheBuiltinJvm(@TempDir Path tempDir) throws IOException {
        assertShadowJvmOutput(tempDir, "x932-jvm", USER_FN, "user writeFile called\ndone");
    }

    @Test
    void externPollIsNotStolenByThePollBuiltinJvm(@TempDir Path tempDir) throws IOException {
        assertShadowJvmOutput(tempDir, "x931-jvm", EXTERN_FN, "r=0");
    }

    @Test
    void externPollIsNotStolenByThePollBuiltinNativeX86(@TempDir Path tempDir)
            throws IOException, InterruptedException {
        Path out = compileTo(tempDir, "x931-x86", EXTERN_FN, Target.NATIVE);
        Process p = new ProcessBuilder(out.resolve("Default").resolve("Main").toString())
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "x931-x86 não terminou em 120s");
        assertEquals(0, p.exitValue(), "x931 exit, output: " + output);
        assertEquals("r=0", output, "#931 no x86_64: a FFI vence o builtin de mesmo nome");
    }

    private Path compileTo(Path tempDir, String name, String source, Target target) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path out = tempDir.resolve(name + "-out");
        CompilationResult r = driver.compile(src, out, target);
        assertTrue(r.success(), name + " deve compilar p/ " + target + ": "
                + r.diagnostics().getDiagnostics());
        return out;
    }

    private void assertShadowJvmOutput(Path tempDir, String name, String source, String expected)
            throws IOException {
        Path out = compileTo(tempDir, name, source, Target.JVM);
        Process p = new ProcessBuilder("java", "--enable-native-access=ALL-UNNAMED",
                "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String output;
        try {
            output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), name + " exit, output: " + output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
        assertEquals(expected, output, name + " deve imprimir a saída do usuário");
    }
}
