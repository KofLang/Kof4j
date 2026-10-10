package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the VP8 (lossy WebP) DCT/WHT coefficient decoder
 * ({@code libs/image/Vp8Coeffs.kf}, RFC 6386 §13) — slice 4 of the pure-Kof VP8
 * chain ({@code D-WEBP-LOSSY-PURE-KOF}).
 *
 * <p>The oracle is an <b>independent RFC §7.3/§13 coefficient decoder</b> (a
 * small Python implementation run offline) over real libwebp-generated lossy
 * files: a DC-only 16x16 ({@code flat16}), an all-{@code B_PRED} 32x32
 * ({@code diag32}), a 64x64 with {@code mb_no_skip_coeff=1} and many skipped
 * macroblocks ({@code skip64}) and a 64x64 mixing coefficient categories
 * ({@code cat64}), plus a 16x128 frame encoded by libvpx with 2, 4 and 8 token
 * partitions ({@code np2}/{@code np4}/{@code np8}) whose coefficient golden is
 * identical to the one-partition encode. For every macroblock the Kof decoder
 * must reproduce the non-empty-block count and the signed/absolute coefficient
 * sums exactly.
 * The oracle's per-position band lookup was cross-checked against the RFC
 * §20.16 reference {@code tokens.c} ({@code prob += bands_x[c]}, a single
 * mapping) — the Kof decoder applies the band mapping exactly once.
 * Proven on JVM + Native x86-64 + riscv64 (qemu) + Script; no compiler change.
 */
