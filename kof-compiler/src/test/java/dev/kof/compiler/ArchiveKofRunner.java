package dev.kof.compiler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compiles and runs a Kof probe (`Main.kf` under a root) against the installed
 * `archive` + `image` library trees — the part the streaming-reader suites
 * (`ZipReader*E2ETest`) share. It is a runner, not a test: the JVM run can take
 * JVM flags (the memory proof runs under a small `-Xmx`), and {@link #jvmRaw}
 * hands back the exit code for the one case that is expected to die.
 */
final class ArchiveKofRunner implements LibraryInstallSupport {

    /** Exit code + merged stdout/stderr of one JVM run. */
    record Run(int exit, String out) {
    }

    /** The Kof helper the probes share: drains one entry in chunks and reduces it to
     *  `length + polynomial hash + chunk count + verified` (the hash is
     *  {@link ArchiveFixtureSupport#hash}, so the host computes the same line). */
    static final String DRAIN = """
            import archive.Zip
            import archive.ZipReader

            String drainEntry(ZipReader z, String name, Int chunk) {
                var e = z.find(name)
                if (e == null) {
                    return name + " MISSING"
                }
                var s = z.open(e, 100000000, chunk)
                var h = 0
                var len = 0
                var chunks = 0
                var c = s.readChunk()
                while (c != null) {
                    var i = 0
                    while (i < c.length) {
                        h = (h * 31 + c[i]) % 1000003
                        i = i + 1
                    }
                    len = len + c.length
                    chunks = chunks + 1
                    c = s.readChunk()
                }
                return name + " chunk=" + chunk + " len=" + len + " hash=" + h + " chunks=" + chunks
                    + " verified=" + s.verified()
            }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    /** Runs the probe on the JVM and returns its stdout; the run must exit 0. */
    String jvm(Path root, String... jvmFlags) throws Exception {
        Run run = jvmRaw(root, jvmFlags);
        assertEquals(0, run.exit(), "JVM run: " + run.out());
        return run.out();
    }

    /** Same, but returns the exit code instead of asserting it. */
    Run jvmRaw(Path root, String... jvmFlags) throws Exception {
        Path outDir = root.resolve("out");
        return withLibrary(root, () -> {
            CompilationResult r = driver.compileSources(List.of(root.resolve("Main.kf")), outDir, Target.JVM, root);
            assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
            List<String> cmd = new ArrayList<>();
            cmd.add("java");
            cmd.addAll(List.of(jvmFlags));
            cmd.addAll(List.of("-cp", outDir.toString(), "Default.Main"));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return new Run(p.waitFor(), out);
        });
    }

    /** Compiles the probe to a native binary (x86-64 here, riscv64/aarch64 under
     *  qemu) and runs it; the run must exit 0. */
    String nativeRun(Target target, Path root) throws Exception {
        Path out = root.resolve("out");
        return withLibrary(root, () -> {
            CompilationResult r = driver.compile(root.resolve("Main.kf"), out, target);
            assertTrue(r.success(), target + " compile: " + r.diagnostics().getDiagnostics());
            Path binary = out.resolve("Default/Main");
            assertTrue(Files.isRegularFile(binary), target + ": the binary must exist");
            ProcessBuilder pb = target == Target.NATIVE
                    ? new ProcessBuilder(binary.toString())
                    : NativeRiscv64E2ETest.qemu(target.nativeArch(), binary);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").strip();
            assertEquals(0, p.waitFor(), target + " output: " + o);
            return o;
        });
    }

    /** Runs the probe in the Script interpreter and returns its stdout. */
    String script(Path root) throws Exception {
        KofInterpreter.Result r = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, r.exitCode(), "Script exit, stderr=" + r.stderr());
        return r.stdout().strip();
    }

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

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @Override
    public String libraryName() {
        return "archive";
    }

    /** `ZipReader.kf` composes `image.InflateStream`, so BOTH trees are installed. */
    @Override
    public List<String> libraryNames() {
        return List.of("archive", "image");
    }

    @Override
    public List<String> libraryMarkers() {
        return List.of("ZipReader.kf");
    }
}
