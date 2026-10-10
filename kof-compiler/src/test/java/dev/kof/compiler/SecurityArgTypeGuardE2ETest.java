package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * §563 (R6): os digests do `kof.security` têm ramo declarado String/Int,
 * mas a guarda do typer era só de aridade — {@code crypto.sha256(new
 * Byte[4])} compilava e DEGRADAVA por alvo: o Script digestava a
 * IDENTIDADE do objeto (medido: dois arrays de mesmo conteúdo → digests
 * diferentes; o mesmo array → estável — integridade silenciosamente
 * errada) e a JVM morria {@code VerifyError: Bad type on operand stack}
 * no load. A forma errada agora é recusada no compile com código NOMEADO
 * ({@code SECN011}) em todo alvo — nunca fallback silencioso. As formas
 * corretas seguem verdes e rodando (a face String-AAD do KofShare é o
 * contrato).
 */
class SecurityArgTypeGuardE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compileJvm(Path dir, String name, String src) throws Exception {
        Path s = dir.resolve(name + ".kf");
        Files.writeString(s, src);
        return driver.compile(s, dir.resolve("out-" + name), Target.JVM);
    }

    private String runJvm(Path dir, String name) throws Exception {
        Path classes = dir.resolve("out-" + name);
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED",
                "-cp", classes.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(30, TimeUnit.SECONDS), name + " must finish");
        assertEquals(0, p.exitValue(), name + " exit: " + out);
        return out;
    }

    @Test
    @DisplayName("§563: crypto.sha256 with a Byte[] actual refuses with SECN011 (was: identity digest / VerifyError)")
    void sha256ByteArrayRefused(@TempDir Path dir) throws Exception {
        CompilationResult r = compileJvm(dir, "shaarr", """
                main() {
                    var b = new Byte[4]
                    b[0] = 1
                    println(crypto.sha256(b))
                }
                """);
        assertFalse(r.success(), "must refuse: " + r.diagnostics().getDiagnostics());
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("SECN011"),
                "must name SECN011: " + r.diagnostics().getDiagnostics());
    }

    @Test
    @DisplayName("§563: crypto.hmacSha256(key, Byte[]) refuses with SECN011")
    void hmacByteArrayRefused(@TempDir Path dir) throws Exception {
        CompilationResult r = compileJvm(dir, "hmacarr", """
                main() {
                    var b = new Byte[2]
                    println(crypto.hmacSha256("k0", b))
                }
                """);
        assertFalse(r.success(), "must refuse: " + r.diagnostics().getDiagnostics());
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("SECN011"),
                "must name SECN011: " + r.diagnostics().getDiagnostics());
    }

    @Test
    @DisplayName("§563: crypto.randomHex with a String actual refuses with SECN011 (declared Int face)")
    void randomHexStringRefused(@TempDir Path dir) throws Exception {
        CompilationResult r = compileJvm(dir, "rndstr", """
                main() {
                    println(crypto.randomHex("nope"))
                }
                """);
        assertFalse(r.success(), "must refuse: " + r.diagnostics().getDiagnostics());
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("SECN011"),
                "must name SECN011: " + r.diagnostics().getDiagnostics());
    }

    @Test
    @DisplayName("§563: the correct String/Int forms still compile AND run (64/64/16)")
    void correctFormsStillGreen(@TempDir Path dir) throws Exception {
        CompilationResult r = compileJvm(dir, "ok", """
                main() {
                    var h = crypto.sha256("hello")
                    var m = crypto.hmacSha256("k", "m")
                    var n = crypto.randomHex(8)
                    println(h.length + "/" + m.length + "/" + n.length)
                }
                """);
        assertTrue(r.success(), "green control must compile: " + r.diagnostics().getDiagnostics());
        String out = runJvm(dir, "ok");
        assertTrue(out.contains("64"), "digest lengths: " + out);
    }

    @Test
    @DisplayName("§563: the refusal holds on the Script leg too (same named diagnostic)")
    void scriptLegRefused(@TempDir Path dir) throws Exception {
        Path s = dir.resolve("sc.kf");
        Files.writeString(s, """
                main() {
                    var b = new Byte[4]
                    b[0] = 1
                    println(crypto.sha256(b))
                }
                """);
        try {
            KofInterpreter.Result r = driver.interpret(java.util.List.of(s), dir, new String[0]);
            assertTrue((r.stderr() + r.stdout()).contains("SECN011"),
                    "script leg must name SECN011: " + r.stderr());
        } catch (dev.kof.compiler.KofInterpretException e) {
            assertTrue(e.getMessage().contains("SECN011"),
                    "script leg must name SECN011: " + e.getMessage());
        }
    }
}
