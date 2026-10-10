package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §575 (02/10, KofShare /shutdown experiment): a closure that CAPTURES the
 * phantom `kof.web.App` handle materialized the phantom as a lambda field
 * descriptor (`Lkof/web/App;`) — the JVM compiled clean and died at Main LOAD
 * with `NoClassDefFoundError: kof/web/App`, while Script ran it (the
 * interpreter has no descriptors). Fix: the JVM type mapper erases every known
 * phantom handle type (`KofWeb.isPhantomHandleType`) to `java.lang.String` in
 * both descriptor and owner position, so a captured-app lambda field reads and
 * writes the real String handle — the Kof type stays `kof.web.App` so the
 * instance dispatch (`app.port()`) still routes to `kof_web_*`.
 */
class WebRouteCaptureE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Test
    void capturedAppHandleInRouteLoadsAndRunsOnJvm(@TempDir Path dir) throws Exception {
        int port = freePort();
        Path src = dir.resolve("Cap.kf");
        Files.writeString(src, """
                main() {
                    var app = web.app()
                    app.get("/info") {
                        return "port=" + app.port()
                    }
                    spawn {
                        app.listen(PORT)
                    }
                    var got = ""
                    var i = 0
                    while (i < 100) {
                        try {
                            got = http.get("http://127.0.0.1:PORT/info")
                            if (got.startsWith("port=")) {
                                i = 100
                            }
                        } catch (String e) {
                            i = i + 1
                        }
                    }
                    println("captured=" + got)
                    app.close()
                    println("CAPTURE-OK")
                }
                """.replace("PORT", String.valueOf(port)));

        Path out = dir.resolve("out");
        CompilationResult r = driver.compileSources(List.of(src), out, Target.JVM, dir);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());

        String output = runJvm(out);
        assertTrue(output.contains("captured=port=" + port),
                "captured-app handler did not answer (§575 load/dispatch): " + output);
        assertTrue(output.contains("CAPTURE-OK"), output);
    }

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
