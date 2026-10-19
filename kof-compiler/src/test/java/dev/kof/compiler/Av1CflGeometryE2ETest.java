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
 * Geometry coverage for the AVIF CHROMA-FROM-LUMA primitives
 * ({@code libs/image/Av1Cfl.kf}): the CHROMA ORIGIN mapping and the CFL
 * REGION SELECTION, both pinned against libaom.
 *
 * <p>Split out of {@code Av1CflE2ETest} (which keeps the arithmetic table) so
 * each class stays inside the size ratchet.
 */
class Av1CflGeometryE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String ORIGIN_RESOURCE = "/av1_cfl_origin_golden.txt.b64";
    private static final String ORIGIN_SHA256 =
        "304033b8669b53e6682236b650c6b4fb12e94f04b8838109c83f6dd0f006ca3d";

    private static String resource(String name, String sha) {
        try (InputStream raw = Av1CflE2ETest.class.getResourceAsStream(name)) {
            if (raw == null) throw new IllegalStateException("missing " + name);
            byte[] b64 = raw.readAllBytes();
            StringBuilder sb = new StringBuilder(b64.length);
            for (byte b : b64) {
                char c = (char) (b & 0xFF);
                if (c != '\n' && c != '\r' && c != ' ' && c != '\t') sb.append(c);
            }
            byte[] gz = Base64.getDecoder().decode(sb.toString());
            try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
                String actual = sha256(text);
                if (!sha.equals(actual)) {
                    throw new IllegalStateException("stale golden " + name + ": " + actual);
                }
                return text;
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + name, e);
        }
    }

    private static String sha256(String text) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String originGolden() {
        return resource(ORIGIN_RESOURCE, ORIGIN_SHA256);
    }

    /**
     * Probe for the chroma-origin mapping. It is TINY (no large local array), so
     * it runs on every native face including riscv64 — the {@code known-bugs}
     * §687 frame limit does not apply here.
     */
    private static String originProbe() {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1Cfl\n");
        sb.append("import image.Av1Block\n\n");
        /* One emitter per block size: a single `main` with all 1408 rows
         * overflows the JVM's 64 KiB method limit (COMP002), so the rows are
         * split. */
        Map<Integer, List<String>> byBs = new LinkedHashMap<>();
        for (String line : originGolden().split("\n")) {
            String[] f = line.split(" ");
            byBs.computeIfAbsent(Integer.parseInt(f[0]), k -> new ArrayList<>()).add(line);
        }
        for (Map.Entry<Integer, List<String>> e : byBs.entrySet()) {
            sb.append("void emit").append(e.getKey()).append("() {\n");
            for (String line : e.getValue()) {
                String[] f = line.split(" ");
                int bs = Integer.parseInt(f[0]);
                int mr = Integer.parseInt(f[1]);
                int mc = Integer.parseInt(f[2]);
                int sx = Integer.parseInt(f[3]);
                int sy = Integer.parseInt(f[4]);
                sb.append("    println(\"").append(bs).append(' ').append(mr).append(' ')
                  .append(mc).append(' ').append(sx).append(' ').append(sy).append(" \" + ")
                  .append("av1CflChromaOriginUnitX(").append(mc).append(", av1BlockNum4x4Wide(")
                  .append(bs).append("), ").append(sx).append(") + \" \" + ")
                  .append("av1CflChromaOriginUnitY(").append(mr).append(", av1BlockNum4x4High(")
                  .append(bs).append("), ").append(sy).append("))\n");
            }
            sb.append("}\n\n");
        }
        sb.append("main() {\n");
        for (Integer bs : byBs.keySet()) sb.append("    emit").append(bs).append("()\n");
        sb.append("}\n");
        return sb.toString();
    }

    @Test
    void cflOriginGoldenShape() {
        assertEquals(22 * 4 * 4 * 2 * 2, originGolden().split("\n").length,
                "22 block sizes x 4 mi_row x 4 mi_col x 2 ssX x 2 ssY");
    }

    @Test
    void av1CflOriginOnJvm() throws Exception {
        assertEquals(originGolden(), runJvm(originProbe()));
    }

    @Test
    void av1CflOriginOnScript() throws Exception {
        Path root = tmp.resolve("script-origin");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), originProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(originGolden(), result.stdout().strip());
    }

    @Test
    void av1CflOriginOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(originGolden(), runNativeProbe("origin-x86", Target.NATIVE, originProbe()));
    }

    @Test
    void av1CflOriginOnNativeRiscv64() throws Exception {
        /* This probe is small, so §687 (the riscv64 >2 KiB frame limit) does NOT
         * bite — the cross face runs for real here. */
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(originGolden(), runCrossProbe("origin-riscv64", Target.NATIVE_RISCV64, originProbe()));
    }

    @Test
    void av1CflOriginOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(originGolden(), runCrossProbe("origin-aarch64", Target.NATIVE_AARCH64, originProbe()));
    }

    private String runNativeProbe(String tag, Target target, String code) throws Exception {
        Path root = tmp.resolve(tag);
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult r = driver.compile(root.resolve("Main.kf"), out, target);
            assertTrue(r.success(), () -> tag + " compile: " + r.diagnostics().getDiagnostics());
            return null;
        });
        return runBinary(out.resolve("Default/Main"), tag);
    }

    private String runCrossProbe(String tag, Target target, String code) throws Exception {
        Path root = tmp.resolve(tag);
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult r = driver.compile(root.resolve("Main.kf"), out, target);
            assertTrue(r.success(), () -> tag + " compile: " + r.diagnostics().getDiagnostics());
            return null;
        });
        String arch = tag.endsWith("riscv64") ? "riscv64" : "aarch64";
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, out.resolve("Default/Main"));
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), tag + " output: " + output);
        return output;
    }

    private static final String REGION_RESOURCE = "/av1_cfl_region_golden.txt.b64";
    private static final String REGION_SHA256 =
        "294da1f24d4245a1bbf9f6d783f876e086942bfed2965407a9a429df82c56b16";

    private static String regionGolden() {
        return resource(REGION_RESOURCE, REGION_SHA256);
    }

    /**
     * Independent check of the CFL REGION SELECTION (the border convention slice
     * 3am fixed). The oracle subsamples a deterministic LUMA PLANE from the plane
     * origin; the probe reads the same region out of a BORDERED buffer at
     * {@code origin + 1} — exactly what the driver does — so a mismatch means the
     * border arithmetic is wrong. The plane content is a function of the PLANE
     * coordinate, so a one-sample slip changes every value.
     */
    private static String regionProbe() {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1Cfl\n\n");
        sb.append("void emitRegion(Int[] buf, Int stride, Int cw, Int ch, Int ox, Int oy, ")
          .append("Int alpha, Int log2n, Int planeW, Int planeH) {\n");
        sb.append("    var ac = av1CflAcFromLuma(buf, stride, ox + 1, oy + 1, cw, ch, 1, 1, log2n, planeW, planeH)\n");
        sb.append("    var dc = new Int[cw * ch]\n");
        sb.append("    var k = 0\n");
        sb.append("    while (k < dc.size) {\n");
        sb.append("        dc[k] = 128\n");
        sb.append("        k = k + 1\n");
        sb.append("    }\n");
        sb.append("    var p = av1CflPredict(dc, ac, alpha, cw, ch, 8)\n");
        sb.append("    var h = 0\n");
        sb.append("    k = 0\n");
        sb.append("    while (k < p.size) {\n");
        sb.append("        h = (h * 31 + p[k]) & 65535\n");
        sb.append("        k = k + 1\n");
        sb.append("    }\n");
        sb.append("    println(\"C \" + cw + \" \" + ch + \" \" + ox + \" \" + oy + \" \" ")
          .append("+ alpha + \" \" + h)\n");
        sb.append("}\n\n");
        sb.append("main() {\n");
        sb.append("    var PW = 128\n");
        sb.append("    var stride = PW + 2\n");
        sb.append("    var buf = new Int[stride * (PW + 2)]\n");
        sb.append("    var py = 0\n");
        sb.append("    while (py < PW) {\n");
        sb.append("        var px = 0\n");
        sb.append("        while (px < PW) {\n");
        sb.append("            buf[(py + 1) * stride + px + 1] = ")
          .append("((px * 37) ^ (py * 53) ^ (px * py * 11)) & 255\n");
        sb.append("            px = px + 1\n");
        sb.append("        }\n");
        sb.append("        py = py + 1\n");
        sb.append("    }\n");
        for (String line : regionGolden().split("\n")) {
            String[] f = line.split(" ");
            int cw = Integer.parseInt(f[1]);
            int ch = Integer.parseInt(f[2]);
            int ox = Integer.parseInt(f[3]);
            int oy = Integer.parseInt(f[4]);
            int alpha = Integer.parseInt(f[5]);
            int log2n = Integer.numberOfTrailingZeros(cw * ch);
            sb.append("    emitRegion(buf, stride, ").append(cw).append(", ").append(ch)
              .append(", ").append(ox).append(", ").append(oy).append(", ")
              .append(alpha).append(", ").append(log2n).append(", PW, PW)\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    @Test
    void cflRegionGoldenShape() {
        assertEquals(7 * 5 * 6, regionGolden().split("\n").length,
                "7 transform sizes x 5 plane origins x 6 alphas");
    }

    @Test
    void av1CflRegionOnJvm() throws Exception {
        assertEquals(regionGolden(), runJvm(regionProbe()));
    }

    @Test
    void av1CflRegionOnScript() throws Exception {
        Path root = tmp.resolve("script-region");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), regionProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(regionGolden(), result.stdout().strip());
    }

    @Test
    void av1CflRegionOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(regionGolden(), runNativeProbe("region-x86", Target.NATIVE, regionProbe()));
    }

    @Test
    void av1CflRegionOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(regionGolden(), runCrossProbe("region-aarch64", Target.NATIVE_AARCH64, regionProbe()));
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
