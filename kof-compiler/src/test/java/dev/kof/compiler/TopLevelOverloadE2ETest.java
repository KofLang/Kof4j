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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SG-011B — sobrecarga de função TOP-LEVEL com assinatura DIFERENTE (oracle: a
 * JVM). O mesmo programa deve produzir saída byte-idêntica nos 5 targets:
 * JVM, Script (interpretador), JS, Native x86 e Native cross (riscv64/aarch64
 * sob qemu, quando a toolchain existe). A resolução acontece no frontend
 * (TopLevelOverload.pick) e cada backend referencia o candidato pela
 * ASSINATURA (descritor JVM / símbolo sufixado / nome JS / dispatch do
 * interpretador) — nunca escolha silenciosa.
 */
class TopLevelOverloadE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String OVERLOAD = """
            Int g(Int x) { return x }
            Int g(Int x, Int y) { return x + y }
            String twice(String s) { return s + s }
            Int twice(Int n) { return n * 2 }
            main() {
                println(g(5))
                println(g(5, 6))
                println(twice("ab"))
                println(twice(21))
            }
            """;
    private static final String OVERLOAD_OUT = "5\n11\nabab\n42";

    private static final String DEFAULTS = """
            Int d(Int x, Int y = 2) { return x + y }
            main() {
                println(d(5))
                println(d(5, 6))
            }
            """;
    private static final String DEFAULTS_OUT = "7\n11";

    private String runJvm(Path src, Path out) throws IOException {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        return exec(List.of("java", "-cp", out.toString(), "Default.Main"), true);
    }

    private String runScript(Path src) {
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(src),
                src.getParent(), new String[0]);
        assertEquals(0, r.exitCode(), "Script exit/stderr: " + r.stdout() + " " + r.stderr());
        return r.stdout().trim();
    }

    private String runJs(Path src, Path out) throws IOException {
        CompilationResult r = driver.compile(src, out, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), bos,
                new ByteArrayInputStream(new byte[0]), bos);
        assertEquals(0, ec, "JS exit: " + bos);
        return bos.toString(StandardCharsets.UTF_8).trim();
    }

    private String runNative(Path src, Path out, Target t, String[] prefix) throws IOException {
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), t + " binary missing");
        List<String> cmd = new java.util.ArrayList<>(java.util.Arrays.asList(prefix));
        cmd.add(bin.toString());
        return exec(cmd, true);
    }

    private static String exec(List<String> cmd, boolean mergeErr) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (mergeErr) pb.redirectErrorStream(true);
            Process p = pb.start();
            String outStr = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "exit code, output: " + outStr);
            return outStr;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Test
    void overloadParityJvmScriptJs(@TempDir Path tmp) throws IOException {
        String[][] cases = {{OVERLOAD, OVERLOAD_OUT, "ov"}, {DEFAULTS, DEFAULTS_OUT, "def"}};
        for (String[] c : cases) {
            Path src = tmp.resolve(c[2] + ".kf");
            Files.writeString(src, c[0]);
            assertEquals(c[1], runJvm(src, tmp.resolve("j-" + c[2])), c[2] + " JVM");
            assertEquals(c[1], runScript(src), c[2] + " Script");
            assertEquals(c[1], runJs(src, tmp.resolve("js-" + c[2])), c[2] + " JS");
        }
    }

    @Test
    void overloadParityNativeX86(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("ov.kf");
        Files.writeString(src, OVERLOAD);
        assertEquals(OVERLOAD_OUT, runNative(src, tmp.resolve("nat"), Target.NATIVE, new String[0]), "Native x86");
        Path d = tmp.resolve("def.kf");
        Files.writeString(d, DEFAULTS);
        assertEquals(DEFAULTS_OUT, runNative(d, tmp.resolve("nat-d"), Target.NATIVE, new String[0]), "Native x86 defaults");
    }

    @Test
    void overloadParityNativeRiscv64(@TempDir Path tmp) throws IOException {
        assumeTool("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        Path src = tmp.resolve("ov.kf");
        Files.writeString(src, OVERLOAD);
        assertEquals(OVERLOAD_OUT, runNative(src, tmp.resolve("rv"), Target.NATIVE_RISCV64, new String[]{"qemu-riscv64"}), "riscv64");
    }

    @Test
    void overloadParityNativeAarch64(@TempDir Path tmp) throws IOException {
        assumeTool("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        Path src = tmp.resolve("ov.kf");
        Files.writeString(src, OVERLOAD);
        assertEquals(OVERLOAD_OUT, runNative(src, tmp.resolve("aa"), Target.NATIVE_AARCH64, new String[]{"qemu-aarch64"}), "aarch64");
    }

    private static final String SUBTYPE = """
            class Animal { }
            class Dog extends Animal { }
            Int w(Animal a) { return 1 }
            Int w(Dog d) { return 2 }
            main() {
                var d = Dog()
                println(w(d))
                var a = Animal()
                println(w(a))
            }
            """;
    private static final String SUBTYPE_OUT = "2\n1";

    @Test
    void mostSpecificCandidateWinsJvmOracle(@TempDir Path tmp) throws IOException {
        // desempate por subtipagem como a JVM: w(Dog) vence w(Animal) p/ Dog.
        Path src = tmp.resolve("sub.kf");
        Files.writeString(src, SUBTYPE);
        assertEquals(SUBTYPE_OUT, runJvm(src, tmp.resolve("sub-j")), "JVM");
        assertEquals(SUBTYPE_OUT, runScript(src), "Script");
        assertEquals(SUBTYPE_OUT, runJs(src, tmp.resolve("sub-js")), "JS");
        assertEquals(SUBTYPE_OUT, runNative(src, tmp.resolve("sub-n"), Target.NATIVE, new String[0]), "Native x86");
    }

    @Test
    void duplicateExactSignatureFailsAllTargets(@TempDir Path tmp) throws IOException {        Path src = tmp.resolve("dup.kf");
        Files.writeString(src, "Int f(Int x) { return x }\nInt f(Int x) { return x + 1 }\nmain() { println(f(1)) }\n");
        for (Target t : new Target[]{Target.JVM, Target.JS, Target.NATIVE}) {
            CompilationResult r = driver.compile(src, tmp.resolve("dup-" + t), t);
            assertFalse(r.success(), t + " deve rejeitar duplicata exata");
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("SEM047"),
                    t + ": " + r.diagnostics().getDiagnostics());
        }
    }

    private static void assumeTool(String... tools) {
        for (String tool : tools) {
            boolean found;
            try {
                found = new ProcessBuilder("which", tool).start().waitFor() == 0;
            } catch (Exception e) {
                found = false;
            }
            Assumptions.assumeTrue(found, tool + " ausente — pulando (NATIVE002)");
        }
    }
}
