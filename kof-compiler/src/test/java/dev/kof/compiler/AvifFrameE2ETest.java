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

import static dev.kof.compiler.AvifFrameSupport.errorFixtures;
import static dev.kof.compiler.AvifFrameSupport.errorProbe;
import static dev.kof.compiler.AvifFrameSupport.fixtures;
import static dev.kof.compiler.AvifGroupSupport.groupFixtures;
import static dev.kof.compiler.AvifGroupSupport.groupErrorFixtures;
import static dev.kof.compiler.AvifGroupSupport.groupProbe;
import static dev.kof.compiler.AvifGroupSupport.groupErrorProbe;
import static dev.kof.compiler.AvifGroupJavaSupport.javaGroupFacts;
import static dev.kof.compiler.AvifGroupJavaSupport.javaGroupFactsError;
import static dev.kof.compiler.AvifGroupJavaSupport.javaTileFacts;
import static dev.kof.compiler.AvifFrameJavaSupport.javaFrameFacts;
import static dev.kof.compiler.AvifFrameJavaSupport.javaFrameFactsError;
import static dev.kof.compiler.AvifFrameSupport.probe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for AVIF slices 2d/2e/2l/2m (image-vision front, plan
 * §34): the pure-Kof frame-header walk {@code libs/image/AvifFrame.kf} parses
 * {@code frame_header_obu}/{@code uncompressed_header} per the AV1 Bitstream
 * Specification §5.9.2/§5.9.5/§5.9.6/§5.9.8-§5.9.20 (quoted from the spec PDF
 * read on the dev host 02/10): frame type (spec numbering KEY=0/INTER=1/
 * INTRA_ONLY=2/SWIT), show flag, screen-content and force-mv signalling,
 * frame-id width, size override + coded size, superres refusal, render size,
 * allow_intrabc, the 5.9.15 uniform tile grid, and the 5.9.2 header tail
 * (quantization, segmentation, delta_q/lf, loop filter, cdef, restoration, tx
 * mode, reduced_tx_set) through byte_alignment. Slice 2l measured on the real
 * host file that the intra path must NOT read read_interpolation_filter /
 * is_motion_mode_switchable (5.9.2 has them only off-intra); slice 2m
 * located the OBU_FRAME inline tile group at the aligned header size and
 * enumerated it on the real file. Fixtures are hand-built byte-exactly to the
 * spec (no AVIF encoder exists on the host — measured); METADATA ONLY —
 * {@code decodeRaster} keeps refusing AVIF, and the coefficient syntax and
 * tile payloads past the header are a documented refusal frontier, not a
 * silent pass.
 */
