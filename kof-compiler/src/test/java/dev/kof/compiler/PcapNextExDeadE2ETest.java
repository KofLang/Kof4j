package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Universal platform Stage 5.8 — the CAPTURE-face OFFLINE probe (the
 * #1160 follow-up, the {@code pkthdr**} out-param): {@code pcap_next_ex}
 * on a {@code pcap_open_dead} handle through the pointer-to-pointer face
 * (the borrowed {@code Handle[]} copy-out arrays). MEASURED offline: the
 * rc is -1 (a dead handle has no packets and no device behind it), the out
 * slots stay untouched — no device is opened, nothing is captured; the rc
 * proves the whole chain (the downcall + the out-param marshaling).
 * Defensive-only law carried from the version + lifecycle probes. Proven
 * on the JVM (FFM ADDRESS out-params) and native x86-64 (the raw payload
 * write-through).
 */
class PcapNextExDeadE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "net"; }

    @Override public List<String> libraryMarkers() { return List.of("Pcap.kf"); }

    private static final String PROBE = """
            import net.Pcap

            main() {
                var ok = nextExDeadProbe()
                println(ok)
            }
            """;

    private static final String EXPECTED = "true";

    private void compileAndRun(Path root, Target target) throws Exception {
        Files.writeString(root.resolve("Main.kf"), PROBE);
        Path out = root.resolve("out-" + target.name().toLowerCase());
        CompilationResult r = withLibrary(root,
                () -> driver.compile(root.resolve("Main.kf"), out, target));
        assertTrue(r.success(), target + " compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        if (target == Target.JVM) {
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED",
                    "-cp", out.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().transferTo(baos);
            assertEquals(0, p.waitFor(), target + " exit, output: "
                    + baos.toString(java.nio.charset.StandardCharsets.UTF_8));
        } else {
            Path bin = out.resolve("Default/Main");
            assertTrue(Files.exists(bin), "binary must exist");
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().transferTo(baos);
            assertEquals(0, p.waitFor(), target + " exit, output: "
                    + baos.toString(java.nio.charset.StandardCharsets.UTF_8));
        }
        assertEquals(EXPECTED, baos.toString(java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim(), target + " golden (next_ex dead offline)");
    }

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

    @FunctionalInterface
    private interface CheckedSupplier<T> { T get() throws Exception; }

    @Test
    void nextExDeadJvm(@TempDir Path root) throws Exception {
        compileAndRun(root, Target.JVM);
    }

    @Test
    void nextExDeadNativeX86(@TempDir Path root) throws Exception {
        compileAndRun(root, Target.NATIVE);
    }
}
