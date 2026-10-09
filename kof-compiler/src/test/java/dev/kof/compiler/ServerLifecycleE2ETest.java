package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §5 — integration harness: the HTTP-server lifecycle helper
 * {@code withServer(app, port, body)}, written in Kof (no new syntax/primitive,
 * D-KOF-FIRST item 12) and authorized by D-MAINT-BATCH-0610B/B ("temp dir /
 * server / db lifecycle with try/finally cleanup; Kof library, injected flat on
 * import kof.test").
 *
 * <p>The helper lives in a SEPARATE virtual host {@code kof.test.web} (not in
 * {@code kof.test}) — the {@code CompilerTestDb} precedent: it depends on
 * {@code kof.web}/{@code kof.http} and the {@code spawn} primitive, and
 * {@code kof.test} is flat-injected whole, so a web helper living there would
 * make every unit test carry the web tree.
 *
 * <p>{@code app.listen(port)} blocks, so a same-thread {@code withServer { }}
 * could never run its body. The helper spawns {@code app.listen} on a task,
 * waits until the port accepts (a bounded {@code http.get} probe), runs the
 * body and CLOSES the app in a {@code finally} — both the success and the throw
 * path.
 *
 * <p><b>Observable of the close.</b> Kof has no {@code isClosed}, so the
 * cleanup is proven indirectly: after the helper returns, a fresh
 * {@code http.get} to the port must be REFUSED (the listener is gone). The same
 * program proves the throw path (the port is refused after the body throws,
 * i.e. the finally ran). A leaked listener would keep answering.
 */
class ServerLifecycleE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static String program(int okPort, int throwPort) {
        return """
                import kof.test.web

                main() {
                    var app = web.app()
                    app.get("/ping") { return "pong" }
                    var seen = ""
                    withServer(app, %d, (url: String) -> {
                        seen = http.get(url + "/ping")
                    })
                    println("body=" + seen)
                    println("closed=" + refused(%d))

                    var app2 = web.app()
                    app2.get("/x") { return "y" }
                    var bodyRan = false
                    var threw = false
                    try {
                        withServer(app2, %d, (url: String) -> {
                            bodyRan = true
                            throw "boom"
                        })
                    } catch (String e) {
                        threw = true
                    }
                    println("bodyRan=" + bodyRan)
                    println("threw=" + threw)
                    println("closedAfterThrow=" + refused(%d))
                }

                Bool refused(Int port) {
                    try {
                        http.get("http://127.0.0.1:" + port + "/")
                        return false
                    } catch (String e) {
                        return true
                    }
                }
                """.formatted(okPort, okPort, throwPort, throwPort);
    }

    // The body sees its own response; the port is refused after the helper on
    // both the success and the throw path (the finally ran).
    private static final String GOLDEN = """
            body=pong
            closed=true
            bodyRan=true
            threw=true
            closedAfterThrow=true""";

    private record Run(int exitCode, String output) {}

    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private Run runJvm(Path source, Path outDir) throws IOException, InterruptedException {
        CompilationResult r = driver.compileForTests(source, outDir, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        return new Run(p.waitFor(), out);
    }

    @Test
    void serverLifecycleOnJvm(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("S.kf");
        Files.writeString(src, program(freePort(), freePort()));
        Run r = runJvm(src, tempDir.resolve("out"));
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertEquals(GOLDEN, r.output(), "JVM oracle for the server lifecycle");
    }

    // The helper is written in Kof and closes the app in a finally, but
    // `app.close()` (kof_web_close) is a JVM-only runtime symbol today; on the
    // cross targets the existing WEB001 gap fires HONESTLY at compile time (R6
    // — never a silent fallback, never a leak). This pin makes the day the gap
    // closes a conscious edit, not a silent flip.
    @Test
    void crossTargetsReportTheHonestWebCloseGap(@TempDir Path tempDir) throws IOException {
        for (Target t : java.util.List.of(Target.NATIVE, Target.JS)) {
            Path src = tempDir.resolve("S-" + t + ".kf");
            Files.writeString(src, program(freePort(), freePort()));
            CompilationResult r = driver.compile(src, tempDir.resolve("out-" + t), t);
            assertTrue(!r.success(), t + ": app.close is not lowered on this target yet");
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("WEB001"),
                    t + ": the gap must be the honest WEB001: " + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void withServerHostIsOptIn(@TempDir Path tempDir) throws IOException {
        // Sem `import kof.test.web` o helper NAO existe: `import kof.test` sozinho
        // nao carrega a arvore web (o host isolado e o ponto da fatia).
        Path src = tempDir.resolve("NoImport.kf");
        Files.writeString(src, """
                import kof.test

                main() {
                    withServer(web.app(), 1, (url: String) -> { })
                }
                """);
        CompilationResult r = driver.compile(src, tempDir.resolve("out"), Target.JVM);
        assertTrue(!r.success(), "withServer must be undefined without the opt-in import");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("withServer"),
                "the gap must name withServer: " + r.diagnostics().getDiagnostics());
    }
}