class AvifFrameE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String GOLDEN = String.join("\n",
            "red-k t=0 show=1 err=1 ov=0 w=32 h=32 rd=0 rw=32 rh=32 tiles=1x1 hb=3 bq=0 lf=0 cd=0",
            "nr-k t=0 show=1 err=1 ov=1 w=5 h=3 rd=0 rw=5 rh=3 tiles=1x1 hb=4 bq=0 lf=0 cd=0",
            "rend t=0 show=1 err=1 ov=0 w=32 h=32 rd=1 rw=11 rh=6 tiles=1x1 hb=7 bq=0 lf=0 cd=0",
            "tile4 t=0 show=1 err=1 ov=0 w=128 h=128 rd=0 rw=128 rh=128 tiles=2x2 hb=3 bq=0 lf=0 cd=0",
            "intra t=2 show=1 err=0 ov=1 w=5 h=3 rd=0 rw=5 rh=3 tiles=1x1 hb=5 bq=0 lf=0 cd=0",
            "q32 t=0 show=1 err=1 ov=0 w=32 h=32 rd=0 rw=32 rh=32 tiles=1x1 hb=6 bq=32 lf=7 cd=0",
            "nonuni t=0 show=1 err=1 ov=0 w=128 h=128 rd=0 rw=128 rh=128 tiles=2x1 hb=3 bq=0 lf=0 cd=0");

    @Test
    void avifFrameHeaderOnJvm() throws Exception {
        Path dir = fixtures(tmp.resolve("jvm-fixtures"));
        assertEquals(GOLDEN, runJvm(probe(dir)));
    }

    @Test
    void avifFrameHeaderOnScript() throws Exception {
        Path root = tmp.resolve("script-frame");
        Files.createDirectories(root);
        Path dir = fixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void avifFrameHeaderOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = fixtures(tmp.resolve("native-fixtures"));
        assertEquals(GOLDEN, runNativeX86(probe(dir)));
    }

    @Test
    void avifFrameHeaderOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path dir = fixtures(tmp.resolve("riscv-fixtures"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, probe(dir)));
    }

    @Test
    void avifFrameHeaderOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        Path dir = fixtures(tmp.resolve("aarch-fixtures"));
        assertEquals(GOLDEN, runCrossCode("aarch64", Target.NATIVE_AARCH64, probe(dir)));
    }

    @Test
    void frameReaderRefusesOnJs() throws Exception {
        Path root = tmp.resolve("js-frame");
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
    void frameRefusalsAreHonest() throws Exception {
        Path root = tmp.resolve("frame-errors");
        Files.createDirectories(root);
        Path dir = errorFixtures(root);
        String goldens = String.join("\n",
                "IMAGE: avif frame show-existing not covered",
                "IMAGE: avif inter frame not covered",
                "IMAGE: avif intra block copy not covered",
                "IMAGE: truncated avif frame header",
                "IMAGE: avif item has no frame header");
        assertEquals(goldens, runJvm(errorProbe(dir)));
    }

    @Test
    void javaFrameReaderAgreesOnFixtures() throws Exception {
        // second independent implementation: plain-Java AV1 5.9 prefix walk
        // vs the pure-Kof reader over the same fixtures and same refusals
        Path dir = fixtures(tmp.resolve("xcheck-fixtures"));
        String kof = runJvm(probe(dir));
        String java = String.join("\n",
                "red-k " + javaFrameFacts(dir.resolve("red-k.avif")),
                "nr-k " + javaFrameFacts(dir.resolve("nr-k.avif")),
                "rend " + javaFrameFacts(dir.resolve("rend.avif")),
                "tile4 " + javaFrameFacts(dir.resolve("tile4.avif")),
                "intra " + javaFrameFacts(dir.resolve("intra.avif")),
                "q32 " + javaFrameFacts(dir.resolve("q32.avif")),
                "nonuni " + javaFrameFacts(dir.resolve("nonuni.avif")));
        assertEquals(kof, java);
        assertEquals(GOLDEN, java);

        Path errDir = errorFixtures(tmp.resolve("xcheck-errors"));
        String javaErrors = String.join("\n",
                "showexisting:" + javaFrameFactsError(errDir.resolve("showexisting.avif")),
                "inter:" + javaFrameFactsError(errDir.resolve("inter.avif")),
                "intrabc:" + javaFrameFactsError(errDir.resolve("intrabc.avif")),
                "trunc:" + javaFrameFactsError(errDir.resolve("trunc.avif")),
                "noframe:" + javaFrameFactsError(errDir.resolve("noframe.avif")));
        String expectedErrors = String.join("\n",
                "showexisting:REFUSED:showexisting",
                "inter:REFUSED:inter",
                "intrabc:REFUSED:intrabc",
                "trunc:REFUSED:trunc",
                "noframe:REFUSED:noframe");
        assertEquals(expectedErrors, javaErrors);
    }


    private static final String GOLDEN_GROUPS = String.join("\n",
            "one 0..0 n=1 sizes= last=2 total=2",
            "split 0..1 n=2 sizes=,5 last=3 total=8",
            "split 2..3 n=2 sizes=,4 last=2 total=6",
            "inline 0..3 n=4 sizes=,5,4,3 last=1 total=13");

    // slice 2n: per-tile payload facts (length + rolling hash) in tile order,
    // from the tg-inline (4 tiles) and tg-split (two sibling groups) fixtures.
    private static final String GOLDEN_TILES = String.join("\n",
            "tile len=5 h=31810",
            "tile len=4 h=54943",
            "tile len=3 h=8970",
            "tile len=1 h=12",
            "stile len=5 h=31810",
            "stile len=3 h=4998",
            "stile len=4 h=1026",
            "stile len=2 h=129");

    private static final String GOLDEN_GROUP_ALL = GOLDEN_GROUPS + "\n" + GOLDEN_TILES;

    private static final String GROUP_REFUSALS = String.join("\n",
            "IMAGE: avif tile group full range flag set",
            "IMAGE: avif tile group order invalid",
            "IMAGE: avif tile group range invalid",
            "IMAGE: avif tile group end incomplete",
            "IMAGE: truncated avif tile group",
            "IMAGE: avif frame has no tile group",
            "IMAGE: avif tile group without frame header");

    @Test
    void avifTileGroupsOnJvm() throws Exception {
        Path dir = groupFixtures(tmp.resolve("group-jvm"));
        assertEquals(GOLDEN_GROUP_ALL, runJvm(groupProbe(dir)));
    }

    @Test
    void avifTileGroupsOnScript() throws Exception {
        Path root = tmp.resolve("script-groups");
        Files.createDirectories(root);
        Path dir = groupFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), groupProbe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN_GROUP_ALL, result.stdout().strip());
    }

    @Test
    void avifTileGroupsOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = groupFixtures(tmp.resolve("group-native"));
        assertEquals(GOLDEN_GROUP_ALL, runNativeX86(groupProbe(dir)));
    }

    @Test
    void avifTileGroupsOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path dir = groupFixtures(tmp.resolve("group-riscv"));
        assertEquals(GOLDEN_GROUP_ALL, runCrossCode("riscv64", Target.NATIVE_RISCV64, groupProbe(dir)));
    }

    @Test
    void avifTileGroupsOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        Path dir = groupFixtures(tmp.resolve("group-aarch"));
        assertEquals(GOLDEN_GROUP_ALL, runCrossCode("aarch64", Target.NATIVE_AARCH64, groupProbe(dir)));
    }

    @Test
    void groupReaderRefusesOnJs() throws Exception {
        Path root = tmp.resolve("js-groups");
        Files.createDirectories(root);
        Path dir = groupFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), groupProbe(dir));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(!result.success(), "JS must refuse the missing kof.io readRange binding (R6)");
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("IOJS001"),
                () -> "expected IOJS001, got: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void groupRefusalsAreHonest() throws Exception {
        Path dir = groupErrorFixtures(tmp.resolve("group-errors"));
        assertEquals(GROUP_REFUSALS, runJvm(groupErrorProbe(dir)));
    }

    @Test
    void javaGroupReaderAgreesOnFixtures() throws Exception {
        // second independent implementation: plain-Java AV1 5.11 prefix walk
        // vs the pure-Kof group face over the same fixtures and refusals
        Path dir = groupFixtures(tmp.resolve("gxcheck-fixtures"));
        String[] one = javaGroupFacts(dir.resolve("tg-one.avif")).split("\n");
        String[] sp = javaGroupFacts(dir.resolve("tg-split.avif")).split("\n");
        String[] inl = javaGroupFacts(dir.resolve("tg-inline.avif")).split("\n");
        assertEquals(1, one.length);
        assertEquals(2, sp.length);
        assertEquals(1, inl.length);
        String java = String.join("\n",
                "one " + one[0], "split " + sp[0], "split " + sp[1],
                "inline " + inl[0],
                javaTileFacts(dir.resolve("tg-inline.avif"), "tile"),
                javaTileFacts(dir.resolve("tg-split.avif"), "stile"));
        assertEquals(GOLDEN_GROUP_ALL, java);

        Path errDir = groupErrorFixtures(tmp.resolve("gxcheck-errors"));
        String[] names = {"full", "order", "range", "incomplete", "trunc", "nogroup", "before"};
        StringBuilder javaErrors = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            javaErrors.append(javaGroupFactsError(errDir.resolve(names[i] + ".avif")));
            if (i + 1 < names.length) javaErrors.append("\n");
        }
        assertEquals(GROUP_REFUSALS, javaErrors.toString());
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
