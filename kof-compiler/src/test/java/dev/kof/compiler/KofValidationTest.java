package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofValidationTest {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void validationJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                assert(validation.required("hello"))
                assert(!validation.required(""))
                assert(!validation.notBlank("  "))
                assert(validation.notBlank(" a "))
                assert(validation.minLength("abc", 2))
                assert(!validation.minLength("a", 2))
                assert(validation.maxLength("abc", 5))
                assert(!validation.maxLength("abc", 2))
                assert(validation.lengthBetween("abcd", 2, 5))
                assert(!validation.lengthBetween("a", 2, 5))
                assert(validation.isEmail("test@example.com"))
                assert(!validation.isEmail("bad-email"))
                assert(validation.isUrl("https://example.com"))
                assert(!validation.isUrl("ftp://x"))
                assert(validation.matches("hello", "ell"))
                assert(!validation.matches("hello", "xyz"))
                assert(validation.isInt("123"))
                assert(!validation.isInt("12a"))
                assert(validation.inRange(5, 1, 10))
                assert(!validation.inRange(15, 1, 10))
                assert(validation.min(5, 3))
                assert(!validation.min(2, 3))
                assert(validation.max(5, 10))
                assert(!validation.max(15, 10))
                println("ok")
            }
            """, "ok");
    }

    @Test
    void validationNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(validation.required("hello"))
                assert(!validation.required(""))
                assert(validation.isEmail("a@b.c"))
                assert(!validation.isEmail("a@b"))
                assert(validation.inRange(5, 1, 10))
                println("ok")
            }
            """, "ok");
    }

    @Test
    void validationJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(validation.required("hi"))
                println(validation.isUrl("https://kof.dev"))
                println(validation.isEmail("a@b.c"))
                println("done")
            }
            """, "true\ntrue\ntrue\ndone");
    }

    @Test
    void validationBrJvm(@TempDir Path tmp) throws Exception {
        // S5 — CPF/CNPJ/CEP/PIS (vetores derivados em Python, pesos mod-11).
        runJvm(tmp, """
            main() {
                println(validation.isCpf("529.982.247-25"))
                println(validation.isCpf("52996581504"))
                println(validation.isCpf("111.111.111-11"))
                println(validation.isCpf("529.982.247-24"))
                println(validation.isCpf("00000000000"))
                println(validation.isCpf(""))
                println(validation.isCnpj("34.546.401/0001-63"))
                println(validation.isCnpj("11.222.333/0001-82"))
                println(validation.isCep("01310-100"))
                println(validation.isCep("0131010"))
                println(validation.isPis("123.4567.890-0"))
                println(validation.isPis("12345678901"))
            }
            """, "true\ntrue\nfalse\nfalse\nfalse\nfalse\ntrue\nfalse\ntrue\nfalse\ntrue\nfalse");
    }

    @Test
    void validationBrNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(validation.isCpf("529.982.247-25"))
                assert(validation.isCpf("52996581504"))
                assert(!validation.isCpf("111.111.111-11"))
                assert(!validation.isCpf("529.982.247-24"))
                assert(!validation.isCpf("00000000000"))
                assert(!validation.isCpf(""))
                assert(validation.isCnpj("34.546.401/0001-63"))
                assert(validation.isCnpj("11.222.333/0001-81"))
                assert(!validation.isCnpj("11.222.333/0001-82"))
                assert(validation.isCep("01310-100"))
                assert(!validation.isCep("0131010"))
                assert(validation.isPis("123.4567.890-0"))
                assert(!validation.isPis("12345678901"))
                println("ok")
            }
            """, "ok");
    }

    @Test
    void validationBrJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(validation.isCpf("529.982.247-25"))
                println(validation.isCpf("111.111.111-11"))
                println(validation.isCnpj("34.546.401/0001-63"))
                println(validation.isCep("01310-100"))
                println(validation.isCep("0131010"))
                println(validation.isPis("123.4567.890-0"))
            }
            """, "true\nfalse\ntrue\ntrue\nfalse\ntrue");
    }

    // S5 cross-arch: é a PRIMEIRA verificação que EXECUTA código runtime
    // riscv/aarch — usa só assert (sem println: bug 59 é no link do
    // System.out), padrão descoberto ao corrigir o bug do .rodata herdado.
    @Test
    void validationBrNativeRiscv(@TempDir Path tmp) throws Exception {
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemu(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", """
            main() {
                assert(validation.isCpf("529.982.247-25"))
                assert(validation.isCpf("52996581504"))
                assert(!validation.isCpf("111.111.111-11"))
                assert(!validation.isCpf("529.982.247-24"))
                assert(!validation.isCpf("00000000000"))
                assert(!validation.isCpf(""))
                assert(validation.isCnpj("34.546.401/0001-63"))
                assert(validation.isCnpj("11.222.333/0001-81"))
                assert(!validation.isCnpj("11.222.333/0001-82"))
                assert(validation.isCep("01310-100"))
                assert(!validation.isCep("0131010"))
                assert(validation.isPis("123.4567.890-0"))
                assert(!validation.isPis("12345678901"))
            }
            """);
    }

    @Test
    void validationBrNativeAarch64(@TempDir Path tmp) throws Exception {
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemu(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", """
            main() {
                assert(validation.isCpf("529.982.247-25"))
                assert(!validation.isCpf("111.111.111-11"))
                assert(validation.isCnpj("11.222.333/0001-81"))
                assert(!validation.isCnpj("11.222.333/0001-82"))
                assert(validation.isCep("01310-100"))
                assert(!validation.isCep("0131010"))
                assert(validation.isPis("123.4567.890-0"))
                assert(!validation.isPis("12345678901"))
            }
            """);
    }

    private static void assumeToolchain(String... bins) {
        for (String b : bins) {
            try {
                Process p = new ProcessBuilder(b, "--version")
                        .redirectOutput(new java.io.File("/dev/null"))
                        .redirectErrorStream(true).start();
                org.junit.jupiter.api.Assumptions.assumeTrue(p.waitFor() == 0,
                        b + " ausente — pulando (NATIVE002)");
            } catch (Exception e) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, b + " ausente — pulando");
            }
        }
    }

    private void runQemu(Path tempDir, Target target, String qemu, String source) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, target);
        assertTrue(result.success(), target + " compile failed: "
                + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = new ProcessBuilder(qemu, bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        int ec = p.waitFor();
        assertEquals(0, ec, target + " runtime (qemu) exit " + ec + ", out: " + output);
    }

    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runNative(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runJs(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "JS output");
            return output;
        }
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new java.io.IOException("no .mjs in " + dir));
        }
    }
}
