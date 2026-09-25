package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-FULL-PARITY-050 — linha 1, fatia B: {@code process.spawn} + handle ops no
 * Native x86-64 ({@code RuntimeProcessSpawn}) com golden vs o oráculo JVM
 * (subprocesso vs subprocesso — a mesma fonte Kof, binários reais). Mesmos 4
 * cenários do {@code ProcessSpawnE2ETest} JVM/JS, medidos byte a byte contra
 * a JVM: linha a linha, EOF honesto, programa inexistente = handle morto e
 * {@code exitCode} sentinela/vivo + {@code kill} transformando o handle em
 * morto. {@code write} é no-op nos dois lados (stdin de /dev/null).
 */
class ProcessSpawnNativeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private record Run(int exit, String out) {}

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM deve compilar: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return new Run(p.waitFor(), o);
    }

    private Run runNative(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "Native deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binário nativo deve existir");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return new Run(p.waitFor(), o);
    }

    private void assertParity(String source) throws Exception {
        Path src = tmp.resolve("s.kf");
        Files.writeString(src, source);
        Run jvm = runJvm(src, tmp.resolve("out-jvm"));
        Run nat = runNative(src, tmp.resolve("out-nat"));
        assertEquals(jvm.out(), nat.out(), "paridade JVM≡Native quebrou no stdout");
        assertEquals(jvm.exit(), nat.exit(), "paridade JVM≡Native quebrou no exit code");
    }

    @Test
    void spawnEchoReadsStdoutLineByLine() throws Exception {
        assertParity("""
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
        assertParity("""
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
        assertParity("""
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
        assertParity("""
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
