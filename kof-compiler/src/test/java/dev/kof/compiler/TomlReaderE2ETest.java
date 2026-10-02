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
 * End-to-end coverage for the pure-Kof TOML configuration reader in
 * {@code libs/file} (kof.file slice 3.2, {@code D-KOF-FILE-GO}): comments,
 * dotted keys, quoted keys and values, escapes, integers with {@code _},
 * floats, booleans, single-line arrays, {@code [table]} headers, and the
 * explicit unsupported/duplicate/malformed diagnostics. Chunk size 4 forces
 * cross-chunk line reassembly. No compiler change. JVM, Native x86-64 +
 * riscv64 (qemu) and Script run the real golden; JS inherits the
 * {@code IOJS001} compile-time gap.
 */
class TomlReaderE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String TOML =
            "# service config\n"
            + "title = \"Kof app\"\n"
            + "uni = \"\\u0041\\u00e9\"\n"
            + "enabled = true\n"
            + "count = 1_000\n"
            + "ratio = 1.5\n"
            + "path = 'C:\\raw'\n"
            + "empty = \"\"\n"
            + "\n"
            + "[service]\n"
            + "name = \"api\"\n"
            + "port = 8080\n"
            + "tags = [\"web\", 'edge', \"a,b\"]\n"
            + "\n"
            + "[service.limits]\n"
            + "rps = 25\n"
            + "\n"
            + "[a.b]\n"
            + "c = 1\n"
            + "d.e = 2\n";
    private static final String TOML_GOLDEN = String.join("\n",
            "title=Kof app",
            "uni=Aé",
            "path=C:\\raw",
            "empty=",
            "ratio=1.5",
            "enabled=true",
            "count=1000",
            "port=8080",
            "name=api",
            "rps=25",
            "dotted=2",
            "ratio_ok=true",
            "tags=3",
            "tag0=web",
            "tag1=edge",
            "tag2=a,b",
            "kind=float",
            "has=true",
            "missing=0");

    @Test
    void tomlParsesOnJvm() throws Exception {
        Path src = tmp.resolve("app.toml");
        Files.writeString(src, TOML, StandardCharsets.UTF_8);
        assertEquals(TOML_GOLDEN, runJvm(parseProbe(src)));
    }

    @Test
    void tomlDuplicateKeyThrows() throws Exception {
        Path src = tmp.resolve("dup.toml");
        Files.writeString(src, "a = 1\na = 2\n", StandardCharsets.UTF_8);
        assertEquals("TOML: duplicate key 'a' at line 2", runJvm(errorProbe(src)));
    }

    @Test
    void tomlArrayOfTablesThrows() throws Exception {
        Path src = tmp.resolve("aot.toml");
        Files.writeString(src, "[[x]]\n", StandardCharsets.UTF_8);
        assertEquals("TOML: array-of-tables is not supported (line 1)", runJvm(errorProbe(src)));
    }

    @Test
    void tomlInlineTableThrows() throws Exception {
        Path src = tmp.resolve("inline.toml");
        Files.writeString(src, "a = {x = 1}\n", StandardCharsets.UTF_8);
        assertEquals("TOML: inline tables are not supported (line 1)", runJvm(errorProbe(src)));
    }

    @Test
    void tomlMalformedLineThrows() throws Exception {
        Path src = tmp.resolve("bad.toml");
        Files.writeString(src, "name\n", StandardCharsets.UTF_8);
        assertEquals("TOML: malformed line 1: missing '='", runJvm(errorProbe(src)));
    }

    @Test
    void tomlMissingValueThrows() throws Exception {
        Path src = tmp.resolve("novalue.toml");
        Files.writeString(src, "a =\n", StandardCharsets.UTF_8);
        assertEquals("TOML: malformed line 1: missing value", runJvm(errorProbe(src)));
    }

    @Test
    void tomlEmptyFileYieldsNoKeys() throws Exception {
        Path src = tmp.resolve("empty.toml");
        Files.writeString(src, "", StandardCharsets.UTF_8);
        assertEquals("0", runJvm(countProbe(src)));
    }

    @Test
    void tomlParsesOnScript() throws Exception {
        Path root = tmp.resolve("script-toml");
        Files.createDirectories(root);
        Path src = root.resolve("app.toml");
        Files.writeString(src, TOML, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Main.kf"), parseProbe(src));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(TOML_GOLDEN, result.stdout().strip());
    }

    @Test
    void tomlParsesOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("app-x86.toml");
        Files.writeString(src, TOML, StandardCharsets.UTF_8);
        assertEquals(TOML_GOLDEN, runNativeX86(parseProbe(src)));
    }

    @Test
    void tomlParsesOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path src = tmp.resolve("app-riscv.toml");
        Files.writeString(src, TOML, StandardCharsets.UTF_8);
        assertEquals(TOML_GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, parseProbe(src)));
    }

    @Test
    void tomlReadRangeGapOnJsApplies() throws Exception {
        Path root = tmp.resolve("js-toml");
        Files.createDirectories(root);
        Path src = root.resolve("app.toml");
        Files.writeString(src, TOML, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Main.kf"), parseProbe(src));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(!result.success(), "JS must refuse the missing kof.io readRange binding (R6)");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("IOJS001"),
                () -> "expected the explicit IOJS001 gap diagnostic, got: " + diag);
    }

    private static String parseProbe(Path src) {
        return """
            import file.Toml

            String show(String label, String? v) {
                if (v == null) {
                    return label + "=<none>"
                }
                return label + "=" + v
            }

            String flag(Bool b) {
                if (b) {
                    return "true"
                }
                return "false"
            }

            main() {
                var t = Toml("%s", 4)
                println(show("title", t.getString("title")))
                println(show("uni", t.getString("uni")))
                println(show("path", t.getString("path")))
                println(show("empty", t.getString("empty")))
                println(show("ratio", t.getRaw("ratio")))
                println("enabled=" + flag(t.getBool("enabled", false)))
                println("count=" + t.getInt("count", -1))
                println("port=" + t.getInt("service.port", -1))
                println(show("name", t.getString("service.name")))
                println("rps=" + t.getInt("service.limits.rps", -1))
                println("dotted=" + t.getInt("a.b.d.e", -1))
                println("ratio_ok=" + flag(t.getDouble("ratio", 0.0) == 1.5))
                var tags = t.getArray("service.tags")
                if (tags != null) {
                    println("tags=" + tags.size())
                    var i = 0
                    while (i < tags.size()) {
                        println("tag" + i + "=" + tags.get(i))
                        i = i + 1
                    }
                }
                println(show("kind", t.kindOf("ratio")))
                println("has=" + flag(t.has("service.name")))
                println("missing=" + t.getInt("nope", 0))
            }
            """.formatted(path(src));
    }

    private static String errorProbe(Path src) {
        return """
            import file.Toml

            main() {
                try {
                    var t = Toml("%s", 4)
                    println("no error")
                } catch (String e) {
                    println(e)
                }
            }
            """.formatted(path(src));
    }

    private static String countProbe(Path src) {
        return """
            import file.Toml

            main() {
                var t = Toml("%s", 4)
                println(t.keys().size())
            }
            """.formatted(path(src));
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
        if (Files.isRegularFile(fromRepository.resolve("Toml.kf"))) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/file").normalize();
        if (Files.isRegularFile(fromModule.resolve("Toml.kf"))) return fromModule;

        throw new IllegalStateException("libs/file not found from " + workingDirectory);
    }
}
