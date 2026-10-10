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

import static dev.kof.compiler.AvifItemsSupport.errorProbe;
import static dev.kof.compiler.AvifItemsSupport.fact;
import static dev.kof.compiler.AvifItemsSupport.fixtures;
import static dev.kof.compiler.AvifItemsSupport.itemProbe;
import static dev.kof.compiler.AvifItemsSupport.readItemJava;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for AVIF slice 2a (image-vision front, plan §34):
 * the pure-Kof item-location reader {@code libs/image/AvifItems.kf}
 * extracts a stored item's bytes through the ISOBMFF {@code iloc} box
 * (ISO 14496-12 §8.7.4; version 0 and 1, base_offset_size 0/4/8, one or
 * more extents, construction 0 = file offsets and 1 = {@code idat}
 * payload). Fixtures are built byte-exactly to the spec, which was
 * cross-checked against FFmpeg {@code mov_read_iloc}, the mp4parser
 * {@code ItemLocationBox} javadoc and a real AVIF file measured 03/10; the
 * oracle is a SECOND independent Java reader
 * ({@link AvifItemsSupport#readItemJava}) plus spec-computed goldens.
 * Pixel decoding is NOT part of this slice.
 */
class AvifItemsE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String GOLDEN = String.join("\n",
            "mdat1 len=40 first=0 last=22 sum=4456",
            "mdat2 len=20 first=200 last=143 sum=3430",
            "idat1 len=40 first=0 last=22 sum=4456",
            "v01 len=40 first=0 last=22 sum=4456",
            "multi1 len=60 first=0 last=143 sum=7886");

    @Test
    void avifItemBytesOnJvm() throws Exception {
        Path dir = fixtures(tmp.resolve("jvm-fixtures"));
        assertEquals(GOLDEN, runJvm(itemProbe(dir)));
    }

    @Test
    void avifItemBytesOnScript() throws Exception {
        Path root = tmp.resolve("script-items");
        Files.createDirectories(root);
        Path dir = fixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), itemProbe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void avifItemBytesOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = fixtures(tmp.resolve("native-fixtures"));
        assertEquals(GOLDEN, runNativeX86(itemProbe(dir)));
    }

    @Test
    void avifItemBytesOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path dir = fixtures(tmp.resolve("riscv-fixtures"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, itemProbe(dir)));
    }

    @Test
    void avifItemBytesOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        Path dir = fixtures(tmp.resolve("aarch-fixtures"));
        assertEquals(GOLDEN, runCrossCode("aarch64", Target.NATIVE_AARCH64, itemProbe(dir)));
    }

    @Test
    void itemReaderRefusesOnJs() throws Exception {
        Path root = tmp.resolve("js-items");
        Files.createDirectories(root);
        Path dir = fixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), itemProbe(dir));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(!result.success(), "JS must refuse the missing kof.io readRange binding (R6)");
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("IOJS001"),
                () -> "expected IOJS001, got: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void itemRefusalsAreHonest() throws Exception {
        Path root = tmp.resolve("item-errors");
        Files.createDirectories(root);
        Path dir = fixtures(root);
        String goldens = String.join("\n",
                "IMAGE: avif item beyond read prefix",
                "IMAGE: avif iloc version not covered",
                "IMAGE: avif has no iloc box",
                "IMAGE: avif item not found");
        assertEquals(goldens, runJvm(errorProbe(dir)));
    }

    @Test
    void secondJavaReaderAgreesOnItemBytes() throws Exception {
        Path dir = fixtures(tmp.resolve("xcheck-fixtures"));
        String kof = runJvm(itemProbe(dir));
        String java = String.join("\n",
                "mdat1 " + fact(readItemJava(dir.resolve("mdat.avif"), 1)),
                "mdat2 " + fact(readItemJava(dir.resolve("mdat.avif"), 2)),
                "idat1 " + fact(readItemJava(dir.resolve("idat.avif"), 1)),
                "v01 " + fact(readItemJava(dir.resolve("v0.avif"), 1)),
                "multi1 " + fact(readItemJava(dir.resolve("multi.avif"), 1)));
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
