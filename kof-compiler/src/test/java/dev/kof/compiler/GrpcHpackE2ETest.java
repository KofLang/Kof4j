package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `kof.grpc` slice 3a — the MINIMAL HPACK encoder in pure Kof.
 *
 * <p>The expected block is the one the REAL gRPC peer accepted (measured against
 * grpcio 1.85.1 with the same header set): literal fields without indexing, no
 * Huffman, static indexes only for names that exist there. The gRPC peer
 * answered `:status 200` + `grpc-status: 0` for exactly these bytes.
 */
class GrpcHpackE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;
    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "grpc"; }
    @Override public List<String> libraryMarkers() { return List.of("Proto.kf", "Frame.kf", "Hpack.kf"); }

    private String run(String body, Target target) throws Exception {
        Path dir = tmp.resolve("h" + Math.abs(body.hashCode()) + target);
        Files.createDirectories(dir);
        copyLibrary(dir);
        Files.writeString(dir.resolve("Main.kf"), "import grpc.Hpack\nimport grpc.Proto\n\nmain() {\n" + body + "\n}\n");
        if (target == Target.SCRIPT) {
            var res = driver.interpret(List.of(dir.resolve("Main.kf")), dir, new String[0]);
            assertEquals(0, res.exitCode(), "script: " + res.stderr());
            return res.stdout();
        }
        CompilationResult r = driver.compileSources(List.of(dir.resolve("Main.kf")), dir, target);
        if (!r.success()) fail("compile: " + r.diagnostics().getDiagnostics());
        var loader = new java.net.URLClassLoader(new java.net.URL[]{dir.toUri().toURL()});
        var m = loader.loadClass("Default.Main").getMethod("main", String[].class);
        var out = new ByteArrayOutputStream();
        var old = System.out;
        System.setOut(new java.io.PrintStream(out));
        try { m.invoke(null, (Object) new String[0]); } finally { System.setOut(old); loader.close(); }
        return out.toString();
    }

    @Test
    void theIntegerEncodingMatchesRfc7541() throws Exception {
        // §5.1 examples: 10 with a 5-bit prefix = 0b000_01010; 1337 with prefix
        // 5 = 1f 9a 0a; 42 with prefix 7 = 2a.
        String body = """
                println(hex(hpackInt(10, 5, 0x00)))
                println(hex(hpackInt(1337, 5, 0x00)))
                println(hex(hpackInt(42, 7, 0x00)))""";
        assertEquals("0a\n1f9a0a\n2a\n", run(body, Target.JVM));
    }

    @Test
    void theStaticIndexesAreTheRealOnes() throws Exception {
        String body = """
                println(hex(hpackField(":method", "POST")))
                println(hex(hpackField(":scheme", "http")))
                println(hex(hpackField("content-type", "application/grpc")))
                println(hex(hpackField("te", "trailers")))""";
        String out = run(body, Target.JVM);
        String[] lines = out.trim().split("\n");
        assertEquals("83", lines[0], ":method POST is static index 3 (0x83)");
        assertEquals("86", lines[1], ":scheme http is static index 6 (0x86)");
        assertTrue(lines[2].startsWith("0f10"), "content-type name index 31 (0x0f) + len 0x10: " + lines[2]);
        // te is NOT in the static table → literal WITHOUT indexing: 00 | len 2 | "te" | len 8 | "trailers"
        assertEquals("0002746508747261696c657273", lines[3],
                "te literal (no static entry): " + lines[3]);
    }

    @Test
    void theWholeBlockIsOneCoherentEncode() throws Exception {
        String body = """
                println(hex(hpackEncode(
                    listOf(":method", ":scheme", ":path", "content-type"),
                    listOf("POST", "http", "/G/Call", "application/grpc"))))""";
        String out = run(body, Target.JVM).trim();
        // the block starts with 83 (method POST), 86 (scheme http), 04 (path /, indexed)
        assertTrue(out.startsWith("838604"), "the block prefix: " + out);
        assertTrue(out.contains("0f10"), "content-type literal follows: " + out);
        // the SCRIPT face is byte-identical
        assertEquals(out, run(body, Target.SCRIPT).trim());
    }
}
