package dev.kof.cli;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suporte do teste de {@code kof deploy} ({@code CmdDeployTest}): os helpers de processo
 * CLI, release JSON, HTTP e inspeção de tar. Vive fora da classe de teste para
 * mantê-la abaixo do limite de 500 linhas de teste (Fase 3 da arquitetura de
 * testes, {@code D-TEST-ARCHITECTURE-GO}); os testes e o nome da classe seguem
 * no {@code CmdDeployTest}.
 */
abstract class CmdDeploySupport {

    protected static Process startCli(Path workDir, String... cliArgs) throws IOException {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(java.util.List.of(cliArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    protected static Path writeApp(Path dir, String body) throws IOException {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), body);
        return src;
    }

    protected record CliResult(int exit, String out) {}

    protected static CliResult runEnv(Path workDir, java.util.Map<String, String> env,
                                    String... cliArgs) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(java.util.List.of(cliArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        pb.environment().putAll(env);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(180, TimeUnit.SECONDS);
        return new CliResult(p.exitValue(), out);
    }

    protected static CliResult run(Path workDir, String... cliArgs) throws Exception {
        Process p = startCli(workDir, cliArgs);
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(180, TimeUnit.SECONDS);
        return new CliResult(p.exitValue(), out);
    }

    protected static String releaseJson(String base, long id, String upPath, String assetsPath) {
        String Q = "\"";
        StringBuilder b = new StringBuilder();
        b.append('{').append(Q).append("id").append(Q).append(':').append(id)
         .append(',').append(Q).append("upload_url").append(Q).append(':')
         .append(Q).append(base).append(upPath).append("{?name,label}").append(Q);
        if (assetsPath != null) {
            b.append(',').append(Q).append("assets_url").append(Q).append(':')
             .append(Q).append(base).append(assetsPath).append(Q);
        }
        b.append('}');
        return b.toString();
    }

    protected static String serverAddr(com.sun.net.httpserver.HttpServer s) {
        return "http://127.0.0.1:" + s.getAddress().getPort();
    }

    protected static CliResult runWithEnv(Path workDir, java.util.Map<String, String> env,
                                        String... cliArgs) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(java.util.List.of(cliArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile()).redirectErrorStream(true);
        env.forEach(pb.environment()::put);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(180, java.util.concurrent.TimeUnit.SECONDS);
        return new CliResult(p.exitValue(), out);
    }

    protected static boolean hasCrossToolchain() {
        for (String tool : new String[]{"riscv64-linux-gnu-as", "aarch64-linux-gnu-as"}) {
            try {
                if (new ProcessBuilder(tool, "--version").start().waitFor() == 0) return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    protected static boolean hasNode() {
        try {
            return new ProcessBuilder("node", "--version")
                    .redirectErrorStream(true).start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    protected static String runNode(Path releaseDir, String entry) throws Exception {
        Process p = new ProcessBuilder("node", entry)
                .directory(releaseDir.toFile())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "node " + entry + " (release autocontida):\n" + out);
        return out;
    }

    protected record TarEntry(String name, long size) {}

    protected static TarEntry tarEntry(GZIPInputStream in) throws IOException {
        byte[] header = new byte[512];
        assertEquals(512, in.readNBytes(header, 0, 512), "tar header truncado");
        String magic = new String(header, 257, 6, StandardCharsets.US_ASCII);
        assertTrue(magic.startsWith("ustar"), "magic ustar ausente");
        int end = 0;
        while (end < 100 && header[end] != 0) end++;
        String name = new String(header, 0, end, StandardCharsets.UTF_8);
        long size = 0;
        for (int i = 124; i < 136 && header[i] != 0; i++) {
            char c = (char) header[i];
            if (c == ' ') continue;
            size = size * 8 + (c - '0');
        }
        return new TarEntry(name, size);
    }

    protected static void skipTarPayload(GZIPInputStream in, long size) throws IOException {
        long toSkip = (size + 511) / 512 * 512;
        while (toSkip > 0) {
            long n = in.skip(toSkip);
            if (n <= 0) break;
            toSkip -= n;
        }
    }

    protected static int tarMode(byte[] header) {
        int mode = 0;
        for (int i = 100; i < 108 && header[i] != 0; i++) {
            char c = (char) header[i];
            if (c == ' ') continue;
            mode = mode * 8 + (c - '0');
        }
        return mode;
    }

    protected static boolean isZeroBlock(byte[] block) {
        for (byte b : block) if (b != 0) return false;
        return true;
    }
}
