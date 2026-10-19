package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Table coverage for AVIF slice 3aj (image-vision front, plan §34): the pure-Kof
 * AV1 CHROMA-FROM-LUMA primitives in {@code libs/image/Av1Cfl.kf}.
 *
 * <p>libaom runs these when {@code uv_mode} is {@code UV_CFL_PRED} (13): the
 * chroma prediction is a DC prediction MODULATED by the reconstructed luma —
 * {@code cfl_store_tx} subsamples the luma to the chroma grid scaled by 8
 * ({@code cfl_luma_subsampling_420_lbd_c}: a 2x2 cell summed and shifted by 1,
 * which preserves the q3 scale), {@code subtract_average} removes the rounded
 * mean ({@code round_offset = 1 << (num_pel_log2 - 1)}, {@code num_pel_log2 =
 * log2(w*h)} — derivable from the tx size, the {@code CFL_SUB_AVG_FN} table in
 * {@code cfl.h} just enumerates it), and {@code cfl_predict_lbd_c} writes
 * {@code clip(ROUND_POWER_OF_TWO_SIGNED(alpha_q3 * ac_q3, 6) + dc)}.
 *
 * <p>The oracle is libaom's OWN {@code av1/common/cfl.c}, compiled with its
 * static helpers reachable, driven over every CFL-valid transform size (4x4 up
 * to 32x32 including the rectangular ones) with a deterministic luma and eight
 * alpha values from -9 to 9. gzip+base64 as
 * {@code av1_cfl_golden.txt.b64}.
 */
