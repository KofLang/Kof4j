package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §738 (NATIVE x86 FACE, o par do {@code split("")}): o
 * {@code String.replace("", to)} — agulha VAZIA — no-opava no native. O
 * {@code kof_string_replace} procura a SUBSTRING da agulha e uma agulha de 0
 * bytes nunca casa, então a string voltava INTACTA onde o JVM/Script inserem
 * {@code to} em TODA posição, inclusive nas pontas (regra 5 — valor errado).
 *
 * <p>Fix: o ramo da agulha vazia chama o helper ADITIVO
 * {@code kof_string_replace_empty} (por code point UTF-8, a direção do
 * {@code D-STR-UNICODE}); o caminho da agulha não-vazia fica byte-idêntico.
 *
 * <p>Oráculo é o JVM (≡ o Script, ambos medidos). Fronteira documentada: um
 * caractere ASTRAL insere UMA vez (o JVM, por code unit UTF-16, inseriria 2).
 * Os alvos CROSS seguem ABERTOS — face separada.
 */
class NativeReplaceEmptySeparatorE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            main() {
                println("r1=[" + "aXbXc".replace("X", "-") + "]")
                println("r2=[" + "aXbXc".replace("", "-") + "]")
                println("r3=[" + "".replace("", "-") + "]")
                println("r4=[" + "abc".replace("", "") + "]")
                println("r5=[" + "abc".replace("", "XY") + "]")
                println("r6=[" + "caf\u00e9".replace("", ".") + "]")
                println("r7=[" + "a".replace("", "-") + "]")
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "r1=[a-b-c]",
            "r2=[-a-X-b-X-c-]",
            "r3=[-]",
            "r4=[abc]",
            "r5=[XYaXYbXYcXY]",
            "r6=[.c.a.f.\u00e9.]",
            "r7=[-a-]");

    @Test
    void jvmIsTheContractForNativeReplaceEmpty() throws Exception {
        assertEquals(GOLDEN, runJvm(), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnNativeReplaceEmpty() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnNativeReplaceEmpty() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        var so = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), so,
                new java.io.ByteArrayInputStream(new byte[0]), so);
        assertEquals(0, exit, () -> "js saída: " + so);
        assertEquals(GOLDEN, so.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void nativeX86ReplaceEmptyMatchesTheJvm() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = tmp.resolve("x86");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(r.success(), () -> "x86 compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "x86");
        assertEquals(0, p.exitValue(), () -> "x86 saída: " + o);
        assertEquals(GOLDEN, o);
    }

    private String runJvm() throws Exception {
        Path dir = tmp.resolve("jvm");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> "JVM compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "jvm");
        assertEquals(0, p.exitValue(), () -> "oráculo JVM: " + o);
        return o;
    }

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}
