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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the pure-Kof streaming library in {@code libs/file}
 * (kof.file slice 1, {@code D-KOF-FILE-GO}). Proves incremental chunk reading
 * and constant-memory chunked copy over the existing {@code File.readRange}
 * primitive — no new syntax, no compiler change.
 *
 * <p>Slice 2: the library is measured on every target. JVM, Script and Native
 * (x86-64 + riscv64/aarch64 under qemu) run the real golden; JS has no host
 * binding for {@code readRange} and must refuse HONESTLY at compile time
 * (R6 {@code IOJS001}), never emit an undefined {@code kof_io_read_range}
 * import that only fails at runtime.
 */
class FileLibraryE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String GOLDEN = "3\n10\ntrue\n10\n10\n0123456789\n0123456789";

    // aé€\nb\r\n\nlast — 2-byte é and 3-byte € straddle 2-byte chunks, plus a
    // CRLF line, an empty line and a final line without terminator.
    private static final String TEXT_CONTENT = "a\u00e9\u20ac\nb\r\n\nlast";
    private static final String TEXT_GOLDEN = "a\u00e9\u20ac\nb\n\nlast\ntrue";

    // Non-BMP (surrogate pair) round-trips on JVM and Script. Native stores
    // strings as UTF-8 and cannot represent a surrogate pair (§43, WTF-8
    // storage not yet built), so astral output there is a measured divergence.
    private static final String ASTRAL_CONTENT = "x\uD83D\uDE00y\nz";
    private static final String ASTRAL_GOLDEN = "x\uD83D\uDE00y\nz\ntrue";

    @Test
    void streamsInChunksAndCopiesWithoutLoadingTheWholeFile() throws Exception {
        Path src = tmp.resolve("source.txt");
        Path dst = tmp.resolve("dest.txt");
        String output = runJvm(probe(src, dst));
        assertEquals(GOLDEN, output);
        assertEquals(Files.readString(src), Files.readString(dst),
                "chunked copy must be byte-identical");
    }

    @Test
    void emptyFileEndsImmediately() throws Exception {
        Path src = tmp.resolve("empty.txt");
        String output = runJvm("""
            import file.FileStream

            main() {
                File("%s").writeText("")
                var stream = FileStream("%s", 16)
                var chunk = stream.readChunk()
                println(chunk == null)
            }
            """.formatted(src.toString().replace('\\', '/'),
                src.toString().replace('\\', '/')));
        assertEquals("true", output, "empty file must yield null on the first read");
    }

    @Test
    void streamsOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path root = tmp.resolve("native");
        Path src = root.resolve("source.txt");
        Path dst = root.resolve("dest.txt");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe(src, dst));
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(), () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), "native binary must exist");
        assertEquals(GOLDEN, runBinary(binary));
    }

    @Test
    void streamsOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runCross("riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void streamsOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runCross("aarch64", Target.NATIVE_AARCH64));
    }

    @Test
    void readRangeIsAnHonestGapOnJs() throws Exception {
        Path root = tmp.resolve("js");
        Path src = root.resolve("source.txt");
        Path dst = root.resolve("dest.txt");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe(src, dst));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(!result.success(), "JS must refuse the missing kof.io binding (R6)");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("IOJS001"),
                () -> "expected the explicit IOJS001 gap diagnostic, got: " + diag);
    }

    @Test
    void streamsOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Path src = root.resolve("source.txt");
        Path dst = root.resolve("dest.txt");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe(src, dst));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void textStreamDecodesUtf8AcrossChunks() throws Exception {
        Path src = tmp.resolve("text.txt");
        Files.writeString(src, TEXT_CONTENT, StandardCharsets.UTF_8);
        assertEquals(TEXT_GOLDEN, runJvm(textProbe(src)));
    }

    @Test
    void textStreamAstralRoundTripsOnJvm() throws Exception {
        Path src = tmp.resolve("astral.txt");
        Files.writeString(src, ASTRAL_CONTENT, StandardCharsets.UTF_8);
        assertEquals(ASTRAL_GOLDEN, runJvm(textProbe(src)));
    }

    @Test
    void textStreamAstralRoundTripsOnScript() throws Exception {
        Path root = tmp.resolve("script-astral");
        Files.createDirectories(root);
        Path src = root.resolve("astral.txt");
        Files.writeString(src, ASTRAL_CONTENT, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Main.kf"), textProbe(src));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(ASTRAL_GOLDEN, result.stdout().strip());
    }

    @Test
    void textStreamOnScript() throws Exception {
        Path root = tmp.resolve("script-text");
        Files.createDirectories(root);
        Path src = root.resolve("text.txt");
        Files.writeString(src, TEXT_CONTENT, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Main.kf"), textProbe(src));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(TEXT_GOLDEN, result.stdout().strip());
    }

    @Test
    void textStreamOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("text-x86.txt");
        Files.writeString(src, TEXT_CONTENT, StandardCharsets.UTF_8);
        assertEquals(TEXT_GOLDEN, runNativeX86(textProbe(src)));
    }

    @Test
    void textStreamOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path src = tmp.resolve("text-riscv.txt");
        Files.writeString(src, TEXT_CONTENT, StandardCharsets.UTF_8);
        assertEquals(TEXT_GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, textProbe(src)));
    }

    private static String probe(Path src, Path dst) {
        return """
            import file.FileStream

            main() {
                File("%s").writeText("0123456789")

                var stream = FileStream("%s", 4)
                var chunks = 0
                var total = 0
                var chunk = stream.readChunk()
                while (chunk != null) {
                    chunks = chunks + 1
                    total = total + chunk.length
                    chunk = stream.readChunk()
                }
                println(chunks)
                println(total)
                println(stream.done())
                println(stream.position())

                var copied = copyStream("%s", "%s", 3)
                println(copied)
                println(File("%s").readText())
                println(File("%s").readText())
            }
            """.formatted(src.toString().replace('\\', '/'),
                src.toString().replace('\\', '/'),
                src.toString().replace('\\', '/'),
                dst.toString().replace('\\', '/'),
                src.toString().replace('\\', '/'),
                dst.toString().replace('\\', '/'));
    }

    private static String textProbe(Path src) {
        return """
            import file.TextStream

            main() {
                var stream = TextStream("%s", 2)
                var line = stream.nextLine()
                while (line != null) {
                    println(line)
                    line = stream.nextLine()
                }
                println(stream.done())
            }
            """.formatted(src.toString().replace('\\', '/'));
    }

    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> compile(source, out, Target.JVM));
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

    private String runCross(String arch, Target target) throws Exception {
        Path root = tmp.resolve("cross-" + arch);
        Path src = root.resolve("source.txt");
        Path dst = root.resolve("dest.txt");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe(src, dst));
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = compile(root.resolve("Main.kf"), out, target);
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

    private static void copyLibrary(Path destinationRoot) throws Exception {
        Path sourceRoot = findLibraryRoot();
        try (var files = Files.walk(sourceRoot)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path destination = destinationRoot.resolve("file")
                        .resolve(sourceRoot.relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path findLibraryRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path fromRepository = workingDirectory.resolve("libs/file");
        if (Files.isRegularFile(fromRepository.resolve("FileStream.kf"))) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/file").normalize();
        if (Files.isRegularFile(fromModule.resolve("FileStream.kf"))) return fromModule;

        throw new IllegalStateException("libs/file not found from " + workingDirectory);
    }
}