class Av1CflE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String RESOURCE = "/av1_cfl_golden.txt.b64";
    private static final String GOLDEN_SHA256 =
        "df4417c9b6d6698fab0e4045fdfa3912049977ed1a93f10e9fb2f71adaa0f560";

    private static String goldenText() {
        try (InputStream raw = Av1CflE2ETest.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) throw new IllegalStateException("missing " + RESOURCE);
            byte[] b64 = raw.readAllBytes();
            StringBuilder sb = new StringBuilder(b64.length);
            for (byte b : b64) {
                char c = (char) (b & 0xFF);
                if (c != '\n' && c != '\r' && c != ' ' && c != '\t') sb.append(c);
            }
            byte[] gz = Base64.getDecoder().decode(sb.toString());
            try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    /** The {@code A} rows: {@code A tx cw ch <ac...>}. */
    private static List<String> acRows() {
        List<String> out = new ArrayList<>();
        for (String line : goldenText().split("\n")) {
            if (line.startsWith("A ")) out.add(line);
        }
        return out;
    }

    /** The {@code P} rows: {@code P tx alpha <pred...>}. */
    private static List<String> predRows() {
        List<String> out = new ArrayList<>();
        for (String line : goldenText().split("\n")) {
            if (line.startsWith("P ")) out.add(line);
        }
        return out;
    }

    private static String probe() {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1Cfl\n");
        sb.append("import image.Av1Tx\n\n");
        sb.append("main() {\n");
        /* The oracle's luma buffer: 64x64, stride 64, filled (i*37+11)&255. */
        sb.append("    var luma = new Int[64 * 64]\n");
        sb.append("    var i = 0\n");
        sb.append("    while (i < luma.size) {\n");
        sb.append("        luma[i] = (i * 37 + 11) & 255\n");
        sb.append("        i = i + 1\n");
        sb.append("    }\n");
        for (String row : acRows()) {
            String[] f = row.split(" ");
            int tx = Integer.parseInt(f[1]);
            int cw = Integer.parseInt(f[2]);
            int ch = Integer.parseInt(f[3]);
            int numPelLog2 = Integer.numberOfTrailingZeros(cw * ch);
            sb.append("    var ac").append(tx).append(" = av1CflAcFromLuma(luma, 64, 0, 0, ")
              .append(cw).append(", ").append(ch).append(", 1, 1, ").append(numPelLog2).append(", 64, 64)\n");
            sb.append("    var sa").append(tx).append(" = \"A ").append(tx).append(' ')
              .append(cw).append(' ').append(ch).append("\"\n");
            sb.append("    i = 0\n");
            sb.append("    while (i < ac").append(tx).append(".size) {\n");
            sb.append("        sa").append(tx).append(" = sa").append(tx)
              .append(" + \" \" + ac").append(tx).append("[i]\n");
            sb.append("        i = i + 1\n");
            sb.append("    }\n");
            sb.append("    println(sa").append(tx).append(")\n");
            /* The oracle emits each tx's 8 alpha predictions right after its A row. */
            for (String prow : predRows()) {
                String[] g = prow.split(" ");
                if (Integer.parseInt(g[1]) != tx) continue;
                int alpha = Integer.parseInt(g[2]);
                String av = (alpha < 0 ? "m" + (-alpha) : "p" + alpha);
                sb.append("    var dc").append(tx).append('_').append(av)
                  .append(" = new Int[").append(cw * ch).append("]\n");
                sb.append("    i = 0\n");
                sb.append("    while (i < dc").append(tx).append('_').append(av).append(".size) {\n");
                sb.append("        dc").append(tx).append('_').append(av).append("[i] = 128\n");
                sb.append("        i = i + 1\n");
                sb.append("    }\n");
                sb.append("    var p").append(tx).append('_').append(av)
                  .append(" = av1CflPredict(dc").append(tx).append('_').append(av)
                  .append(", ac").append(tx).append(", ").append(alpha).append(", ")
                  .append(cw).append(", ").append(ch).append(", 8)\n");
                sb.append("    var sp").append(tx).append('_').append(av).append(" = \"P ")
                  .append(tx).append(' ').append(alpha).append("\"\n");
                sb.append("    i = 0\n");
                sb.append("    while (i < p").append(tx).append('_').append(av).append(".size) {\n");
                sb.append("        sp").append(tx).append('_').append(av).append(" = sp")
                  .append(tx).append('_').append(av).append(" + \" \" + p")
                  .append(tx).append('_').append(av).append("[i]\n");
                sb.append("        i = i + 1\n");
                sb.append("    }\n");
                sb.append("    println(sp").append(tx).append('_').append(av).append(")\n");
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    @Test
    void cflGoldenShape() {
        assertEquals(14, acRows().size(), "14 CFL-valid transform sizes");
        assertEquals(14 * 8, predRows().size(), "each size crossed with 8 alpha values");
        assertTrue(goldenText().startsWith("A 0 4 4"), "golden must be the libaom cfl.c dump");
    }

    @Test
    void av1CflOnJvm() throws Exception {
        assertEquals(goldenText(), runJvm(probe()));
    }

    @Test
    void av1CflOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(goldenText(), result.stdout().strip());
    }

    @Test
    void av1CflOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(goldenText(), runNative("native-x86", Target.NATIVE));
    }

    @Test
    void av1CflOnNativeRiscv64() throws Exception {
        /* known-bugs §687 FIXED 13/10: this probe's frame crosses ~2 KiB and the
         * riscv64 prologue emitted an out-of-range `sd zero,<offset>(s11)`, so
         * the assembler rejected the build (the skip that used to live here
         * NAMED §687 — it is gone because the face now RUNS). The backend
         * materializes the slot address past the 12-bit immediate; the failure
         * was the BACKEND's, not this primitive's. */
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(goldenText(), runCross("riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void av1CflOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(goldenText(), runCross("aarch64", Target.NATIVE_AARCH64));
    }


    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JVM));
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

    private String runNative(String tag, Target target) throws Exception {
        Path root = tmp.resolve(tag);
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe());
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult r = driver.compile(root.resolve("Main.kf"), out, target);
            assertTrue(r.success(), () -> tag + " compile: " + r.diagnostics().getDiagnostics());
            return null;
        });
        return runBinary(out.resolve("Default/Main"), tag);
    }

    private String runCross(String arch, Target target) throws Exception {
        Path root = tmp.resolve("cross-" + arch);
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe());
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult r = driver.compile(root.resolve("Main.kf"), out, target);
            assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
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

    private String runBinary(Path binary, String tag) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), tag + " output: " + output);
        return output;
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
    public List<String> libraryMarkers() {
        return List.of("Av1Cfl.kf");
    }
}
