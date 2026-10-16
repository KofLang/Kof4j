package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.kof.compiler.Av1PartitionSupport.expectedGrid;
import static dev.kof.compiler.Av1PartitionSupport.golden;
import static dev.kof.compiler.Av1PartitionSupport.kofGridProbe;
import static dev.kof.compiler.Av1PartitionSupport.REAL_TILE_HEAD;
import static dev.kof.compiler.Av1PartitionSupport.kofProbe;
import static dev.kof.compiler.Av1PartitionSupport.kofRealTileProbe;
import static dev.kof.compiler.Av1PartitionSupport.REAL_TILE_DECODED;
import static dev.kof.compiler.Av1PartitionSupport.kofSeamProbe;
import static dev.kof.compiler.Av1PartitionSupport.kofTileTreeProbe;
import static dev.kof.compiler.Av1PartitionSupport.kofSharedReaderProbe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for AVIF slice 3o (image-vision front, plan §34): the
 * pure-Kof AV1 recursive {@code decode_partition} tile walk
 * {@code libs/image/Av1Partition.kf} — over the slice-3a symbol decoder, the
 * slice-3m partition CDFs and the slice-3n block descriptor it reads the
 * partition symbol of every square block of a tile, maintains libaom's
 * {@code partition_plane_context} neighbour state and recurses exactly as the
 * spec §5.11.4 does, emitting one {@code P}/{@code B} decision per step. The
 * oracle is libaom's real range ENCODER driving its own partition helpers; the
 * test requires the Kof walk to reproduce every decision (see
 * {@link Av1PartitionSupport}).
 */
