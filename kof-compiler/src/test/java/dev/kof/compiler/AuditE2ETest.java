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
 * Universal platform Stage 5.10 slice — the TAMPER-EVIDENT AUDIT TRAIL
 * ({@code libs/audit/Audit.kf}): an append-only chain where every entry's
 * digest is {@code crypto.sha256(prevDigest + "|" + event + "|" + at)} over
 * the audited stdlib primitive (NOT homemade crypto — the library is the
 * CHAIN), plus the pure {@code verifyChain(entries)} pass that recomputes
 * every digest and checks every link. The tamper faces prove verification
 * CATCHES a broken link and a swapped digest.
 */
class AuditE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "audit"; }

    @Override public List<String> libraryMarkers() { return List.of("Audit.kf"); }

    private static final String PROBE = """
            import audit.Audit

            main() {
                var t = AuditTrail()
                t.append("login ok", 1000L)
                t.append("file read", 1100L)
                t.append("config write", 1200L)
                println(t.size())
                println(verifyChain(t.all()))
                var good = t.all()
                var broken = listOf<AuditEntry>()
                broken.add(AuditEntry(1, "login ok", 1000L, "genesis", good.get(0).digest))
                broken.add(AuditEntry(2, "file read", 1100L, "genesis", good.get(1).digest))
                broken.add(AuditEntry(3, "config write", 1200L, good.get(1).digest, good.get(2).digest))
                println(verifyChain(broken))
                var swapped = listOf<AuditEntry>()
                swapped.add(good.get(0))
                swapped.add(AuditEntry(2, "file read", 1100L, good.get(0).digest, "0000000000000000000000000000000000000000000000000000000000000000"))
                swapped.add(good.get(2))
                println(verifyChain(swapped))
                var last = t.all().get(2)
                println(last.seq)
                println(last.event)
            }
            """;

    // 3 appended entries; the intact chain verifies TRUE; a BROKEN LINK
    // (entry 2's prev points at genesis) verifies FALSE; a SWAPPED DIGEST
    // (entry 2's digest zeroed) verifies FALSE; the last entry carries its
    // sequence and event text.
    private static final String EXPECTED = "3\ntrue\nfalse\nfalse\n3\nconfig write";

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
    void auditOnJvm() throws Exception {
        Path root = tmp.resolve("audit-jvm");
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
    void auditOnScript() throws Exception {
        Path root = tmp.resolve("audit-script");
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
