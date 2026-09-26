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
 * D-FULL-PARITY-050 linha 1, fatia E: impressao/concatenacao do record
 * {@code Result} INTEIRO nos alvos nativos com paridade byte-a-byte com o
 * oraculo JVM (§367). Antes o caminho era {@code PROC001}; o MCU freestanding
 * continua recusando honestamente (testado em
 * {@link ProcessResultWholePrintGuardTest}).
 */
class ProcessResultWholePrintE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String PROGRAM = """
            main() {
                var ok = process.run("echo", "x")
                println(ok)
                println("res: " + ok)
                var bad = process.run("sh", "-c", "echo out; echo err 1>&2; exit 3")
                println(bad)
                var multi = process.run("sh", "-c", "printf 'a\\nb\\n'")
                println(multi)
            }
            """;

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

    private static String capture(Process p) throws IOException, InterruptedException {
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        int code = p.waitFor();
        assertEquals(0, code, "exit code do programa Kof, output: " + out);
        return out;
    }

    private String run(Path src, Path out, Target target) throws IOException {
        CompilationResult r = driver.compile(src, out, target);
        assertTrue(r.success(), target + " compile: " + r.diagnostics().getDiagnostics());
        if (target == Target.JVM) {
            ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8", "-cp", out.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            try {
                return capture(pb.start());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            }
        }
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), target + " binary should exist");
        ProcessBuilder pb = target == Target.NATIVE
                ? new ProcessBuilder(bin.toString())
                : NativeRiscv64E2ETest.qemu(target.nativeArch(), bin);
        pb.redirectErrorStream(true);
        try {
            return capture(pb.start());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    @Test
    void wholeResultPrintMatchesJvmOnAllHostNativeTargets() throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, PROGRAM);
        String oracle = run(src, tmp.resolve("out-jvm"), Target.JVM);

        String x86 = run(src, tmp.resolve("out-x86"), Target.NATIVE);
        assertEquals(oracle, x86, "x86-64 deve imprimir o Result por conteudo");

        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && NativeRiscv64E2ETest.qemuPrefix("riscv64") != null,
                "cross riscv64 toolchain/sysroot ausente — pulando");
        String riscv = run(src, tmp.resolve("out-riscv"), Target.NATIVE_RISCV64);
        assertEquals(oracle, riscv, "riscv64 deve imprimir o Result por conteudo");

        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64")
                        && NativeRiscv64E2ETest.qemuPrefix("aarch64") != null,
                "cross aarch64 toolchain/sysroot ausente — pulando");
        String aarch = run(src, tmp.resolve("out-aarch"), Target.NATIVE_AARCH64);
        assertEquals(oracle, aarch, "aarch64 deve imprimir o Result por conteudo");
    }
}
