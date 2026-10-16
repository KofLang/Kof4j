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
 * known-bugs §739: {@code String.trim()} divergia em SILÊNCIO nos dois backends,
 * em SENTIDOS OPOSTOS. O contrato é o do JVM: tira TODO char {@code <= ' '}
 * (U+0020) — nada acima, nem os controles abaixo de espaço.
 *
 * <ul>
 *   <li><b>JS</b>: o {@code String.prototype.trim} do host usa o conjunto
 *       WhiteSpace+LineTerminator — REMOVE NBSP (U+00A0) e U+2000 (que o JVM
 *       mantém) e NÃO remove os controles C0 \u0001/\u0008/\u001F (que o JVM tira).</li>
 *   <li><b>NATIVE</b> (x86 + riscv64; aarch64 herda pelo tradutor): o
 *       {@code kof_string_trim} só reconhecia espaço/tab/LF/CR — VT (U+000B),
 *       FF (U+000C) e os controles passavam intactos.</li>
 * </ul>
 *
 * <p>Fix: o JS passa a chamar o helper {@code kofStrTrim} (fatia {@code strbounds},
 * a forma do §730) e o asm nativo troca as quatro comparações por um teste
 * único {@code <= 0x20} (x86 {@code cmpb $32/jbe}, riscv {@code bltu} — que o
 * tradutor aarch64 já conhece).
 *
 * <p>Oráculo é o JVM (≡ o Script, ambos medidos). Cada face afirma o MESMO
 * golden; riscv64/aarch64 rodam sob qemu e PULAM honestamente sem a toolchain.
 */
class StringTrimContractE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            main() {
                println("c01=" + ("\\u0001x\\u0001").trim().length)
                println("c08=" + ("\\u0008x\\u0008").trim().length)
                println("c0b=" + ("\\u000Bx\\u000B").trim().length)
                println("c0c=" + ("\\u000Cx\\u000C").trim().length)
                println("c1f=" + ("\\u001Fx\\u001F").trim().length)
                println("c20=" + (" x ").trim().length)
                println("c21=" + ("!x!").trim().length)
                println("ca0=" + ("\\u00A0x\\u00A0").trim().length)
                println("c2k=" + ("\\u2000x\\u2000").trim().length)
                println("cok=" + ("  ok  ").trim())
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "c01=1",
            "c08=1",
            "c0b=1",
            "c0c=1",
            "c1f=1",
            "c20=1",
            "c21=3",
            "ca0=3",
            "c2k=3",
            "cok=ok");

    @Test
    void jvmTrimIsTheContract() throws Exception {
        assertEquals(GOLDEN, runJvm(), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptTrimMatchesTheJvm() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsTrimMatchesTheJvm() throws Exception {
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
    void nativeX86TrimMatchesTheJvm() throws Exception {
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

    @Test
    void riscv64TrimMatchesTheJvm() throws Exception {
        runCross("riscv64", Target.NATIVE_RISCV64);
    }

    @Test
    void aarch64TrimMatchesTheJvm() throws Exception {
        runCross("aarch64", Target.NATIVE_AARCH64);
    }

    private void runCross(String arch, Target target) throws Exception {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                arch + " cross toolchain/qemu absent");
        Path dir = tmp.resolve(arch);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, target);
        assertTrue(r.success(), () -> arch + " compile: " + r.diagnostics().getDiagnostics());
        String o = NativeRiscv64E2ETest.runQemu(arch, out.resolve("Default/Main"));
        assertEquals(GOLDEN, o.strip());
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
