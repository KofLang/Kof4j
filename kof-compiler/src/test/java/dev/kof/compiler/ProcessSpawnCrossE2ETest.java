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
 * D-FULL-PARITY-050 — linha 1, fatia D: {@code process.spawn} + handle ops no
 * CROSS riscv64/aarch64 ({@link dev.kof.compiler.nat.NativeRiscvAsmProcessSpawn},
 * clone SIGCHLD + execvp + pipe + tabela de handles persistente + reap
 * preguiçoso). Golden byte-a-byte contra o oráculo JVM (a mesma fonte roda na
 * JVM e nos 2 alvos cross). Mesmos 4 cenários do
 * {@code ProcessSpawnNativeE2ETest} x86-64: readLine linha a linha, EOF honesto,
 * programa inexistente = handle morto/{@code -1}, sentinela vivo + kill.
 *
 * <p>Paridade (não golden fixo): JVM == riscv64 == aarch64 no MESMO stdout —
 * a divergência entre alvos é a regra 5, e o oráculo é a JVM. As 4 saídas são
 * determinísticas (spin até morrer antes de ler o exit code; kill esquece o
 * handle → {@code -1}).
 */
class ProcessSpawnCrossE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

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

    private String runJvm(Path src, Path out) throws IOException {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "jvm compile: " + r.diagnostics().getDiagnostics());
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

    private String runCross(Path src, Path out, String arch, Target target) throws IOException {
        CompilationResult r = driver.compile(src, out, target);
        assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), arch + " binary should exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, bin);
        pb.redirectErrorStream(true);
        try {
            return capture(pb.start());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    /** Roda a fonte nos 3 alvos e exige o MESMO stdout (JVM oracle ≡ cross). */
    private void assertCrossParity(String tag, String program) throws IOException {
        Path src = tmp.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        String oracle = runJvm(src, tmp.resolve(tag + "-jvm"));
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && NativeRiscv64E2ETest.qemuPrefix("riscv64") != null,
                "cross riscv64 toolchain/sysroot ausente — pulando (NATIVE002)");
        String rv = runCross(src, tmp.resolve(tag + "-rv"), "riscv64", Target.NATIVE_RISCV64);
        assertEquals(oracle, rv, "paridade JVM==riscv64 quebrou em " + tag);
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64")
                        && NativeRiscv64E2ETest.qemuPrefix("aarch64") != null,
                "cross aarch64 toolchain/sysroot ausente — pulando (NATIVE002)");
        String aa = runCross(src, tmp.resolve(tag + "-aa"), "aarch64", Target.NATIVE_AARCH64);
        assertEquals(oracle, aa, "paridade JVM==aarch64 quebrou em " + tag);
    }

    @Test
    void spawnEchoReadsStdoutLineByLine() throws Exception {
        assertCrossParity("echo", """
            main() {
                val h = process.spawn("echo", "vivo")
                println(h.readLine())
                var spin = 0
                while (h.alive() && spin < 1000000) { spin = spin + 1 }
                println(h.exitCode())
            }
            """);
    }

    @Test
    void readLineAtEofIsHonestEmpty() throws Exception {
        assertCrossParity("eof", """
            main() {
                val h = process.spawn("echo", "uma")
                println(h.readLine())
                var spin = 0
                while (h.alive() && spin < 1000000) { spin = spin + 1 }
                println("|" + h.readLine() + "|")
            }
            """);
    }

    @Test
    void spawnMissingProgramIsHonestDeadHandle() throws Exception {
        assertCrossParity("missing", """
            main() {
                val h = process.spawn("no-such-binary-9f3c7a")
                println(if (h.alive()) "alive" else "dead")
                println("|" + h.readLine() + "|")
                println(h.exitCode())
            }
            """);
    }

    @Test
    void exitCodeWhileAliveIsSentinelAndKillMakesHandleDead() throws Exception {
        assertCrossParity("kill", """
            main() {
                val h = process.spawn("sleep", "30")
                println(h.exitCode())
                println(if (h.alive()) "alive" else "dead")
                h.kill()
                println(if (h.alive()) "alive" else "dead")
                println(h.exitCode())
            }
            """);
    }
}
