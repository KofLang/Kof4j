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
 * Fatia 16 do plano Kof Connector Ecosystem ({@code D-CONNECTORS-GO}, 2.º
 * connector oficial = C ABI per {@code D-CONNECTORS}): o perfil declarativo do
 * connector C {@code interop.CAbiConnector} (plano §5.2/§9.16) — compõe o Core
 * (foreign module + custos visíveis + tipos suportados + tier de estabilidade)
 * numa descrição validada. Honesto (R6): custo/tipo/tier desconhecido lança pelo
 * vocabulário do Core; símbolo não declarado lança {@code FOREIGN: unknown symbol
 * <x>}. O round-trip de runtime espera as fatias de compilador (§9.16 A/B). Sem
 * mudança no compilador; sem IO → JVM + Script + JS + Native x86-64.
 */
class CAbiConnectorE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String GOLDEN = String.join("\n",
            "symbols=2",
            "costs=2",
            "types=2",
            "stability=experimental",
            "foreign=kof_add",
            "describe=c-abi connector kof-cc module=foreign module kof-cc library=libkofcc "
                    + "abi=c ownership=borrowed symbols=2 costs=2 types=2 stability=experimental");

    @Test
    void cabiProfileMapsOnJvm() throws Exception {
        assertEquals(GOLDEN, runJvm(profileProbe()));
    }

    @Test
    void cabiProfileMapsOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), profileProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void cabiProfileMapsOnJs() throws Exception {
        // No file IO here, so even JS has no gap: this library is target-neutral.
        assertEquals(GOLDEN, runJs(profileProbe()));
    }

    @Test
    void cabiProfileMapsOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        assertEquals(GOLDEN, runNativeX86(profileProbe()));
    }

    @Test
    void cabiUnknownCostIsAnExplicitDiagnostic() throws Exception {
        assertEquals("INTEROP: unknown cost widget", runJvm(unknownCostProbe()));
    }

    @Test
    void cabiUnknownTypeIsAnExplicitDiagnostic() throws Exception {
        assertEquals("INTEROP: unknown type widget", runJvm(unknownTypeProbe()));
    }

    @Test
    void cabiUnknownSymbolIsAnExplicitDiagnostic() throws Exception {
        assertEquals("FOREIGN: unknown symbol nope", runJvm(unknownSymbolProbe()));
    }

    private static String profileProbe() {
        return """
            import interop.CAbiConnector

            main() {
                var cc = CAbiConnector("kof-cc", "libkofcc", "borrowed")
                cc.declare("add", "kof_add", "(Int,Int)->Int")
                cc.declare("mul", "kof_mul", "(Int,Int)->Int")
                cc.cost("copy")
                cc.cost("crossing")
                cc.supportedType("integer")
                cc.supportedType("string")
                cc.stabilityTier("experimental")
                println("symbols=" + cc.symbolCount())
                println("costs=" + cc.costCount())
                println("types=" + cc.typeCount())
                println("stability=" + cc.stabilityName())
                println("foreign=" + cc.foreignNameOf("add"))
                println("describe=" + cc.describe())
            }
            """;
    }

    private static String unknownCostProbe() {
        return """
            import interop.CAbiConnector

            main() {
                var cc = CAbiConnector("kof-cc", "libkofcc", "borrowed")
                try {
                    cc.cost("widget")
                    println("no error")
                } catch (String e) {
                    println(e)
                }
            }
            """;
    }

    private static String unknownTypeProbe() {
        return """
            import interop.CAbiConnector

            main() {
                var cc = CAbiConnector("kof-cc", "libkofcc", "borrowed")
                try {
                    cc.supportedType("widget")
                    println("no error")
                } catch (String e) {
                    println(e)
                }
            }
            """;
    }

    private static String unknownSymbolProbe() {
        return """
            import interop.CAbiConnector

            main() {
                var cc = CAbiConnector("kof-cc", "libkofcc", "borrowed")
                try {
                    println(cc.foreignNameOf("nope"))
                } catch (String e) {
                    println(e)
                }
            }
            """;
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
                    CAbiConnectorE2ETest.class.getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runJs(String code) throws Exception {
        Path root = tmp.resolve("js-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(result.success(), () -> "JS compile: " + result.diagnostics().getDiagnostics());
        var buf = new java.io.ByteArrayOutputStream();
        int rc = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), buf,
                java.io.InputStream.nullInputStream(), buf);
        assertEquals(0, rc, "JS exit, output: " + buf);
        return buf.toString(StandardCharsets.UTF_8).strip();
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
        Path sourceRoot = findLibraryRoot();
        try (var files = Files.walk(sourceRoot)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path destination = destinationRoot.resolve("interop")
                        .resolve(sourceRoot.relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path findLibraryRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        for (Path candidate : List.of(workingDirectory.resolve("libs/interop"),
                workingDirectory.resolve("../libs/interop").normalize())) {
            if (Files.isRegularFile(candidate.resolve("ConnectorManifest.kf"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("libs/interop not found from " + workingDirectory);
    }
}