class Av1PartitionE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void av1PartitionOnJvm() throws Exception {
        assertEquals(golden(), runJvm(kofProbe()));
    }

    @Test
    void av1PartitionOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(golden(), result.stdout().strip());
    }

    @Test
    void av1PartitionOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(golden(), runNativeX86(kofProbe()));
    }

    @Test
    void av1PartitionOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(golden(), runCrossCode("riscv64", Target.NATIVE_RISCV64, kofProbe()));
    }

    @Test
    void av1PartitionOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(golden(), runCrossCode("aarch64", Target.NATIVE_AARCH64, kofProbe()));
    }

    @Test
    void av1PartitionOnJs() throws Exception {
        Path root = tmp.resolve("js");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofProbe());
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> driver.compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        assertEquals(golden(), stdout.toString(StandardCharsets.UTF_8).strip());
    }

    /**
     * The SHARED-READER seam (slice 3at): {@code Av1Partition.useReader} lets a
     * caller bind an {@code Av1Symbol} it already owns, because a real tile's
     * partition symbols and block payloads live in ONE entropy stream (libaom's
     * {@code decode_partition} reads the partition symbol and then calls
     * {@code decode_block} on the same {@code aom_reader}). The seam must be
     * behaviour-preserving: the walk must emit exactly the same decisions as the
     * constructor-owned reader does on the same bytes.
     */
    @Test
    void av1SharedReaderSeamOnJvm() throws Exception {
        assertEquals(golden(), runJvm(kofSharedReaderProbe()));
    }

    @Test
    void av1SharedReaderSeamOnScript() throws Exception {
        Path root = tmp.resolve("script-shared");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofSharedReaderProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(golden(), result.stdout().strip());
    }

    @Test
    void av1SharedReaderSeamOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(golden(), runNativeX86(kofSharedReaderProbe()));
    }

    @Test
    void av1SharedReaderSeamOnJs() throws Exception {
        Path root = tmp.resolve("js-shared");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofSharedReaderProbe());
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> driver.compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        assertEquals(golden(), stdout.toString(StandardCharsets.UTF_8).strip());
    }

    /**
     * The REAL-TILE end-to-end face (slice 3au): the walk DRIVES the tile driver
     * over the interleaved stream, decoding each block at its `decode_block` step
     * before reading the next partition symbol — libaom's own interleaving. The
     * reference is the oracle's own decode of the same stream (four
     * BLOCK_32X32/TX_32X32/DC_PRED blocks and a final entropy context of 17), so
     * this pins the whole loop, not just the walk.
     */
    @Test
    void av1TileTreeDecodesRealInterleavedTile() throws Exception {
        assertEquals(REAL_TILE_DECODED, runJvm(kofTileTreeProbe()));
    }

    @Test
    void av1TileTreeDecodesRealInterleavedTileOnScript() throws Exception {
        Path root = tmp.resolve("tree-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofTileTreeProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(REAL_TILE_DECODED, result.stdout().strip());
    }

    @Test
    void av1TileTreeDecodesRealInterleavedTileOnJs() throws Exception {
        Path root = tmp.resolve("tree-js");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofTileTreeProbe());
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> driver.compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        assertEquals(REAL_TILE_DECODED, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    /**
     * The walk reads a REAL INTERLEAVED tile stream (slice 3at): partition
     * symbols and block payloads in one entropy stream, as libaom's
     * {@code decode_partition} produces them. This is the first time the Kof walk
     * consumes a stream that carries block payloads at all. Only the first two
     * decisions are asserted — the walk has no block decoder to consume the
     * payload between symbols, so it is in sync exactly until the first
     * {@code decode_block}, which is the boundary of the next slice.
     */
    @Test
    void av1WalkReadsRealInterleavedTile() throws Exception {
        assertEquals(REAL_TILE_HEAD, runJvm(kofRealTileProbe()));
    }

    /**
     * The seam must actually BIND, not merely preserve behaviour: a probe walks
     * the same bytes with the walk bound to a caller-owned reader and with the
     * constructor's own reader, and the two final bit positions must be EQUAL.
     * That equality is the discriminating assertion — a no-op {@code useReader}
     * leaves the shared reader at its construction position (the 15-bit
     * {@code init_symbol} seed, so a plain {@code > 0} would not catch it) while
     * the walk's own reader advances past it. Measured RED: 15 vs 24.
     */
    @Test
    void av1SharedReaderSeamBinds() throws Exception {
        String out = runJvm(kofSeamProbe());
        int checked = 0;
        for (String line : out.split("\n")) {
            if (!line.startsWith("R ")) {
                continue;
            }
            String[] f = line.trim().split(" ");
            long shared = Long.parseLong(f[1]);
            long own = Long.parseLong(f[2]);
            assertEquals(own, shared,
                    "the walk must consume the reader it was GIVEN: " + line);
            checked++;
        }
        assertEquals(2, checked, "expected one R record per case, got: " + out);
    }

    /**
     * The BLOCK GRID the tile driver consumes (slice 3as): {@code av1TileBlockGrid}
     * must extract exactly the {@code decode_block} calls libaom makes, in decode
     * order — the link between the partition walk (3o) and the driver (3ag). The
     * reference is the golden's own {@code B r c subSize} records, so this is
     * libaom's own block list, not a re-derivation.
     */
    @Test
    void av1TileBlockGridOnJvm() throws Exception {
        assertEquals(expectedGrid(), runJvm(kofGridProbe()));
    }

    @Test
    void av1TileBlockGridOnScript() throws Exception {
        Path root = tmp.resolve("script-grid");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofGridProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(expectedGrid(), result.stdout().strip());
    }

    @Test
    void av1TileBlockGridOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(expectedGrid(), runNativeX86(kofGridProbe()));
    }

    @Test
    void av1TileBlockGridOnJs() throws Exception {
        Path root = tmp.resolve("js-grid");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofGridProbe());
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> driver.compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        assertEquals(expectedGrid(), stdout.toString(StandardCharsets.UTF_8).strip());
    }

    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> driver.compile(source, out, Target.JVM));
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
        return java.util.List.of("Av1Symbol.kf");
    }
}
