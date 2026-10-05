package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan §7 (`D-CONNECTORS-GO`) slice 1: {@code interop.CHeaderBindings} — read a C
 * header and emit the Kof {@code foreign module} block (slice A grammar) that
 * binds the declared symbols through the EXISTING FFI path (rule 54). Bounded to
 * scalar types; unmapped declarations are recorded in {@code skipped()}, never
 * emitted wrong.
 *
 * <p>The golden proves the deterministic text; the round-trip compiles the
 * GENERATED block against real libm on the JVM and runs it — the emitted source
 * is valid Kof, not just a string.</p>
 */
class CHeaderBindingsE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String HEADER = """
            #ifndef SAMPLE_H
            #define SAMPLE_H

            int add(int a, int b);
            double sqrt(double x);
            const char *greet(const char *name);
            void log_msg(const char *msg);
            long ticks(void);
            unsigned long long total(void);
            float ratio(float a);
            bool flag(int x);
            static int hidden(int x);
            typedef int myint;
            int apply(int (*cb)(int));
            int sum(int xs[]);
            int printf(const char *fmt, ...);

            #endif
            """;

    private static final String GOLDEN_RENDER = String.join("\n",
            "foreign module sample {",
            "    library \"libsample.so\"",
            "    abi c",
            "    ownership borrowed",
            "    extern add(Int a, Int b): Int",
            "    extern sqrt(Double x): Double",
            "    extern greet(String name): String",
            "    extern log_msg(String msg): void",
            "    extern ticks(): Long",
            "    extern total(): Long",
            "    extern ratio(Float a): Float",
            "    extern flag(Int x): Bool",
            "}",
            "");

    @Test
    void rendersGoldenOnJvm() throws Exception {
        assertEquals(GOLDEN_RENDER, runJvm(renderProbe()));
    }

    @Test
    void rendersGoldenOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), renderProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN_RENDER, result.stdout().strip() + "\n");
    }

    @Test
    void rendersGoldenOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        assertEquals(GOLDEN_RENDER, runNativeX86(renderProbe()));
    }

    @Test
    void unsupportedDeclarationsAreSkippedHonestly() throws Exception {
        String output = runJvm(skipProbe());
        assertTrue(output.contains("skipped=5"), output);
        assertTrue(output.contains("int apply"), output);
        assertTrue(output.contains("int sum"), output);
        assertTrue(output.contains("int printf"), output);
    }

    @Test
    void generatedBlockCompilesAndRunsOnJvm() throws Exception {
        Path root = tmp.resolve("roundtrip");
        Files.createDirectories(root);
        // 1) generate the block from a real header via the pure-Kof generator.
        Files.writeString(root.resolve("libm.h"), """
                double fmod(double a, double b);
                double sqrt(double x);
                """);
        String rendered = runJvm(renderProbe(root.resolve("libm.h"), "libm.so.6", "libm"));
        // 2) the emitted block is valid Kof: append a main and compile+run it.
        String program = rendered + """
                main() {
                    println("fmod=" + fmod(10.0, 3.0))
                    println("sqrt=" + sqrt(144.0))
                }
                """;
        Path source = root.resolve("Main.kf");
        Files.writeString(source, program);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> compile(source, out, Target.JVM));
        assertTrue(result.success(), () -> "generated block must compile: "
                + result.diagnostics().getDiagnostics());
        assertEquals("fmod=1.0\nsqrt=12.0", invokeMain(out));
    }

    @Test
    void headerReadRequiresKofIoOnJs() throws Exception {
        Path header = tmp.resolve("js/io.h");
        Files.createDirectories(header.getParent());
        Files.writeString(header, "int add(int a, int b);\n");
        Path root = tmp.resolve("js");
        Files.writeString(root.resolve("Main.kf"), renderProbe(header, "libsample.so", "sample"));
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), root.resolve("out"), Target.JS));
        assertFalse(result.success(), "JS must refuse the missing kof.io binding (R6)");
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("IOJS001"));
    }

    private String renderProbe() throws Exception {
        Path header = tmp.resolve("render/sample.h");
        Files.createDirectories(header.getParent());
        Files.writeString(header, HEADER);
        return renderProbe(header, "libsample.so", "sample");
    }

    private String renderProbe(Path header, String library, String module) {
        return """
            import interop.CHeaderBindings

            main() {
                var b = CHeaderBindings("%s")
                println(b.render("%s", "%s"))
            }
            """.formatted(path(header), module, library);
    }

    private String skipProbe() throws Exception {
        Path header = tmp.resolve("skip/sample.h");
        Files.createDirectories(header.getParent());
        Files.writeString(header, HEADER);
        return """
            import interop.CHeaderBindings

            main() {
                var b = CHeaderBindings("%s")
                var s = b.skipped()
                println("skipped=" + s.size())
                var i = 0
                while (i < s.size()) {
                    println(s.get(i))
                    i = i + 1
                }
            }
            """.formatted(path(header));
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
        return invokeMain(out) + "\n";
    }

    private static String invokeMain(Path out) throws Exception {
        var stdout = new java.io.ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()},
                    CHeaderBindingsE2ETest.class.getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
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
        return output + "\n";
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

    @Override
    public String libraryName() {
        return "interop";
    }

    @Override
    public List<String> libraryMarkers() {
        return List.of("ConnectorManifest.kf");
    }

    @Override
    public List<String> libraryNames() {
        return List.of("file", "interop");
    }
}
