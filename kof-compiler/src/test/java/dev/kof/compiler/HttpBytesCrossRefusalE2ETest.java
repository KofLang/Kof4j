package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * known-bugs §1228 (issue #1184, the cross slice): the `Response.bytes()` face
 * landed on the JVM — on the NATIVE/JS targets the `body()` is a DECODED String
 * and re-encoding it does NOT recover the bytes (measured: the 0x89 of a PNG
 * becomes U+FFFD, and `getBytes()` re-encodes it as 0xEF — an 8-byte body came
 * back as 11 bytes). The honest contract on those targets is the NAMED
 * refusal `HTTP003`, never wrong bytes.
 */
class HttpBytesCrossRefusalE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult build(String source, Target target) throws Exception {
        Path main = tmp.resolve("Main.kf");
        Files.writeString(main, source);
        return driver.compileSources(
                java.util.List.of(main), tmp.resolve("out-" + target), target,
                tmp.toAbsolutePath().normalize());
    }

    private static String diags(CompilationResult r) {
        return r.diagnostics().getDiagnostics().toString();
    }

    private static final String SRC = """
            import kof.http

            main() {
                var resposta = http.send("GET", "http://127.0.0.1:1/x", "", "")
                var bs = resposta.bytes()
                println(bs.length)
            }
            """;

    @Test
    void bytesCompiledOnNativeX86() throws Exception {
        // §1229: o NATIVO x86 TEM a face agora (o simbolo kof_http_response_bytes
        // — o modelo kof_buffer_bytes, os bytes crus da KofString); o rastreamento
        // do link é a própria compilação (o binário nasce).
        CompilationResult r = build(SRC, Target.NATIVE);
        assertTrue(r.success(),
                "§1229: the NATIVE x86 bytes face compiles (the symbol exists): " + diags(r));
    }

    @Test
    void bytesRefusedOnJsWithHttp003() throws Exception {
        CompilationResult r = build(SRC, Target.JS);
        assertFalse(r.success(), "Response.bytes() on JS must be refused: " + diags(r));
        assertTrue(diags(r).contains("HTTP003"), diags(r));
    }

    @Test
    void bytesAvailableOnJvm() throws Exception {
        CompilationResult r = build(SRC, Target.JVM);
        assertTrue(r.success(),
                "the JVM face is the landed slice (the embedded-server proof is "
                        + "HttpBytesResponseE2ETest): " + diags(r));
    }
}
