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

import static dev.kof.compiler.AvifMetaSupport.errorFixtures;
import static dev.kof.compiler.AvifMetaSupport.errorProbe;
import static dev.kof.compiler.AvifMetaSupport.fixtures;
import static dev.kof.compiler.AvifMetaSupport.probe;
import static dev.kof.compiler.AvifMetaJavaSupport.javaMetaFacts;
import static dev.kof.compiler.AvifMetaJavaSupport.javaMetaFactsError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for AVIF slice 2g (image-vision front, plan §34):
 * the pure-Kof OBU_METADATA payload walk {@code libs/image/AvifMeta.kf}
 * parses metadata_obu per the AV1 Bitstream Specification §5.8.1–§5.8.7 +
 * the §6.4.1 metadata_type table (quoted from the spec source and the AOM
 * {@code aom_codec.h} enum): leb128 type, ITUT-T35 country/extension/payload
 * size (type 4), HDR CLL numbers (type 1), HDR MDCV's ten values (type 2),
 * the metadata_timecode() bitfields (type 5, §5.8.7 syntax + §6.7.7
 * semantics), and honest name+size enumeration for scalability/private/
 * reserved types. Fixtures are hand-built byte-exactly to the spec (no AVIF
 * encoder exists on the host — measured); METADATA ONLY —
 * {@code decodeRaster} keeps refusing AVIF.
 */
class AvifMetaE2ETest implements LibraryInstallSupport {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String GOLDEN = String.join("\n",
            "t35 4 itutT35 pb=5 cc=181 ext=0 t35=4",
            "t35x 4 itutT35 pb=4 cc=65281 ext=1 t35=2",
            "cll 1 hdrCll pb=4 cll=1000 fall=400",
            "mdcv 2 hdrMdcv pb=24 mdcv=,13252,34591,22413,60000,3596,7146,15635,16450,10000000,1",
            "tc 5 timecode pb=5 tc=,3,1,0,1,24,45,59,23,0",
            "tcf 5 timecode pb=4 tc=,1,0,1,0,0,-1,-1,-1,9",
            "mixed 4 itutT35 pb=3 cc=16 ext=0 t35=2",
            "mixed 3 scalability pb=5",
            "mixed 7 private pb=2",
            "mixed 32 reserved pb=1");

    private static final String REFUSALS = String.join("\n",
            "IMAGE: truncated avif metadata obu",
            "IMAGE: truncated avif metadata obu",
            "IMAGE: truncated avif metadata obu",
            "IMAGE: truncated avif metadata obu",
            "IMAGE: avif obu truncated");

    @Test
    void avifMetadataObusOnJvm() throws Exception {
        Path dir = fixtures(tmp.resolve("jvm-fixtures"));
        assertEquals(GOLDEN, runJvm(probe(dir)));
    }

    @Test
    void avifMetadataObusOnScript() throws Exception {
        Path root = tmp.resolve("script-meta");
        Files.createDirectories(root);
        Path dir = fixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void avifMetadataObusOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = fixtures(tmp.resolve("native-fixtures"));
        assertEquals(GOLDEN, runNativeX86(probe(dir)));
    }

    @Test
    void avifMetadataObusOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path dir = fixtures(tmp.resolve("riscv-fixtures"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, probe(dir)));
    }

    @Test
    void avifMetadataObusOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        Path dir = fixtures(tmp.resolve("aarch-fixtures"));
        assertEquals(GOLDEN, runCrossCode("aarch64", Target.NATIVE_AARCH64, probe(dir)));
    }

    @Test
    void metadataReaderRefusesOnJs() throws Exception {
        Path root = tmp.resolve("js-meta");
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
    void metadataRefusalsAreHonest() throws Exception {
        Path root = tmp.resolve("meta-errors");
        Files.createDirectories(root);
        Path dir = errorFixtures(root);
        assertEquals(REFUSALS, runJvm(errorProbe(dir)));
    }

    @Test
    void javaMetadataReaderAgreesOnFixtures() throws Exception {
        // second independent implementation: plain-Java AV1 5.8 prefix walk
        // vs the pure-Kof reader over the same fixtures and same refusals
        Path dir = fixtures(tmp.resolve("mxcheck-fixtures"));
        String java = String.join("\n",
                "t35 " + String.join("\nt35 ", javaMetaFacts(dir.resolve("t35.avif"))),
                "t35x " + String.join("\nt35x ", javaMetaFacts(dir.resolve("t35x.avif"))),
                "cll " + String.join("\ncll ", javaMetaFacts(dir.resolve("cll.avif"))),
                "mdcv " + String.join("\nmdcv ", javaMetaFacts(dir.resolve("mdcv.avif"))),
                "tc " + String.join("\ntc ", javaMetaFacts(dir.resolve("tc.avif"))),
                "tcf " + String.join("\ntcf ", javaMetaFacts(dir.resolve("tcf.avif"))),
                "mixed " + String.join("\nmixed ", javaMetaFacts(dir.resolve("mixed.avif"))));
        assertEquals(GOLDEN, java);

        Path errDir = errorFixtures(tmp.resolve("mxcheck-errors"));
        String javaErrors = String.join("\n",
                javaMetaFactsError(errDir.resolve("mtrunc.avif")),
                javaMetaFactsError(errDir.resolve("mdcvshort.avif")),
                javaMetaFactsError(errDir.resolve("t35short.avif")),
                javaMetaFactsError(errDir.resolve("tcshort.avif")),
                javaMetaFactsError(errDir.resolve("obutrun.avif")));
        assertEquals(REFUSALS, javaErrors);
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
