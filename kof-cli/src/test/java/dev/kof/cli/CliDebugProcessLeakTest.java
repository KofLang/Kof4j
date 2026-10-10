package dev.kof.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * §438 (RED-first regression): killing a {@code kof debug} session must not
 * leave the launched debuggee JVM ({@code -agentlib:jdwp=...,suspend=y}) or its
 * {@code /tmp/kof-debug-*} dir behind. Before the fix, SIGTERM bypassed every
 * cleanup (the session had no shutdown hook, unlike {@code KofDebugNativeDap}),
 * so repeated suite runs accumulated orphans until the tmpfs died.
 */
class CliDebugProcessLeakTest {

    private record Cli(Process p, OutputStream to, InputStream from) {
    }

    private static Cli cli(Path dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of(args));
        Process proc = new ProcessBuilder(cmd).directory(dir.toFile()).start();
        return new Cli(proc, proc.getOutputStream(), proc.getInputStream());
    }

    private static void send(Cli c, int seq, String command, String argsJson) throws Exception {
        String body = "{\"seq\":" + seq + ",\"type\":\"request\",\"command\":\"" + command
                + "\",\"arguments\":{" + argsJson + "}}";
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        c.to().write(("Content-Length: " + b.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        c.to().write(b);
        c.to().flush();
    }

    private static String await(Cli c, String needle, String label) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        List<String> seen = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
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
            if (line == null || len < 0) {
                continue;
            }
            String m = new String(c.from().readNBytes(len), StandardCharsets.UTF_8);
            seen.add(m);
            if (m.contains(needle)) {
                return m;
            }
        }
        fail(label + " nunca chegou; vistos: " + seen);
        return null;
    }

    private static Set<String> debugDirs() throws Exception {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        try (var s = Files.list(tmp)) {
            return s.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("kof-debug-"))
                    .collect(Collectors.toSet());
        }
    }

    @Test
    @DisplayName("§438: matar a sessao nao deixa debuggee orfao nem dir kof-debug-*")
    void killingTheSessionLeavesNoOrphanDebuggeeNorTempDir(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"),
                "main() {\n    var x = 1\n    println(x)\n}\n");
        Set<String> before = debugDirs();
        Cli c = cli(dir, "debug", "--dap", "Main.kf");
        List<ProcessHandle> debuggee = List.of();
        try {
            send(c, 1, "initialize", "");
            await(c, "\"command\":\"initialize\"", "initialize");
            send(c, 2, "launch",
                    "\"program\":\"" + dir.resolve("Main.kf").toString().replace("\\", "/") + "\"");
            assertTrue(await(c, "\"command\":\"launch\"", "launch").contains("\"success\":true"),
                    "launch sobe o debuggee JVM (jdwp suspend=y)");
            debuggee = c.p().descendants().toList();
            assertFalse(debuggee.isEmpty(),
                    "§438: o launch deve subir o debuggee JVM como filho do CLI");
        } finally {
            CliProcessTree.terminate(c.p());
        }
        for (ProcessHandle h : debuggee) {
            assertTrue(CliAwaitFixture.awaitExit(h, 5000),
                    "§438: o debuggee nao pode sobreviver ao teardown");
        }
        Set<String> leaked = debugDirs();
        leaked.removeAll(before);
        assertTrue(leaked.isEmpty(),
                "§438: nenhum diretorio kof-debug-* novo pode sobrar (o shutdown hook limpa): " + leaked);
    }
}
