package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `kof.grpc` slice 2 — the gRPC MESSAGE FRAME + the STATUS contract in pure Kof.
 *
 * <p>The reference bytes were produced by the REAL gRPC stack (grpcio 1.85.1):
 * the 5-byte frame is `compressed-flag | big-endian uint32 length | payload`,
 * and the status numbers are the spec's canonical code space (OK=0 ...
 * UNAUTHENTICATED=16).
 */
class GrpcFrameE2ETest implements LibraryInstallSupport {

    @TempDir Path tmp;
    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "grpc"; }
    @Override public List<String> libraryMarkers() { return List.of("Proto.kf", "Frame.kf"); }

    private String run(String body, Target target) throws Exception {
        Path dir = tmp.resolve("f" + Math.abs(body.hashCode()) + target);
        Files.createDirectories(dir);
        copyLibrary(dir);
        Files.writeString(dir.resolve("Main.kf"), "import grpc.Frame\n\nmain() {\n" + body + "\n}\n");
        if (target == Target.SCRIPT) {
            var res = driver.interpret(List.of(dir.resolve("Main.kf")), dir, new String[0]);
            assertEquals(0, res.exitCode(), "script exit: " + res.stderr());
            return res.stdout();
        }
        CompilationResult r = driver.compileSources(List.of(dir.resolve("Main.kf")), dir, target);
        if (!r.success()) fail("compile failed: " + r.diagnostics().getDiagnostics());
        var loader = new java.net.URLClassLoader(new java.net.URL[]{dir.toUri().toURL()});
        var main = loader.loadClass("Default.Main");
        var m = main.getMethod("main", String[].class);
        var out = new ByteArrayOutputStream();
        var oldOut = System.out;
        System.setOut(new java.io.PrintStream(out));
        try { m.invoke(null, (Object) new String[0]); } finally { System.setOut(oldOut); loader.close(); }
        return out.toString();
    }

    @Test
    void theFrameMatchesTheReferenceBytes() throws Exception {
        // the reference: 00 | 0000000a | 0807120467727063 1801 (grpcio's frame)
        String body = """
                var payload = parseHex("08071204677270631801")
                println(hex(frame(payload)))""";
        assertEquals("000000000a08071204677270631801\n", run(body, Target.JVM));
        assertEquals("000000000a08071204677270631801\n", run(body, Target.SCRIPT));
    }

    @Test
    void unframeRecoversThePayloadAndTheNextPosition() throws Exception {
        String body = """
                var framed = parseHex("000000000a08071204677270631801")
                var f = unframe(framed, 0)
                println(hex(f.message))
                println(f.nextPos)""";
        assertEquals("08071204677270631801\n15\n", run(body, Target.JVM));
    }

    @Test
    void theStatusNumbersMatchTheSpec() throws Exception {
        String body = """
                println(code(Status.OK))
                println(code(Status.NOT_FOUND))
                println(code(Status.UNAUTHENTICATED))
                println(statusOf(5))
                println(statusOf(99))""";
        assertEquals("0\n5\n16\nNOT_FOUND\nUNKNOWN\n", run(body, Target.JVM));
        assertEquals("0\n5\n16\nNOT_FOUND\nUNKNOWN\n", run(body, Target.SCRIPT));
    }

    @Test
    void aCompressedFrameIsRefusedByName() throws Exception {
        String body = """
                var framed = parseHex("01000000010807")
                try {
                    var f = unframe(framed, 0)
                    println("NO-REFUSAL")
                } catch (String e) {
                    println(e)
                }""";
        String out = run(body, Target.JVM);
        assertTrue(out.contains("GRPC002"), "o refusal nomeia o gap: " + out);
    }

    @Test
    void aTruncatedFrameIsRefusedByName() throws Exception {
        String body = """
                var framed = parseHex("00000000ff")
                try {
                    var f = unframe(framed, 0)
                    println("NO-REFUSAL")
                } catch (String e) {
                    println(e)
                }""";
        String out = run(body, Target.JVM);
        assertTrue(out.contains("GRPC003"), "o refusal nomeia o truncamento: " + out);
    }

    @Test
    void theTrailerLinesCarryTheStatus() throws Exception {
        String body = """
                println(trailerLines(trailers(Status.OK, "")))
                println(trailerLines(trailers(Status.NOT_FOUND, "no such user")))""";
        String out = run(body, Target.JVM);
        assertTrue(out.contains("grpc-status: 0"), out);
        assertTrue(out.contains("grpc-status: 5"), out);
        assertTrue(out.contains("grpc-message: no such user"), out);
    }
}
