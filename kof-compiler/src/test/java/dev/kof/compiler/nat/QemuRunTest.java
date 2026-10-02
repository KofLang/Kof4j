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
 */
class QemuRunTest {

    @Test
    void retriesTransientSegvThenSucceeds(@TempDir Path dir) throws Exception {
        Path counter = dir.resolve("n");
        // exits 139 twice, then prints "ok" — a transient guest crash.
        String[] cmd = {"sh", "-c",
                "n=$(cat " + counter + " 2>/dev/null || echo 0); n=$((n+1)); echo $n > " + counter
                        + "; if [ $n -lt 3 ]; then exit 139; fi; echo ok"};
        QemuRun.Exit e = QemuRun.run(3, 10, cmd);
        assertEquals(0, e.exitCode(), "third attempt succeeds: " + e);
        assertEquals("ok", e.output());
        assertEquals("3", Files.readString(counter).trim(), "exactly 3 attempts were made");
    }

    @Test
    void persistentSegvIsNotHidden(@TempDir Path dir) throws Exception {
        Path counter = dir.resolve("n");
        String[] cmd = {"sh", "-c", "echo x >> " + counter + "; exit 139"};
        QemuRun.Exit e = QemuRun.run(3, 10, cmd);
        assertEquals(139, e.exitCode(), "a real crash still surfaces as 139: " + e);
        assertEquals(3, Files.readAllLines(counter).size(), "retries bounded at 3");
    }

    @Test
    void nonSegvFailureIsReturnedImmediately(@TempDir Path dir) throws Exception {
        Path counter = dir.resolve("n");
        String[] cmd = {"sh", "-c", "echo x >> " + counter + "; exit 7"};
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
    void zeroExitIsReturnedAsIs() throws Exception {
        QemuRun.Exit e = QemuRun.run(3, 10, "sh", "-c", "echo hi");
        assertEquals(0, e.exitCode());
        assertEquals("hi", e.output());
    }
}
