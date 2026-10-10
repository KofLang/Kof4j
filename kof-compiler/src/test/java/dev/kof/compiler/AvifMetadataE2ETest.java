package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dev.kof.compiler.AvifMetadataJavaSupport.javaFacts;
import static dev.kof.compiler.AvifMetadataSupport.fixtures;
import static dev.kof.compiler.AvifMetadataSupport.probe;
import static dev.kof.compiler.AvifMetadataSupport.errorProbe;
import static dev.kof.compiler.AvifMetadataSupport.errorFixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for AVIF slice 1 (image-vision front, plan §34,
 * {@code D-WEBP-LOSSY-PURE-KOF}): the pure-Kof container reader
 * {@code libs/image/Avif.kf} reports brand, item count, primary item, ispe
 * dimensions, iref/auxc alpha and the av1C configuration record (profile,
 * level, tier, bit depths, monochrome, subsampling). Fixtures are built
 * byte-exactly to the AV1-ISOBMFF/AVIF/AV1 specs (no encoder exists on
 * this host — ffmpeg/avifenc/pip measured absent 01/10; the system libheif
 * could not be pinned honestly without headers); independent verification
 * runs against a SECOND reader written in plain Java against the box
 * grammar in {@link AvifMetadataSupport#javaFacts} — real-file encoder
 * goldens ride a later slice when a fixture host exists. Pixel decoding is
 * NOT part of this slice: {@code decodeRaster} keeps refusing AVIF.
 */
class AvifMetadataE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String GOLDEN = String.join("\n",
            "avif 8x8 items=1 primary=1 alpha=0 profile=0 level=2 tier=0 mono=1 sub=0/0 depth=8/8",
            "avif 16x16 items=2 primary=1 alpha=1 profile=1 level=4 tier=0 mono=0 sub=1/1 depth=10/10",
            "avis 32x24 items=1 primary=7 alpha=0 profile=2 level=0 tier=1 mono=0 sub=0/0 depth=12/12");

    @Test
    void avifContainerOnJvm() throws Exception {
        Path dir = fixtures(tmp.resolve("jvm-fixtures"));
        assertEquals(GOLDEN, runJvm(probe(dir)));
    }

    @Test
    void avifContainerOnScript() throws Exception {
        Path root = tmp.resolve("script-avif");
        Files.createDirectories(root);
        Path dir = fixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void avifContainerOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = fixtures(tmp.resolve("native-fixtures"));
        assertEquals(GOLDEN, runNativeX86(probe(dir)));
    }

    @Test
    void avifContainerOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path dir = fixtures(tmp.resolve("riscv-fixtures"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, probe(dir)));
    }

    @Test
    void avifContainerOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        Path dir = fixtures(tmp.resolve("aarch-fixtures"));
        assertEquals(GOLDEN, runCrossCode("aarch64", Target.NATIVE_AARCH64, probe(dir)));
    }

    @Test
    void avifReaderRefusesOnJs() throws Exception {
        Path root = tmp.resolve("js-avif");
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
    void refusalsAndErrorsAreHonest() throws Exception {
        Path root = tmp.resolve("errors");
        Files.createDirectories(root);
        Path dir = errorFixtures(root);
        String goldens = String.join("\n",
                "IMAGE: not an isobmff file",
                "IMAGE: unsupported isobmff brand",
                "IMAGE: avif sequence header form not covered",
                "IMAGE: inconsistent avif profile bit depth",
                "IMAGE: file too short for a valid header");
        assertEquals(goldens, runJvm(errorProbe(dir)));
    }

    /**
     * Independent cross-check: a SECOND reader, written directly against the
     * box grammar in plain Java (different code path, same fixtures), must
     * agree with the pure-Kof library byte-for-byte on every fact. The real
     * encoder/toolchain oracle is unavailable on this host (ffmpeg, avifenc,
     * pip measured absent 01/10; the libheif C ABI could not be pinned
     * honestly without headers) — recorded as the AVIF front's tooling gap;
     * real-file fixtures ride the next slices when a fixture host exists.
     */
    @Test
    void secondJavaReaderAgreesWithKofLibrary() throws Exception {
        Path dir = fixtures(tmp.resolve("cross-fixtures"));
        String kof = runJvm(probe(dir));
        String javaFacts = String.join("\n",
                javaFacts(dir.resolve("flat.avif")),
                javaFacts(dir.resolve("alpha.avif")),
                javaFacts(dir.resolve("compat.avif")));
        assertEquals(kof, javaFacts);
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
