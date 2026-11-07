package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SentinelHttpE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;
    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "sentinel"; }
    @Override public List<String> libraryMarkers() { return List.of("Sentinel.kf", "Http.kf"); }

    private static final String PROBE = """
            import sentinel.Sentinel
            import sentinel.Http

            main() {
                var sent = Sentinel(60000, 100)
                var app = web.app()
                app.use(sentinelHttp(sent))
                app.get("/ping") {
                    return "pong"
                }
                app.get("/other") {
                    return "ok"
                }
                var server = spawn {
                    app.listen(0)
                }
                while (app.port() == -1) {
                    time.sleep(20)
                }
                var port = app.port()
                var b1 = http.get("http://127.0.0.1:" + port + "/ping")
                println(b1.contains("pong"))
                var b2 = http.get("http://127.0.0.1:" + port + "/other")
                println(b2.contains("ok"))
                var now = time.now()
                println(sent.count("/ping", "request", now) == 1)
                println(sent.count("/other", "request", now) == 1)
                app.close()
                println(sent.count("/ping", "request", now + 1000L) == 1)
                println("done")
            }
            """;

    private static final String EXPECTED = "true\ntrue\ntrue\ntrue\ntrue\ndone";

    private <T> T withLibrary(Path root, CheckedSupplier<T> action) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
        try { return action.get(); }
        finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
    }

    @Test
    void sentinelHttpOnJvm() throws Exception {
        Path root = tmp.resolve("sent-http-jvm");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        Path out = root.resolve("out");
        CompilationResult r = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JVM));
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new java.net.URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally { System.setOut(previousOut); }
        assertEquals(EXPECTED, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void sentinelHttpOnScript() throws Exception {
        Path root = tmp.resolve("sent-http-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals(EXPECTED, result.stdout().strip());
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> { T get() throws Exception; }
}
