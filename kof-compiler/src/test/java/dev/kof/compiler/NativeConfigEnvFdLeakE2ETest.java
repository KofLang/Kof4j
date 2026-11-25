package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUG (measured) — {@code kof_env_getc} ARMAVA o {@code close} e nunca o
 * executava.
 *
 * <p><b>O DEFEITO:</b> em {@code runtime/RuntimeConfig1.java} o sítio de
 * fechar o {@code /proc/self/environ} faz
 * {@code movq %r12, %rdi; movq $3, %rax # close} e salta direto para a
 * validação do nome — o {@code syscall} não existe. São os dois sítios irmãos
 * que fecham certo ({@code kof_sec_secret_get} em {@code RuntimeSecurity11} e
 * {@code .Llog_parse_level} em {@code RuntimeLog1}); este é o único que não
 * fecha, então a cada {@code config.env(...)}/nome procurado no environ o
 * processo perde um descritor.
 *
 * <p><b>A PROVA (executada, não estrutural):</b> o binário nativo roda com
 * {@code ulimit -n 64} e chama {@code config.env} 200 vezes. Com o fd vazando,
 * a abertura falha por volta da 60ª chamada e a variável some; com o fd
 * fechado, as 200 chamadas veem o valor. O RED foi medido na árvore antiga.
 */
class NativeConfigEnvFdLeakE2ETest {

    /** Recursão: 200 buscas no environ (o limite do fd e' 64). */
    private static final String PROBE = """
            String probe(Int n) {
                if (n == 0) {
                    return "done"
                }
                var v = config.env("KOF_PROBE_LEAK")
                if (v != "presente") {
                    return "leak at " + n
                }
                return probe(n - 1)
            }

            main() {
                println(probe(200))
            }
            """;

    private String runWithSmallFdLimit(Path tmp) throws IOException, InterruptedException {
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, PROBE);
        CompilationResult result = new CompilerDriver().compile(
                source, tmp.resolve("out"), Target.NATIVE);
        assertTrue(result.success(),
                "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out/Default/Main");
        assertTrue(Files.exists(bin), "Binary should exist");

        ProcessBuilder pb = new ProcessBuilder("bash", "-c",
                "ulimit -n 64 && exec \"$0\"", bin.toString());
        pb.redirectErrorStream(true);
        pb.environment().put("KOF_PROBE_LEAK", "presente");
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
        return output;
    }

    /**
     * 200 buscas com 64 descritores: o valor continua visível — o fd e'
     * fechado a cada chamada.
     */
    @Test
    void everyEnvLookupClosesItsDescriptor(@TempDir Path tmp)
            throws IOException, InterruptedException {
        String out = runWithSmallFdLimit(tmp);
        assertEquals("done", out,
                "the environ fd must be closed on every lookup: '" + out + "'");
    }

    /**
     * A causa raiz no texto emitido: entre o {@code read} do environ e a
     * varredura existe um {@code close} de verdade.
     */
    @Test
    void theEmittedEnvLookupClosesTheDescriptor(@TempDir Path tmp) throws IOException {
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, PROBE);
        String prior = System.getProperty("KOF_KEEP_ASM");
        System.setProperty("KOF_KEEP_ASM", "1");
        try {
            CompilationResult result = new CompilerDriver().compile(
                    source, tmp.resolve("out-asm"), Target.NATIVE);
            assertTrue(result.success(), result.diagnostics().getDiagnostics().toString());
            String asm = Files.readString(tmp.resolve("out-asm/Default/Main.s"));
            int at = asm.indexOf("kof_env_getc:");
            assertTrue(at > 0, "kof_env_getc must be emitted");
            String body = asm.substring(at, Math.min(asm.length(), at + 2000));
            int read = body.indexOf("# SYS_read");
            assertTrue(read > 0, "the environ read is emitted:\n" + body);
            String afterRead = body.substring(read);
            int close = afterRead.indexOf("call kof_plat_close");
            assertTrue(close > 0,
                    "the close after the environ read is emitted:\n" + afterRead);
            int scan = afterRead.indexOf(".Lceg_scan");
            assertTrue(scan < 0 || close < scan,
                    "the close precedes the scan:\n" + afterRead.substring(0, 200));
        } finally {
            if (prior == null) System.clearProperty("KOF_KEEP_ASM");
            else System.setProperty("KOF_KEEP_ASM", prior);
        }
    }
}
