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
 * Fatia 6 do plano Kof Connector Ecosystem ({@code D-CONNECTORS-GO}): o contrato
 * de posse/tempo de vida de interop {@code interop.InteropOwnership} (plano §3.2)
 * — o Core define o vocabulário (owned/borrowed/shared/opaque/immutable/mutable)
 * e o connector declara o que suporta; nenhum connector inventa sua própria
 * regra. Honesto por construção (R6): conceito desconhecido →
 * {@code INTEROP: unknown ownership <x>}; nunca um chute. O manifesto do
 * connector passa a validar via esta fonte única. Sem mudança no compilador;
 * sem IO → roda em JVM + Script + JS + Native x86-64.
 */
class InteropOwnershipE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String GOLDEN = String.join("\n",
            "owned=receiver",
            "borrowed=call",
            "shared=refcounted",
            "opaque=owner",
            "immutable=owner",
            "mutable=owner",
            "concepts=6",
            "owned_known=true",
            "widget_known=false",
            "borrowed=valid only for the duration of the call; caller owns; foreign may not retain",
            "shared=reference-counted / GC-managed on one side; no manual free");

    @Test
    void ownershipContractMapsOnJvm() throws Exception {
        assertEquals(GOLDEN, runJvm(ownershipProbe()));
    }

    @Test
    void ownershipContractMapsOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), ownershipProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void ownershipContractMapsOnJs() throws Exception {
        // No file IO here, so even JS has no gap: this library is target-neutral.
        assertEquals(GOLDEN, runJs(ownershipProbe()));
    }

    @Test
    void ownershipContractMapsOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        assertEquals(GOLDEN, runNativeX86(ownershipProbe()));
    }

    @Test
    void ownershipUnknownConceptIsAnExplicitDiagnostic() throws Exception {
        Path root = tmp.resolve("unsupported");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), ownershipUnknownProbe());
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> compile(root.resolve("Main.kf"), out, Target.JVM));
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());
        assertEquals("INTEROP: unknown ownership widget", invokeMain(out));
    }

    private static String ownershipProbe() {
        return """
            import interop.InteropOwnership

            String flag(Bool b) {
                if (b) {
                    return "true"
                }
                return "false"
            }

            main() {
                var o = InteropOwnership()
                var names = listOf("owned", "borrowed", "shared", "opaque", "immutable", "mutable")
                var i = 0
                while (i < names.size()) {
                    var n = names.get(i)
                    println(n + "=" + o.lifetime(n))
                    i = i + 1
                }
                println("concepts=" + o.concepts().size())
                println("owned_known=" + flag(o.isKnown("owned")))
                println("widget_known=" + flag(o.isKnown("widget")))
                println("borrowed=" + o.contract("borrowed"))
                println("shared=" + o.contract("shared"))
            }
            """;
    }

    private static String ownershipUnknownProbe() {
        return """
            import interop.InteropOwnership

            main() {
                var o = InteropOwnership()
                try {
                    println(o.describe("widget"))
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
                    InteropOwnershipE2ETest.class.getClassLoader())) {
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