class Vp8CoeffE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String FLAT16_HEX =
            "52494646240000005745425056503820180000005001009d012a1000100000c01225a400047400"
          + "00fe000000";
    private static final String DIAG32_HEX =
            "524946465800000057454250565038204c0000009004009d012a200020002e25188c462122"
          + "22220200244b480005cf044784367991338898ded3a8a046b6753b8000fefffe13039ed077"
          + "8e57d36a60c4b734e4a2442744e950c19c65de000000";
    private static final String SKIP64_HEX =
            "524946465c000000574542505650382050000000b004009d012a400040003ed168b052a82624"
          + "a2a20801001a0969062807eff89d000857b2d956263da7d79b57400000feeb36fffe84d8f6a2"
          + "bffff424ffe26cff89b3e115b4f4b331cb99d6de02000000";
    private static final String CAT64_HEX =
            "524946468a00000057454250565038207e0000003005009d012a400040003f71b6ce5dbbb1"
          + "29bfac1d5803f02e096206700c40ffa23f042099e275feffeb03ffffe4c86ffbf000feefea"
          + "3c87dbba1ae81df7cea6b0ba4b7d01db3e51344d74e50de3adef74cd294734473b63de1363"
          + "d6c97e017c2accc9adb06c756c3ebda3959f373f50db6ff0739c41c1136c0872340000";
    // Multi-token-partition fixtures (libvpx 1.14, `VP8E_SET_TOKEN_PARTITIONS`):
    // a 16x128 frame (8 macroblock rows) encoded with 2, 4 and 8 token
    // partitions. The row-to-partition map (`row % n`) is the only difference
    // from the single-partition encode; libwebp decodes all four to the same
    // picture, and the coefficient golden is identical to the 1-partition
    // encode of the same content.
    private static final String NP2_HEX =
            "52494646c40000005745425056503820b8000000b007009d012a100080000007"
          + "0885858885848842020051a9f5e7f3ecdc9288a816d05f80703a496cddbb317a"
          + "e0815e964971e56bf54b37ad8cf803e7647f3830af3e14912042003e0000feff"
          + "ff02f75a8c052e10cf80f675b0a046923dffe64c27813e7025c3c367e3c8c0a1"
          + "d3af38d846f96a57efff90c7fffef7383ffe6ee7b7589b31106d1f00d4a9fedf"
          + "ed5fb99618a1052c9b3c2ffef2f450051e11bb323514df98a2b23d68cd426993"
          + "191688dec1cddf111e943a00";
    private static final String NP4_HEX =
            "52494646cc0000005745425056503820c0000000b007009d012a100080000007"
          + "0885858885848882020051a9f5e7f3ecdc9288a816d05f80703a496cddbb317a"
          + "e0815e964971e56bf54b37ad8cf803e7647f3830af3e14912042001d00001800"
          + "00220000feffff02f75a8c052e10cf80f675b0aba107d123172836a1723b76b3"
          + "00d4a9fedfed5fb99618a1052a3806b8bb5afc8dabafe63d007cbdb158480c8d"
          + "1135a590fc5b36c33ffc863ffff7b9c1fff3773dbac4d9888368f8be7d1c3fef"
          + "2f450051e11bb323614fb78a09df5e2372eff000";
    private static final String NP8_HEX =
            "52494646e00000005745425056503820d3000000b007009d012a100080000007"
          + "08858588858488c2020051a9f5e7f3ecdc9288a816d05f80703a496cddbb317a"
          + "e0815e964971e56bf54b37ad8cf803e7647f3830af3e14912042001100000d00"
          + "000c00000f00000d00000d0000180000feffff02f75a8c052e10cf80f675b090"
          + "00d4a9fedfed5fb99618a10524007cbdb158480c8d1135a56800be7d1c3fef2f"
          + "450051e11bb3233400d4d9d8422518f3a07fffdbb598890019d0476d1fd031db"
          + "708000d4d9d8648afff218ffffdee707ffcddcf6eb1366220da3e0d4d7f4a96a"
          + "3bf3d71bc4530000";
    // Dense 20x28 fixture (libwebp, quality 100): the pre-fix per-coefficient
    // `listOf` zig-zag rebuild corrupted the riscv64 decode (§547).
    private static final String BP2028_HEX =
            "52494646140400005745425056503820080400001019009d012a14001c0000000025b00274ca"
          + "11d41e31f901f8cdd10bac5da5fd95dc11205d35fe1fed57ddcff00fc6eea0ffee1f8e5c003f"
          + "4ebfc3ff72f7e3f166f700fd33eb00f400fd00f429feabfd27e033f513fbbff63f80dfe43fcf"
          + "7ef82f19f271fa97fb2ba003e817c43f113f68ffd1ea08fe71f867f94d9c65f19fe4bf8d7fcc"
          + "ff5bb6407f08fe61f8b3b211fc63f8d7e3dff63f77afcbff077cc5bf8c7e19fd007f08fe0ffc"
          + "abf11bfad7fcfe508fd08fe127a6c36acc7b222b8737592490fa7efae4ef1fd6b98fefd4b3eb"
          + "3000fef0574bb6e2183e2cd42aa7d3eeb26a1fb32873cd3d6867fea05ec88a8ac8e5e743ae33"
          + "a09f4228cbcc667b7a0d8ad593af67d7328c30302af4c4fee89dbaccc3cfb7b80b811c1ffc2c"
          + "a2a5c8c6672ccd4d4fd673ced138db89a4c0b5500c59fd600dd2cdffd1e101097dba502c3f39"
          + "e58d0825b25f969c9f9c2873c8656470fcb0fe90aa7e1c66503644b436045ddd5ebf2d2f7b68"
          + "3684427144cf3bec7fff94a7ead6110a6687821203ffc3ae33438efb497d0168e970276ef835"
          + "3d354140ee781f000b3ece6959b6a0374d21b97a2702cc2c57a81fc85768a5c456ba2616ab84"
          + "d6e1ffde9bfde0f66c29c3f699d3f2982277f85755a652e045fbb867ec114a6a50fc90c1138e"
          + "c33c77e4d5a7ad521656e6ffe4b57410173040dd55849eabc733c8274f39dd6c119f1ea7b1ab"
          + "ffaa7fe047a578b08df07d13fff90f241d11c6ca991abba7ba4ab45a7f0e922ac9758cc87f07"
          + "c4ff71a2a4a1a8e7bf2be7cf7d94edd22f2b72f5ac100cd0aeab4cc787ab64f165e4dbfffc53"
          + "b9b6edf6d4cb5b6d596be3336c84571ffd06aa6b08eda4ec26834fb413e0b3029cc03bfc1e1f"
          + "74d953d93b485d6a7962156fd0785a9d5332a2a397d0e81806c8cf2b7cd59f5ce9b07b36b1ea"
          + "4672c2bb87d8f378f3977a4a6fab397e421d8dad73ad9ae4dff3b61fff77f060c9ce2634aadf"
          + "e049241cef1e821c98de70a890262217f448f5fdea133ffbae8fa7ab95a4c5584785b82e7c2d"
          + "4a9b3f9d5fa068dd1e47837ec670bbcd42afae492595f29eacc42392ea5112a177f9e85a25ed"
          + "e1dba0b555fffff441718c978618d11ad088fe28b4e163a45d9f2830f7304a05ada86c26cccb"
          + "3b7a4ba5dcfde7c13c1b3e28fdfec01fcbc7b6d8c69c6bffd34251f8fffec8d8b0a202ccd562"
          + "a445edd08b0121e79d11bcb83d5d4c9d9b72db0998eeb344d77a59d7749d2ed82d6a2f469066"
          + "f754cbda69e9ef72922070c8bab32ad5bfd3d28b7e568c9df9512e0748a7966e71b0cdf42a8c"
          + "1db4f4b94789ac8a49ff9f9b00df7ffefa22f954afe908d849a72545f9d42768bca0fe3b7fd3"
          + "4799819705084ebe801d041cfe3141d57fe86bd5c07c73f77d0b77f97b4769adc0042dbc5ee8"
          + "f59a64c705502a1b981b679a6d7e941eda844f06fcec80000000";

    private static final String GOLDEN =
              "flat16 17:-3:3\n"
            + "diag32 6:-59:99,5:-1:31,1:1:1,2:0:2\n"
            + "skip64 25:21:135,18:-43:43,17:-42:42,17:-42:42,18:-2:2,0:0:0,0:0:0,"
            + "0:0:0,0:0:0,0:0:0,0:0:0,0:0:0,0:0:0,0:0:0,0:0:0,0:0:0\n"
            + "cat64 23:-69:73,21:10:14,23:8:16,21:10:14,24:-6:22,23:-7:13,22:4:8,21:-4:6,"
            + "25:-6:24,23:2:10,21:-5:5,20:-5:5,24:-4:20,23:-7:11,19:3:3,18:2:2\n"
            + "bp2028 24:-291:4807,22:-173:1847,23:338:3792,14:369:1483\n"
            + "np2 14:-96:152,14:0:58,13:2:48,14:-94:150,13:-5:51,15:17:53,15:39:137,15:-5:49\n"
            + "np4 14:-96:152,14:0:58,13:2:48,14:-94:150,13:-5:51,15:17:53,15:39:137,15:-5:49\n"
            + "np8 14:-96:152,14:0:58,13:2:48,14:-94:150,13:-5:51,15:17:53,15:39:137,15:-5:49";

    @Test
    void vp8CoeffsOnJvm() throws Exception {
        Path root = tmp.resolve("jvm-vp8c");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runJvm(root, probe(dir)));
    }

    @Test
    void vp8CoeffsOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path root = tmp.resolve("x86-vp8c");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runNativeX86(root, probe(dir)));
    }

    @Test
    void vp8CoeffsOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path root = tmp.resolve("riscv-vp8c");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, root, probe(dir)));
    }

    @Test
    void vp8CoeffsOnScript() throws Exception {
        Path root = tmp.resolve("script-vp8c");
        Files.createDirectories(root);
        Path dir = writeFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    private Path writeFixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.write(dir.resolve("flat16.webp"), hex(FLAT16_HEX));
        Files.write(dir.resolve("diag32.webp"), hex(DIAG32_HEX));
        Files.write(dir.resolve("skip64.webp"), hex(SKIP64_HEX));
        Files.write(dir.resolve("cat64.webp"), hex(CAT64_HEX));
        Files.write(dir.resolve("bp2028.webp"), hex(BP2028_HEX));
        Files.write(dir.resolve("np2.webp"), hex(NP2_HEX));
        Files.write(dir.resolve("np4.webp"), hex(NP4_HEX));
        Files.write(dir.resolve("np8.webp"), hex(NP8_HEX));
        return dir;
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String probe(Path dir) {
        String base = RasterDecodeFixtures.path(dir);
        return "import image.Vp8Frame\n"
             + "import image.Vp8Coeffs\n\n"
             + "String coeffs(Int[] bytes) {\n"
             + "    var c = Vp8Coeffs(vp8FrameFromWebp(bytes))\n"
             + "    var cc = c.coeffs()\n"
             + "    var n = cc.length / 400\n"
             + "    var out = \"\"\n"
             + "    var mb = 0\n"
             + "    while (mb < n) {\n"
             + "        var eob = 0\n"
             + "        var ci = 0\n"
             + "        while (ci < 25) {\n"
             + "            if (c.eobAt(mb, ci) > 0) {\n"
             + "                eob = eob + 1\n"
             + "            }\n"
             + "            ci = ci + 1\n"
             + "        }\n"
             + "        var vs = 0\n"
             + "        var va = 0\n"
             + "        var k = 0\n"
             + "        while (k < 400) {\n"
             + "            var v = cc[mb * 400 + k]\n"
             + "            vs = vs + v\n"
             + "            if (v < 0) {\n"
             + "                va = va - v\n"
             + "            } else {\n"
             + "                va = va + v\n"
             + "            }\n"
             + "            k = k + 1\n"
             + "        }\n"
             + "        if (mb > 0) {\n"
             + "            out = out + \",\"\n"
             + "        }\n"
             + "        out = out + eob + \":\" + vs + \":\" + va\n"
             + "        mb = mb + 1\n"
             + "    }\n"
             + "    return out\n"
             + "}\n\n"
             + "main() {\n"
             + "    println(\"flat16 \" + coeffs(File(\"" + base + "/flat16.webp\").readBytes()))\n"
             + "    println(\"diag32 \" + coeffs(File(\"" + base + "/diag32.webp\").readBytes()))\n"
             + "    println(\"skip64 \" + coeffs(File(\"" + base + "/skip64.webp\").readBytes()))\n"
             + "    println(\"cat64 \" + coeffs(File(\"" + base + "/cat64.webp\").readBytes()))\n"
             + "    println(\"bp2028 \" + coeffs(File(\"" + base + "/bp2028.webp\").readBytes()))\n"
             + "    println(\"np2 \" + coeffs(File(\"" + base + "/np2.webp\").readBytes()))\n"
             + "    println(\"np4 \" + coeffs(File(\"" + base + "/np4.webp\").readBytes()))\n"
             + "    println(\"np8 \" + coeffs(File(\"" + base + "/np8.webp\").readBytes()))\n"
             + "}\n";
    }

    private String runJvm(Path root, String code) throws Exception {
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> driver.compile(source, out, Target.JVM));
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
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

    private String runNativeX86(Path root, String code) throws Exception {
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runBinary(out.resolve("Default/Main"));
    }

    private String runCrossCode(String arch, Target target, Path root, String code) throws Exception {
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, target);
            assertTrue(result.success(),
                    () -> arch + " compile: " + result.diagnostics().getDiagnostics());
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

    private static String runBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
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

    private <T> T withLibrary(Path root, Vp8Action<T> action) throws Exception {
        Path lib = root.resolve("kof-install/lib/kof-libs/image");
        Path sourceRoot = libraryRoot();
        try (var files = Files.walk(sourceRoot)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path destination = lib.resolve(sourceRoot.relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
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
    private interface Vp8Action<T> {
        T get() throws Exception;
    }

    private static Path libraryRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path fromRepository = workingDirectory.resolve("libs/image");
        if (Files.isDirectory(fromRepository)) return fromRepository;
        Path fromModule = workingDirectory.resolve("../libs/image").normalize();
        if (Files.isDirectory(fromModule)) return fromModule;
        throw new IllegalStateException("libs/image not found from " + workingDirectory);
    }
}
