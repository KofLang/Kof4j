package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofUuidTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void uuidV4Jvm(@TempDir Path tmp) throws Exception {
        // v4 válido: 32 hex minúsculos, 8-4-4-4-12, dígito 13 = '4',
        // dígito 17 ∈ {8,9,a,b}. Dois uuids devem diferir (entropia).
        runJvm(tmp, """
            main() {
                var u = uuid.v4()
                assert(u.length == 36)
                assert(u.charAt(8) == 45)
                assert(u.charAt(13) == 45)
                assert(u.charAt(18) == 45)
                assert(u.charAt(23) == 45)
                assert(u.charAt(14) == 52)
                var v = u.charAt(19)
                assert(v == 56 || v == 57 || v == 97 || v == 98)
                var w = uuid.v4()
                assert(u != w)
                println("ok")
            }
            """, "ok");
    }

    @Test
    void uuidV4Native(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                var u = uuid.v4()
                assert(u.length == 36)
                assert(u.charAt(8) == 45)
                assert(u.charAt(13) == 45)
                assert(u.charAt(14) == 52)
                assert(u.charAt(18) == 45)
                var v = u.charAt(19)
                assert(v == 56 || v == 57 || v == 97 || v == 98)
                assert(u.charAt(23) == 45)
                var w = uuid.v4()
                assert(u != w)
                println("ok")
            }
            """, "ok");
    }

    @Test
    void uuidV4Js(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                var u = uuid.v4()
                assert(u.length == 36)
                assert(u.charAt(14) == 52)
                var v = u.charAt(19)
                assert(v == 56 || v == 57 || v == 97 || v == 98)
                println("ok")
            }
            """, "ok");
    }

    @Test
    void uuidV7Jvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                var u = uuid.v7()
                assert(u.length == 36)
                assert(u.charAt(8) == 45)
                assert(u.charAt(13) == 45)
                assert(u.charAt(18) == 45)
                assert(u.charAt(23) == 45)
                assert(u.charAt(14) == 55)
                var v = u.charAt(19)
                assert(v == 56 || v == 57 || v == 97 || v == 98)
                assert(uuid.isUuid(u))
                var w = uuid.v7()
                assert(u != w)
                println("ok")
            }
            """, "ok");
    }

    @Test
    void uuidV7Native(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                var u = uuid.v7()
                assert(u.length == 36)
                assert(u.charAt(8) == 45)
                assert(u.charAt(13) == 45)
                assert(u.charAt(14) == 55)
                assert(u.charAt(18) == 45)
                var v = u.charAt(19)
                assert(v == 56 || v == 57 || v == 97 || v == 98)
                assert(u.charAt(23) == 45)
                assert(uuid.isUuid(u))
                var w = uuid.v7()
                assert(u != w)
                println("ok")
            }
            """, "ok");
    }

    @Test
    void uuidV7Js(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                var u = uuid.v7()
                assert(u.length == 36)
                assert(u.charAt(8) == 45)
                assert(u.charAt(13) == 45)
                assert(u.charAt(14) == 55)
                assert(u.charAt(18) == 45)
                var v = u.charAt(19)
                assert(v == 56 || v == 57 || v == 97 || v == 98)
                assert(u.charAt(23) == 45)
                assert(uuid.isUuid(u))
                var w = uuid.v7()
                assert(u != w)
                println("ok")
            }
            """, "ok");
    }

    @Test
    void uuidV7MonotonicOrderJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                var u1 = uuid.v7()
                var u2 = uuid.v7()
                assert(u2.charAt(0) >= u1.charAt(0))
                println("ok")
            }
            """, "ok");
    }

    @Test
    void uuidV4CrossArch(@TempDir Path tmp) throws Exception {
        // SECN000 FECHADO (09/09): getrandom(2) via ecall (syscall 278, probe
        // riscv64+aarch64 no qemu) na fatia riscv B25 + aarch translator.
        // Shape: 36, traços, char[14]='4', char[19] ∈ {8,9,a,b} (máscara de
        // variant — paridade JVM/JS), unicidade. Não-determinístico: assert-only.
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", """
            main() {
                var u = uuid.v4()
                assert(u.length == 36)
                assert(u.charAt(8) == 45)
                assert(u.charAt(13) == 45)
                assert(u.charAt(18) == 45)
                assert(u.charAt(23) == 45)
                assert(u.charAt(14) == 52)
                var v = u.charAt(19)
                assert(v == 56 || v == 57 || v == 97 || v == 98)
                var w = uuid.v4()
                assert(u != w)
            }
            """);
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", """
            main() {
                var u = uuid.v4()
                assert(u.length == 36)
                assert(u.charAt(8) == 45)
                assert(u.charAt(13) == 45)
                assert(u.charAt(18) == 45)
                assert(u.charAt(23) == 45)
                assert(u.charAt(14) == 52)
                var v = u.charAt(19)
                assert(v == 56 || v == 57 || v == 97 || v == 98)
                var w = uuid.v4()
                assert(u != w)
            }
            """);
    }

    @Test
    void isUuidShapeJvmJsNative(@TempDir Path tmp) throws Exception {
        // isUuid = predicado de forma 8-4-4-4-12 (hex min ou maiúsculo;
        // traços em 8/13/18/23; version/variant NÃO verificadas — paridade
        // travada na matriz stduuidform). Últimas linhas: v4() e v7() do próprio target.
        String src = """
            main() {
                println(uuid.isUuid("550e8400-e29b-41d4-a716-446655440000"))
                println(uuid.isUuid("550E8400-E29B-41D4-A716-446655440000"))
                println(uuid.isUuid("550e8400e29b41d4a716446655440000"))
                println(uuid.isUuid("550e8400-e29b-41d4-a716-44665544000"))
                println(uuid.isUuid("550e8400-e29b-41d4-a716-44665544000g"))
                println(uuid.isUuid(""))
                println(uuid.isUuid(uuid.v4()))
                println(uuid.isUuid(uuid.v7()))
            }
            """;
        String gold = "true\ntrue\nfalse\nfalse\nfalse\nfalse\ntrue\ntrue";
        runJvm(tmp, src, gold);
        runJs(tmp, src, gold);
        runNative(tmp, src, gold);
    }

    @Test
    void isUuidJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(uuid.isUuid("550e8400-e29b-41d4-a716-446655440000"))
                println(uuid.isUuid("550e8400e29b41d4a716446655440000"))
                println(uuid.isUuid(""))
                println(uuid.isUuid(uuid.v4()))
            }
            """, "true\nfalse\nfalse\ntrue");
    }

    @Test
    void isUuidNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(uuid.isUuid("550e8400-e29b-41d4-a716-446655440000"))
                assert(uuid.isUuid("550E8400-E29B-41D4-A716-446655440000"))
                assert(!uuid.isUuid("550e8400e29b41d4a716446655440000"))
                assert(!uuid.isUuid("550e8400-e29b-41d4-a716-44665544000"))
                assert(!uuid.isUuid("550e8400-e29b-41d4-a716-44665544000g"))
                assert(!uuid.isUuid(""))
                assert(uuid.isUuid(uuid.v4()))
                println("ok")
            }
            """, "ok");
    }

    @Test
    void isUuidCrossArch(@TempDir Path tmp) throws Exception {
        // assert-only (bug 59 no println cross). LIÇÕES travadas: (1) wrapper
        // riscv salva ra antes de call; (2) upper-bound hex = EXCLUSIVO (58/71/103).
        String src = """
            main() {
                assert(uuid.isUuid("550e8400-e29b-41d4-a716-446655440000"))
                assert(uuid.isUuid("550E8400-E29B-41D4-A716-446655440000"))
                assert(!uuid.isUuid("550e8400e29b41d4a716446655440000"))
                assert(!uuid.isUuid("550e8400-e29b-41d4-a716-44665544000"))
                assert(!uuid.isUuid("550e8400-e29b-41d4-a716-44665544000g"))
                assert(!uuid.isUuid(""))
                assert(uuid.isUuid(uuid.v4()))
            }
            """;
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", src);
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", src);
    }

    @Test
    void uuidV7GatedOnCrossArch(@TempDir Path tmp) throws Exception {
        // UUID002 (R6 — nunca silencioso): uuid.v7 tem JVM/JS/x86;
        // riscv64/aarch64 aguardam implementação nativa.
        String src = """
            main() {
                var u = uuid.v7()
            }
            """;
        Path gateSrc = tmp.resolve("UuidV7Gate-" + System.nanoTime() + ".kf");
        Files.writeString(gateSrc, src);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(
                    gateSrc, tmp.resolve("gate-v7-" + t + "-" + System.nanoTime()), t);
            assertFalse(r.success(), t + " deve rejeitar uuid.v7 (UUID002)");
            boolean has = r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> "UUID002".equals(d.code())
                            || (d.message() != null && d.message().contains("UUID002")));
            assertTrue(has, t + " deve reportar UUID002, veio: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    private static void assumeToolchain(String... tools) {
        for (String c : tools) {
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                String o = new String(p.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || o.isEmpty()) {
                    org.junit.jupiter.api.Assumptions.assumeTrue(false, "toolchain ausente: " + c);
                }
            } catch (Exception e) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "toolchain ausente: " + c);
            }
        }
    }

    private void runQemu(Path tempDir, Target target, String qemu, String source) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path out = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult r = driver.compile(file, out, target);
        assertTrue(r.success(), target + " compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(qemu, out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).trim();
        int ec = p.waitFor();
        assertEquals(0, ec, target + " qemu exit " + ec + ", out: " + o);
    }

    private String runJvm(Path tempDir, String source, String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code, output: " + output);
        assertEquals(expected, output, "JVM output");
        return output;
    }

    private String runNative(Path tempDir, String source, String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native exit code, output: " + output);
        assertEquals(expected, output, "Native output");
        return output;
    }

    private String runJs(Path tempDir, String source, String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
             java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                    java.io.InputStream.nullInputStream(), err);
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output
                    + " err: " + err.toString(java.nio.charset.StandardCharsets.UTF_8).trim());
            assertEquals(expected, output, "JS output");
            return output;
        }
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        return Files.walk(dir).flatMap(p -> java.util.stream.Stream.of(p))
                .filter(p -> p.toString().endsWith(".mjs"))
                .findFirst().orElseThrow(() -> new java.io.IOException("no .mjs in " + dir));
    }
}
