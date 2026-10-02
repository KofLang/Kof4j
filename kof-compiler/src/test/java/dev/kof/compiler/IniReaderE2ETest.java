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
 * End-to-end coverage for the pure-Kof INI configuration reader in
 * {@code libs/file} (kof.file slice 3.1, {@code D-KOF-FILE-GO}): sections,
 * global keys, {@code =} and {@code :} separators, full-line comments,
 * quoted values, last-wins duplicates, empty values, and the malformed-line /
 * malformed-header errors. Chunk size 4 forces cross-chunk line reassembly.
 * No compiler change. JVM, Native x86-64 + riscv64 (qemu) and Script run the
 * real golden; JS inherits the {@code IOJS001} compile-time gap.
 */
class IniReaderE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String INI =
            "; comment\n"
            + "# comment 2\n"
            + "name = kof\n"
            + "version=0.5.0-beta\n"
            + "empty =\n"
            + "\n"
            + "[db]\n"
            + "host = localhost\n"
            + "port: 5432\n"
            + "user = \"admin\"\n"
            + "user = root\n"
            + "\n"
            + "[cache]\n"
            + "ttl = 60\n";
    private static final String INI_GOLDEN = String.join("\n",
            "name=kof",
            "version=0.5.0-beta",
            "empty=",
            "db.host=localhost",
            "db.port=5432",
            "db.user=root",
            "cache.ttl=60",
            "3",
            "has_db_host=true",
            "missing=false");

    @Test
    void parsesSectionsOnJvm() throws Exception {
        Path src = tmp.resolve("app.ini");
        Files.writeString(src, INI, StandardCharsets.UTF_8);
        assertEquals(INI_GOLDEN, runJvm(parseProbe(src)));
    }

    @Test
    void malformedLineThrows() throws Exception {
        Path src = tmp.resolve("bad-line.ini");
        Files.writeString(src, "name\n", StandardCharsets.UTF_8);
        assertEquals("INI: malformed line 1: name", runJvm(errorProbe(src)));
    }

    @Test
    void malformedSectionHeaderThrows() throws Exception {
        Path src = tmp.resolve("bad-header.ini");
        Files.writeString(src, "[db\n", StandardCharsets.UTF_8);
        assertEquals("INI: malformed section header at line 1", runJvm(errorProbe(src)));
    }

    @Test
    void iniParsesOnScript() throws Exception {
        Path root = tmp.resolve("script-ini");
        Files.createDirectories(root);
        Path src = root.resolve("app.ini");
        Files.writeString(src, INI, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Main.kf"), parseProbe(src));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(INI_GOLDEN, result.stdout().strip());
    }

    @Test
    void iniParsesOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("app-x86.ini");
        Files.writeString(src, INI, StandardCharsets.UTF_8);
        assertEquals(INI_GOLDEN, runNativeX86(parseProbe(src)));
    }

    @Test
    void iniParsesOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path src = tmp.resolve("app-riscv.ini");
        Files.writeString(src, INI, StandardCharsets.UTF_8);
        assertEquals(INI_GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, parseProbe(src)));
    }

    @Test
    void readRangeGapOnJsAppliesToIni() throws Exception {
        Path root = tmp.resolve("js-ini");
        Files.createDirectories(root);
        Path src = root.resolve("app.ini");
        Files.writeString(src, INI, StandardCharsets.UTF_8);
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
            import file.Ini

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
                var ini = Ini("%s", 4)
                println(show("name", ini.get("", "name")))
                println(show("version", ini.get("", "version")))
                println(show("empty", ini.get("", "empty")))
                println(show("db.host", ini.get("db", "host")))
                println(show("db.port", ini.get("db", "port")))
                println(show("db.user", ini.get("db", "user")))
                println(show("cache.ttl", ini.get("cache", "ttl")))
                println(ini.keysOf("db").size())
                println("has_db_host=" + flag(ini.has("db", "host")))
                println("missing=" + flag(ini.has("x", "y")))
            }
            """.formatted(path(src));
    }

    private static String errorProbe(Path src) {
        return """
            import file.Ini

            main() {
                try {
                    var ini = Ini("%s", 4)
                    var v = ini.get("", "name")
                    println("no error")
                } catch (String e) {
                    println(e)
                }
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
        if (Files.isRegularFile(fromRepository.resolve("Ini.kf"))) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/file").normalize();
        if (Files.isRegularFile(fromModule.resolve("Ini.kf"))) return fromModule;

        throw new IllegalStateException("libs/file not found from " + workingDirectory);
    }
}
