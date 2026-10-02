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
 * Fatia 4 do plano Kof Connector Ecosystem ({@code D-CONNECTORS-GO}): o catálogo
 * {@code interop.ConnectorCatalogue} descobre os manifests {@code *.toml} de um
 * diretório e os expõe tipados ({@code count}`/`names`/`find`/`has`), ignorando
 * entradas não-`.toml` (não são connectors) — nunca pulando um manifest malformado
 * em silêncio. Sem mudança no compilador (library-first). JVM + Native x86-64 +
 * Script; JS herda a lacuna {@code IOJS001} do `kof.io`.
 */
class ConnectorCatalogueE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String JAVA_MANIFEST =
            "name = \"kof-java\"\nlanguage = \"java\"\nversion = \"0.5.0\"\nabi = \"jni\"\nruntime = \"jvm\"\n";
    private static final String CC_MANIFEST =
            "name = \"kof-cc\"\nlanguage = \"c\"\nversion = \"1.0\"\nabi = \"c\"\nruntime = \"native\"\n";

    private static final String GOLDEN = String.join("\n",
            "count=2",
            "name0=kof-cc",
            "name1=kof-java",
            "has_java=true",
            "has_nope=false",
            "cc=kof-cc 1.0 (c, abi c, runtime native)");

    @Test
    void catalogueDiscoversOnJvm() throws Exception {
        Path dir = catalogueDir();
        assertEquals(GOLDEN, runJvm(probe(dir)));
    }

    @Test
    void catalogueDiscoversOnScript() throws Exception {
        Path dir = catalogueDir();
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void catalogueDiscoversOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        assertEquals(GOLDEN, runNativeX86(probe(catalogueDir())));
    }

    @Test
    void catalogueRequiresKofIoOnJs() throws Exception {
        Path dir = catalogueDir();
        Path root = tmp.resolve("js");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), root.resolve("out"), Target.JS));
        assertFalse(result.success(), "JS must refuse the missing kof.io directory/readRange binding (R6)");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("IOJS001"), () -> "expected IOJS001, got: " + diag);
    }

    // A directory with two manifests plus a non-manifest file that must be ignored.
    private Path catalogueDir() throws Exception {
        Path dir = tmp.resolve("connectors");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("kof-cc.toml"), CC_MANIFEST, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("kof-java.toml"), JAVA_MANIFEST, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("notes.txt"), "not a connector\n", StandardCharsets.UTF_8);
        return dir;
    }

    private static String probe(Path dir) {
        return """
            import interop.ConnectorCatalogue

            String flag(Bool b) {
                if (b) {
                    return "true"
                }
                return "false"
            }

            main() {
                var cat = ConnectorCatalogue("%s")
                println("count=" + cat.count())
                var ns = cat.names()
                var i = 0
                while (i < ns.size()) {
                    println("name" + i + "=" + ns.get(i))
                    i = i + 1
                }
                println("has_java=" + flag(cat.has("kof-java")))
                println("has_nope=" + flag(cat.has("nope")))
                var m = cat.find("kof-cc")
                if (m != null) {
                    println("cc=" + m.describe())
                }
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
                    ConnectorCatalogueE2ETest.class.getClassLoader())) {
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
