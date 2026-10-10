package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan §7 (`D-CONNECTORS-GO`) slices 1–3: {@code interop.CHeaderBindings} — read a
 * C header and emit the Kof {@code foreign module} block (slice A grammar) that
 * binds the declared symbols through the EXISTING FFI path (rule 54). Slice 1 =
 * scalar functions; slice 2 = {@code struct}/typedef declarations emitted as Kof
 * {@code record}s and used in signatures; slice 3 = C {@code enum}s resolved to
 * the integer ABI ({@code Int}). Unmapped declarations are recorded in
 * {@code skipped()}, never emitted wrong.
 *
 * <p>The golden proves the deterministic text; the round-trips compile the
 * GENERATED source against real libm (scalars) and a host {@code .so} (structs)
 * and RUN it — the emitted source is valid Kof, not just a string.</p>
 */
class CHeaderBindingsE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String HEADER = """
            #ifndef SAMPLE_H
            #define SAMPLE_H

            struct Point { int x; int y; };
            struct Rect { struct Point a; struct Point b; };
            typedef struct { double d; int i; } Mix;
            typedef struct Point PtAlias;
            typedef int myint;

            enum Color { Red, Green, Blue };
            typedef enum { A, B } Letter;
            typedef enum Color AliasColor;
            enum Explicit { X = 1, Y = 2 };

            int add(int a, int b);
            double sqrt(double x);
            const char *greet(const char *name);
            void log_msg(const char *msg);
            long ticks(void);
            unsigned long long total(void);
            float ratio(float a);
            bool flag(int x);
            struct Point mkpoint(int x, int y);
            int sumpoint(struct Point p);
            Mix mixret(double d, int i);
            int takealias(PtAlias p);
            int paint(enum Color c);
            enum Color pick(void);
            Letter letterOf(int x);
            AliasColor aliasPick(void);
            static int hidden(int x);
            typedef struct { int (*cb)(int); } WithFn;
            int apply(int (*cb)(int));
            int sum(int xs[]);
            int printf(const char *fmt, ...);

            #endif
            """;

    private static final String GOLDEN_RENDER = String.join("\n",
            "record Point(Int x, Int y)",
            "record Mix(Double d, Int i)",
            "",
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
            "    extern mkpoint(Int x, Int y): Point",
            "    extern sumpoint(Point p): Int",
            "    extern mixret(Double d, Int i): Mix",
            "    extern takealias(Point p): Int",
            "    extern paint(Int c): Int",
            "    extern pick(): Int",
            "    extern letterOf(Int x): Int",
            "    extern aliasPick(): Int",
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
        assertTrue(output.contains("skipped=7"), output);
        assertTrue(output.contains("Rect"), output);
        assertTrue(output.contains("WithFn"), output);
        assertTrue(output.contains("int apply"), output);
        assertTrue(output.contains("int sum"), output);
        assertTrue(output.contains("int printf"), output);
        assertTrue(output.contains("enum Explicit"), output);
    }

    @Test
    void generatedScalarBlockCompilesAndRunsOnJvm() throws Exception {
        Path root = tmp.resolve("roundtrip-scalar");
        Files.createDirectories(root);
        Files.writeString(root.resolve("libm.h"), """
                double fmod(double a, double b);
                double sqrt(double x);
                """);
        String rendered = runJvm(renderProbe(root.resolve("libm.h"), "libm.so.6", "libm"));
        String program = rendered + """
                main() {
                    println("fmod=" + fmod(10.0, 3.0))
                    println("sqrt=" + sqrt(144.0))
                }
                """;
        assertEquals("fmod=1.0\nsqrt=12.0", runGenerated(root, program, null, null));
    }

    @Test
    void generatedStructBlockCompilesAndRunsOnJvm() throws Exception {
        Path root = tmp.resolve("roundtrip-struct");
        Files.createDirectories(root);
        Files.writeString(root.resolve("shapes.h"), """
                struct Point { int x; int y; };
                int sumpoint(struct Point p);
                struct Point mkpoint(int x, int y);
                """);
        String rendered = runJvm(renderProbe(root.resolve("shapes.h"), "libkofchb.so", "shapes"));
        String program = rendered + """
                main() {
                    println("sum=" + sumpoint(Point(3, 4)))
                    var p = mkpoint(5, 6)
                    println("mk=" + p.x + "," + p.y)
                }
                """;
        String c = """
                struct Point { int x; int y; };
                int sumpoint(struct Point p) { return p.x + p.y; }
                struct Point mkpoint(int x, int y) { struct Point p; p.x = x; p.y = y; return p; }
                """;
        assertEquals("sum=7\nmk=5,6", runGenerated(root, program, "libkofchb.so", c));
    }

    @Test
    void generatedEnumBlockCompilesAndRunsOnJvm() throws Exception {
        Path root = tmp.resolve("roundtrip-enum");
        Files.createDirectories(root);
        Files.writeString(root.resolve("colors.h"), """
                enum Color { Red, Green, Blue };
                typedef enum { A, B } Letter;
                int paint(enum Color c);
                enum Color pick(void);
                Letter letterOf(int x);
                """);
        String rendered = runJvm(renderProbe(root.resolve("colors.h"), "libkofenum.so", "colors"));
        String program = rendered + """
                main() {
                    println("paint=" + paint(1))
                    println("pick=" + pick())
                    println("letter=" + letterOf(0))
                }
                """;
        String c = """
                enum Color { Red, Green, Blue };
                typedef enum { A, B } Letter;
                int paint(enum Color c) { return c + 10; }
                enum Color pick(void) { return Blue; }
                Letter letterOf(int x) { return x == 0 ? A : B; }
                """;
        assertEquals("paint=11\npick=2\nletter=0", runGenerated(root, program, "libkofenum.so", c));
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

    /**
     * Compile and run a generated program; when {@code libName} is given, build the
     * host {@code .so} from {@code cSource} and point the generated block at it.
     */
    private String runGenerated(Path root, String program, String libName, String cSource)
            throws Exception {
        if (libName != null) {
            program = program.replace(libName, buildHostLib(root, libName, cSource));
        }
        Path source = root.resolve("Main.kf");
        Files.writeString(source, program);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> compile(source, out, Target.JVM));
        assertTrue(result.success(), () -> "generated program must compile: "
                + result.diagnostics().getDiagnostics());
        return invokeMain(out);
    }

    /** Build the host .so for a round-trip (environmental skip when no cc/Linux). */
    private String buildHostLib(Path dir, String libName, String cSource) throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "the host lib is a native .so (Linux)");
        String cc = firstPresent("/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc");
        Assumptions.assumeTrue(cc != null, "no C toolchain (cc/gcc) for the host lib");
        Path c = dir.resolve(libName.replace(".so", ".c"));
        Files.writeString(c, cSource);
        Path so = dir.resolve(libName);
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), c.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Assumptions.assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                "cc/gcc failed to build the host lib: " + output);
        return so.toString();
    }

    private static String firstPresent(String... candidates) {
        for (String candidate : candidates) {
            try {
                Process p = new ProcessBuilder(candidate, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
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
