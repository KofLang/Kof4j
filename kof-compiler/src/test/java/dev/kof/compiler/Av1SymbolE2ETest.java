package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.kof.compiler.Av1SymbolSupport.caseProbe;
import static dev.kof.compiler.Av1SymbolSupport.fixtures;
import static dev.kof.compiler.Av1SymbolSupport.golden;
import static dev.kof.compiler.Av1SymbolSupport.javaDecode;
import static dev.kof.compiler.Av1SymbolSupport.javaRealFacts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for AVIF slice 3a (image-vision front, plan §34): the
 * pure-Kof AV1 symbol / entropy decoder {@code libs/image/Av1Symbol.kf}
 * implements the AV1 spec §9.2/§9.3 range coder (init/read_symbol/read_bool/
 * read_literal/exit). Fixtures are byte streams produced by libaom's entropy
 * encoder with libaom's decoder as the round-trip oracle (dev host 04/10);
 * the pinned golden is the symbol sequence. A second independent Java reader
 * ({@link Av1SymbolSupport#javaDecode}) agrees fact-for-fact. The real-file
 * test decodes the first booleans/literals of the host AVIF's tile payload.
 */
class Av1SymbolE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void av1SymbolOnJvm() throws Exception {
        assertEquals(golden(), runJvm(caseProbe()));
    }

    @Test
    void av1SymbolOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), caseProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(golden(), result.stdout().strip());
    }

    @Test
    void av1SymbolOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(golden(), runNativeX86(caseProbe()));
    }

    @Test
    void av1SymbolOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(golden(), runCrossCode("riscv64", Target.NATIVE_RISCV64, caseProbe()));
    }

    @Test
    void av1SymbolOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(golden(), runCrossCode("aarch64", Target.NATIVE_AARCH64, caseProbe()));
    }

    @Test
    void symbolDecoderOnJs() throws Exception {
        Path root = tmp.resolve("js");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), caseProbe());
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

    @Test
    void javaReaderAgreesWithKof() {
        // second independent implementation (plain Java spec walk) vs the
        // pinned libaom golden
        StringBuilder sb = new StringBuilder();
        for (var f : fixtures()) {
            sb.append(f.name());
            for (int s : javaDecode(Av1SymbolSupport.bytesFor(f.name()), f.cdf(), f.nsyms(), f.update(), f.syms().length)) {
                sb.append(' ').append(s);
            }
            sb.append('\n');
        }
        assertEquals(golden(), sb.toString().strip());
    }

    @Test
    void realAvifTileEntropyDecodes() throws Exception {
        Path avif = Path.of("/home/mel/.vscode/extensions/openai.chatgpt-26.930.31730-linux-x64"
                + "/webview/assets/fallen-pet-28c3cddba20a.avif");
        Assumptions.assumeTrue(Files.isRegularFile(avif), "host AVIF fixture absent");
        // The tile payload is extracted by the pure-Kof chain (slice 2n); the
        // probe decodes its first 64 booleans and 8 literals and prints them.
        String probe = """
                import image.AvifGroup
                import image.Av1Symbol

                main() {
                    var ps = readAvifTilePayloads("%s")
                    var tile = ps[0]
                    var d = Av1Symbol(tile)
                    var sb = "BOOLS"
                    var i = 0
                    while (i < 64) {
                        sb = sb + " " + d.readBool()
                        i = i + 1
                    }
                    sb = sb + " LIT"
                    var j = 0
                    while (j < 8) {
                        sb = sb + " " + d.readLiteral(8)
                        j = j + 1
                    }
                    println(sb)
                }
                """.formatted(avif);
        String kof = runJvm(probe);
        // independent libaom-derived expectation (real.c on the same payload)
        String expected = "BOOLS 1 0 0 1 0 1 1 1 1 1 0 1 0 0 0 0 0 0 0 1 1 1 1 1 0 1 1 1 1 1 0 0"
                + " 1 1 1 0 0 1 0 0 1 0 1 0 1 1 0 0 0 1 1 0 1 1 0 0 1 0 1 1 1 1 0 0"
                + " LIT 236 22 144 184 174 78 131 116";
        assertEquals(expected, kof);
        // the Java second reader must agree with the C oracle too
        int[] tile = Av1SymbolSupport.realTileBytes();
        assertEquals(expected, javaRealFacts(tile, 64, 8));
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
