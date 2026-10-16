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
 * known-bugs §741: {@code String.equalsIgnoreCase} divergia em SILÊNCIO (um
 * booleano errado, sem diagnóstico) em DUAS faces, em sentidos diferentes.
 * O contrato é o do JVM: comparação CHAR-A-CHAR com fold por code unit
 * (upper OU lower, mapeamento simples 1:1).
 *
 * <ul>
 *   <li><b>NATIVE</b> (x86 + riscv64; aarch64 herda pelo tradutor): o
 *       {@code kof_string_equals_ignore_case} comparava BYTES com fold ASCII
 *       (A-Z/a-z) — {@code "É".equalsIgnoreCase("é")} dava <b>false</b>
 *       (o JVM dá true) e {@code "café"}/{@code "CAFÉ"} também.</li>
 *   <li><b>JS</b>: a baixa era {@code a.toUpperCase() === b.toUpperCase()} do
 *       HOST (mapeamento COMPLETO) — {@code "straße"}/{@code "STRASSE"} dava
 *       true (o host expande o eszett; o JVM dá false) e {@code "İ"}/{@code "i"}
 *       dava false (o host gera "i"+marca combinante; o JVM dá true).</li>
 * </ul>
 *
 * <p>Fix: o nativo COMPÕE o fold Unicode já existente
 * ({@code kof_string_to_upper}/{@code kof_string_to_lower}, do D-STR-UNICODE)
 * com o {@code kof_string_equals} — upper OU lower, como o JVM; o JS passa a
 * usar o helper {@code kofStrEqIgnoreCase} (fold SIMPLES 1:1 por code unit:
 * uma expansão "base + marca combinante" usa a base, uma expansão real mantém
 * o original).
 *
 * <p>Oráculo é o JVM (≡ o Script, ambos medidos).
 */
class StringEqualsIgnoreCaseUnicodeE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            main() {
                println("p1=" + "\\u00c9".equalsIgnoreCase("\\u00e9"))
                println("p2=" + "ABC".equalsIgnoreCase("abc"))
                println("p3=" + "caf\\u00e9".equalsIgnoreCase("CAF\\u00c9"))
                println("p4=" + "stra\\u00dfe".equalsIgnoreCase("STRASSE"))
                println("p5=" + "\\u0130".equalsIgnoreCase("i"))
                println("p6=" + "abc".equalsIgnoreCase("abd"))
                println("p7=" + "abc".equalsIgnoreCase("ab"))
                println("p8=" + "123!".equalsIgnoreCase("123!"))
                println("p9=" + "caf\\u00e9".equalsIgnoreCase("CAFE"))
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "p1=true",
            "p2=true",
            "p3=true",
            "p4=false",
            "p5=true",
            "p6=false",
            "p7=false",
            "p8=true",
            "p9=false");

    @Test
    void jvmIsTheContractForEqualsIgnoreCase() throws Exception {
        assertEquals(GOLDEN, runJvm(), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnEqualsIgnoreCase() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsMatchesTheJvmOnEqualsIgnoreCase() throws Exception {
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
    void nativeX86EqualsIgnoreCaseMatchesTheJvm() throws Exception {
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
    void riscv64EqualsIgnoreCaseMatchesTheJvm() throws Exception {
        runCross("riscv64", Target.NATIVE_RISCV64);
    }

    @Test
    void aarch64EqualsIgnoreCaseMatchesTheJvm() throws Exception {
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
