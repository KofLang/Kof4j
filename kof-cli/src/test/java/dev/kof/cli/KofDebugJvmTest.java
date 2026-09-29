package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
 * O E2E de LAUNCH do `kof debug` na JVM que NUNCA EXISTIU no repositorio —
 * a ausencia dele e que manteve vivo o bug do IDSizes do JDK 25 (connect()
 * lia 6 tamanhos quando o HotSpot moderno manda 5; o 6o readInt roubava 4
 * bytes do proximo pacote e o stream JDWP morvia na primeira resposta
 * multi-campo). Testa o fluxo COMPLETO pelo CLI real: initialize -> launch
 -> setBreakpoints -> configurationDone -> stopped -> stackTrace -> variables
 * -> disconnect, com processo Java de verdade (sem stub).
 */
class KofDebugJvmTest {

    private record Cli(Process p, OutputStream to, InputStream from) {
    }

    private static Cli cli(Path dir, String... args) throws Exception {
        return cli(dir, List.of(), args);
    }

    private static Cli cli(Path dir, List<String> jvmFlags, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.addAll(jvmFlags);
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile());
        pb.redirectErrorStream(false);
        Process proc = pb.start();
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
        assertNotNull(line, "fluxo DAP fechou");
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
        fail(label + " nunca chegou; vistos: " + seen);
        return null;
    }

    @Test
    void dapLaunchStopsAtBreakpointAndReadsFrames(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"),
                "main() {\n    var x = 1\n    var y = 2\n    println(x + y)\n}\n");
        Cli c = cli(dir, "debug", "--dap", "Main.kf");
        try {
            send(c, 1, "initialize", "");
            assertTrue(await(c, "\"command\":\"initialize\"", "initialize").contains("success"));
            send(c, 2, "launch", "\"program\":\"" + dir.resolve("Main.kf").toString().replace("\\", "/") + "\"");
            assertTrue(await(c, "\"command\":\"launch\"", "launch").contains("\"success\":true"),
                    "launch compila, lanca o JVM com -agentlib e conecta o JDWP");
            send(c, 3, "setBreakpoints", "\"source\":{\"path\":\"" + dir.resolve("Main.kf").toString().replace("\\", "/") + "\"},\"breakpoints\":[{\"line\":4}]");
            String bp = await(c, "\"command\":\"setBreakpoints\"", "setBreakpoints");
            assertTrue(bp.contains("\"verified\""), bp);
            send(c, 4, "configurationDone", "");
            String stopped = await(c, "\"event\":\"stopped\"", "stopped no breakpoint (linha 4 = println)");
            assertTrue(stopped.contains("breakpoint"), stopped);
            send(c, 5, "stackTrace", "\"startFrame\":0");
            assertTrue(await(c, "\"command\":\"stackTrace\"", "stackTrace").contains("Main.kf"),
                    "o frame parado aponta para a fonte Kof");
            send(c, 6, "scopes", "\"frameId\":0");
            String scopes = await(c, "\"command\":\"scopes\"", "scopes");
            assertTrue(scopes.contains("variablesReference"), scopes);
            send(c, 7, "variables", "\"variablesReference\":1");
            String vars = await(c, "\"command\":\"variables\"", "variables");
            assertTrue(vars.contains("\"x\"") && vars.contains("\"y\""),
                    "locals da linha parada sao visiveis: " + vars);
            send(c, 8, "disconnect", "");
            await(c, "\"command\":\"disconnect\"", "disconnect");
            assertTrue(c.p().waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, c.p().exitValue());
        } finally {
            CliProcessTree.terminate(c.p());
        }
    }

    /**
     * §541: a saida do debuggee chega ao stderr do adaptador EXATAMENTE como o
     * programa a escreveu — sem bytes velhos de uma leitura anterior maior, sem
     * perder as quebras de linha, e no charset do proprio stderr do adaptador
     * (ISO-8859-1 aqui, para o acento provar o byte E1 em qualquer SO). O
     * breakpoint na linha 3 separa a 1a linha (longa) das seguintes (curtas):
     * duas leituras do pipe, a segunda menor que a primeira.
     */
    @Test
    void debuggeeOutputReachesAdapterStderrByteExact(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"),
                "main() {\n"
                + "    println(\"primeira linha do debuggee, mais longa que o resto\")\n"
                + "    println(\"Bye\")\n"
                + "    println(\"Olá, café\")\n"
                + "    println(\"fim\")\n"
                + "}\n");
        String expected = "primeira linha do debuggee, mais longa que o resto\nBye\nOlá, café\nfim\n";
        Cli c = cli(dir, List.of("-Dstderr.encoding=ISO-8859-1"), "debug", "--dap", "Main.kf");
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Thread drain = new Thread(() -> {
            try {
                c.p().getErrorStream().transferTo(err);
            } catch (IOException ignored) {
            }
        });
        drain.setDaemon(true);
        drain.start();
        try {
            String main = dir.resolve("Main.kf").toString().replace("\\", "/");
            send(c, 1, "initialize", "");
            await(c, "\"command\":\"initialize\"", "initialize");
            send(c, 2, "launch", "\"program\":\"" + main + "\"");
            assertTrue(await(c, "\"command\":\"launch\"", "launch").contains("\"success\":true"), "launch");
            send(c, 3, "setBreakpoints", "\"source\":{\"path\":\"" + main + "\"},\"breakpoints\":[{\"line\":3}]");
            assertTrue(await(c, "\"command\":\"setBreakpoints\"", "setBreakpoints").contains("\"verified\""));
            send(c, 4, "configurationDone", "");
            assertTrue(await(c, "\"event\":\"stopped\"", "stopped na linha 3").contains("breakpoint"));
            send(c, 5, "continue", "");
            await(c, "\"command\":\"continue\"", "continue");

            String seen = "";
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline && !seen.contains(expected)) {
                Thread.sleep(100);
                seen = new String(err.toByteArray(), StandardCharsets.ISO_8859_1).replace("\r\n", "\n");
            }
            assertTrue(seen.contains(expected),
                    "stderr do adaptador = saida do debuggee byte a byte; visto: "
                            + seen.replace("\n", "\\n").replace("\0", "\\0"));
            send(c, 6, "disconnect", "");
        } finally {
            CliProcessTree.terminate(c.p());
        }
    }

    @Test
    void unimplementedRequestFailsHonestlyNotSilentSuccess(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), "main() {\n    println(1)\n}\n");
        Cli c = cli(dir, "debug", "--dap", "Main.kf");
        try {
            send(c, 1, "initialize", "");
            await(c, "\"command\":\"initialize\"", "initialize");
            send(c, 2, "restart", "");
            String r = await(c, "\"command\":\"restart\"", "restart");
            assertTrue(r.contains("\"success\":false"),
                    "§428: request nao implementado precisa erro honesto, nunca success:true vazio: " + r);
            assertFalse(r.contains("\"success\":true"), r);
            send(c, 3, "disconnect", "");
            await(c, "\"command\":\"disconnect\"", "disconnect");
            assertTrue(c.p().waitFor(30, TimeUnit.SECONDS));
        } finally {
            CliProcessTree.terminate(c.p());
        }
    }
}
