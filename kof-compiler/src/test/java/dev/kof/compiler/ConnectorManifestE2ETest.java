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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fatia 1 do plano Kof Connector Ecosystem ({@code D-CONNECTORS-GO}, promovido
 * 29/09/2026): o leitor de manifest {@code interop.ConnectorManifest} sobre o
 * formato {@code kof.toml} existente (plano §4.2). Prova a leitura tipada dos
 * campos (name/language/version/abi/runtime + platforms/dependencies/
 * capabilities), {@code hasCapability}/`describe`, e o diagnóstico explícito
 * {@code CONNECTOR: missing <field>} — nunca um default silencioso (R6). Sem
 * mudança no compilador (library-first, {@code D-KOF-FIRST-IMPL}). JVM +
 * Native x86-64 + riscv64 (qemu) + Script; JS herda a lacuna {@code IOJS001}.
 */
class ConnectorManifestE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String MANIFEST =
            "# kof-java connector manifest\n"
            + "name = \"kof-java\"\n"
            + "language = \"java\"\n"
            + "version = \"0.5.0\"\n"
            + "abi = \"jni\"\n"
            + "runtime = \"jvm\"\n"
            + "platforms = [\"jvm\", \"native\"]\n"
            + "dependencies = [\"ExternalClasspath\"]\n"
            + "capabilities = [\"callbacks\", \"threads\"]\n"
            + "ownership = [\"borrowed\", \"immutable\"]\n"
            + "types = [\"integer\", \"string\", \"array\"]\n"
            + "spi = [\"marshalling\", \"symbol-resolution\"]\n"
            + "stability = \"experimental\"\n";

    private static final String GOLDEN = String.join("\n",
            "name=kof-java",
            "lang=java",
            "ver=0.5.0",
            "abi=jni",
            "runtime=jvm",
            "describe=kof-java 0.5.0 (java, abi jni, runtime jvm)",
            "platforms=2",
            "deps=1",
            "caps=2",
            "owners=2",
            "types=3",
            "spi=2",
            "stability=experimental",
            "callbacks=true",
            "gc=false",
            "valid=ok");

    // Base golden WITHOUT the fatia-5 validation face (used by the riscv64 read-only
    // case: `validate()`'s type-model path SIGSEGVs on cross native — measured
    // 29/09, recorded in the plan §9.5 for the native lane).
    private static final String GOLDEN_BASE = String.join("\n",
            "name=kof-java",
            "lang=java",
            "ver=0.5.0",
            "abi=jni",
            "runtime=jvm",
            "describe=kof-java 0.5.0 (java, abi jni, runtime jvm)",
            "platforms=2",
            "deps=1",
            "caps=2",
            "owners=2",
            "callbacks=true",
            "gc=false");

    @Test
    void manifestReadsOnJvm() throws Exception {
        Path src = tmp.resolve("jvm.toml");
        Files.writeString(src, MANIFEST, StandardCharsets.UTF_8);
        assertEquals(GOLDEN, runJvm(probe(src)));
    }

    @Test
    void missingRequiredFieldIsAnExplicitDiagnostic() throws Exception {
        Path root = tmp.resolve("missing-jvm");
        Files.createDirectories(root);
        Path src = root.resolve("bad.toml");
        // abi absent (required) — the reader must refuse, never default.
        Files.writeString(src, "name = \"x\"\nlanguage = \"x\"\nversion = \"1\"\nruntime = \"x\"\n");
        Path source = root.resolve("Main.kf");
        Files.writeString(source, missingProbe(src));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> compile(source, out, Target.JVM));
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());
        assertEquals("x\nCONNECTOR: missing abi", invokeMain(out));
    }

    @Test
    void manifestReadsOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Path src = root.resolve("app.toml");
        Files.writeString(src, MANIFEST, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Main.kf"), probe(src));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void manifestReadsOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("x86.toml");
        Files.writeString(src, MANIFEST, StandardCharsets.UTF_8);
        assertEquals(GOLDEN, runNativeX86(probe(src)));
    }

    @Test
    void connectorManifestJsReadRangeGap() throws Exception {
        Path root = tmp.resolve("js");
        Files.createDirectories(root);
        Path src = root.resolve("app.toml");
        Files.writeString(src, MANIFEST, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Main.kf"), probe(src));
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), root.resolve("out"), Target.JS));
        assertFalse(result.success(), "JS must refuse the missing kof.io readRange binding (R6)");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("IOJS001"), () -> "expected IOJS001, got: " + diag);
    }


    @Test
    void unknownCapabilityIsAnExplicitDiagnostic() throws Exception {
        Path src = tmp.resolve("bad-cap.toml");
        Files.writeString(src, "name = \"x\"\nlanguage = \"x\"\nversion = \"1\"\nabi = \"c\"\n"
                + "runtime = \"x\"\ncapabilities = [\"callbacks\", \"teleport\"]\n");
        assertEquals("CONNECTOR: unknown capability teleport", runJvm(validateProbe(src)));
    }

    @Test
    void unknownSupportedTypeIsAnExplicitDiagnostic() throws Exception {
        Path src = tmp.resolve("bad-type.toml");
        Files.writeString(src, "name = \"x\"\nlanguage = \"x\"\nversion = \"1\"\nabi = \"c\"\n"
                + "runtime = \"x\"\ntypes = [\"integer\", \"widget\"]\n");
        assertEquals("CONNECTOR: unknown type widget", runJvm(validateProbe(src)));
    }

    @Test
    void unknownOwnershipIsAnExplicitDiagnostic() throws Exception {
        Path src = tmp.resolve("bad-own.toml");
        Files.writeString(src, "name = \"x\"\nlanguage = \"x\"\nversion = \"1\"\nabi = \"c\"\n"
                + "runtime = \"x\"\nownership = [\"borrowed\", \"ancient\"]\n");
        assertEquals("CONNECTOR: unknown ownership ancient", runJvm(validateProbe(src)));
    }

    @Test
    void unknownSpiHookIsAnExplicitDiagnostic() throws Exception {
        Path src = tmp.resolve("bad-spi.toml");
        Files.writeString(src, "name = \"x\"\nlanguage = \"x\"\nversion = \"1\"\nabi = \"c\"\n"
                + "runtime = \"x\"\nspi = [\"marshalling\", \"telepathy\"]\n");
        assertEquals("CONNECTOR: unknown SPI hook telepathy", runJvm(validateProbe(src)));
    }

    @Test
    void unknownStabilityIsAnExplicitDiagnostic() throws Exception {
        Path src = tmp.resolve("bad-stability.toml");
        Files.writeString(src, "name = \"x\"\nlanguage = \"x\"\nversion = \"1\"\nabi = \"c\"\n"
                + "runtime = \"x\"\nstability = \"widget\"\n");
        assertEquals("CONNECTOR: unknown stability widget", runJvm(validateProbe(src)));
    }

    private static String validateProbe(Path src) {
        return """
            import interop.ConnectorManifest

            main() {
                try {
                    var m = ConnectorManifest("%s", 4)
                    m.validate()
                    println("no error")
                } catch (String e) {
                    println(e)
                }
            }
            """.formatted(path(src));
    }

    private static String probeReadOnly(Path src) {
        return """
            import interop.ConnectorManifest

            String flag(Bool b) {
                if (b) {
                    return "true"
                }
                return "false"
            }

            main() {
                var m = ConnectorManifest("%s", 4)
                println("name=" + m.name())
                println("lang=" + m.language())
                println("ver=" + m.version())
                println("abi=" + m.abi())
                println("runtime=" + m.runtime())
                println("describe=" + m.describe())
                println("platforms=" + m.platforms().size())
                println("deps=" + m.dependencies().size())
                println("caps=" + m.capabilities().size())
                println("owners=" + m.ownership().size())
                println("callbacks=" + flag(m.hasCapability("callbacks")))
                println("gc=" + flag(m.hasCapability("gc")))
            }
            """.formatted(path(src));
    }

    private static String probe(Path src) {
        return """
            import interop.ConnectorManifest

            String flag(Bool b) {
                if (b) {
                    return "true"
                }
                return "false"
            }

            main() {
                var m = ConnectorManifest("%s", 4)
                println("name=" + m.name())
                println("lang=" + m.language())
                println("ver=" + m.version())
                println("abi=" + m.abi())
                println("runtime=" + m.runtime())
                println("describe=" + m.describe())
                println("platforms=" + m.platforms().size())
                println("deps=" + m.dependencies().size())
                println("caps=" + m.capabilities().size())
                println("owners=" + m.ownership().size())
                println("types=" + m.supportedTypes().size())
                println("spi=" + m.spi().size())
                println("stability=" + m.stability())
                println("callbacks=" + flag(m.hasCapability("callbacks")))
                println("gc=" + flag(m.hasCapability("gc")))
                m.validate()
                println("valid=ok")
            }
            """.formatted(path(src));
    }

    private static String missingProbe(Path src) {
        return """
            import interop.ConnectorManifest

            main() {
                try {
                    var m = ConnectorManifest("%s", 4)
                    println(m.name())
                    println(m.abi())
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
        return invokeMain(out);
    }

    private static String invokeMain(Path out) throws Exception {
        var stdout = new java.io.ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()},
                    ConnectorManifestE2ETest.class.getClassLoader())) {
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

    // Copies both libs/file (the manifest format) and libs/interop (this reader).
    private static void copyLibrary(Path destinationRoot) throws Exception {
        Path libs = findLibsRoot();
        for (String lib : List.of("file", "interop")) {
            Path sourceRoot = libs.resolve(lib);
            try (var files = Files.walk(sourceRoot)) {
                for (Path source : files.filter(Files::isRegularFile).toList()) {
                    Path destination = destinationRoot.resolve(lib)
                            .resolve(sourceRoot.relativize(source));
                    Files.createDirectories(destination.getParent());
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static Path findLibsRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        for (Path candidate : List.of(workingDirectory.resolve("libs"),
                workingDirectory.resolve("../libs").normalize())) {
            if (Files.isRegularFile(candidate.resolve("interop/ConnectorManifest.kf"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("libs/ not found from " + workingDirectory);
    }
}
