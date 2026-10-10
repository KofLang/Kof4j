package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Saída redirecionada (arquivo/pipe) é UTF-8 em todo target que o CLI executa —
 * paridade JVM × KofJS × Native. No Windows a JVM filha escrevia na codificação
 * do sistema (cp1252: `á` = E1) e os runners que capturam a saída (kof test,
 * kof compare) a decodificavam como UTF-8 → `Ol?`. Em Linux/macOS o sistema já
 * é UTF-8 e estes testes passam de qualquer jeito; no Windows são o reprodutor.
 */
class StdioEncodingE2ETest {

    private record Cli(int exit, byte[] out, String err) {
        String text() { return new String(out, StandardCharsets.UTF_8); }
        /** Bytes crus como texto 1:1 (ISO-8859-1) — contains() compara byte a byte. */
        String raw() { return new String(out, StandardCharsets.ISO_8859_1); }
        String hex() { return java.util.HexFormat.ofDelimiter(" ").formatHex(out); }
    }

    private static Cli cli(Path workDir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of(args));
        Path err = Files.createTempFile(workDir, "stderr", ".txt");
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(workDir.toFile());
        pb.redirectError(err.toFile());
        Process p = pb.start();
        byte[] out = p.getInputStream().readAllBytes();
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "o proprio CLI nao pode hangar");
        return new Cli(p.exitValue(), out, Files.readString(err, StandardCharsets.UTF_8));
    }

    private static Path hello(Path dir) throws Exception {
        Path src = dir.resolve("hello.kf");
        Files.writeString(src, "main() {\n    println(\"Olá, mundo!\")\n}\n", StandardCharsets.UTF_8);
        return src;
    }

    private static void assertUtf8Ola(Cli r) {
        String utf8 = new String("Olá".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        assertEquals(0, r.exit(), "exit\nstdout: " + r.text() + "\nstderr: " + r.err());
        assertTrue(r.raw().contains(utf8), "stdout deve ter `á` como C3 A1 (UTF-8); bytes: " + r.hex());
    }

    @Test
    void jvmRunRedirectedWritesUtf8(@TempDir Path dir) throws Exception {
        assertUtf8Ola(cli(dir, "run", hello(dir).toString()));
    }

    @Test
    void jsRunRedirectedWritesUtf8(@TempDir Path dir) throws Exception {
        assertUtf8Ola(cli(dir, "run", hello(dir).toString(), "--target=js"));
    }

    @Test
    void testRunnerKeepsAccentsPrintedByJvmHarness(@TempDir Path dir) throws Exception {
        // o runner só mostra a saída do teste quando ele falha — falha proposital
        Path src = dir.resolve("acento.kf");
        Files.writeString(src, "test \"acento\" {\n    println(\"Olá do teste\")\n"
                + "    assert(false, \"falhou: café\")\n}\n", StandardCharsets.UTF_8);
        Cli r = cli(dir, "test", src.toString());
        assertEquals(1, r.exit(), r.text() + "\nstderr: " + r.err());
        assertTrue(r.text().contains("Olá do teste"), "println do harness JVM: " + r.text());
        assertTrue(r.text().contains("falhou: café"), "mensagem do assert: " + r.text());
    }

    @Test
    void compareDecodesJvmOutputAsUtf8(@TempDir Path dir) throws Exception {
        Compare.RunResult r = Compare.runKof(hello(dir), List.of(), null);
        assertEquals(0, r.exitCode(), r.stderr());
        assertEquals("Olá, mundo!", r.stdout());
    }
}
