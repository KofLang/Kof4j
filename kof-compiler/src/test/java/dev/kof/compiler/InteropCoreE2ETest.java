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
 * Fatia 13 do plano Kof Connector Ecosystem ({@code D-CONNECTORS-GO}): a fachada
 * do Interop Core {@code interop.InteropCore} (plano §2, "camada unificadora") —
 * enumera os manifests de um diretório, valida cada um (capabilities/ownership/
 * types/SPI via {@code ConnectorManifest.validate}) e produz um audit determinístico
 * (nomes ordenados), exatamente o que um `kof connector` de listagem imprimiria.
 * Honesto (R6): connector inválido sai como {@code INVALID: <diagnóstico>}, nunca
 * escondido. Sem mudança no compilador. JVM + Native x86-64 + Script; JS herda a
 * lacuna {@code IOJS001} do `kof.io`.
 */
class InteropCoreE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String JAVA_MANIFEST =
            "name = \"kof-java\"\nlanguage = \"java\"\nversion = \"0.5.0\"\nabi = \"jni\"\nruntime = \"jvm\"\n";
    private static final String CC_MANIFEST =
            "name = \"kof-cc\"\nlanguage = \"c\"\nversion = \"1.0\"\nabi = \"c\"\nruntime = \"native\"\n";
    private static final String BAD_MANIFEST =
            "name = \"kof-bad\"\nlanguage = \"c\"\nversion = \"1.0\"\nabi = \"c\"\nruntime = \"native\"\n"
            + "capabilities = [\"teleport\"]\n";

    private static final String GOLDEN = String.join("\n",
            "count=3",
            "names=3",
            "audit=",
            "kof-bad c c native INVALID: CONNECTOR: unknown capability teleport",
            "kof-cc c c native valid",
            "kof-java java jni jvm valid");

    @Test
    void coreAuditRunsOnJvm() throws Exception {
        assertEquals(GOLDEN, runJvm(probe(coreDir())));
    }

    @Test
    void coreAuditRunsOnScript() throws Exception {
        Path dir = coreDir();
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void coreAuditRunsOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        assertEquals(GOLDEN, runNativeX86(probe(coreDir())));
    }

    @Test
    void coreAuditRequiresKofIoOnJs() throws Exception {
        Path dir = coreDir();
        Path root = tmp.resolve("js");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), root.resolve("out"), Target.JS));
        assertFalse(result.success(), "JS must refuse the missing kof.io directory/readRange binding (R6)");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("IOJS001"), () -> "expected IOJS001, got: " + diag);
    }

    // Two valid manifests plus one invalid (unknown capability) plus a non-manifest.
    private Path coreDir() throws Exception {
        Path dir = tmp.resolve("connectors");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("kof-cc.toml"), CC_MANIFEST, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("kof-java.toml"), JAVA_MANIFEST, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("kof-bad.toml"), BAD_MANIFEST, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("notes.txt"), "not a connector\n", StandardCharsets.UTF_8);
        return dir;
    }

    private static String probe(Path dir) {
        return """
            import interop.InteropCore

            main() {
                var core = InteropCore("%s")
                println("count=" + core.connectorCount())
                println("names=" + core.names().size())
                println("audit=")
                println(core.audit())
            }
            """.formatted(path(dir));
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
                    InteropCoreE2ETest.class.getClassLoader())) {
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
            assertTrue(result.success(), () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        Process process = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private CompilationResult compile(Path source, Path out, Target target) {
        return driver.compile(source, out, target);
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
