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
 * End-to-end coverage for the pure-Kof JSON Lines / NDJSON seam in
 * {@code libs/file} (kof.file slice 2.3, {@code D-KOF-FILE-GO}): each non-blank
 * line is one JSON document read through {@code TextStream} (chunk size 4
 * forces cross-chunk line reassembly), decoded by the caller with
 * {@code json.decode<T>} at a CONCRETE type, and written back by
 * {@code JsonLinesWriter}. No compiler change; the reader deliberately stays
 * untyped because {@code json.decode<T>} over an open type parameter is the
 * compiler defect {@code §538}. JVM, Native x86-64 + riscv64 (qemu) and Script
 * run the real golden; JS inherits the {@code IOJS001} compile-time gap.
 *
 * <p>Records decode on JVM but not on riscv64 ({@code kof_json_find_value} is
 * absent from the cross runtime — the pre-existing {@code NATIVE002}-stdlib
 * family), so the all-target golden decodes {@code List<Int>} per line and a
 * JVM-only case adds record decoding.
 */
class JsonLinesE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String JSONL =
            "[1,2]\n[3,4]\n   \t\n[5,6]\n";
    private static final String JSONL_GOLDEN = "3\n21";
    private static final String WRITE_RAW = "[1,2]\n[3,4]\n[5,6]\n";

    @Test
    void readsDocumentsSkippingBlankLines() throws Exception {
        Path src = tmp.resolve("data.jsonl");
        Files.writeString(src, JSONL, StandardCharsets.UTF_8);
        assertEquals(JSONL_GOLDEN, runJvm(read1Probe(src)));
    }

    @Test
    void readsRecordDocumentsOnJvm() throws Exception {
        Path src = tmp.resolve("records.jsonl");
        Files.writeString(src, "{\"x\":1,\"y\":2}\n{\"x\":3,\"y\":4}\n\n{\"x\":5,\"y\":6}\n",
                StandardCharsets.UTF_8);
        assertEquals(JSONL_GOLDEN, runJvm(recordProbe(src)));
    }

    @Test
    void writerRoundTrips() throws Exception {
        Path dst = tmp.resolve("roundtrip.jsonl");
        assertEquals("3\n7\n11", runJvm(writerProbe(dst)));
    }

    @Test
    void writerEncodesExactly() throws Exception {
        Path dst = tmp.resolve("raw.jsonl");
        assertEquals(WRITE_RAW.strip(), runJvm(writerRawProbe(dst)));
    }

    @Test
    void readsOnScript() throws Exception {
        Path root = tmp.resolve("script-jsonl");
        Files.createDirectories(root);
        Path src = root.resolve("data.jsonl");
        Files.writeString(src, JSONL, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Main.kf"), read1Probe(src));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(JSONL_GOLDEN, result.stdout().strip());
    }

    @Test
    void readsOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("data-x86.jsonl");
        Files.writeString(src, JSONL, StandardCharsets.UTF_8);
        assertEquals(JSONL_GOLDEN, runNativeX86(read1Probe(src)));
    }

    @Test
    void readsOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path src = tmp.resolve("data-riscv.jsonl");
        Files.writeString(src, JSONL, StandardCharsets.UTF_8);
        assertEquals(JSONL_GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, read1Probe(src)));
    }

    @Test
    void readRangeGapOnJsAppliesToJsonLines() throws Exception {
        Path root = tmp.resolve("js-jsonl");
        Files.createDirectories(root);
        Path src = root.resolve("data.jsonl");
        Files.writeString(src, JSONL, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Main.kf"), read1Probe(src));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(!result.success(), "JS must refuse the missing kof.io readRange binding (R6)");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("IOJS001"),
                () -> "expected the explicit IOJS001 gap diagnostic, got: " + diag);
    }

    private static String read1Probe(Path src) {
        return """
            import file.JsonLines

            main() {
                var reader = JsonLinesReader("%s", 4)
                var doc = reader.nextJson()
                var count = 0
                var sum = 0
                while (doc != null) {
                    if (doc != null) {
                        var nums = json.decode<List<Int>>(doc)
                        var i = 0
                        while (i < nums.size()) {
                            sum = sum + nums.get(i)
                            i = i + 1
                        }
                        count = count + 1
                    }
                    doc = reader.nextJson()
                }
                println(count)
                println(sum)
            }
            """.formatted(path(src));
    }

    private static String recordProbe(Path src) {
        return """
            import file.JsonLines

            record Pt(Int x, Int y)

            main() {
                var reader = JsonLinesReader("%s", 4)
                var doc = reader.nextJson()
                var count = 0
                var sum = 0
                while (doc != null) {
                    if (doc != null) {
                        var p = json.decode<Pt>(doc)
                        sum = sum + p.x + p.y
                        count = count + 1
                    }
                    doc = reader.nextJson()
                }
                println(count)
                println(sum)
            }
            """.formatted(path(src));
    }

    private static String writerProbe(Path dst) {
        return """
            import file.JsonLines

            main() {
                var writer = JsonLinesWriter("%s")
                writer.writeJson(json.encode(listOf(1, 2)))
                writer.writeJson(json.encode(listOf(3, 4)))
                writer.writeJson(json.encode(listOf(5, 6)))

                var reader = JsonLinesReader("%s", 4)
                var doc = reader.nextJson()
                while (doc != null) {
                    if (doc != null) {
                        var nums = json.decode<List<Int>>(doc)
                        var lineSum = 0
                        var i = 0
                        while (i < nums.size()) {
                            lineSum = lineSum + nums.get(i)
                            i = i + 1
                        }
                        println(lineSum)
                    }
                    doc = reader.nextJson()
                }
            }
            """.formatted(path(dst), path(dst));
    }

    private static String writerRawProbe(Path dst) {
        return """
            import file.JsonLines

            main() {
                var writer = JsonLinesWriter("%s")
                writer.writeJson(json.encode(listOf(1, 2)))
                writer.writeJson(json.encode(listOf(3, 4)))
                writer.writeJson(json.encode(listOf(5, 6)))
                println(File("%s").readText())
            }
            """.formatted(path(dst), path(dst));
    }

    private static String path(Path p) {
        return p.toString().replace('\\', '/');
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
        if (Files.isRegularFile(fromRepository.resolve("JsonLines.kf"))) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/file").normalize();
        if (Files.isRegularFile(fromModule.resolve("JsonLines.kf"))) return fromModule;

        throw new IllegalStateException("libs/file not found from " + workingDirectory);
    }
}
