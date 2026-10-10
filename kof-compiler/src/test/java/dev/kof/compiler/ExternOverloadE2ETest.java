package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * #763 (`D-MAINT-BATCH-0610`/B): duas declarações `extern` de mesmo nome e
 * aridades diferentes (o padrão C variádico — `syscall`, `printf`) são
 * OVERLOADS por assinatura, não colisão. Antes o registro por nome tinha 1
 * slot: a última declaração vencia e toda chamada de outra aridade morria
 * SEM013. A seleção é UMA passagem só (typer → `ExternOverload`; o lowering
 * consome a escolha registrada — nunca um segundo oracle).
 */
class ExternOverloadE2ETest {

    private static final String C_SRC = """
            int arity_probe(int a) { return a * 2; }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    private static String buildHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "FFI usa um .so real (Linux + cc)");
        Path src = dir.resolve("libkoffixture.c");
        Files.writeString(src, C_SRC);
        Path so = dir.resolve("libkoffixture.so");
        String cc = null;
        for (String cand : new String[] {"/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc"}) {
            try {
                Process p = new ProcessBuilder(cand, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) { cc = cand; break; }
            } catch (Exception ignored) { /* tenta o próximo */ }
        }
        assumeTrue(cc != null, "sem toolchain C (cc/gcc) para a fixture FFI");
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), src.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0, "cc falhou: " + out);
        return so.toString();
    }

    private static String runJvm(Path dir, String base, String kof) throws IOException {
        Path src = dir.resolve(base + "-jvm.kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-" + base + "-jvm");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
        assertTrue(r.success(), () -> "JVM compile " + base + ": " + r.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED", "-cp", out.toString(), "Default.Main")
                    .directory(dir.toFile()).redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
            int ec = p.waitFor();
            assertEquals(0, ec, () -> "JVM run " + base + " exit code, output: " + o);
            return o.trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    @Test
    void sameNameDifferentArityOverloadsBothResolveAndRun(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        String kof = """
                extern "%1$s" arity_probe(Int a, Int b): Int
                extern "%1$s" arity_probe(Int a): Int

                main() {
                    println(arity_probe(21))
                    println(arity_probe(21, 999))
                }
                """.formatted(lib);
        // 42 nas duas chamadas: a de 2 args resolve o candidato (Int,Int) — o
        // símbolo C lê só o primeiro argumento (variádico por omissão), a de
        // 1 arg resolve o candidato (Int). Ordem de declaração invertida de
        // propósito: a última NÃO vence mais (era o bug).
        assertEquals("42\n42", runJvm(dir, "overload", kof),
                "pre-fix RED: SEM013 'Wrong number of arguments for 'arity_probe': expected 1 but got 2'");
    }

    @Test
    void unmatchedArityStillDiagnosedAtCallSite(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        Path src = dir.resolve("bad-arity.kf");
        Files.writeString(src, """
                extern "%1$s" arity_probe(Int a): Int
                extern "%1$s" arity_probe(Int a, Int b): Int

                main() {
                    println(arity_probe(1, 2, 3))
                }
                """.formatted(lib));
        CompilationResult r = driver.compile(src, dir.resolve("out-bad"), Target.JVM);
        assertFalse(r.success());
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM013"), diags);
        assertTrue(diags.contains("Wrong number of arguments for 'arity_probe'"), diags);
    }

    @Test
    void typeMismatchOnChosenCandidateStillSem014(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        Path src = dir.resolve("bad-type.kf");
        Files.writeString(src, """
                extern "%1$s" arity_probe(Int a): Int

                main() {
                    println(arity_probe("x"))
                }
                """.formatted(lib));
        CompilationResult r = driver.compile(src, dir.resolve("out-type"), Target.JVM);
        assertFalse(r.success());
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM014"), diags);
    }

    @Test
    void nativeTargetBindsBothOverloads(@TempDir Path dir) throws Exception {
        assumeTrue(Files.exists(Path.of("/usr/bin/as")) && Files.exists(Path.of("/usr/bin/ld")),
                "cross/native assembler ausente — condição de ambiente");
        String lib = buildHostLib(dir);
        Path src = dir.resolve("overload-nat.kf");
        Files.writeString(src, """
                extern "%1$s" arity_probe(Int a, Int b): Int
                extern "%1$s" arity_probe(Int a): Int

                main() {
                    println(arity_probe(21))
                    println(arity_probe(21, 999))
                }
                """.formatted(lib));
        CompilationResult r = driver.compile(src, dir.resolve("out-overload-nat"), Target.NATIVE);
        assertTrue(r.success(), "Native deve BINDAR os dois candidatos: " + r.diagnostics().getDiagnostics());
    }
}
