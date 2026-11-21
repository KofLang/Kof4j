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

/** O seam {@code kof.wasi.host} desliga a busca do host: a recusa honesta
 *  (R6) — sem host, o CLI tem que NOMEAR o host que falta. */
class WasiRunnerSeamTest {

    @Test
    void withoutAHostTheRunnerRefusesHonestly(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, "main() { println(1) }\n", StandardCharsets.UTF_8);
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("-Dkof.wasi.host=");
        cmd.add("dev.kof.cli.Main");
        cmd.add("run");
        cmd.add(src.toString());
        cmd.add("--target");
        cmd.add("wasi");
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(240, TimeUnit.SECONDS), "the CLI must not hang\n" + out);
        assertEquals(1, p.exitValue(), "without a host the run must fail:\n" + out);
        assertTrue(out.contains("needs a WASI host"),
                "the refusal NAMES the missing host:\n" + out);
        assertTrue(out.contains("wasmtime"),
                "the refusal names the host that would serve it:\n" + out);
    }
}
