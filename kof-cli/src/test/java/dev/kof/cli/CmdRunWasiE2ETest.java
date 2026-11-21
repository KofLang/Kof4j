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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * O runner WASI do {@code kof run --target wasi} (TIER 15 — issue #776).
 *
 * <p>UM modulo WASI e' um modulo de COMANDO (exporta {@code _start}), entao um
 * host WASI o roda direto — a mesma forma do alvo nativo (um ELF que roda).
 * Antes desta fatia o CLI recusava {@code --target wasi} honesto e apontava
 * para {@code kof build --target wasi} + uma invocacao manual do runtime; o
 * usuario tinha de saber a linha de comando do host. O runner fecha o ciclo:
 * o MESMO programa roda com {@code kof run --target wasi}.
 *
 * <p>A PROVA e' a PARIDADE com o oraculo JVM: o mesmo fonte, o mesmo stdout.
 * Tres medidas sustentam a fatia:
 * <ol>
 *   <li>o argv: o programa ve {@code args[0]} = o primeiro argumento DIGITADO
 *       (o runtime WASI descarta o argv[0] do host — a MESMA lei do
 *       {@code main(String[])} da JVM); o oraculo JVM diz {@code n=2,
 *       arg0=alpha} e o WASI precisa dizer o MESMO;</li>
 *   <li>o filesystem: as faces {@code kof.io} vivem sob o preopen fd 3 — sem
 *       um preopen elas falham em SILENCIO (o arquivo nunca e' criado e o
 *       {@code readText} devolve null); o runner preabre o CWD, a raiz do
 *       sandbox do app;</li>
 *   <li>o preopen do cwd: o app roda no diretorio de onde o usuario o
 *       lancou.</li>
 * </ol>
 *
 * <p>Os hosts: {@code wasmtime} (a referencia) e o {@code node:wasi} (o
 * segundo oraculo — o mesmo que o {@code WasmWasiNodeHostTest} usa). Sem
 * nenhum dos dois a recusa e' honesta e NOMEIA o host que falta (R6).
 */
class CmdRunWasiE2ETest {

    private record Cli(int exit, String out) {}

    private static Cli cli(Path workDir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(240, TimeUnit.SECONDS), "the CLI itself must not hang\n" + out);
        return new Cli(p.exitValue(), out);
    }

    /** O oráculo JVM do MESMO fonte (com os MESMOS argumentos). */
    private static Cli jvmOracle(Path dir, Path src, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("run", src.toString(), "--target", "jvm"));
        cmd.addAll(List.of(args));
        return cli(dir, cmd.toArray(new String[0]));
    }

    private static boolean hostPresent() {
        return WasiRunner.hostAvailable();
    }

    private static final String ARGV_SRC = """
            main(String[] args) {
                println("n=" + args.length)
                var i = 0
                while (i < args.length) {
                    println("arg" + i + "=" + args[i])
                    i = i + 1
                }
            }
            """;

    private static final String IO_SRC = """
            import kof.io

            main(String[] args) {
                val f = File("notas.txt")
                if (!f.exists()) { f.writeText("ola kof\\n") }
                val t = f.readText()
                if (t != null) { println("len=" + t.length()) } else { println("null") }
            }
            """;

    @Test
    void theWasiTargetRunsWithTheSameArgumentsAsTheJvm(@TempDir Path dir) throws Exception {
        assumeTrue(hostPresent(), "no WASI host on this machine — skipping (environment)");
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, ARGV_SRC, StandardCharsets.UTF_8);
        Cli oracle = jvmOracle(dir, src, "alpha", "beta");
        assertEquals(0, oracle.exit(), "the JVM oracle must run:\n" + oracle.out());
        Cli wasi = cli(dir, "run", src.toString(), "--target", "wasi", "alpha", "beta");
        assertEquals(0, wasi.exit(), "the WASI run must succeed:\n" + wasi.out());
        assertEquals(oracle.out().trim(), wasi.out().trim(),
                "o argv do WASI deve ser o do oráculo JVM (o runtime descarta o argv[0] do host)");
    }

    @Test
    void theWasiTargetCreatesAndReadsAFileInTheCurrentDirectory(@TempDir Path dir) throws Exception {
        assumeTrue(hostPresent(), "no WASI host on this machine — skipping (environment)");
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, IO_SRC, StandardCharsets.UTF_8);
        Cli wasi = cli(dir, "run", src.toString(), "--target", "wasi");
        assertEquals(0, wasi.exit(), "the WASI run must succeed:\n" + wasi.out());
        assertTrue(wasi.out().contains("len="),
                "o readText deve devolver o conteudo (sem preopen ele falha em SILENCIO):\n" + wasi.out());
        assertFalse(wasi.out().contains("null"),
                "o arquivo deve ter sido criado e lido:\n" + wasi.out());
        Path created = dir.resolve("notas.txt");
        assertTrue(Files.isRegularFile(created),
                "o arquivo foi realmente criado no CWD (a raiz do sandbox do app)");
        assertEquals("ola kof", Files.readString(created).trim(),
                "o conteudo gravado pelo app");
    }
}
