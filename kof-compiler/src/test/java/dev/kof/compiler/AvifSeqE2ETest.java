package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static dev.kof.compiler.AvifSeqSupport.errorProbe;
import static dev.kof.compiler.AvifSeqSupport.fixtures;
import static dev.kof.compiler.AvifSeqSupport.javaSeqFacts;
import static dev.kof.compiler.AvifSeqSupport.probe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for AVIF slice 2b (image-vision front, plan §34):
 * the pure-Kof sequence-header field walk {@code libs/image/AvifSeq.kf}
 * parses the reduced AND non-reduced OBU_SEQUENCE_HEADER forms per the AV1
 * spec §5.5.1 (widths verified line-by-line against the spec PDF on the dev
 * host 01/10). Fixtures are hand-built byte-exactly to the spec (no AVIF
 * encoder exists on the host — measured); goldens are the spec facts.
 * Pixel decoding is NOT part of this slice: {@code decodeRaster} keeps
 * refusing AVIF.
 */
class AvifSeqE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String GOLDEN = String.join("\n",
            "nr8 p=0 r=0 w=8 h=8 mono=1 sub=1/1 depth=8 still=0",
            "nr12t p=2 r=0 w=32 h=24 mono=1 sub=1/1 depth=12 still=0",
            "flat p=0 r=1 w=32 h=32 mono=1 sub=1/1 depth=8 still=1",
            "r1 p=1 r=1 w=32 h=32 mono=0 sub=0/0 depth=8 still=1");

    @Test
    void avifSeqHeaderOnJvm() throws Exception {
        Path dir = fixtures(tmp.resolve("jvm-fixtures"));
        assertEquals(GOLDEN, runJvm(probe(dir)));
    }

    @Test
    void avifSeqHeaderOnScript() throws Exception {
        Path root = tmp.resolve("script-items");
        Files.createDirectories(root);
        Path dir = fixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void avifSeqHeaderOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = fixtures(tmp.resolve("native-fixtures"));
        assertEquals(GOLDEN, runNativeX86(probe(dir)));
    }

    @Test
    void avifSeqHeaderOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path dir = fixtures(tmp.resolve("riscv-fixtures"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, probe(dir)));
    }

    @Test
    void avifSeqHeaderOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        Path dir = fixtures(tmp.resolve("aarch-fixtures"));
        assertEquals(GOLDEN, runCrossCode("aarch64", Target.NATIVE_AARCH64, probe(dir)));
    }

    @Test
    void seqReaderRefusesOnJs() throws Exception {
        Path root = tmp.resolve("js-items");
        Files.createDirectories(root);
        Path dir = fixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(!result.success(), "JS must refuse the missing kof.io readRange binding (R6)");
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("IOJS001"),
                () -> "expected IOJS001, got: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void seqRefusalsAreHonest() throws Exception {
        Path root = tmp.resolve("item-errors");
        Files.createDirectories(root);
        Path dir = fixtures(root);
        String goldens = String.join("\n",
                "IMAGE: avif decoder model info not covered",
                "IMAGE: avif film grain not covered",
                "IMAGE: avif sequence profile not covered",
                "IMAGE: avif config obu absent");
        assertEquals(goldens, runJvm(errorProbe(dir)));
    }

    @Test
    void javaSeqReaderAgreesOnBothForms() throws Exception {
        // second independent implementation: Java bit walk vs the pure-Kof
        // reader over the same fixtures (reduced + non-reduced forms)
        Path dir = fixtures(tmp.resolve("xcheck-fixtures"));
        String kof = runJvm(probe(dir));
        String java = String.join("\n",
                "nr8 " + javaSeqFacts(dir.resolve("nr8.avif")),
                "nr12t " + javaSeqFacts(dir.resolve("nr12t.avif")),
                "flat " + javaSeqFacts(dir.resolve("flat.avif")),
                "r1 " + javaSeqFacts(dir.resolve("r1.avif")));
        assertEquals(kof, java);
        assertEquals(GOLDEN, java);
    }

    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> compile(source, out, Target.JVM));
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runNativeX86(String code) throws Exception {
        Path root = tmp.resolve("native-x86-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runBinary(out.resolve("Default/Main"));
    }

    private String runCrossCode(String arch, Target target, String code) throws Exception {
        Path root = tmp.resolve("cross-" + arch + "-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = compile(root.resolve("Main.kf"), out, target);
            assertTrue(result.success(), arch + " compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), arch + " binary must exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, binary);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), arch + " output: " + output);
        return output;
    }

    private String runBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private CompilationResult compile(Path source, Path out, Target target) {
        return driver.compile(source, out, target);
    }

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
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
        return "image";
    }

    @Override
    public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Avif.kf");
    }

}
