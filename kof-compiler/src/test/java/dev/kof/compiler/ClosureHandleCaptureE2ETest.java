package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * §575: um closure que captura o handle fantasma do `web.app()` materializava
 * `Lkof/web/App;` como campo do lambda (classe sem host — em runtime o handle
 * é um String id) e morria `NoClassDefFoundError` no LOAD. Fix:
 * `CompilerTypes.captureErasure` aplicada na coleta de captures
 * (`CompilerCaptures`) e no box mutável (`BoxClassFactory`) — apagamento para
 * `java.lang.String` (face medida) mantendo o contrato inalterado.
 */
class ClosureHandleCaptureE2ETest {

    private final CompilerDriver driver = new CompilerDriver();
    private static final String JAVA_BIN =
            System.getProperty("java.home") + "/bin/java";

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Test
    @DisplayName("§575 JVM: route lambda capturing the web.app() handle loads and answers")
    void capturedWebAppHandleLoadsOnJvm(@TempDir Path tempDir) throws Exception {
        int port = freePort();
        Path source = tempDir.resolve("CaptureApp.kf");
        Files.writeString(source, """
                main() {
                    var app = web.app()
                    app.get("/ping") {
                        return "pong"
                    }
                    app.get("/shutdown") {
                        app.close()
                        return "BYE"
                    }
                    spawn {
                        app.listen(PORT)
                    }
                    var got = ""
                    var i = 0
                    while (i < 200) {
                        try {
                            got = http.get("http://127.0.0.1:PORT/ping")
                            if (got == "pong") {
                                i = 200
                            }
                        } catch (String e) {
                            i = i + 1
                        }
                    }
                    var bye = http.get("http://127.0.0.1:PORT/shutdown")
                    println("bye=" + (bye == "BYE"))
                    println("CAPTURE-OK")
                }
                """.replace("PORT", String.valueOf(port)));
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(JAVA_BIN, "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "server did not exit: " + out);
        assertEquals(0, p.exitValue(), "exit wrong (CNFE §575?): " + out);
        assertTrue(out.contains("bye=true"), "shutdown route broken: " + out);
        assertTrue(out.contains("CAPTURE-OK"), out);
    }
}
