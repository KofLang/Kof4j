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
 * Fatia 10 do plano Kof Connector Ecosystem ({@code D-CONNECTORS-GO}): os nomes
 * concretos de biblioteca estrangeira {@code interop.InteropLibrary} (plano §3.8)
 * — a face do library-loading que é expressável em Kof puro: nome lógico → nome
 * de arquivo por formato (`.so`/`.dylib`/`.dll`, + estáticas `.a`/`.lib`) e
 * candidatos por raiz. A chave é o PRÓPRIO sufixo dado pelo plano; nenhuma
 * adivinhação de nome de SO. Honesto por construção (R6): formato desconhecido →
 * {@code INTEROP: unknown library kind <x>}; nunca um chute. Sem mudança no
 * compilador; sem IO → roda em JVM + Script + JS + Native x86-64.
 */
class InteropLibraryE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String GOLDEN = String.join("\n",
            "so=libkofcc.so",
            "dylib=libkofcc.dylib",
            "dll=kofcc.dll",
            "a=libkofcc.a",
            "lib=kofcc.lib",
            "kinds=3",
            "statics=2",
            "so_known=true",
            "widget_known=false",
            "cand=2",
            "cand0=/opt/kof/lib/libkofcc.so",
            "describe=library kofcc file=libkofcc.so");

    @Test
    void libraryNamesMapOnJvm() throws Exception {
        assertEquals(GOLDEN, runJvm(libraryProbe()));
    }

    @Test
    void libraryNamesMapOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), libraryProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void libraryNamesMapOnJs() throws Exception {
        // No file IO here, so even JS has no gap: this library is target-neutral.
        assertEquals(GOLDEN, runJs(libraryProbe()));
    }

    @Test
    void libraryNamesMapOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        assertEquals(GOLDEN, runNativeX86(libraryProbe()));
    }

    @Test
    void libraryUnknownKindIsAnExplicitDiagnostic() throws Exception {
        assertEquals("INTEROP: unknown library kind widget", runJvm(unknownKindProbe()));
    }

    private static String libraryProbe() {
        return """
            import interop.InteropLibrary

            String flag(Bool b) {
                if (b) {
                    return "true"
                }
                return "false"
            }

            main() {
                var lib = InteropLibrary("kofcc")
                println("so=" + lib.fileName("so"))
                println("dylib=" + lib.fileName("dylib"))
                println("dll=" + lib.fileName("dll"))
                println("a=" + lib.staticFileName("a"))
                println("lib=" + lib.staticFileName("lib"))
                println("kinds=" + lib.kinds().size())
                println("statics=" + lib.staticKinds().size())
                println("so_known=" + flag(lib.isKnownKind("so")))
                println("widget_known=" + flag(lib.isKnownKind("widget")))
                var roots = listOf("/opt/kof/lib", "/usr/lib")
                var cands = lib.candidates("so", roots)
                println("cand=" + cands.size())
                println("cand0=" + cands.get(0))
                println("describe=" + lib.describe("so"))
            }
            """;
    }

    private static String unknownKindProbe() {
        return """
            import interop.InteropLibrary

            main() {
                var lib = InteropLibrary("kofcc")
                try {
                    println(lib.fileName("widget"))
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
                    InteropLibraryE2ETest.class.getClassLoader())) {
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
