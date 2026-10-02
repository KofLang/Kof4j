package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #679 — `kof debug` (JVM) must relay the debuggee's output to the adapter's
 * stderr byte-exactly: no stale tail (the read count is honoured), newlines
 * preserved (no {@code trim()}), and accents correct (the debuggee is forced to
 * UTF-8 and the relay decodes UTF-8). The adapter runs with
 * {@code -Dstderr.encoding=ISO-8859-1} so an accent is a single byte (E1),
 * making the defect deterministic on any OS.
 */
class KofDebugJvmOutputRelayTest {

    private record Cli(Process p, OutputStream to, InputStream from, ByteArrayOutputStream err) {
    }

    private static Cli cli(Path dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-Dstderr.encoding=ISO-8859-1");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile());
        pb.redirectErrorStream(false);
        Process proc = pb.start();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Thread collector = new Thread(() -> {
            try (InputStream in = proc.getErrorStream()) {
                in.transferTo(err);
            } catch (Exception ignored) {
            }
        }, "adapter-stderr");
        collector.setDaemon(true);
        collector.start();
        return new Cli(proc, proc.getOutputStream(), proc.getInputStream(), err);
    }

    private static void send(Cli c, int seq, String command, String argsJson) throws Exception {
        String body = "{\"seq\":" + seq + ",\"type\":\"request\",\"command\":\"" + command
                + "\",\"arguments\":{" + argsJson + "}}";
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        c.to().write(("Content-Length: " + b.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        c.to().write(b);
        c.to().flush();
    }

    private static String next(Cli c) throws Exception {
        String line;
        int len = -1;
        while ((line = KofDebug.readLine(c.from())) != null) {
            if (line.isBlank()) {
                break;
            }
            if (line.toLowerCase().startsWith("content-length:")) {
                len = Integer.parseInt(line.substring(15).trim());
            }
        }
        assertNotNull(line, "DAP stream closed");
        if (len < 0) {
            return null;
        }
        return new String(c.from().readNBytes(len), StandardCharsets.UTF_8);
    }

    private static String await(Cli c, String needle, String label) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        List<String> seen = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            String m = next(c);
            if (m == null) {
                continue;
            }
            seen.add(m);
            if (m.contains(needle)) {
                return m;
            }
        }
        fail(label + " never arrived; seen: " + seen);
        return null;
    }

    private static String stderrText(Cli c) {
        synchronized (c.err()) {
            return c.err().toString(StandardCharsets.ISO_8859_1);
        }
    }

    private static boolean pollFor(Cli c, String needle, long ms) {
        int attempts = (int) Math.max(1, ms / 50);
        return CliAwaitFixture.awaitTrue(attempts, 50, () -> stderrText(c).contains(needle));
    }

    @Test
    void relaysDebuggeeOutputExactly(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"),
                "main() {\n"
                + "    println(\"primeira linha do debuggee, mais longa que o resto\")\n"
                + "    println(\"Bye\")\n"
                + "    println(\"Olá, café\")\n"
                + "    println(\"fim\")\n"
                + "}\n");
        String program = dir.resolve("Main.kf").toString().replace("\\", "/");
        Cli c = cli(dir, "debug", "--dap", "Main.kf");
        try {
            send(c, 1, "initialize", "");
            assertTrue(await(c, "\"command\":\"initialize\"", "initialize").contains("success"));
            send(c, 2, "launch", "\"program\":\"" + program + "\"");
            assertTrue(await(c, "\"command\":\"launch\"", "launch").contains("\"success\":true"));
            send(c, 3, "setBreakpoints", "\"source\":{\"path\":\"" + program + "\"},\"breakpoints\":[{\"line\":5}]");
            assertTrue(await(c, "\"command\":\"setBreakpoints\"", "setBreakpoints").contains("\"verified\""));
            send(c, 4, "configurationDone", "");
            await(c, "\"event\":\"stopped\"", "stopped at line 5");

            String expected = "primeira linha do debuggee, mais longa que o resto\nBye\nOlá, café\n";
            assertTrue(pollFor(c, expected, 15_000),
                    () -> "#679 relay must keep newlines and the accent; stderr="
                            + stderrText(c).replace("\n", "\\n"));
            assertFalse(stderrText(c).contains("restoBye"),
                    "#679: glued lines (trim) must not appear: " + stderrText(c).replace("\n", "\\n"));

            send(c, 5, "continue", "");
            assertTrue(pollFor(c, "fim\n", 15_000),
                    () -> "#679: trailing line missing after continue: " + stderrText(c).replace("\n", "\\n"));
            send(c, 6, "disconnect", "");
            await(c, "\"command\":\"disconnect\"", "disconnect");
            assertTrue(c.p().waitFor(30, TimeUnit.SECONDS));
        } finally {
            CliProcessTree.terminate(c.p());
        }
    }
}
