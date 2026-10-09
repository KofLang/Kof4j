package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `known-bugs` §618/B (maintainer decision 08/10: recursive everywhere):
 * {@code Directory.delete()} on a non-empty tree now removes the whole tree
 * on JS too — previously JVM/Native deleted recursively while JS returned
 * {@code false} and leaked the tree. The JS bridge
 * ({@code KofJsRunner} {@code dirDelete}) walks reverse-order exactly like
 * {@code JvmRuntimeIo.kof_io_dir_delete} (same absent→{@code false}
 * contract; {@code Files.walk} never follows symlinks).
 *
 * <p>Proof RED-first: pre-fix the JS leg prints {@code false} and the tree
 * survives (measured by stashing the bridge change); post-fix the JVM
 * oracle golden ({@code true/false/false}) holds byte-identical on JS and
 * Native x86-64 (rule 5). Cross riscv64/aarch64 already pin the same golden
 * in {@link NativeIoDirDeleteCrossTest} (untouched native runtimes).
 */
class IoDirDeleteJsParityE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String GOLDEN = "true\nfalse\nfalse";

    private static Path dirDeleteProbe(Path dir, String tag) throws IOException {
        Path probe = dir.resolve("probe-" + tag).toAbsolutePath();
        String p = probe.toString();
        String program = """
                main() {
                    Directory("%1$s").create()
                    File("%1$s/f.txt").writeText("x")
                    Directory("%1$s/sub").create()
                    File("%1$s/sub/g.txt").writeText("y")
                    println(Directory("%1$s").delete())
                    println(File("%1$s").exists())
                    println(Directory("%1$s/none").delete())
                }
                """.formatted(p);
        Path src = dir.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        return src;
    }

    @Test
    void dirDeleteRecursiveMatchesJvmOnJs(@TempDir Path tempDir) throws Exception {
        Path src = dirDeleteProbe(tempDir, "js");
        Path outJvm = tempDir.resolve("out-jvm");
        CompilationResult rj = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rj.success(), "JVM oracle compile: " + rj.diagnostics().getDiagnostics());
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                "-cp", outJvm.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String oracle = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM oracle run: " + oracle);
        assertEquals(GOLDEN, oracle, "JVM oracle golden");
        assertTrue(!Files.exists(tempDir.resolve("probe-js")), "JVM must remove the tree");

        Path outJs = tempDir.resolve("out-js");
        CompilationResult r = driver.compile(src, outJs, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        Path jsFile = outJs.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        String js = stdout.toString(StandardCharsets.UTF_8).strip();
        assertEquals(0, exit, "js output: " + js);
        assertEquals(oracle, js, "JS must match the JVM oracle (rule 5)");
    }

    @Test
    void dirDeleteRecursiveMatchesJvmOnNativeX86(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        Path src = dirDeleteProbe(tempDir, "nat");
        Path outJvm = tempDir.resolve("out-jvm-nat");
        CompilationResult rj = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rj.success(), "JVM oracle compile: " + rj.diagnostics().getDiagnostics());
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                "-cp", outJvm.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String oracle = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM oracle run: " + oracle);

        Path out = tempDir.resolve("out-nat");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "native binary must exist");
        ProcessBuilder pb2 = new ProcessBuilder(bin.toString());
        pb2.redirectErrorStream(true);
        Process p2 = pb2.start();
        String output = new String(p2.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, p2.waitFor(), "native output: " + output);
        assertEquals(oracle, output, "Native x86-64 must match the JVM oracle (rule 5)");
    }
}
