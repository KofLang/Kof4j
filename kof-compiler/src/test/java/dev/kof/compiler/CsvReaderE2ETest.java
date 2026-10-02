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
 * End-to-end coverage for the pure-Kof CSV/TSV reader and writer in
 * {@code libs/file} (kof.file slice 2.2, {@code D-KOF-FILE-GO}). Proves
 * character-level parsing over {@code TextStream} — quoted fields with
 * delimiters and newlines, {@code ""} escapes, {@code \n}/{@code \r\n}
 * records — as a streaming reader (chunk size 4 forces multi-byte and record
 * boundaries) and the inverse {@code CsvWriter} encoder (quotes only when
 * needed, first write truncates) that round-trips through the reader, with no
 * new syntax and no compiler change. JVM, Native x86-64 + riscv64 (qemu) and
 * Script run the real golden; JS inherits the {@code IOJS001} compile-time gap.
 */
class CsvReaderE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String CSV = "id,name,note\n1,\"Doe, John\",\"line1\nline2\"\n2,\"He said \"\"hi\"\"\",plain\n3,,\n";
    private static final String CSV_GOLDEN = "3\nid|name|note\n3\n1|Doe, John|line1\nline2\n3\n2|He said \"hi\"|plain\n3\n3||";
    private static final String TSV = "a\tb\tc\n1\t2\t3\n";
    private static final String TSV_GOLDEN = "3\na|b|c\n3\n1|2|3";
    private static final String NO_TRAILING_NEWLINE = "x,y\nz,w";
    private static final String NO_TRAILING_GOLDEN = "2\nx|y\n2\nz|w";
    private static final String WRITE_RAW =
            "id,name,note\n1,\"Doe, John\",\"line1\nline2\"\n2,\"He said \"\"hi\"\"\",plain\n3,,\n";

    @Test
    void csvReadsQuotedFieldsAcrossChunks() throws Exception {
        Path src = tmp.resolve("data.csv");
        Files.writeString(src, CSV, StandardCharsets.UTF_8);
        assertEquals(CSV_GOLDEN, runJvm(csvProbe(src)));
    }

    @Test
    void csvWithoutTrailingNewlineReadsLastRow() throws Exception {
        Path src = tmp.resolve("no-trailing.csv");
        Files.writeString(src, NO_TRAILING_NEWLINE, StandardCharsets.UTF_8);
        assertEquals(NO_TRAILING_GOLDEN, runJvm(csvProbe(src)));
    }

    @Test
    void tsvUsesTabDelimiter() throws Exception {
        Path src = tmp.resolve("data.tsv");
        Files.writeString(src, TSV, StandardCharsets.UTF_8);
        assertEquals(TSV_GOLDEN, runJvm(tsvProbe(src)));
    }

    @Test
    void emptyFileYieldsNoRows() throws Exception {
        Path src = tmp.resolve("empty.csv");
        Files.writeString(src, "", StandardCharsets.UTF_8);
        assertEquals("", runJvm(csvProbe(src)));
    }

    @Test
    void csvOnScript() throws Exception {
        Path root = tmp.resolve("script-csv");
        Files.createDirectories(root);
        Path src = root.resolve("data.csv");
        Files.writeString(src, CSV, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Main.kf"), csvProbe(src));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(CSV_GOLDEN, result.stdout().strip());
    }

    @Test
    void csvOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("data-x86.csv");
        Files.writeString(src, CSV, StandardCharsets.UTF_8);
        assertEquals(CSV_GOLDEN, runNativeX86(csvProbe(src)));
    }

    @Test
    void csvOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path src = tmp.resolve("data-riscv.csv");
        Files.writeString(src, CSV, StandardCharsets.UTF_8);
        assertEquals(CSV_GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, csvProbe(src)));
    }

    @Test
    void readRangeGapOnJsAppliesToCsv() throws Exception {
        Path root = tmp.resolve("js-csv");
        Files.createDirectories(root);
        Path src = root.resolve("data.csv");
        Files.writeString(src, CSV, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Main.kf"), csvProbe(src));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(!result.success(), "JS must refuse the missing kof.io readRange binding (R6)");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("IOJS001"),
                () -> "expected the explicit IOJS001 gap diagnostic, got: " + diag);
    }

    // ----- CsvWriter (slice 2.2): encoding + reader/writer round-trip -----

    @Test
    void writerRoundTripsThroughReader() throws Exception {
        Path dst = tmp.resolve("roundtrip.csv");
        assertEquals(CSV_GOLDEN, runJvm(writerProbe(dst, "','", true)));
    }

    @Test
    void writerEncodesQuotingExactly() throws Exception {
        Path dst = tmp.resolve("raw.csv");
        assertEquals(WRITE_RAW.strip(), runJvm(writerRawProbe(dst)));
    }

    @Test
    void writerTsvRoundTrips() throws Exception {
        Path dst = tmp.resolve("roundtrip.tsv");
        assertEquals(CSV_GOLDEN, runJvm(writerProbe(dst, "'\\t'", true)));
    }

    @Test
    void writerOnScript() throws Exception {
        Path root = tmp.resolve("script-writer");
        Files.createDirectories(root);
        Path dst = root.resolve("roundtrip.csv");
        Files.writeString(root.resolve("Main.kf"), writerProbe(dst, "','", true));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(CSV_GOLDEN, result.stdout().strip());
    }

    @Test
    void writerOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path dst = tmp.resolve("roundtrip-x86.csv");
        assertEquals(CSV_GOLDEN, runNativeX86(writerProbe(dst, "','", true)));
    }

    @Test
    void writerOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path dst = tmp.resolve("roundtrip-riscv.csv");
        assertEquals(CSV_GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, writerProbe(dst, "','", true)));
    }

    @Test
    void writeTextGapOnJsAppliesToCsvWriter() throws Exception {
        Path root = tmp.resolve("js-writer");
        Files.createDirectories(root);
        Path dst = root.resolve("roundtrip.csv");
        Files.writeString(root.resolve("Main.kf"), writerProbe(dst, "','", true));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(!result.success(), "JS must refuse the missing kof.io readRange binding (R6)");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("IOJS001"),
                () -> "expected the explicit IOJS001 gap diagnostic, got: " + diag);
    }

    private static String csvProbe(Path src) {
        return probe(src, "','");
    }

    private static String tsvProbe(Path src) {
        return probe(src, "'\\t'");
    }

    private static String writerProbe(Path dst, String delimiter, boolean readBack) {
        String read = readBack ? """
                var reader = CsvReader("%s", %s, 4)
                var row = reader.nextRow()
                while (row != null) {
                    if (row != null) {
                        println(row.size())
                        println(join(row))
                    }
                    row = reader.nextRow()
                }
            """.formatted(path(dst), delimiter) : "";
        return """
            import file.Csv

            String join(List<String> cells) {
                var line = ""
                var i = 0
                while (i < cells.size()) {
                    if (i > 0) {
                        line = line + "|"
                    }
                    line = line + cells.get(i)
                    i = i + 1
                }
                return line
            }

            main() {
                var writer = CsvWriter("%s", %s)
                writer.writeRow(listOf("id", "name", "note"))
                writer.writeRow(listOf("1", "Doe, John", "line1\\nline2"))
                writer.writeRow(listOf("2", "He said \\"hi\\"", "plain"))
                writer.writeRow(listOf("3", "", ""))
            %s
            }
            """.formatted(path(dst), delimiter, read);
    }

    private static String writerRawProbe(Path dst) {
        return """
            import file.Csv

            main() {
                var writer = CsvWriter("%s", ',')
                writer.writeRow(listOf("id", "name", "note"))
                writer.writeRow(listOf("1", "Doe, John", "line1\\nline2"))
                writer.writeRow(listOf("2", "He said \\"hi\\"", "plain"))
                writer.writeRow(listOf("3", "", ""))
                println(File("%s").readText())
            }
            """.formatted(path(dst), path(dst));
    }

    private static String path(Path p) {
        return p.toString().replace('\\', '/');
    }

    private static String probe(Path src, String delimiter) {
        return """
            import file.Csv

            String join(List<String> cells) {
                var line = ""
                var i = 0
                while (i < cells.size()) {
                    if (i > 0) {
                        line = line + "|"
                    }
                    line = line + cells.get(i)
                    i = i + 1
                }
                return line
            }

            main() {
                var reader = CsvReader("%s", %s, 4)
                var row = reader.nextRow()
                while (row != null) {
                    if (row != null) {
                        println(row.size())
                        println(join(row))
                    }
                    row = reader.nextRow()
                }
            }
            """.formatted(src.toString().replace('\\', '/'), delimiter);
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
        if (Files.isRegularFile(fromRepository.resolve("Csv.kf"))) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/file").normalize();
        if (Files.isRegularFile(fromModule.resolve("Csv.kf"))) return fromModule;

        throw new IllegalStateException("libs/file not found from " + workingDirectory);
    }
}
