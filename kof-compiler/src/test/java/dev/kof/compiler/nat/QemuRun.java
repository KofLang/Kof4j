package dev.kof.compiler.nat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * §524: the qemu-aarch64 harnesses in {@code NativeRiscvGc*}/{@code NativeRiscvDtoa}/
 * {@code NativeRiscvDbWireTest} die with a guest-side SIGSEGV ({@code rc=139}) under
 * heavy CPU load while the SAME command is green in isolation — a known environment
 * flake, never a code regression (owner lane native-cross). This is the one
 * bound+retry policy those harnesses share.
 *
 * <p>A transient guest crash under scheduler pressure is retried; a REAL failure is
 * never hidden: any non-139 exit is returned immediately, and a 139 on <em>every</em>
 * attempt is returned as-is so the caller's {@code assertEquals(0, …)} still fires.
 * The bounded wait (§418 precedent) guarantees a stuck qemu is killed instead of
 * hanging the suite.
 */
final class QemuRun {

    /** qemu's guest-crash exit code (signal 11). */
    static final int SIGSEGV = 139;
    static final int DEFAULT_RETRIES = 3;
    static final long DEFAULT_TIMEOUT_SECONDS = 180;

    private QemuRun() {}

    /** Run to completion; retry only a transient {@link #SIGSEGV}. */
    static Exit run(String... cmd) throws IOException {
        return run(DEFAULT_RETRIES, DEFAULT_TIMEOUT_SECONDS, cmd);
    }

    static Exit run(Map<String, String> env, int retries, long timeoutSeconds, String... cmd)
            throws IOException {
        Exit last = null;
        for (int attempt = 1; attempt <= retries; attempt++) {
            last = once(env, timeoutSeconds, cmd);
            if (last.exitCode() != SIGSEGV) return last;
        }
        return last;
    }

    static Exit run(int retries, long timeoutSeconds, String... cmd) throws IOException {
        return run(Map.of(), retries, timeoutSeconds, cmd);
    }

    private static Exit once(Map<String, String> env, long timeoutSeconds, String... cmd)
            throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        if (env != null) env.forEach((k, v) -> pb.environment().put(k, v));
        Process p = pb.start();
        try {
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                p.waitFor(10, TimeUnit.SECONDS);
                throw new IOException(String.join(" ", cmd)
                        + " não terminou em " + timeoutSeconds + "s — qemu morto (§418/§524)");
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            return new Exit(p.exitValue(), out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("interrompido ao rodar " + String.join(" ", cmd), e);
        }
    }

    /** Run and require exit 0 (bounded + retried); returns the merged output. */
    static String runExpect0(String... cmd) throws IOException {
        return runExpect0(Map.of(), cmd);
    }

    static String runExpect0(Map<String, String> env, String... cmd) throws IOException {
        Exit e = run(env, DEFAULT_RETRIES, DEFAULT_TIMEOUT_SECONDS, cmd);
        assertEquals(0, e.exitCode(),
                "comando falhou (" + e.exitCode() + "): " + String.join(" ", cmd) + "\n" + e.output());
        return e.output();
    }

    /** Exit code + merged (stdout+stderr) output of a run. */
    record Exit(int exitCode, String output) {}
}
