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
 * Fatia 15 do plano Kof Connector Ecosystem ({@code D-CONNECTORS-GO}): o gerador
 * do manifest canônico {@code interop.ConnectorTemplate} (plano §8, metade
 * pura-Kof do "connector generator") — renderiza o `kof.toml` (§4.2) que o Core
 * lê/valida. Prova: (a) o texto renderizado bate (alvo-neutro, todos os alvos);
 * (b) round-trip REAL — escreve com `File.writeText`, relê com
 * {@code ConnectorManifest} e `validate()` passa. JS herda a lacuna {@code IOJS001}
 * no round-trip; `render()` é sem IO. Sem mudança no compilador.
 */
class ConnectorTemplateE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String GOLDEN_RENDER = String.join("\n",
            "name = \"kof-cc\"",
            "language = \"c\"",
            "version = \"1.0\"",
            "abi = \"c\"",
            "runtime = \"native\"",
            "platforms = [\"jvm\", \"native\"]",
            "capabilities = [\"callbacks\"]",
            "ownership = [\"borrowed\"]",
            "types = [\"integer\", \"string\"]",
            "spi = [\"marshalling\"]",
            "stability = \"experimental\"");

    private static final String GOLDEN_ROUNDTRIP = String.join("\n",
            "name=kof-rt",
            "lang=c",
            "ver=2.0",
            "platforms=1",
            "caps=1",
            "owners=1",
            "valid=ok");

    @Test
    void templateRendersOnJvm() throws Exception {
        assertEquals(GOLDEN_RENDER, runJvm(renderProbe()));
    }

    @Test
    void templateRendersOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), renderProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN_RENDER, result.stdout().strip());
    }

    @Test
    void templateRendersOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        assertEquals(GOLDEN_RENDER, runNativeX86(renderProbe()));
    }

    @Test
    void templateRoundTripsOnJvm() throws Exception {
        Path manifest = tmp.resolve("rt-jvm/kof-connector.toml");
        Files.createDirectories(manifest.getParent());
        assertEquals(GOLDEN_ROUNDTRIP, runJvm(roundTripProbe(manifest)));
    }

    @Test
    void templateRoundTripRequiresKofIoOnJs() throws Exception {
        Path manifest = tmp.resolve("rt-js/kof-connector.toml");
        Files.createDirectories(manifest.getParent());
        Path root = tmp.resolve("js");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), roundTripProbe(manifest));
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), root.resolve("out"), Target.JS));
        assertFalse(result.success(), "JS must refuse the missing kof.io binding (R6)");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("IOJS001"), () -> "expected IOJS001, got: " + diag);
    }

    private static String renderProbe() {
        return """
            import interop.ConnectorTemplate

            main() {
                var t = ConnectorTemplate("kof-cc", "c", "1.0", "c", "native")
                t.platform("jvm")
                t.platform("native")
                t.capability("callbacks")
                t.ownershipConcept("borrowed")
                t.type("integer")
                t.type("string")
                t.hook("marshalling")
                t.stabilityTier("experimental")
                println(t.render())
            }
            """;
    }

    private static String roundTripProbe(Path manifest) {
        return """
            import interop.ConnectorTemplate
            import interop.ConnectorManifest

            main() {
                var t = ConnectorTemplate("kof-rt", "c", "2.0", "c", "native")
                t.platform("native")
                t.capability("callbacks")
                t.ownershipConcept("borrowed")
                File("%s").writeText(t.render())
                var m = ConnectorManifest("%s")
                m.validate()
                println("name=" + m.name())
                println("lang=" + m.language())
                println("ver=" + m.version())
                println("platforms=" + m.platforms().size())
                println("caps=" + m.capabilities().size())
                println("owners=" + m.ownership().size())
                println("valid=ok")
            }
            """.formatted(path(manifest), path(manifest));
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
                    ConnectorTemplateE2ETest.class.getClassLoader())) {
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
