package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-FULL-PARITY-050 (row 9, fatia 3) — {@code kof.config} nos cross-arch
 * (riscv64/aarch64): lookup real (KOF_CONFIG → env {@code KOF_<KEY>} → perfil
 * {@code kof.<KOF_PROFILE>.config}/kof.config), interpolação {@code ${key}} e
 * wrappers tipados. Contra o oracle JVM (JvmConfigRuntime) com valores
 * fixos — o mesmo contrato do RuntimeConfig1/2 (x86).
 */
class KofConfigCrossTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private String[] run(Path workDir, String source, String archFlag, String qemuArch,
                         Map<String, String> env) throws IOException {
        Path src = workDir.resolve("Cfg-" + archFlag + ".kf");
        Files.writeString(src, source);
        Path outDir = workDir.resolve("out-" + archFlag);
        CompilationResult result = driver.compile(src, outDir, Target.valueOf(archFlag));
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(qemuArch, bin);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(false);
        for (var e : env.entrySet()) pb.environment().put(e.getKey(), e.getValue());
        Process p = pb.start();
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        return new String[]{stdout, stderr, Integer.toString(ec)};
    }

    private static final String MAIN_PROGRAM = """
            main() {
                println(config.str("app.name", "def"))
                println(config.int("server.port", 1))
                println(config.bool("app.debug", false))
                println(config.long("big", 0))
                println(config.has("app.name"))
                println(config.has("nope"))
                println(config.env("KOF_TEST_ENV"))
                println(config.get("host"))
                println(config.str("missing", "fallback"))
                println(config.int("bad", 42))
                println(config.str("profile.only", "x"))
            }
            """;

    private static final String EXPECTED = String.join("\n",
            "MyApp", "8080", "true", "9000000000", "true", "false",
            "literal-env", "8080", "fallback", "42", "from-profile");

    @Test
    void lookupEnvFileProfileAndTypedBothArches(@TempDir Path workDir) throws IOException {
        assumeCross();
        Files.writeString(workDir.resolve("app.config"), """
                # comment
                app.name = MyApp
                server.port = 8080
                app.debug = yes
                host = ${server.port}
                bad = notanumber
                """);
        Files.writeString(workDir.resolve("kof.dev.config"), """
                profile.only = from-profile
                """);
        Map<String, String> env = Map.of(
                "KOF_CONFIG", workDir.resolve("app.config").toString(),
                "KOF_PROFILE", "dev",
                "KOF_BIG", "9000000000",
                "KOF_TEST_ENV", "literal-env");
        String[] riscv = run(workDir, MAIN_PROGRAM, "NATIVE_RISCV64", "riscv64", env);
        assertEquals("0", riscv[2], "riscv64 exit, stderr: " + riscv[1]);
        assertEquals(EXPECTED, riscv[0], "riscv64 stdout");
        String[] arm = run(workDir, MAIN_PROGRAM, "NATIVE_AARCH64", "aarch64", env);
        assertEquals("0", arm[2], "aarch64 exit, stderr: " + arm[1]);
        assertEquals(EXPECTED, arm[0], "aarch64 stdout");
    }

    @Test
    void defaultFileBothArches(@TempDir Path workDir) throws IOException {
        assumeCross();
        Files.writeString(workDir.resolve("kof.config"), """
                default.key = from-default
                """);
        String src = """
                main() {
                    println(config.str("default.key", "none"))
                }
                """;
        for (String[] a : new String[][]{{"NATIVE_RISCV64", "riscv64"}, {"NATIVE_AARCH64", "aarch64"}}) {
            String[] out = run(workDir, src, a[0], a[1], Map.of());
            assertEquals("0", out[2], a[1] + " exit, stderr: " + out[1]);
            assertEquals("from-default", out[0], a[1] + " stdout");
        }
    }

    @Test
    void requiredMissingPanicsBothArches(@TempDir Path workDir) throws IOException {
        assumeCross();
        String src = """
                main() {
                    println(config.required("must.exist"))
                }
                """;
        for (String[] a : new String[][]{{"NATIVE_RISCV64", "riscv64"}, {"NATIVE_AARCH64", "aarch64"}}) {
            String[] out = run(workDir, src, a[0], a[1], Map.of());
            assertNotEquals("0", out[2], a[1] + " should panic, stdout: " + out[0]);
            assertTrue((out[0] + out[1]).contains("missing required key"),
                    a[1] + " output (esperado panic CONF001): " + out[0] + "|" + out[1]);
        }
    }

    private static void assumeCross() {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain + qemu ausente — pulando (NATIVE002)");
    }
}
