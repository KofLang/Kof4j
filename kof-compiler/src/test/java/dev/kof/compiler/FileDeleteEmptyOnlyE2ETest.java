package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #860 — o contrato REAL de remoção, travado por alvo. As duas páginas do corpus
 * discordavam entre si E da implementação: `docs/stdlib/IO.md` prometia que
 * `File.delete()` apaga diretórios não-vazios recursivamente "em todo alvo", e
 * `training/language/io.md` dizia que `Directory("d").delete()` remove um
 * diretório vazio. O medido (JVM ≡ Script, 15/10) é o inverso em ambos:
 *
 * <pre>
 *   File(nonEmptyDir).delete() -> false   (o diretório FICA)
 *   File(emptyDir).delete()    -> true
 *   File(file).delete()        -> true
 *   File(absent).delete()      -> false
 *   Directory(nonEmptyDir).delete() -> true (recursivo; §618/B decidido 08/10)
 * </pre>
 *
 * O golden é o oráculo da JVM (rule 5): a mesma saída tem de valer em JS e no
 * Native x86-64. O contrato recursivo do `Directory.delete` já é travado por
 * {@link IoDirDeleteJsParityE2ETest}; o que faltava travar era o `File.delete`.
 */
class FileDeleteEmptyOnlyE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    /** 9 linhas, na ordem do programa abaixo. */
    private static final String GOLDEN = String.join("\n",
            "false",  // File.delete(non-empty)  — não recursivo
            "true",   // o diretório ficou
            "true",   // File.delete(empty dir)
            "false",  // o diretório vazio sumiu
            "true",   // File.delete(file)
            "false",  // o arquivo sumiu
            "false",  // File.delete(inexistente)
            "true",   // Directory.delete(non-empty) — recursivo (§618/B)
            "false"); // a árvore sumiu

    private static Path probe(Path dir, String tag) throws IOException {
        Path base = dir.resolve("probe-filedel-" + tag).toAbsolutePath();
        String p = base.toString();
        String program = """
                main() {
                    Directory("%1$s/nonempty/sub").createDirectories()
                    File("%1$s/nonempty/sub/a.txt").writeText("x")
                    println(File("%1$s/nonempty").delete())
                    println(Directory("%1$s/nonempty").exists())
                    Directory("%1$s/empty").create()
                    println(File("%1$s/empty").delete())
                    println(Directory("%1$s/empty").exists())
                    File("%1$s/f.txt").writeText("z")
                    println(File("%1$s/f.txt").delete())
                    println(File("%1$s/f.txt").exists())
                    println(File("%1$s/none.txt").delete())
                    Directory("%1$s/tree/sub").createDirectories()
                    File("%1$s/tree/sub/a.txt").writeText("x")
                    println(Directory("%1$s/tree").delete())
                    println(Directory("%1$s/tree").exists())
                }
                """.formatted(p);
        Path src = dir.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        return src;
    }

    private String runJvmOracle(Path src, Path outDir) throws IOException, InterruptedException {
        CompilationResult rj = driver.compile(src, outDir, Target.JVM);
        assertTrue(rj.success(), "JVM oracle compile: " + rj.diagnostics().getDiagnostics());
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM oracle run: " + out);
        assertEquals(GOLDEN, out, "JVM oracle golden");
        return out;
    }

    @Test
    void fileDeleteIsNotRecursiveOnJvmAndJs(@TempDir Path tempDir) throws Exception {
        Path src = probe(tempDir, "js");
        String oracle = runJvmOracle(src, tempDir.resolve("out-jvm"));

        Path outJs = tempDir.resolve("out-js");
        CompilationResult r = driver.compile(src, outJs, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        Path jsFile = outJs.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new ByteArrayInputStream(new byte[0]), stdout);
        String js = stdout.toString(StandardCharsets.UTF_8).strip();
        assertEquals(0, exit, "JS exit, output: " + js);
        assertEquals(oracle, js, "JS must match the JVM oracle (rule 5)");
    }

    @Test
    void fileDeleteIsNotRecursiveOnNativeX86(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        Path src = probe(tempDir, "nat");
        String oracle = runJvmOracle(src, tempDir.resolve("out-jvm-nat"));

        Path out = tempDir.resolve("out-nat");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "native binary must exist");
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, p.waitFor(), "native output: " + output);
        assertEquals(oracle, output, "Native x86-64 must match the JVM oracle (rule 5)");
    }
}