package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Universal platform Stage 5.10 slice — the OBSERVABILITY→AUDIT EXPORT
 * ({@code libs/audit/Observability.kf}): the {@code observability.metrics()}
 * Prometheus-format snapshot becomes audit-chain entries, so the metric
 * state joins the tamper-evident history. Faces: the export appends the
 * snapshot to a live {@code AuditTrail} (one entry per sample line, the
 * event carries {@code obs:<name>=<value>} verbatim), {@code verifyChain}
 * holds over the exported trail, and an EDITED entry breaks it.
 */
class AuditObservabilityE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "audit"; }

    @Override public List<String> libraryMarkers() {
        return List.of("Audit.kf", "Observability.kf");
    }

    private static final String PROBE = """
            import audit.Audit
            import audit.Observability

            main() {
                observability.increment("http.requests", 3)
                observability.gauge("queue.depth", 7)
                var trail = AuditTrail()
                var n = exportMetrics(trail, 1000L)
                println(n >= 2)
                println(trail.size() == n)
                println(verifyChain(trail.all()))
                var ev = trail.all().get(0).event
                println(ev.startsWith("obs:"))
                println(ev.contains("=3"))
                var all = trail.all()
                var tampered = listOf<AuditEntry>()
                var i = 0
                while (i < all.size()) {
                    var e = all.get(i)
                    if (i == 0) {
                        tampered.add(AuditEntry(e.seq, "obs:TAMPERED", e.atMillis, e.prevDigest, e.digest))
                    } else {
                        tampered.add(e)
                    }
                    i = i + 1
                }
                println(verifyChain(tampered) == false)
                println("done")
            }
            """;

    // the snapshot has at least the two samples this probe registered (the
    // observability state is per-JVM static — never an exact count); the
    // chain verifies over the exported entries; the first event carries the
    // obs: prefix and its verbatim value; an edited event with the ORIGINAL
    // digest breaks the chain.
    private static final String EXPECTED = "true\ntrue\ntrue\ntrue\ntrue\ntrue\ndone";

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

    @Test
    void auditObservabilityOnJvm() throws Exception {
        Path root = tmp.resolve("aud-obs-jvm");
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
        } finally {
            System.setOut(previousOut);
        }
        assertEquals(EXPECTED, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void auditObservabilityOnScript() throws Exception {
        Path root = tmp.resolve("aud-obs-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals(EXPECTED, result.stdout().strip());
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
