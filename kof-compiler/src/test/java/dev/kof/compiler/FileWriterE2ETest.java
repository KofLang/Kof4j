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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #854 route 1 (the library route, D-KOF-FIRST) — a pure-Kof block writer
 * over the existing `File(path).appendBytes` primitive: blocks accumulate
 * and each flush appends ONCE (the runtime open+write+close amortized over
 * blockSize bytes instead of per block). The exclusive-creation face is
 * `createNew` (landed, 5/5); the host-primitive route stays the maintainer
 * gate — this proves the library answer on every target that has the
 * primitive (JVM, Script, JS — appendBytes has a JS face, unlike readRange)
 * plus native x86-64.
 */
class FileWriterE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String GOLDEN_A = "hello wxywxywxyw";
    private static final String GOLDEN_B = "hello hello ";

    // bytes1 = "hello ", bytes2 = 10 bytes of 119+(i%3) = "wxywxywxyw"; the
    // third write is EMPTY (n=0 — must add nothing); blockSize 8 forces two
    // auto-flushes mid-stream; the second writer exercises explicit flush().
    private static String probe(String pathA, String pathB) {
        return """
            import file.FileWriter

            main() {
                var bytes1 = new Int[6]
                bytes1[0] = 104
                bytes1[1] = 101
                bytes1[2] = 108
                bytes1[3] = 108
                bytes1[4] = 111
                bytes1[5] = 32
                var bytes2 = new Int[10]
                var i = 0
                while (i < 10) {
                    bytes2[i] = 119 + (i %% 3)
                    i += 1
                }
                var w = FileWriter("%s", 8)
                w.write(bytes1, 6)
                w.write(bytes2, 10)
                w.write(bytes2, 0)
                w.close()
                println(File("%s").readText() == "%s")
                var w2 = FileWriter("%s", 64)
                w2.write(bytes1, 6)
                w2.flush()
                w2.write(bytes1, 6)
                w2.close()
                println(File("%s").readText() == "%s")
            }
            """.formatted(pathA, pathA, GOLDEN_A, pathB, pathB, GOLDEN_B);
    }

    private static String sealedProbe(String path) {
        return """
            import file.FileWriter

            main() {
                var b = new Int[2]
                b[0] = 65
                b[1] = 66
                var w = FileWriter("%s")
                w.write(b, 2)
                w.close()
                try {
                    w.write(b, 2)
                    println("NO-REFUSAL")
                } catch (String e) {
                    println("refused")
                }
            }
            """.formatted(path);
    }

    @Test
    void blocksRoundTripByteExactOnJvm() throws Exception {
        Path a = tmp.resolve("out.bin");
        Path b = tmp.resolve("out2.bin");
        String output = runJvm(probe(a.toString().replace('\\', '/'),
                b.toString().replace('\\', '/')));
        assertEquals("true\ntrue", output.strip(),
                "6 + 10 bytes with an empty write, then two explicit flushes: " + output);
        assertEquals(GOLDEN_A, Files.readString(a), "byte-exact content");
        assertEquals(GOLDEN_B, Files.readString(b), "byte-exact content (second file, trailing space kept)");
    }

    @Test
    void aWriteAfterCloseIsRefusedHonestly() throws Exception {
        Path target = tmp.resolve("sealed.bin");
        String output = runJvm(sealedProbe(target.toString().replace('\\', '/')));
        assertEquals("refused", output.strip(),
                "a write after close throws the honest string: " + output);
        assertEquals(2, Files.size(target), "the refused write added nothing");
    }

    @Test
    void blocksRoundTripOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Path a = root.resolve("out.bin");
        Path b = root.resolve("out2.bin");
        Files.writeString(root.resolve("Main.kf"),
                probe(a.toString().replace('\\', '/'), b.toString().replace('\\', '/')));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), () -> "script stderr: " + result.stderr());
        assertEquals("true\ntrue", result.stdout().strip(),
                "the writer runs on the interpreter too: " + result.stdout());
    }

    @Test
    void blocksRoundTripOnJs() throws Exception {
        Path root = tmp.resolve("js");
        Files.createDirectories(root);
        Path a = root.resolve("out.bin");
        Path b = root.resolve("out2.bin");
        Files.writeString(root.resolve("Main.kf"),
                probe(a.toString().replace('\\', '/'), b.toString().replace('\\', '/')));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(result.success(), () -> "JS compile: " + result.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        var stderr = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stderr);
        assertEquals(0, exit, () -> "JS exit: " + stderr);
        assertEquals("true\ntrue", stdout.toString().strip(),
                "appendBytes has a JS face, so the writer runs there: " + stdout);
    }

    @Test
    void blocksRoundTripOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path root = tmp.resolve("native");
        Files.createDirectories(root);
        Path a = root.resolve("out.bin");
        Path b = root.resolve("out2.bin");
        Files.writeString(root.resolve("Main.kf"),
                probe(a.toString().replace('\\', '/'), b.toString().replace('\\', '/')));
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(), () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), "native binary must exist");
        Process p = new ProcessBuilder(binary.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, p.waitFor(), "native exit: " + output);
        assertEquals("true\ntrue", output, "the writer runs on native x86-64: " + output);
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
        return "file";
    }

    @Override
    public java.util.List<String> libraryMarkers() {
        return java.util.List.of("FileStream.kf");
    }
}
