package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofEncodingTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void encodingJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                println(encoding.hexEncode("Hi"))
                println(encoding.hexEncode("") + "|")
                println(encoding.hexDecode("4869"))
                println(encoding.hexEncode("café"))
                println(encoding.hexDecode("636166c3a9"))
            }
            """, "4869\n|\nHi\n636166c3a9\ncafé");
    }

    @Test
    void encodingNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(encoding.hexEncode("Hi") == "4869")
                assert(encoding.hexEncode("") == "")
                assert(encoding.hexDecode("4869") == "Hi")
                assert(encoding.hexEncode("café") == "636166c3a9")
                assert(encoding.hexDecode("636166c3a9") == "café")
                // odd length: último char é nibble alto (baixo=0) => 0x36='6'
                assert(encoding.hexDecode("36") == "6")
                println("ok")
            }
            """, "ok");
    }

    @Test
    void encodingJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(encoding.hexEncode("Hi"))
                println(encoding.hexEncode("") + "|")
                println(encoding.hexDecode("4869"))
                println(encoding.hexEncode("café"))
                println(encoding.hexDecode("636166c3a9"))
            }
            """, "4869\n|\nHi\n636166c3a9\ncafé");
    }

    @Test
    void encodingOddLengthAndInvalid(@TempDir Path tmp) throws Exception {
        // bordas travadas só no JVM (re-empacotamos via hexEncode pra não
        // imprimir NUL): ímpar => último char é nibble ALTO (baixo=0);
        // char inválido => aquele nibble é 0.
        runJvm(tmp, """
            main() {
                println(encoding.hexDecode("4869") == "Hi")
                println(encoding.hexEncode(encoding.hexDecode("486")) == "4860")
                println(encoding.hexEncode(encoding.hexDecode("4g")) == "40")
            }
            """, "true\ntrue\ntrue");
    }

    @Test
    void base64Jvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                println(encoding.base64Encode("Hi"))
                println(encoding.base64Encode("Ma"))
                println(encoding.base64Encode("Man"))
                println(encoding.base64Encode("") + "|")
                println(encoding.base64Decode("TWFu"))
                println(encoding.base64Decode("SGk="))
                println(encoding.base64Encode("café"))
                println(encoding.base64Decode(encoding.base64Encode("café")))
            }
            """, "SGk=\nTWE=\nTWFu\n|\nMan\nHi\nY2Fmw6k=\ncafé");
    }

    @Test
    void base64Native(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(encoding.base64Encode("Hi") == "SGk=")
                assert(encoding.base64Encode("Ma") == "TWE=")
                assert(encoding.base64Encode("Man") == "TWFu")
                assert(encoding.base64Encode("") == "")
                assert(encoding.base64Decode("TWFu") == "Man")
                assert(encoding.base64Decode("SGk=") == "Hi")
                assert(encoding.base64Encode("café") == "Y2Fmw6k=")
                assert(encoding.base64Decode(encoding.base64Encode("café")) == "café")
                // tolerante: ignora inválidos, para em '='
                assert(encoding.base64Decode("SG k=") == "Hi")
                println("ok")
            }
            """, "ok");
    }

    @Test
    void base64Js(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(encoding.base64Encode("Hi"))
                println(encoding.base64Encode("Ma"))
                println(encoding.base64Encode("Man"))
                println(encoding.base64Encode("") + "|")
                println(encoding.base64Decode("TWFu"))
                println(encoding.base64Decode("SGk="))
                println(encoding.base64Encode("café"))
                println(encoding.base64Decode(encoding.base64Encode("café")))
            }
            """, "SGk=\nTWE=\nTWFu\n|\nMan\nHi\nY2Fmw6k=\ncafé");
    }

    @Test
    void base64UrlJvm(@TempDir Path tmp) throws Exception {
        // RFC 4648 §5: alfabeto -_, SEM padding (paridade kofSecB64Url JS e
        // kof_b64url_encode_internal x86). Decode tolerante aceita padding e
        // os dois alfabetos (pré-substitui -_/=>+/). Vetores Python-derivados.
        runJvm(tmp, """
            main() {
                println(encoding.base64UrlEncode("Hi"))
                println(encoding.base64UrlEncode("fb&O->f"))
                println(encoding.base64UrlEncode("café"))
                println(encoding.base64UrlDecode("SGk"))
                println(encoding.base64UrlDecode("ZmImTy0-Zg"))
                println(encoding.base64UrlDecode("SGk="))
                println(encoding.base64UrlDecode("Y2Fmw6k"))
            }
            """, "SGk\nZmImTy0-Zg\nY2Fmw6k\nHi\nfb&O->f\nHi\ncaf\u00e9");
    }

    @Test
    void base64UrlNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(encoding.base64UrlEncode("Hi") == "SGk")
                assert(encoding.base64UrlEncode("fb&O->f") == "ZmImTy0-Zg")
                assert(encoding.base64UrlEncode("café") == "Y2Fmw6k")
                assert(encoding.base64UrlDecode("SGk") == "Hi")
                assert(encoding.base64UrlDecode("ZmImTy0-Zg") == "fb&O->f")
                assert(encoding.base64UrlDecode("SGk=") == "Hi")
                assert(encoding.base64UrlDecode("Y2Fmw6k") == "caf\u00e9")
                println("ok")
            }
            """, "ok");
    }

    @Test
    void base64UrlJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(encoding.base64UrlEncode("Hi"))
                println(encoding.base64UrlEncode("fb&O->f"))
                println(encoding.base64UrlEncode("café"))
                println(encoding.base64UrlDecode("SGk"))
                println(encoding.base64UrlDecode("ZmImTy0-Zg"))
                println(encoding.base64UrlDecode("SGk="))
                println(encoding.base64UrlDecode("Y2Fmw6k"))
            }
            """, "SGk\nZmImTy0-Zg\nY2Fmw6k\nHi\nfb&O->f\nHi\ncaf\u00e9");
    }

    @Test
    void urlEncodeDecodeJvm(@TempDir Path tmp) throws Exception {
        // RFC 3986: unreserved [A-Za-z0-9-_.~] preservado; espaço => %20;
        // hex MAIÚSCULO; decode aceita %xx minúsculo, '%' sem 2 dígitos => literal.
        runJvm(tmp, """
            main() {
                println(encoding.urlEncode("a b"))
                println(encoding.urlEncode("caf\u00e9"))
                println(encoding.urlEncode("~-_."))
                println(encoding.urlEncode("!*'()"))
                println(encoding.urlDecode("a%20b%21"))
                println(encoding.urlDecode("caf%C3%A9"))
                println(encoding.urlDecode("caf%c3%a9"))
                println(encoding.urlDecode("%zz"))
                println(encoding.urlDecode("%4"))
                println(encoding.urlDecode(encoding.urlEncode("ol\u00e1 mundo!")))
            }
            """, "a%20b\ncaf%C3%A9\n~-_.\n%21%2A%27%28%29\na b!\ncaf\u00e9\ncaf\u00e9\n%zz\n%4\nol\u00e1 mundo!");
    }

    @Test
    void urlEncodeDecodeNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(encoding.urlEncode("a b") == "a%20b")
                assert(encoding.urlEncode("caf\u00e9") == "caf%C3%A9")
                assert(encoding.urlEncode("~-_.") == "~-_.")
                assert(encoding.urlEncode("!*'()") == "%21%2A%27%28%29")
                assert(encoding.urlDecode("a%20b%21") == "a b!")
                assert(encoding.urlDecode("caf%C3%A9") == "caf\u00e9")
                assert(encoding.urlDecode("caf%c3%a9") == "caf\u00e9")
                assert(encoding.urlDecode("%zz") == "%zz")
                assert(encoding.urlDecode("%4") == "%4")
                assert(encoding.urlDecode(encoding.urlEncode("ol\u00e1 mundo!")) == "ol\u00e1 mundo!")
                println("ok")
            }
            """, "ok");
    }

    @Test
    void urlEncodeDecodeJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(encoding.urlEncode("a b"))
                println(encoding.urlEncode("caf\u00e9"))
                println(encoding.urlEncode("~-_."))
                println(encoding.urlEncode("!*'()"))
                println(encoding.urlDecode("a%20b%21"))
                println(encoding.urlDecode("caf%C3%A9"))
                println(encoding.urlDecode("caf%c3%a9"))
                println(encoding.urlDecode("%zz"))
                println(encoding.urlDecode("%4"))
                println(encoding.urlDecode(encoding.urlEncode("ol\u00e1 mundo!")))
            }
            """, "a%20b\ncaf%C3%A9\n~-_.\n%21%2A%27%28%29\na b!\ncaf\u00e9\ncaf\u00e9\n%zz\n%4\nol\u00e1 mundo!");
    }

    @Test
    void base64RunsOnCrossArch(@TempDir Path tmp) throws Exception {
        // ENC002 FECHADO (09/09): base64/base64Url portados p/ riscv B23 +
        // aarch64 translator. Spec tolerante única (= para, inválido ignora,
        // resto 2/3→1/2 bytes; url aceita os 2 alfabetos). Oracle Python.
        String src = """
            main() {
                println(encoding.base64Encode("Man"))
                println(encoding.base64Decode("Y2Fmw6k="))
                println(encoding.base64UrlEncode("fb&O->f"))
                println(encoding.base64UrlDecode("ZmImTy0-Zg"))
                println(encoding.base64Decode("!!!!TWFu!!!!"))
                println(encoding.base64Decode("QQ"))
            }
            """;
        String expected = "TWFu\ncafé\nZmImTy0-Zg\nfb&O->f\nMan\nA";
        assumeToolchain("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        runQemuE(tmp, Target.NATIVE_RISCV64, "qemu-riscv64", src, expected);
        assumeToolchain("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        runQemuE(tmp, Target.NATIVE_AARCH64, "qemu-aarch64", src, expected);
    }

    private static void assumeToolchain(String... tools) {
        for (String c : tools) {
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) {
                    org.junit.jupiter.api.Assumptions.assumeTrue(false, "toolchain ausente: " + c);
                }
            } catch (Exception e) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "toolchain ausente: " + c);
            }
        }
    }

    private void runQemuE(Path tempDir, Target target, String qemu, String source,
                          String expected) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, target);
        assertTrue(result.success(), target + " compile failed: "
                + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = new ProcessBuilder(qemu, bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).trim();
        int ec = p.waitFor();
        assertEquals(0, ec, target + " runtime (qemu) exit " + ec + ", out: " + output);
        assertEquals(expected, output, target + " output");
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
