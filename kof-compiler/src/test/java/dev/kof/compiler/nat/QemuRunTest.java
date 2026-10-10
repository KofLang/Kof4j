package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §524 (harness of the harness): the bound+retry policy that the qemu-aarch64
 * Gc/Dtoa/DbWire harnesses share. Every branch is exercised with a deterministic
 * shell stand-in, never by trusting the real qemu flake.
 *
 * <p>#782: the probes write a counter file inside {@code @TempDir}. That path
 * must reach the script as ARGV ({@code $1}), never interpolated into the script
 * text — on Windows {@code sh} reads each {@code \} of an embedded {@code C:\…}
 * path as an escape, the path collapses to {@code C:Users…} relative to the
 * working directory and the probe writes to a bogus file (3 errors + junk left
 * in the tree on every run).
 */
class QemuRunTest {

    /**
     * Writes the probe script into {@code @TempDir} and returns the argv. The
     * counter path travels as ARGV ({@code $1}), never inside the script text:
     * on Windows {@code sh -c} reads each {@code \} of an embedded {@code C:\…}
     * path as an escape and the probe writes to a bogus file relative to the
     * working directory (#782). The script path is itself a plain argv element,
     * so it survives the same way.
     */
    static String[] probeCommand(Path dir, String body, Path counter) throws IOException {
        Path script = dir.resolve("probe.sh");
        Files.writeString(script, body);
        return new String[]{"sh", script.toString(), counter.toString()};
    }

    @Test
    void retriesTransientSegvThenSucceeds(@TempDir Path dir) throws Exception {
        Path counter = dir.resolve("n");
        // exits 139 twice, then prints "ok" — a transient guest crash.
        String[] cmd = probeCommand(dir,
                "n=$(cat \"$1\" 2>/dev/null || echo 0); n=$((n+1)); echo $n > \"$1\""
                        + "; if [ $n -lt 3 ]; then exit 139; fi; echo ok", counter);
        QemuRun.Exit e = QemuRun.run(3, 10, cmd);
        assertEquals(0, e.exitCode(), "third attempt succeeds: " + e);
        assertEquals("ok", e.output());
        assertEquals("3", Files.readString(counter).trim(), "exactly 3 attempts were made");
    }

    @Test
    void persistentSegvIsNotHidden(@TempDir Path dir) throws Exception {
        Path counter = dir.resolve("n");
        String[] cmd = probeCommand(dir, "echo x >> \"$1\"; exit 139", counter);
        QemuRun.Exit e = QemuRun.run(3, 10, cmd);
        assertEquals(139, e.exitCode(), "a real crash still surfaces as 139: " + e);
        assertEquals(3, Files.readAllLines(counter).size(), "retries bounded at 3");
    }

    @Test
    void nonSegvFailureIsReturnedImmediately(@TempDir Path dir) throws Exception {
        Path counter = dir.resolve("n");
        String[] cmd = probeCommand(dir, "echo x >> \"$1\"; exit 7", counter);
        QemuRun.Exit e = QemuRun.run(3, 10, cmd);
        assertEquals(7, e.exitCode());
        assertEquals(1, Files.readAllLines(counter).size(), "non-139 is never retried");
    }

    @Test
    void hangingCommandIsKilledByTheBound(@TempDir Path dir) throws Exception {
        long t0 = System.nanoTime();
        assertThrows(IOException.class,
                () -> QemuRun.run(1, 1, "sh", "-c", "sleep 60"));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 30_000, "should give up within the bound, took " + ms + "ms");
    }

    @Test
    void zeroExitIsReturnedAsIs(@TempDir Path dir) throws Exception {
        QemuRun.Exit e = QemuRun.run(3, 10, "sh", "-c", "echo hi");
        assertEquals(0, e.exitCode());
        assertEquals("hi", e.output());
    }

    /**
     * #782 regression: the {@code @TempDir} path must travel as argv, never in
     * the script text. On Linux an embedded path works by accident (no {@code \}),
     * so the assertion pins the property that keeps Windows correct.
     */
    @Test
    void probeKeepsTheCounterPathOutOfTheScriptText(@TempDir Path dir) throws Exception {
        Path counter = dir.resolve("n");
        String[] cmd = probeCommand(dir, "echo x >> \"$1\"", counter);
        assertEquals("sh", cmd[0]);
        assertEquals(counter.toString(), cmd[cmd.length - 1], "o contador viaja como argv");
        String scriptText = Files.readString(Path.of(cmd[1]));
        assertFalse(scriptText.contains(counter.toString()),
                "o caminho do contador não pode estar no texto do script: " + scriptText);
        assertTrue(scriptText.contains("$1"), "o script lê o contador de $1");
    }
}
